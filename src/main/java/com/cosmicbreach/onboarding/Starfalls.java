package com.cosmicbreach.onboarding;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Starfalls (GDD 1.3), server side. Every second each survival or adventure player in the Overworld is checked against their
 * schedule ({@link StarfallSchedule}, kept in {@link OnboardingState}); when one is due, a landing spot is
 * chosen ({@link LandingSite}: 48 to 96 blocks away by default, the highest solid dry ground in a loaded
 * chunk, else within 16 blocks), the streak is sent to every client within 256 blocks ({@link OnboardingNet.Streak},
 * drawn client side), and 3 s later the shard lands: a boom heard to 128 blocks, a small crater lined with
 * Starfall Stone ({@link StarfallCrater}; only blocks in {@link #CRATER_REPLACEABLE} are dug, block entities
 * and fluids are never touched) and the shard block, whose light pillar stands for five minutes. The first
 * time a player picks up a shard, they are given the Codex.
 *
 * <p>For later tasks: {@link #fallFor} calls a Starfall on a player now; {@link #last} tells what fell.
 */
public final class Starfalls {
    /** The streak takes 3 s. */
    public static final int STREAK_TICKS = 60;
    /** Where a streak starts: this many degrees round from the landing, as its player sees it, and this high. */
    static final double STREAK_TURN = 70.0;
    static final double STREAK_HEIGHT = 160.0;
    /** Clients this close to the landing see the streak. */
    public static final double SEND_RADIUS = 256.0;
    /** What a crater may dig out: natural ground (dirt, sand, stone, gravel, snow, ...). */
    public static final TagKey<Block> CRATER_REPLACEABLE =
            TagKey.create(Registries.BLOCK, CosmicBreach.id("starfall_crater_replaceable"));
    private static final int RETRY_TICKS = 200;
    /** The share of the crater's lining that is Meteorite (its ember seams glow faintly). */
    private static final float METEORITE_SHARE = 0.25f;

    /** A fall on its way down. */
    private record Pending(UUID player, LandingSite.Spot spot, int radius, int impactTick, long dayTime, double distance) {}

    /** What fell last: when (Overworld day time at the streak's start), where, how far from its player. */
    public record Fall(UUID player, long dayTime, BlockPos shard, double distance, int radius, long gameTime) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Integer> RETRY_AT = new HashMap<>();
    private static volatile Fall last;
    private static volatile int fallen;

    private Starfalls() {
    }

    // ------------------------------------------------------------------ the schedule

    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        int tick = level.getServer().getTickCount();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.impactTick() <= tick) {
                it.remove();
                impact(level, p);
            }
        }
        // every tick, so a fall comes on the tick it was planned for (checked once a second, a fall planned late in
        // the first-sunset window came up to 19 ticks after the window had closed); a check is two field reads
        if (!OnboardingConfig.enabled()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            check(level, player, tick);
        }
    }

    private static void check(ServerLevel level, ServerPlayer player, int tick) {
        // the invitation is for players on foot: creative builders and spectators are left out (a command can
        // still call one), and a player switching to survival gets theirs at the next sunset
        if (player.isSpectator() || player.isCreative()) {
            return;
        }
        OnboardingState state = player.getData(OnboardingRegistry.STATE);
        long now = level.getDayTime();
        RandomSource random = level.random;
        if (state.nextFall() < 0) {
            player.setData(OnboardingRegistry.STATE, state.withNextFall(
                    StarfallSchedule.firstFall(now, OnboardingConfig.windowStart(), OnboardingConfig.windowEnd(), random.nextDouble())));
            return;
        }
        switch (StarfallSchedule.decide(now, state.nextFall())) {
            case WAIT -> {
            }
            case REPLAN -> player.setData(OnboardingRegistry.STATE, state.withNextFall(state.firstFallen()
                    ? StarfallSchedule.nextNight(now, random.nextDouble())
                    : StarfallSchedule.firstFall(now, OnboardingConfig.windowStart(), OnboardingConfig.windowEnd(), random.nextDouble())));
            case FALL -> {
                Integer retry = RETRY_AT.get(player.getUUID());
                if (retry != null && tick < retry) {
                    return;
                }
                if (!fallFor(player)) {
                    // nowhere to land (at sea, say): try again in a while, until the fall is too late
                    RETRY_AT.put(player.getUUID(), tick + RETRY_TICKS);
                    return;
                }
                RETRY_AT.remove(player.getUUID());
                player.setData(OnboardingRegistry.STATE, state.withFirstFallen()
                        .withNextFall(StarfallSchedule.nightAfter(state.nextFall(), random.nextDouble())));
            }
        }
    }

    /**
     * A Starfall for {@code player} now, if they are in the Overworld and a landing spot is found: the streak
     * starts, the impact follows in {@link #STREAK_TICKS}. The schedule is not touched.
     */
    public static boolean fallFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        Optional<LandingSite.Spot> spot = LandingSite.choose(player.getX(), player.getZ(), OnboardingConfig.minDistance(),
                OnboardingConfig.maxDistance(), (x, z) -> landingY(level, x, z, false), level.random);
        if (spot.isEmpty()) {
            // a closed forest: under the canopy then, so no player is ever left without one
            spot = LandingSite.choose(player.getX(), player.getZ(), OnboardingConfig.minDistance(),
                    OnboardingConfig.maxDistance(), (x, z) -> landingY(level, x, z, true), level.random);
        }
        if (spot.isEmpty()) {
            return false;
        }
        LandingSite.Spot s = spot.get();
        Vec3 to = new Vec3(s.x() + 0.5, s.y() + 1.0, s.z() + 0.5);
        Vec3 flat = new Vec3(to.x - player.getX(), 0.0, to.z - player.getZ());
        double reach = Math.max(8.0, flat.length());
        Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
        // it starts high in the sky 70 degrees round from the spot (as the player sees it) and a little farther
        // out, so it sweeps across the sky in front of them instead of coming straight at them
        double turn = Math.toRadians(STREAK_TURN) * (level.random.nextBoolean() ? 1.0 : -1.0);
        Vec3 round = new Vec3(dir.x * Math.cos(turn) - dir.z * Math.sin(turn), 0.0, dir.x * Math.sin(turn) + dir.z * Math.cos(turn));
        Vec3 from = new Vec3(player.getX(), to.y + STREAK_HEIGHT, player.getZ()).add(round.scale(reach * 1.3));
        PacketDistributor.sendToPlayersNear(level, null, to.x, to.y, to.z, SEND_RADIUS, new OnboardingNet.Streak(from, to, STREAK_TICKS));
        int radius = StarfallCrater.MIN_RADIUS + level.random.nextInt(StarfallCrater.MAX_RADIUS - StarfallCrater.MIN_RADIUS + 1);
        double distance = s.distanceTo(player.getX(), player.getZ());
        PENDING.add(new Pending(player.getUUID(), s, radius, level.getServer().getTickCount() + STREAK_TICKS, level.getDayTime(), distance));
        CosmicBreach.LOGGER.debug("[cosmicbreach] Starfall for {} at day time {}: lands at {} {} {}, {} blocks away",
                player.getGameProfile().getName(), level.getDayTime(), s.x(), s.y(), s.z(), String.format(java.util.Locale.ROOT, "%.1f", distance));
        return true;
    }

    // ------------------------------------------------------------------ where it may land

    /**
     * The ground block a shard may land on at (x, z), or {@link LandingSite.Surface#NONE}: the top of the
     * column under any plants or snow, solid and dry, something the crater may dig, not leaves (unless
     * {@code underCanopy}, when the ground below the leaves counts); the ground round it level within
     * {@link LandingSite#MAX_RISE}; the chunks under the whole crater loaded; no fluid or block entity anywhere
     * in the crater's reach.
     */
    static int landingY(ServerLevel level, int x, int z, boolean underCanopy) {
        int reach = StarfallCrater.MAX_RADIUS + 1;
        for (int dx = -reach; dx <= reach; dx += 2 * reach) {
            for (int dz = -reach; dz <= reach; dz += 2 * reach) {
                if (!level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) {
                    return LandingSite.Surface.NONE;
                }
            }
        }
        int top = groundY(level, x, z, underCanopy);
        if (top == LandingSite.Surface.NONE) {
            return LandingSite.Surface.NONE;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        BlockState ground = level.getBlockState(p);
        if (!ground.is(CRATER_REPLACEABLE) || ground.is(BlockTags.LEAVES) || !ground.getFluidState().isEmpty()
                || ground.hasBlockEntity() || !ground.isFaceSturdy(level, p, Direction.UP)) {
            return LandingSite.Surface.NONE;
        }
        int gy = p.getY();
        int[] rim = new int[8];
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0;
            rim[k] = groundY(level, x + (int) Math.round(Math.cos(a) * reach), z + (int) Math.round(Math.sin(a) * reach), true);
        }
        if (!LandingSite.levelEnough(gy, rim)) {
            return LandingSite.Surface.NONE;
        }
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -StarfallCrater.MAX_RADIUS; dy <= StarfallCrater.CLEAR_ABOVE; dy++) {
                    BlockState s = level.getBlockState(q.set(x + dx, gy + dy, z + dz));
                    if (!s.getFluidState().isEmpty() || s.hasBlockEntity()) {
                        return LandingSite.Surface.NONE;
                    }
                }
            }
        }
        return gy;
    }

    /**
     * The top of the ground at (x, z): under any plants or snow layers (and leaves, if {@code throughLeaves}), the
     * first solid block; {@link LandingSite.Surface#NONE} over a fluid.
     */
    static int groundY(ServerLevel level, int x, int z, boolean throughLeaves) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        for (int i = 0; i < 24; i++) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty()) || (throughLeaves && s.is(BlockTags.LEAVES))) {
                p.move(Direction.DOWN);
            } else {
                break;
            }
        }
        return level.getBlockState(p).getFluidState().isEmpty() ? p.getY() : LandingSite.Surface.NONE;
    }

    // ------------------------------------------------------------------ the impact

    private static void impact(ServerLevel level, Pending p) {
        LandingSite.Spot s = p.spot();
        BlockPos ground = new BlockPos(s.x(), s.y(), s.z());
        if (!level.isLoaded(ground)) {
            return;
        }
        RandomSource random = level.random;
        BlockPos shardAt = null;
        for (StarfallCrater.Cell c : carvingOrder(StarfallCrater.cells(p.radius()))) {
            BlockPos q = ground.offset(c.dx(), c.dy(), c.dz());
            BlockState state = level.getBlockState(q);
            if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
                continue;
            }
            // above the ground only plants go (flowers and saplings too, or they would pop off and litter the
            // crater); below it, whatever the crater tag allows
            boolean diggable = state.isAir() || state.canBeReplaced()
                    || (c.dy() > 0 && state.getBlock() instanceof net.minecraft.world.level.block.BushBlock)
                    || (c.dy() <= 0 && state.is(CRATER_REPLACEABLE));
            if (!diggable) {
                continue;
            }
            switch (c.kind()) {
                case CLEAR -> {
                    if (!state.isAir()) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                case LINING -> level.setBlock(q, (random.nextFloat() < METEORITE_SHARE ? ModBlocks.METEORITE.get()
                        : ModBlocks.STARFALL_STONE.get()).defaultBlockState(), 3);
                case SHARD -> {
                    level.setBlock(q, OnboardingRegistry.STARFALL_SHARD_BLOCK.get().defaultBlockState(), 3);
                    if (level.getBlockEntity(q) instanceof StarfallShardBlockEntity shard) {
                        shard.fell(level.getGameTime());
                        shardAt = q.immutable();
                    }
                }
            }
        }
        Vec3 at = Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0);
        level.playSound(null, at.x, at.y, at.z, OnboardingRegistry.STARFALL_BOOM.get(), SoundSource.WEATHER, 1.0f,
                0.9f + random.nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 60, 1.2, 0.8, 1.2, 0.15);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 1.5, 0.3, 1.5, 0.05);
        if (shardAt != null) {
            last = new Fall(p.player(), p.dayTime(), shardAt, p.distance(), p.radius(), level.getGameTime());
            fallen++;
        }
    }

    /**
     * The crater's cells in the order that leaves no litter: plants first, from the bottom up (a tall flower's
     * lower half goes first, so the upper half falls away without dropping anything), then the ground from
     * the top down, then the lining, then the shard.
     */
    static List<StarfallCrater.Cell> carvingOrder(List<StarfallCrater.Cell> cells) {
        List<StarfallCrater.Cell> out = new ArrayList<>(cells);
        out.sort(java.util.Comparator.comparingInt(Starfalls::carvingRank));
        return out;
    }

    private static int carvingRank(StarfallCrater.Cell c) {
        return switch (c.kind()) {
            case CLEAR -> c.dy() > 0 ? c.dy() : 100 - c.dy();
            case LINING -> 1000;
            case SHARD -> 2000;
        };
    }

    // ------------------------------------------------------------------ the Codex on first pickup

    static void onPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getOriginalStack().is(OnboardingRegistry.STARFALL_SHARD.get())
                && !player.getData(OnboardingRegistry.STATE).codexGiven()) {
            Codex.give(player);
        }
    }

    // ------------------------------------------------------------------ bookkeeping

    static void forget(UUID player) {
        RETRY_AT.remove(player);
    }

    static void reset() {
        PENDING.clear();
        RETRY_AT.clear();
        last = null;
        fallen = 0;
    }

    /** The last shard that landed, or null. */
    public static Fall last() {
        return last;
    }

    /** Shards landed since the server started. */
    public static int fallenCount() {
        return fallen;
    }

    /** Falls still on their way down. */
    public static int pending() {
        return PENDING.size();
    }
}
