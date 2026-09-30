package com.cosmicbreach.sandbox;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.CombatAction;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.entity.gyre.GyreKnights;
import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.entity.stalker.Stalkers;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.world.AetheriaSpots;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.DeepSpans;
import com.cosmicbreach.world.gen.DriftBelts;
import com.cosmicbreach.world.gen.ReachIslands;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The stress scene of GDD 9.4, built by one command ({@code /cosmicbreach stress build}): mobs spread across the three
 * layers, seven fake players fighting (two in the Reach, two in the Drift, three with the caller), and whatever the
 * caller set up around it (the scenario adds the Heliarch in phase 2, a Solar Flare in the Reach and a Meteor Shower
 * in the Drift). The fake players are added to the level as players, so the chunks round them load and tick and the
 * mobs and the boss see them; each runs a full player tick (every mod handler that watches players) and strikes
 * the nearest foe with Meridian through the combat engine every few ticks. They cannot be hurt (NeoForge fake players
 * never take damage).
 *
 * <p>While a scene stands, the server's tick times, the time every Cosmic Breach entity spends ticking (by type) and
 * the fake players' ticks are measured ({@link Stats}); {@code /cosmicbreach stress report} prints them.
 * {@code /cosmicbreach stress load <n>} spawns {@code n} mobs round the caller (the AI budget: 150 mobs), and
 * {@code /cosmicbreach stress chunks} times fresh chunk generation and each layer's terrain. Dev and test use only.
 */
public final class StressScene {
    /** Mobs per layer group, 60 in all. */
    static final int REACH_SHARDLINGS = 24;
    static final int REACH_STAGS = 6;
    static final int DRIFT_KNIGHTS = 10;
    static final int DRIFT_MANTAS = 8;
    static final int DEEP_STALKERS = 12;
    /** The spread load's annulus: loaded mobs beyond their sight of the player (32 to 40 blocks), within view distance. */
    public static final double SPREAD_MIN = 48.0;
    public static final double SPREAD_MAX = 128.0;
    /** A fighter strikes this often (Meridian's light chain runs about this long a blow). */
    static final int STRIKE_EVERY = 12;
    public static final String TAG = "cosmicbreach_stress";

    private static final List<Fighter> FIGHTERS = new ArrayList<>();
    private static final List<UUID> MOBS = new ArrayList<>();
    private static volatile Stats stats = new Stats();
    private static volatile boolean standing;
    private static volatile ChunkReport chunkReport;

    private record Fighter(StressFighter player, Vec3 home) {}

    /** A NeoForge fake player that runs its own player tick each server tick. */
    static final class StressFighter extends FakePlayer {
        StressFighter(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }
    }

    private StressScene() {
    }

    public static void register(IEventBus game) {
        game.addListener(RegisterCommandsEvent.class, e -> commands(e.getDispatcher()));
        game.addListener(ServerTickEvent.Pre.class, e -> onTickStart(e.getServer()));
        game.addListener(ServerTickEvent.Post.class, e -> onTickEnd(e.getServer()));
        game.addListener(EntityTickEvent.Pre.class, StressScene::onEntityTickStart);
        game.addListener(EntityTickEvent.Post.class, StressScene::onEntityTickEnd);
        game.addListener(ServerStoppingEvent.class, e -> stop(e.getServer()));
    }

    private static void commands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cosmicbreach").requires(s -> s.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("stress")
                        .then(Commands.literal("build").executes(c -> {
                            int n = build(c.getSource().getPlayerOrException());
                            c.getSource().sendSuccess(() -> Component.literal("stress scene: " + n + " mobs, " + FIGHTERS.size() + " fake players"), false);
                            return n;
                        }))
                        .then(Commands.literal("load").then(Commands.argument("mobs", IntegerArgumentType.integer(1, 400)).executes(c -> {
                            int n = load(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "mobs"), false);
                            c.getSource().sendSuccess(() -> Component.literal("stress load: " + n + " mobs"), false);
                            return n;
                        }).then(Commands.literal("spread").executes(c -> {
                            int n = load(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "mobs"), true);
                            c.getSource().sendSuccess(() -> Component.literal("stress load: " + n + " mobs, spread"), false);
                            return n;
                        }))))
                        .then(Commands.literal("measure").executes(c -> {
                            stats = new Stats();
                            stats.on = true;
                            c.getSource().sendSuccess(() -> Component.literal("stress: measuring"), false);
                            return 1;
                        }))
                        .then(Commands.literal("report").executes(c -> {
                            String r = stats.report();
                            c.getSource().sendSuccess(() -> Component.literal(r), false);
                            return 1;
                        }))
                        .then(Commands.literal("chunks").executes(c -> {
                            chunks(c.getSource().getLevel());
                            c.getSource().sendSuccess(() -> Component.literal("stress chunks: " + chunkReport), false);
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(c -> {
                            int n = stop(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.literal("stress scene removed (" + n + ")"), false);
                            return n;
                        }))));
    }

    // ------------------------------------------------------------------ the scene

    /** Builds the scene round {@code caller} (in Aetheria). Returns the mobs spawned. */
    public static int build(ServerPlayer caller) {
        stop(caller.server);
        ServerLevel level = caller.serverLevel();
        if (!AetheriaWorld.is(level)) {
            return 0;
        }
        BlockPos near = caller.blockPosition();
        Optional<AetheriaSpots.Spot> reach = AetheriaSpots.safeSpot(level, AetheriaSpots.Kind.SPIRES, near);
        Optional<AetheriaSpots.Spot> drift = AetheriaSpots.safeSpot(level, AetheriaSpots.Kind.DRIFT, near);
        int mobs = 0;
        if (reach.isPresent()) {
            Vec3 at = Vec3.atBottomCenterOf(reach.get().feet());
            addFighters(level, at, 2);
            load(level, at);
            for (int p = 0; p < REACH_SHARDLINGS / 4; p++) {
                Vec3 packAt = around(at, 6 + p * 2, p * 60);
                List<Shardling> pack = Sandbox.spawnPack(level, packAt, at, 4, 1.0f);
                pack.forEach(s -> track(s));
                mobs += pack.size();
            }
            mobs += spawn(level, Mounts.LUMEN_STAG.get(), at, REACH_STAGS, 14);
        }
        if (drift.isPresent()) {
            Vec3 at = Vec3.atBottomCenterOf(drift.get().feet());
            addFighters(level, at, 2);
            load(level, at);
            mobs += spawn(level, GyreKnights.GYRE_KNIGHT.get(), at.add(0, 6, 0), DRIFT_KNIGHTS, 10);
            mobs += spawn(level, Mounts.DRIFT_MANTA.get(), at.add(0, 4, 0), DRIFT_MANTAS, 12);
        }
        // the Deep's group fights here, beside the caller (the Deep's own ground near the Sanctum is past the
        // caller's simulation distance, where nothing would tick)
        Vec3 here = caller.position();
        addFighters(level, here, 3);
        mobs += spawn(level, Stalkers.HOLLOW_STALKER.get(), here, DEEP_STALKERS, 12);
        standing = true;
        CosmicBreach.LOGGER.debug("[cosmicbreach] stress scene: {} mobs, {} fake players (Reach {}, Drift {}, Deep {})", mobs, FIGHTERS.size(),
                reach.map(s -> s.feet().toShortString()).orElse("none"), drift.map(s -> s.feet().toShortString()).orElse("none"),
                caller.blockPosition().toShortString());
        return mobs;
    }

    /**
     * {@code n} Aetheria mobs round {@code caller} (Shardling packs, Gyre Knights, Stalkers, stags in turn): the AI
     * budget. Close: all within 26 blocks, the worst case (a fight). Spread: on ground anywhere from 48 to 128 blocks
     * away, loaded but out of sight, as most loaded mobs are (GDD 9.4's "150 loaded Aetheria mobs").
     */
    public static int load(ServerPlayer caller, int n, boolean spread) {
        ServerLevel level = caller.serverLevel();
        Vec3 at = caller.position();
        int spawned = 0;
        EntityType<?>[] kinds = {null, GyreKnights.GYRE_KNIGHT.get(), Stalkers.HOLLOW_STALKER.get(), Mounts.LUMEN_STAG.get()};
        for (int i = 0; spawned < n && i < n * 20; i++) {
            EntityType<?> kind = kinds[i % kinds.length];
            Vec3 p;
            if (spread) {
                // uniform over the annulus's area, beyond their sight of the player, on ground near the player's height
                double r = Math.sqrt(SPREAD_MIN * SPREAD_MIN + level.random.nextDouble() * (SPREAD_MAX * SPREAD_MAX - SPREAD_MIN * SPREAD_MIN));
                Vec3 q = around(at, r, level.random.nextDouble() * 360.0);
                Optional<BlockPos> ground = AetheriaSpots.settle(level, BlockPos.containing(q), 24, 48);
                if (ground.isEmpty()) {
                    continue;
                }
                p = Vec3.atBottomCenterOf(ground.get());
            } else {
                p = around(at, 8 + (i % 7) * 3, i * 37);
            }
            if (kind == null && spread) {
                // loaded Shardlings wait where they spawned, as a natural pack does, until someone comes near
                spawned += spawn(level, com.cosmicbreach.registry.ModEntities.SHARDLING.get(), p, Math.min(4, n - spawned), 3);
            } else if (kind == null) {
                // a pack that hunts the caller: the fight
                List<Shardling> pack = Sandbox.spawnPack(level, p, at, Math.min(4, n - spawned), 1.0f);
                pack.forEach(StressScene::track);
                spawned += pack.size();
            } else {
                spawned += spawn(level, kind, p, 1, 0);
            }
        }
        standing = true;
        return spawned;
    }

    private static void addFighters(ServerLevel level, Vec3 at, int n) {
        for (int i = 0; i < n; i++) {
            int index = FIGHTERS.size();
            StressFighter f = new StressFighter(level, new GameProfile(UUID.nameUUIDFromBytes(("stress-" + index).getBytes()), "Stress" + index));
            Vec3 p = around(at, 2.5, index * 110);
            f.moveTo(p.x, p.y, p.z, 0f, 0f);
            f.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MERIDIAN.get()));
            level.addNewPlayer(f);
            FIGHTERS.add(new Fighter(f, p));
        }
    }

    private static void load(ServerLevel level, Vec3 at) {
        ChunkPos c = new ChunkPos(BlockPos.containing(at));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(c.x + dx, c.z + dz);
            }
        }
    }

    private static int spawn(ServerLevel level, EntityType<?> type, Vec3 at, int n, double radius) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            Vec3 p = n == 1 ? at : around(at, radius * (0.5 + 0.5 * (i % 3) / 2.0), i * 360.0 / n);
            BlockPos pos = AetheriaSpots.settle(level, BlockPos.containing(p), 6, 12).orElse(BlockPos.containing(p));
            Entity e = type.create(level);
            if (e == null) {
                continue;
            }
            e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            if (e instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
            }
            track(e);
            if (level.addFreshEntity(e)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static void track(Entity e) {
        e.addTag(TAG);
        MOBS.add(e.getUUID());
    }

    private static Vec3 around(Vec3 at, double r, double degrees) {
        double a = Math.toRadians(degrees);
        return at.add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    /** Removes the fake players and every mob the scene spawned. Returns how many things went. */
    public static int stop(MinecraftServer server) {
        int n = 0;
        for (Fighter f : FIGHTERS) {
            if (f.player().level() instanceof ServerLevel level) {
                level.removePlayerImmediately(f.player(), Entity.RemovalReason.DISCARDED);
                n++;
            }
        }
        FIGHTERS.clear();
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> ours = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e.getTags().contains(TAG)) {
                    ours.add(e); // collected first: discarding while walking the lookup skips entries
                }
            }
            for (Entity e : ours) {
                e.discard();
                n++;
            }
        }
        MOBS.clear();
        standing = false;
        return n;
    }

    public static boolean standing() {
        return standing;
    }

    public static int fighters() {
        return FIGHTERS.size();
    }

    public static Stats stats() {
        return stats;
    }

    // ------------------------------------------------------------------ the fighters fight

    private static long tickStart;
    private static final Map<Entity, Long> ENTITY_START = new HashMap<>();

    private static void onTickStart(MinecraftServer server) {
        tickStart = System.nanoTime();
        if (FIGHTERS.isEmpty()) {
            return;
        }
        long t0 = System.nanoTime();
        int tick = server.getTickCount();
        for (int i = 0; i < FIGHTERS.size(); i++) {
            Fighter f = FIGHTERS.get(i);
            StressFighter p = f.player();
            if (p.isRemoved()) {
                continue;
            }
            PlayerCombat combat = PlayerCombat.of(p);
            if ((tick + i * 3) % STRIKE_EVERY == 0) {
                LivingEntity target = nearestFoe(p, f.home(), 20.0);
                if (target != null) {
                    Vec3 to = target.position();
                    Vec3 from = p.position();
                    if (from.distanceTo(to) > 3.0) {
                        Vec3 dir = from.subtract(to).multiply(1, 0, 1);
                        Vec3 stand = dir.lengthSqr() < 1e-4 ? to.add(2.5, 0, 0) : to.add(dir.normalize().scale(2.5));
                        p.moveTo(stand.x, Math.max(stand.y, to.y), stand.z, p.getYRot(), p.getXRot());
                    }
                    Vec3 look = target.getBoundingBox().getCenter().subtract(p.getEyePosition());
                    float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                    float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
                    p.setYRot(yaw);
                    p.setYHeadRot(yaw);
                    p.setXRot(pitch);
                    combat.syncWeapon();
                    combat.apply(CombatAction.ATTACK_PRESS);
                }
            } else if ((tick + i * 3) % STRIKE_EVERY == 1) {
                combat.apply(CombatAction.ATTACK_RELEASE);
            }
            p.doTick();
        }
        if (stats.on) {
            stats.fighterNanos += System.nanoTime() - t0;
        }
    }

    private static LivingEntity nearestFoe(Player p, Vec3 home, double range) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(home, home).inflate(range),
                e -> e.isAlive() && !(e instanceof Player) && e instanceof net.minecraft.world.entity.monster.Enemy)) {
            double d = e.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static void onTickEnd(MinecraftServer server) {
        if (stats.on) {
            stats.tick(System.nanoTime() - tickStart);
        }
    }

    // ------------------------------------------------------------------ entity tick times

    private static void onEntityTickStart(EntityTickEvent.Pre event) {
        if (stats.on && !event.getEntity().level().isClientSide() && ours(event.getEntity())) {
            ENTITY_START.put(event.getEntity(), System.nanoTime());
        }
    }

    private static void onEntityTickEnd(EntityTickEvent.Post event) {
        if (!stats.on || event.getEntity().level().isClientSide()) {
            return;
        }
        Long start = ENTITY_START.remove(event.getEntity());
        if (start != null) {
            stats.entity(event.getEntity().getType(), System.nanoTime() - start);
        }
    }

    private static final Map<EntityType<?>, Boolean> OURS = new java.util.IdentityHashMap<>();

    private static boolean ours(Entity e) {
        return OURS.computeIfAbsent(e.getType(), t -> CosmicBreach.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace()));
    }

    /** Server-side measurements while {@link #on}. */
    public static final class Stats {
        volatile boolean on;
        private final List<Long> ticks = new ArrayList<>();
        private final Map<ResourceLocation, long[]> byType = new HashMap<>();
        long fighterNanos;
        final long skippedAtStart = com.cosmicbreach.world.AiLod.skipped();
        private long entityNanos;
        private int entityTicks;

        void tick(long nanos) {
            ticks.add(nanos);
        }

        void entity(EntityType<?> type, long nanos) {
            byType.computeIfAbsent(BuiltInRegistries.ENTITY_TYPE.getKey(type), k -> new long[2])[0] += nanos;
            byType.get(BuiltInRegistries.ENTITY_TYPE.getKey(type))[1]++;
            entityNanos += nanos;
            entityTicks++;
        }

        public synchronized void stop() {
            on = false;
        }

        public int samples() {
            return ticks.size();
        }

        /** Median server tick, ms. */
        public double tickMedianMs() {
            return percentile(0.5);
        }

        public double tickP95Ms() {
            return percentile(0.95);
        }

        /** Mean time a tick spent ticking Cosmic Breach entities, ms. */
        public double entityMsPerTick() {
            return ticks.isEmpty() ? 0 : entityNanos / 1e6 / ticks.size();
        }

        /** Mean time a tick spent in one entity type, ms. */
        public double typeMsPerTick(ResourceLocation type) {
            long[] v = byType.get(type);
            return v == null || ticks.isEmpty() ? 0 : v[0] / 1e6 / ticks.size();
        }

        /** How many of that type ticked in an average tick. */
        public double typeCount(ResourceLocation type) {
            long[] v = byType.get(type);
            return v == null || ticks.isEmpty() ? 0 : v[1] / (double) ticks.size();
        }

        /** Mean time a tick spent in the fake players (their whole player tick and their strikes), ms. */
        public double fighterMsPerTick() {
            return ticks.isEmpty() ? 0 : fighterNanos / 1e6 / ticks.size();
        }

        private double percentile(double p) {
            if (ticks.isEmpty()) {
                return Double.NaN;
            }
            List<Long> sorted = new ArrayList<>(ticks);
            sorted.sort(null);
            return sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(p * sorted.size()))) / 1e6;
        }

        public String report() {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                    "server over %d ticks: tick median %.2f ms, p95 %.2f; Cosmic Breach entities %.3f ms a tick; fake players %.3f ms a tick; "
                            + "%.1f creature ticks a tick skipped far from players",
                    ticks.size(), tickMedianMs(), tickP95Ms(), entityMsPerTick(), fighterMsPerTick(),
                    ticks.isEmpty() ? 0.0 : (com.cosmicbreach.world.AiLod.skipped() - skippedAtStart) / (double) ticks.size()));
            byType.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0])).forEach(e ->
                    sb.append(String.format(Locale.ROOT, "; %s %.1f x %.1f us = %.3f ms", e.getKey().getPath(),
                            typeCount(e.getKey()), e.getValue()[1] == 0 ? 0 : e.getValue()[0] / 1e3 / e.getValue()[1], typeMsPerTick(e.getKey()))));
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ chunk generation

    /** Fresh chunk generation, timed: whole chunks to FULL, and each layer's terrain density alone. */
    public record ChunkReport(int chunks, double msPerChunk, double reachMs, double driftMs, double deepMs, double columnMs) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d fresh chunks at %.1f ms each; terrain density per chunk: Reach %.2f ms, Drift %.2f ms, "
                    + "Deep %.2f ms, column set-up %.2f ms", chunks, msPerChunk, reachMs, driftMs, deepMs, columnMs);
        }
    }

    public static ChunkReport chunkReport() {
        return chunkReport;
    }

    /** Generates 5 by 5 fresh chunks far out (on the server thread) and times each layer's density over 16 chunks. */
    public static void chunks(ServerLevel level) {
        int ox = 40_000 + level.random.nextInt(1000) * 16;
        int oz = 40_000 + level.random.nextInt(1000) * 16;
        int cx = ox >> 4;
        int cz = oz >> 4;
        long t0 = System.nanoTime();
        int n = 0;
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
                n++;
            }
        }
        double perChunk = (System.nanoTime() - t0) / 1e6 / n;
        AetheriaTerrain t = AetheriaSpots.terrain(level);
        AetheriaTerrain.Column col = new AetheriaTerrain.Column();
        long column = 0;
        long[] layer = new long[3];
        int chunks = 16;
        double sink = 0;
        for (int c = 0; c < chunks; c++) {
            int bx = ox - 2000 + c * 16 * 7;
            int bz = oz - 3000 + c * 16 * 5;
            for (int x = bx; x < bx + 16; x++) {
                for (int z = bz; z < bz + 16; z++) {
                    long a = System.nanoTime();
                    t.sampleColumn(x, z, col);
                    long b = System.nanoTime();
                    column += b - a;
                    for (int y = ReachIslands.FLOOR_Y; y < 400; y++) {
                        sink += t.blocks(col, x, y, z);
                    }
                    long c1 = System.nanoTime();
                    for (int y = DriftBelts.FLOOR_Y; y <= DriftBelts.CEIL_Y; y++) {
                        sink += t.blocks(col, x, y, z);
                    }
                    long c2 = System.nanoTime();
                    for (int y = 1; y <= DeepSpans.CEIL_Y; y++) {
                        sink += t.blocks(col, x, y, z);
                    }
                    long c3 = System.nanoTime();
                    layer[0] += c1 - b;
                    layer[1] += c2 - c1;
                    layer[2] += c3 - c2;
                }
            }
        }
        if (sink == 42.4242) {
            CosmicBreach.LOGGER.debug("unlikely");
        }
        chunkReport = new ChunkReport(n, perChunk, layer[0] / 1e6 / chunks, layer[1] / 1e6 / chunks, layer[2] / 1e6 / chunks,
                column / 1e6 / chunks);
        CosmicBreach.LOGGER.debug("[cosmicbreach] stress chunks: {}", chunkReport);
    }

}
