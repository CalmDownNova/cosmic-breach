package com.cosmicbreach.world;

import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.BreachShape;
import com.cosmicbreach.world.gen.DeepSpans;
import com.cosmicbreach.world.gen.DriftBelts;
import com.cosmicbreach.world.gen.ReachIslands;
import com.cosmicbreach.world.gen.SpireField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finding places in Aetheria: safe ground in each layer, the Breach's rim, and a few viewpoints for tests.
 * The search runs on the terrain model first ({@link AetheriaTerrain}, no chunk loading), then checks the
 * best few candidates in the real world (loading their chunks), since features can change the surface.
 *
 * <p>For the onboarding task: {@link #nearestIslandColumn} is the cheap analytic "where is the nearest
 * island to (x, z)", and {@link #safeSpot} gives feet positions a player can stand at.
 */
public final class AetheriaSpots {
    /** Where to look. */
    public enum Kind { SPIRES, SUNFIELD, DRIFT, DEEP }

    /** A place to put a player: feet position and facing. */
    public record Spot(BlockPos feet, float yaw, float pitch) {}

    private static final int STEP = 8;

    private AetheriaSpots() {
    }

    public static AetheriaTerrain terrain(ServerLevel level) {
        return AetheriaTerrain.of(level.getChunkSource().randomState());
    }

    // ------------------------------------------------------------------ the model's candidates

    /**
     * Analytic candidates of {@code kind} near (x, z), nearest first, at most {@code limit}, within
     * {@code maxRadius} blocks. Each is a column (y is the model's standing height).
     */
    public static List<BlockPos> candidates(AetheriaTerrain t, int x, int z, Kind kind, int maxRadius, int limit) {
        List<BlockPos> out = new ArrayList<>();
        switch (kind) {
            case SPIRES, SUNFIELD -> islandCandidates(t, x, z, kind == Kind.SUNFIELD, maxRadius, limit, out);
            case DRIFT -> asteroidCandidates(t, x, z, maxRadius, limit, out);
            case DEEP -> pillarCandidates(t, x, z, maxRadius, limit, out);
        }
        return out;
    }

    /** The nearest column of an island of either style to (x, z), well inside its rim, or empty. */
    public static Optional<BlockPos> nearestIslandColumn(AetheriaTerrain t, int x, int z, int maxRadius) {
        List<BlockPos> a = candidates(t, x, z, Kind.SPIRES, maxRadius, 1);
        List<BlockPos> b = candidates(t, x, z, Kind.SUNFIELD, maxRadius, 1);
        return java.util.stream.Stream.concat(a.stream(), b.stream())
                .min(Comparator.comparingDouble(p -> p.distToCenterSqr(x, p.getY(), z)));
    }

    private static void islandCandidates(AetheriaTerrain t, int x, int z, boolean sunfield, int maxRadius, int limit, List<BlockPos> out) {
        ReachIslands.Column col = new ReachIslands.Column();
        List<SpireField.Spire> spires = new ArrayList<>();
        for (int ring = 0; ring * STEP <= maxRadius && out.size() < limit; ring++) {
            for (int[] d : ring(ring)) {
                int cx = x + d[0] * STEP;
                int cz = z + d[1] * STEP;
                t.reach.sample(cx + 0.5, cz + 0.5, col);
                if (!col.island || col.isle == null || col.isle.sunfield != sunfield || col.edge < 14 || col.neckness > 0.1) {
                    continue;
                }
                t.spires.spiresTouching(cx - 4, cz - 4, cx + 4, cz + 4, spires);
                if (!spires.isEmpty() || !flatAround(t, cx, cz, col.top, col)) {
                    continue;
                }
                t.reach.sample(cx + 0.5, cz + 0.5, col);
                out.add(new BlockPos(cx, (int) Math.floor(col.top - 0.5) + 1, cz));
                if (out.size() >= limit) {
                    return;
                }
            }
        }
    }

    /** True if the island's top within 5 blocks of (x, z) stays within a block of {@code top} (open ground). */
    private static boolean flatAround(AetheriaTerrain t, int x, int z, double top, ReachIslands.Column scratch) {
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            t.reach.sample(x + 0.5 + Math.cos(a) * 5, z + 0.5 + Math.sin(a) * 5, scratch);
            if (!scratch.island || Math.abs(Math.floor(scratch.top - 0.5) - Math.floor(top - 0.5)) > 1) {
                return false;
            }
        }
        return true;
    }

    private static void asteroidCandidates(AetheriaTerrain t, int x, int z, int maxRadius, int limit, List<BlockPos> out) {
        int ci = Math.floorDiv(x, DriftBelts.CELL);
        int cj = Math.floorDiv(z, DriftBelts.CELL);
        for (int ring = 0; ring * DriftBelts.CELL <= maxRadius && out.size() < limit; ring++) {
            List<BlockPos> found = new ArrayList<>();
            for (int[] d : ring(ring)) {
                for (int k = DriftBelts.K_MIN; k <= DriftBelts.K_MAX; k++) {
                    DriftBelts.Asteroid a = t.drift.asteroid(ci + d[0], cj + d[1], k);
                    // flat-topped rocks first: level ground to land on
                    if (a != null && a.r >= 7.0 && !a.shard && a.flatTop < Double.POSITIVE_INFINITY) {
                        found.add(BlockPos.containing(a.cx, a.flatTop + 2, a.cz));
                    }
                }
            }
            found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(x, p.getY(), z)));
            for (BlockPos p : found) {
                out.add(p);
                if (out.size() >= limit) {
                    return;
                }
            }
        }
    }

    private static void pillarCandidates(AetheriaTerrain t, int x, int z, int maxRadius, int limit, List<BlockPos> out) {
        int ci = (int) Math.floor(x / DeepSpans.CELL);
        int cj = (int) Math.floor(z / DeepSpans.CELL);
        for (int ring = 0; ring * DeepSpans.CELL <= maxRadius && out.size() < limit; ring++) {
            List<BlockPos> found = new ArrayList<>();
            for (int[] d : ring(ring)) {
                DeepSpans.Pillar p = t.deep.pillar(ci + d[0], cj + d[1]);
                if (p.exists && p.platformR >= 9 && !t.deep.scar(p.cx, p.cz)) {
                    found.add(BlockPos.containing(p.cx, p.top + 1, p.cz));
                }
            }
            found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(x, p.getY(), z)));
            for (BlockPos p : found) {
                out.add(p);
                if (out.size() >= limit) {
                    return;
                }
            }
        }
    }

    /** The offsets of Chebyshev ring {@code r} (r = 0 is the centre). */
    private static List<int[]> ring(int r) {
        List<int[]> out = new ArrayList<>();
        if (r == 0) {
            out.add(new int[] {0, 0});
            return out;
        }
        for (int i = -r; i <= r; i++) {
            out.add(new int[] {i, -r});
            out.add(new int[] {i, r});
        }
        for (int j = -r + 1; j <= r - 1; j++) {
            out.add(new int[] {-r, j});
            out.add(new int[] {r, j});
        }
        return out;
    }

    // ------------------------------------------------------------------ the real world

    /**
     * A spot of {@code kind} near {@code near} where a player can stand, checked in the world (loads chunks).
     * In the Drift and the Deep, spots open to the sky (not under a layer above) are preferred.
     */
    public static Optional<Spot> safeSpot(ServerLevel level, Kind kind, BlockPos near) {
        AetheriaTerrain t = terrain(level);
        BlockPos fallback = null;
        for (BlockPos c : candidates(t, near.getX(), near.getZ(), kind, 3000, 24)) {
            Optional<BlockPos> feet = switch (kind) {
                case SPIRES, SUNFIELD -> settle(level, c, 6, 12);
                case DRIFT -> settle(level, c, 2, 40);
                case DEEP -> settle(level, c, 3, 16);
            };
            if (feet.isEmpty()) {
                continue;
            }
            boolean open = level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.get().getX(), feet.get().getZ()) <= feet.get().getY();
            if (open || kind == Kind.SPIRES || kind == Kind.SUNFIELD) {
                return Optional.of(new Spot(feet.get(), 0f, 10f));
            }
            if (fallback == null) {
                fallback = feet.get();
            }
        }
        return Optional.ofNullable(fallback).map(p -> new Spot(p, 0f, 10f));
    }

    /**
     * Scans down the column of {@code start} from {@code up} above it to {@code down} below it for the first
     * floor with two free blocks above; also tries the 8 neighbours two blocks out.
     */
    public static Optional<BlockPos> settle(ServerLevel level, BlockPos start, int up, int down) {
        for (int[] d : new int[][] {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}}) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(start.getX() + d[0], start.getY() + up, start.getZ() + d[1]);
            for (int i = 0; i <= up + down; i++, p.move(Direction.DOWN)) {
                if (standable(level, p)) {
                    return Optional.of(p.immutable());
                }
            }
        }
        return Optional.empty();
    }

    /** True if a player's feet fit at {@code feet}: sturdy floor, two blocks of air, no fluid. */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight() - 2) {
            return false;
        }
        BlockState floor = level.getBlockState(feet.below());
        BlockState legs = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return floor.isFaceSturdy(level, feet.below(), Direction.UP) && legs.isAir() && head.isAir();
    }

    // ------------------------------------------------------------------ the Breach and viewpoints

    /** The Reach's rim of the Breach nearest {@code near}: right at the edge, facing across the Breach, looking down. */
    public static Optional<Spot> breachRim(ServerLevel level, BlockPos near) {
        AetheriaTerrain t = terrain(level);
        ReachIslands.Column col = new ReachIslands.Column();
        double pref = near.getX() == 0 && near.getZ() == 0 ? 0 : Math.atan2(near.getZ(), near.getX());
        List<double[]> found = new ArrayList<>();
        for (int k = 0; k < 64; k++) {
            double a = pref + k * Math.PI * 2 / 64;
            for (double r = BreachShape.REACH_RADIUS * 0.85; r < BreachShape.REACH_RADIUS + 120; r += 1) {
                double x = Math.cos(a) * r;
                double z = Math.sin(a) * r;
                t.reach.sample(x, z, col);
                if (col.island && col.edge > 1.5) {
                    // a firm rim: the island goes on for a while behind this point
                    t.reach.sample(Math.cos(a) * (r + 10), Math.sin(a) * (r + 10), col);
                    if (col.island && col.edge > 6) {
                        found.add(new double[] {x, z, r + Math.abs(k > 32 ? 64 - k : k) * 0.5});
                    }
                    break;
                }
            }
        }
        found.sort(Comparator.comparingDouble(f -> f[2]));
        for (double[] f : found) {
            Optional<BlockPos> feet = settleExact(level, BlockPos.containing(f[0], 385, f[1]), 70);
            if (feet.isPresent()) {
                BlockPos p = feet.get();
                float yaw = (float) Math.toDegrees(Math.atan2(p.getX(), -p.getZ()));
                return Optional.of(new Spot(p, yaw, 32f));
            }
        }
        return Optional.empty();
    }

    /** Scans down one column (no neighbours) for a floor with two free blocks above. */
    private static Optional<BlockPos> settleExact(ServerLevel level, BlockPos start, int down) {
        BlockPos.MutableBlockPos p = start.mutable();
        for (int i = 0; i <= down; i++, p.move(Direction.DOWN)) {
            if (standable(level, p)) {
                return Optional.of(p.immutable());
            }
        }
        return Optional.empty();
    }

    /**
     * A viewpoint right at a Shattered Spires island's rim, facing out: looking down the cliff ({@code across}
     * false) or level, across the gap to the neighbouring islands ({@code across} true). For tests and
     * screenshots.
     */
    public static Optional<Spot> edgeView(ServerLevel level, BlockPos near, boolean across) {
        AetheriaTerrain t = terrain(level);
        ReachIslands.Column col = new ReachIslands.Column();
        for (BlockPos c : candidates(t, near.getX(), near.getZ(), Kind.SPIRES, 3000, 6)) {
            ReachIslands.Isle isle = t.reach.isleAt(c.getX() + 0.5, c.getZ() + 0.5);
            if (isle == null) {
                continue;
            }
            // walk out from the island's heart to its rim, in several directions, away from necks
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8 + (across ? Math.PI / 16 : 0);
                double lastX = Double.NaN;
                double lastZ = Double.NaN;
                for (double r = 4; r < 200; r += 1) {
                    double x = c.getX() + Math.cos(a) * r;
                    double z = c.getZ() + Math.sin(a) * r;
                    t.reach.sample(x, z, col);
                    if (col.neckness > 0.05) {
                        break;
                    }
                    if (!col.island || col.edge < 1.0) {
                        if (Double.isNaN(lastX)) {
                            break;
                        }
                        Optional<BlockPos> feet = settleExact(level, BlockPos.containing(lastX, 390, lastZ), 70);
                        if (feet.isPresent()) {
                            float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
                            return Optional.of(new Spot(feet.get(), yaw, across ? 6f : 48f));
                        }
                        break;
                    }
                    if (col.edge < 2.5) {
                        lastX = x;
                        lastZ = z;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A column of open sky near {@code near}: no rock (and no spire) from {@code minY} to {@code maxY} in a
     * 7 by 7 area around it. The y of the result is 0. For tests (falling into a band).
     */
    public static Optional<BlockPos> openSky(ServerLevel level, BlockPos near, int minY, int maxY) {
        AetheriaTerrain t = terrain(level);
        AetheriaTerrain.Column col = new AetheriaTerrain.Column();
        List<SpireField.Spire> spires = new ArrayList<>();
        for (int ring = 1; ring < 120; ring++) {
            for (int[] d : ring(ring)) {
                int x = near.getX() + d[0] * 3;
                int z = near.getZ() + d[1] * 3;
                boolean clear = true;
                for (int dx = -3; dx <= 3 && clear; dx += 3) {
                    for (int dz = -3; dz <= 3 && clear; dz += 3) {
                        t.sampleColumn(x + dx, z + dz, col);
                        for (int y = minY; y <= maxY && clear; y++) {
                            if (AetheriaTerrain.mayHaveRock(y) && t.blocks(col, x + dx, y, z + dz) > -3) {
                                clear = false;
                            }
                        }
                        if (clear && maxY >= ReachIslands.FLOOR_Y) {
                            t.spires.spiresTouching(x + dx - 2, z + dz - 2, x + dx + 2, z + dz + 2, spires);
                            clear = spires.isEmpty();
                        }
                    }
                }
                if (clear) {
                    return Optional.of(new BlockPos(x, 0, z));
                }
            }
        }
        return Optional.empty();
    }
}
