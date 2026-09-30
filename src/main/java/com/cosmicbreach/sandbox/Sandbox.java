package com.cosmicbreach.sandbox;

import com.cosmicbreach.entity.shardling.ShardFragment;
import com.cosmicbreach.entity.shardling.ShardNeedle;
import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.entity.shardling.ShardlingPack;
import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.registry.ModItems;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The combat sandbox (Slice 0): {@code /cosmicbreach sandbox} builds a sky arena
 * {@value #HEIGHT_ABOVE} blocks above the caller ({@link ArenaLayout}), puts the caller in the middle
 * in survival with its respawn point there, gives Meridian, a diamond sword (the Better Combat
 * comparison) and cooked beef, sets noon, clear weather and keepInventory, then sends Shardling packs
 * on the {@link WaveSchedule}: a slower warm-up pair after 5 s, then 3 to 5 six seconds after each pack
 * is gone. Packs run
 * in from the arena's edge, the first from straight ahead. {@code /cosmicbreach wave [size]} sends a
 * pack now, {@code /cosmicbreach stop} ends it and removes the sandbox's Shardlings.
 *
 * <p>One sandbox per player; it pauses while its player is away from the arena and ends when the
 * player leaves the server. Server thread only.
 */
public final class Sandbox {
    public static final String TAG = "cosmicbreach_sandbox";
    public static final int HEIGHT_ABOVE = 30;
    /** Packs appear this far from the middle and run in. */
    public static final double SPAWN_RING = 17.0;
    /** A sandbox waits while its player is further than this from the middle. */
    private static final double AWAY = 80.0;
    private static final int FOOD = 16;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final UUID player;
        final ResourceKey<Level> dimension;
        final BlockPos centre;
        final float firstYaw;
        final WaveSchedule schedule;
        final List<UUID> shardlings = new ArrayList<>();
        int aliveBefore;

        Session(UUID player, ResourceKey<Level> dimension, BlockPos centre, float firstYaw, WaveSchedule schedule) {
            this.player = player;
            this.dimension = dimension;
            this.centre = centre;
            this.firstYaw = firstYaw;
            this.schedule = schedule;
        }

        Vec3 middle() {
            return Vec3.atBottomCenterOf(centre.above());
        }
    }

    private Sandbox() {
    }

    public static void register(IEventBus game) {
        game.addListener(ServerTickEvent.Post.class, Sandbox::onServerTick);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SESSIONS.remove(event.getEntity().getUUID()));
        game.addListener(ServerStoppingEvent.class, event -> SESSIONS.clear());
    }

    // ------------------------------------------------------------------ commands

    public static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        MinecraftServer server = source.getServer();
        BlockPos centre = player.blockPosition().above(HEIGHT_ABOVE);
        if (centre.getY() + ArenaLayout.CLEAR_HEIGHT >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.sandbox.too_high"));
            return 0;
        }
        Session old = SESSIONS.remove(player.getUUID());
        if (old != null) {
            removeShardlings(server, old);
        }
        ArenaBuilder.build(level, centre);
        float yaw = player.getYRot();
        player.teleportTo(level, centre.getX() + 0.5, centre.getY() + 1.0, centre.getZ() + 0.5, yaw, 0.0f);
        player.setGameMode(GameType.SURVIVAL);
        player.setRespawnPosition(level.dimension(), centre.above(), yaw, true, false);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        give(player, ModItems.MERIDIAN.get(), 1);
        give(player, Items.DIAMOND_SWORD, 1);
        give(player, Items.COOKED_BEEF, FOOD);
        player.containerMenu.broadcastChanges();

        for (ServerLevel each : server.getAllLevels()) {
            each.setDayTime(6000L);
        }
        server.overworld().setWeatherParameters(ServerLevel.RAIN_DELAY.sample(level.getRandom()), 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            server.setDifficulty(Difficulty.NORMAL, true);
            player.sendSystemMessage(Component.translatable("commands.cosmicbreach.sandbox.peaceful"));
        }

        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), level.dimension(), centre, yaw,
                new WaveSchedule(() -> WaveSchedule.MIN_SIZE + level.getRandom().nextInt(WaveSchedule.MAX_SIZE - WaveSchedule.MIN_SIZE + 1))));
        LOGGER.info("[sandbox] arena at {} for {}", centre.toShortString(), player.getName().getString());
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.sandbox.ready"), false);
        return 1;
    }

    /** A pack of {@code size} now: at the arena's edge if a sandbox runs, else 12 blocks ahead of the caller. */
    public static int wave(CommandSourceStack source, int size) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Session running = SESSIONS.get(player.getUUID());
        Session session = running != null && running.dimension == level.dimension() ? running : null;
        Vec3 at = session != null ? ringPoint(session, level.getRandom().nextFloat() * 360.0f)
                : player.position().add(Vec3.directionFromRotation(0.0f, player.getYRot()).scale(12.0));
        List<Shardling> pack = spawnPack(level, at, player.position(), size, 1.0f);
        if (session != null) {
            pack.forEach(s -> session.shardlings.add(s.getUUID()));
        }
        int spawned = pack.size();
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.sandbox.incoming", spawned), false);
        return spawned;
    }

    /** Ends the caller's sandbox and removes every sandbox Shardling (and its shards and needles) near it. */
    public static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Session session = SESSIONS.remove(player.getUUID());
        int removed = session == null ? 0 : removeShardlings(source.getServer(), session);
        removed += removeNear(player.serverLevel(), player.position(), 96.0);
        if (session == null && removed == 0) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.sandbox.not_running"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.cosmicbreach.sandbox.stopped"), false);
        return 1;
    }

    // ------------------------------------------------------------------ waves

    private static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            ServerLevel level = server.getLevel(session.dimension);
            if (player == null || level == null) {
                SESSIONS.remove(session.player);
                continue;
            }
            if (player.level() != level || player.position().distanceTo(session.middle()) > AWAY) {
                continue; // paused while the player is away (its Shardlings' chunks may not even be loaded)
            }
            int alive = countAlive(level, session);
            if (session.aliveBefore > 0 && alive == 0) {
                player.sendSystemMessage(Component.translatable("commands.cosmicbreach.sandbox.cleared"));
            }
            session.aliveBefore = alive;
            int size = session.schedule.tick(alive);
            if (size > 0) {
                int n = session.schedule.sent();
                float angle = n == 1 ? frontAngle(session.firstYaw) : level.getRandom().nextFloat() * 360.0f;
                List<Shardling> pack = spawnPack(level, ringPoint(session, angle), session.middle(), size, WaveSchedule.paceOf(n));
                pack.forEach(s -> session.shardlings.add(s.getUUID()));
                session.aliveBefore = pack.size();
                player.sendSystemMessage(Component.translatable("commands.cosmicbreach.sandbox.incoming", pack.size()));
                LOGGER.info("[sandbox] pack {} of {} at {}, pace {}", n, pack.size(), ringPoint(session, angle), WaveSchedule.paceOf(n));
            }
        }
    }

    /** Living sandbox Shardlings of this session; forgets the dead. */
    private static int countAlive(ServerLevel level, Session session) {
        int alive = 0;
        Iterator<UUID> it = session.shardlings.iterator();
        while (it.hasNext()) {
            Entity entity = level.getEntity(it.next());
            if (entity instanceof Shardling shardling && shardling.isAlive()) {
                alive++;
            } else {
                it.remove();
            }
        }
        return alive;
    }

    /** The angle round the arena (as {@code atan2(dz, dx)}) straight ahead of a player facing {@code yaw}. */
    private static float frontAngle(float yaw) {
        Vec3 ahead = Vec3.directionFromRotation(0.0f, yaw);
        return (float) Math.toDegrees(Math.atan2(ahead.z, ahead.x));
    }

    private static Vec3 ringPoint(Session session, float degrees) {
        double a = Math.toRadians(degrees);
        return session.middle().add(Math.cos(a) * SPAWN_RING, 0.0, Math.sin(a) * SPAWN_RING);
    }

    /**
     * A pack of {@code size} Shardlings round {@code at}, facing {@code toward}; they share one brain and
     * rest {@code pace} times as long as usual between attacks.
     */
    public static List<Shardling> spawnPack(ServerLevel level, Vec3 at, Vec3 toward, int size, float pace) {
        ShardlingPack pack = new ShardlingPack();
        List<Shardling> spawned = new ArrayList<>();
        float yaw = (float) (Mth.atan2(toward.z - at.z, toward.x - at.x) * Mth.RAD_TO_DEG) - 90.0f;
        for (int i = 0; i < size; i++) {
            Shardling shardling = ModEntities.SHARDLING.get().create(level);
            if (shardling == null) {
                continue;
            }
            double angle = size == 1 ? 0.0 : i * Math.PI * 2.0 / size;
            double spread = size == 1 ? 0.0 : 1.5; // five scaled bodies (0.75 wide) stand 1.8 apart
            shardling.moveTo(at.x + Math.cos(angle) * spread, at.y, at.z + Math.sin(angle) * spread, yaw, 0.0f);
            shardling.setYHeadRot(yaw);
            shardling.setYBodyRot(yaw);
            shardling.joinPack(pack);
            shardling.setPace(pace);
            shardling.addTag(TAG);
            shardling.setPersistenceRequired();
            if (level.addFreshEntity(shardling)) {
                spawned.add(shardling);
            }
        }
        return spawned;
    }

    private static int removeShardlings(MinecraftServer server, Session session) {
        ServerLevel level = server.getLevel(session.dimension);
        if (level == null) {
            return 0;
        }
        int removed = 0;
        for (UUID id : session.shardlings) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
                removed++;
            }
        }
        session.shardlings.clear();
        return removed + removeNear(level, session.middle(), ArenaLayout.RADIUS + 16.0);
    }

    /** Discards (no death, no drops) tagged Shardlings and any shards and needles within {@code radius}. */
    private static int removeNear(ServerLevel level, Vec3 around, double radius) {
        AABB box = new AABB(around, around).inflate(radius);
        List<Entity> gone = level.getEntities((Entity) null, box, e -> (e instanceof Shardling && e.getTags().contains(TAG))
                || e instanceof ShardFragment || e instanceof ShardNeedle);
        gone.forEach(Entity::discard);
        return gone.size();
    }

    private static void give(ServerPlayer player, Item item, int count) {
        if (player.getInventory().contains(stack -> stack.is(item))) {
            return; // running the sandbox again doesn't pile up copies
        }
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** The middle of the caller's running sandbox, for tests. */
    public static @Nullable Vec3 middleOf(UUID player) {
        Session session = SESSIONS.get(player);
        return session == null ? null : session.middle();
    }
}
