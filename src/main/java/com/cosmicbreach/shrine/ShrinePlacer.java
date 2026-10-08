package com.cosmicbreach.shrine;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianLairs;
import com.cosmicbreach.guardian.colossus.CrownSpireLayout;
import com.cosmicbreach.guardian.leviathan.LeviathanRiftStructure;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.lift.AscentCurrent;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.structure.sanctum.SanctumRegistry;
import com.cosmicbreach.structure.sanctum.Sanctums;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.ShearBand;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.ReachIslands;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Places the shrines (1.1 design section 9) by code, never by world generation, so existing worlds get them: the shrine
 * of every lair in the lair registry, at the spot its layout gives ({@link ShrineSites}), settled on real ground; the
 * Sanctum's once its Gate stands. Each once ({@link ShrineData}).
 */
public final class ShrinePlacer {
    public static final int EVERY = 100;
    private static final int[][] AROUND = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0},
            {0, 2}, {0, -2}};

    /** Shrines whose current was searched for in this run (a search that finds none is tried again at the next start). */
    private static final Set<String> SEARCHED = ConcurrentHashMap.newKeySet();

    private ShrinePlacer() {
    }

    static void register(IEventBus game) {
        game.addListener(LevelTickEvent.Post.class, ShrinePlacer::onLevelTick);
        game.addListener(ServerStartedEvent.class, ShrinePlacer::onServerStarted);
        game.addListener(ServerStoppedEvent.class, event -> SEARCHED.clear());
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && AetheriaWorld.is(level) && level.getGameTime() % EVERY == 17) {
            placePending(level, false);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().getLevel(AetheriaWorld.LEVEL);
        if (level != null) {
            int n = placePending(level, true);
            CosmicBreach.LOGGER.debug("[cosmicbreach] start-up: placed {} shrine(s) at known lairs", n);
        }
    }

    /**
     * Places every shrine still missing: a lair's where its spot's chunk is loaded (with {@code force}, loading it), the
     * Sanctum's where its Gate is loaded and built. Returns how many it placed.
     */
    public static int placePending(ServerLevel level, boolean force) {
        ShrineData data = ShrineData.get(level);
        int placed = 0;
        for (Map.Entry<BlockPos, GuardianLairs.Lair> e : GuardianLairs.get(level).all().entrySet()) {
            String key = ShrineData.key(e.getValue().guardian(), e.getKey());
            if (data.has(key)) {
                current(level, data, key, data.all().get(key), e.getValue().arenaCentre(), force);
                continue;
            }
            Optional<ShrineSites.Site> site = siteFor(level, e.getKey(), e.getValue(), force);
            if (site.isEmpty()) {
                CosmicBreach.LOGGER.debug("[cosmicbreach] shrine: no site yet for the {} lair at {}", e.getValue().guardian(), e.getKey());
                continue;
            }
            if (!ready(level, site.get().pos(), force)) {
                continue;
            }
            BlockPos at = place(level, site.get());
            if (at != null) {
                data.put(key, new ShrineData.Placed(site.get().kind(), at, site.get().facing()));
                placed++;
                current(level, data, key, data.all().get(key), e.getValue().arenaCentre(), force);
            } else {
                CosmicBreach.LOGGER.debug("[cosmicbreach] shrine: no room near {} for the {} lair's shrine", site.get().pos(), e.getValue().guardian());
            }
        }
        if (!data.has(ShrineData.SANCTUM_KEY)) {
            SanctumLayout layout = Sanctums.layout(level);
            ShrineSites.Site site = ShrineSites.sanctum(layout);
            BlockPos gate = new BlockPos(0, SanctumLayout.HALL_Y, layout.z(SanctumLayout.GATE_D));
            if (level.isLoaded(site.pos()) && level.isLoaded(gate) && level.getBlockState(gate).is(SanctumRegistry.SANCTUM_GATE.get())) {
                BlockPos at = place(level, site);
                if (at != null) {
                    data.put(ShrineData.SANCTUM_KEY, new ShrineData.Placed(site.kind(), at, site.facing()));
                    placed++;
                }
            }
        }
        return placed;
    }

    /** Lets every shrine's current be searched for again (debug: with {@link ShrineData#clearCurrents}, an old world's first start). */
    static void resetSearches() {
        SEARCHED.clear();
    }

    /** Shrines 1 and 2 stand over a drop: their currents climb from the level below. */
    static boolean wantsCurrent(ShrineKind kind) {
        return kind == ShrineKind.COLOSSUS || kind == ShrineKind.LEVIATHAN;
    }

    /**
     * Finds and saves the current beside a placed shrine, once its surroundings are loaded (with {@code force}, loading
     * them), in new and existing worlds alike: from the shrine's real position and the blocks around it, never from the
     * world generator.
     */
    static void current(ServerLevel level, ShrineData data, String key, ShrineData.Placed placed, BlockPos arenaCentre, boolean force) {
        if (!wantsCurrent(placed.kind()) || data.current(key).isPresent() || SEARCHED.contains(key)) {
            return;
        }
        BlockPos s = placed.pos();
        int r = ShrineCurrents.FAR + 1;
        for (int cx = (s.getX() - r) >> 4; cx <= (s.getX() + r) >> 4; cx++) {
            for (int cz = (s.getZ() - r) >> 4; cz <= (s.getZ() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    if (!force) {
                        return;
                    }
                    level.getChunk(cx, cz);
                }
            }
        }
        SEARCHED.add(key);
        List<Vec3> landings = ShrineSpots.standSpots(level, s, placed.facing());
        if (landings.isEmpty()) {
            CosmicBreach.LOGGER.debug("[cosmicbreach] shrine: nowhere to stand beside the {} shrine at {}, so no current", placed.kind().id(), s);
            return; // searched again at the next start: never lower a rider into a block
        }
        boolean one = placed.kind() == ShrineKind.COLOSSUS;
        double keepX = Double.NaN;
        double keepZ = Double.NaN;
        if (!one) {
            Vec3 c = LeviathanRiftStructure.layout(level.getSeed(), arenaCentre).centre();
            keepX = c.x;
            keepZ = c.z;
        }
        Optional<AscentCurrent.Current> found = ShrineCurrents.find((x, y, z) -> {
                    BlockPos p = new BlockPos(x, y, z);
                    return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
                }, s, landings, one ? ShearBand.A.minY : ShearBand.B.minY, one ? Layer.DRIFT.rockMinY : Layer.DEEP.rockMinY, keepX, keepZ,
                RiftLayout.RX + 4.0);
        if (found.isPresent()) {
            data.putCurrent(key, found.get());
        } else {
            CosmicBreach.LOGGER.debug("[cosmicbreach] shrine: no room for a current beside the {} shrine at {}", placed.kind().id(), s);
        }
    }

    private static boolean ready(ServerLevel level, BlockPos pos, boolean force) {
        if (level.isLoaded(pos)) {
            return true;
        }
        if (!force) {
            return false;
        }
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // at start-up: load the chunk of a known lair once
        return level.isLoaded(pos);
    }

    /**
     * The site of a lair's shrine, from its layout. The Crown Spire's island is read from the blocks that stand there (the
     * terrain model draws the island's rim a few blocks further out than erosion leaves it), which needs every chunk the
     * rim search walks over: loaded, or with {@code force} loaded now; empty until then.
     */
    static Optional<ShrineSites.Site> siteFor(ServerLevel level, BlockPos altar, GuardianLairs.Lair lair, boolean force) {
        ShrineKind kind = ShrineKind.ofGuardian(lair.guardian());
        if (kind == null) {
            return Optional.empty();
        }
        BlockPos c = lair.arenaCentre();
        return switch (kind) {
            case COLOSSUS -> {
                AetheriaTerrain terrain = AetheriaTerrain.of(level.getChunkSource().randomState());
                ReachIslands.Column col = new ReachIslands.Column();
                terrain.reach.sample(c.getX() + 0.5, c.getZ() + 0.5, col);
                int baseY = (int) Math.floor(col.top) + 1;
                CrownSpireLayout layout = new CrownSpireLayout(c.getX(), c.getZ(), c.getY(), baseY, baseY - 6);
                if (!corridorReady(level, layout, force)) {
                    yield Optional.empty();
                }
                yield ShrineSites.crown(layout, (x, z) -> groundTop(level, baseY, x, z));
            }
            case LEVIATHAN -> Optional.of(ShrineSites.rift(LeviathanRiftStructure.layout(level.getSeed(), c)));
            case UNSUNG -> Optional.of(ShrineSites.nave(ShrineSites.naveOf(c, altar)));
            case HELIARCH -> Optional.empty();
        };
    }

    /**
     * The island's real top at a column: the highest block with collision from two above the spire's foot to eight below it,
     * else empty. (Leaves, a fence or a player's bridge count as island too; at worst the rim search ends a little later.)
     */
    static OptionalDouble groundTop(ServerLevel level, int baseY, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int y = baseY + 2; y >= baseY - 8; y--) {
            BlockPos p = new BlockPos(bx, y, bz);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty()) {
                return OptionalDouble.of(y + 0.5);
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * True once every chunk along the boss 1 rim search (the door's bearing, beside the walkway, as far as the search
     * walks, every block of the way so no chunk a line only clips is missed) is loaded; with {@code force}, loads them.
     */
    private static boolean corridorReady(ServerLevel level, CrownSpireLayout layout, boolean force) {
        int[] door = layout.riseDoorOutside();
        double a = Math.toRadians(CrownSpireLayout.RISE_ANGLE);
        double ux = Math.cos(a);
        double uz = Math.sin(a);
        for (int t = 0; t <= ShrineSites.CROWN_SEARCH; t++) {
            int x = (int) Math.floor(door[0] + 0.5 + ux * t - uz * ShrineSites.CROWN_SIDE);
            int z = (int) Math.floor(door[2] + 0.5 + uz * t + ux * ShrineSites.CROWN_SIDE);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                if (!force) {
                    return false;
                }
                level.getChunk(x >> 4, z >> 4);
            }
        }
        return true;
    }

    /**
     * Sets the shrine down where {@link #settle} finds it room, on solid ground, without taking anything a player put there:
     * only air and things that yield to any block are built over (a plant is broken and drops), and a player standing
     * on the spot makes it wait for the next pass. A shrine of this kind already standing near the site (placed before
     * a crash lost its record) is taken as the one placed. Returns where, or null.
     */
    static @Nullable BlockPos place(ServerLevel level, ShrineSites.Site site) {
        BlockPos standing = existing(level, site);
        if (standing != null) {
            return standing;
        }
        BlockPos pos = settle(level, site.pos());
        if (pos == null || !level.getEntitiesOfClass(Player.class, new AABB(pos).expandTowards(0.0, 1.0, 0.0), p -> !p.isSpectator()).isEmpty()) {
            return null;
        }
        for (int dy = 0; dy <= 2; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) {
                level.destroyBlock(pos.above(dy), true); // a plant that yields to any block: it drops as it would when broken
            }
        }
        level.setBlock(pos, ShrineRegistry.block(site.kind()).defaultBlockState().setValue(ShrineBlock.FACING, site.facing()), Block.UPDATE_ALL);
        return level.getBlockState(pos).getBlock() instanceof ShrineBlock ? pos : null;
    }

    /** The spot of a shrine of this site's kind already standing where {@link #settle} would look, or null. */
    private static @Nullable BlockPos existing(ServerLevel level, ShrineSites.Site site) {
        Block block = ShrineRegistry.block(site.kind());
        for (int[] d : AROUND) {
            for (int dy : heightOrder()) {
                BlockPos p = site.pos().offset(d[0], dy, d[1]);
                if (level.isLoaded(p) && level.getBlockState(p).is(block)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** The heights {@link #settle} tries, relative to the site: the site's own first, then nearest first, four up to six down. */
    static int[] heightOrder() {
        return new int[] {0, -1, 1, -2, 2, -3, 3, -4, 4, -5, -6};
    }

    /**
     * The nearest spot to {@code want} (the column itself first, then up to two blocks across) where a shrine fits, at the
     * site's own height first and then nearest first: the terrain model's island tops come before erosion, so real ground
     * can sit a little lower, but a roof or a canopy above the spot never beats the ground.
     */
    static @Nullable BlockPos settle(ServerLevel level, BlockPos want) {
        for (int[] d : AROUND) {
            for (int dy : heightOrder()) {
                BlockPos p = want.offset(d[0], dy, d[1]);
                if (fits(level, p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * True if a block of a world may be built over by a shrine: air, or something that yields to any block (grass, a
     * fern, a flower, a sapling, a layer of snow). Never a torch, a sign, a rail, a lever, a wire or anything else a
     * player may have placed, even where it has no collision, and never a fluid.
     */
    static boolean clearToBuild(BlockState s) {
        if (!s.getFluidState().isEmpty()) {
            return false;
        }
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
    }

    /** True if a shrine fits at {@code p}: a sturdy floor, and three blocks that are clear to build over. */
    static boolean fits(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p) || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
            return false;
        }
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos q = p.above(dy);
            BlockState s = level.getBlockState(q);
            if (!clearToBuild(s) || s.getDestroySpeed(level, q) < 0) {
                return false;
            }
        }
        return true;
    }
}
