package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.DriftBelts;
import com.cosmicbreach.world.gen.Hashing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Dresses the Drift's asteroids (GDD 2.2), once each, in the chunk that holds the asteroid's centre (the
 * writes stay within a few blocks of it): big asteroids get a Nebulite core and a petrified Driftwood tree
 * on their crown; medium ones a small Nebulite seam; a third carry Rimeglass crystals on their faces.
 * Place it once per chunk (no placement modifiers).
 */
public final class AsteroidDecorFeature extends Feature<NoneFeatureConfiguration> {
    public AsteroidDecorFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4;
        int z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
        AetheriaTerrain terrain = AetheriaTerrain.of(level.getLevel().getChunkSource().randomState());
        List<DriftBelts.Asteroid> here = new ArrayList<>();
        int i0 = Math.floorDiv(x0, DriftBelts.CELL);
        int j0 = Math.floorDiv(z0, DriftBelts.CELL);
        for (int i = i0 - 1; i <= i0 + 1; i++) {
            for (int j = j0 - 1; j <= j0 + 1; j++) {
                for (int k = DriftBelts.K_MIN; k <= DriftBelts.K_MAX; k++) {
                    DriftBelts.Asteroid a = terrain.drift.asteroid(i, j, k);
                    if (a != null && a.cx >= x0 && a.cx < x0 + 16 && a.cz >= z0 && a.cz < z0 + 16) {
                        here.add(a);
                    }
                }
            }
        }
        for (DriftBelts.Asteroid a : here) {
            RandomSource random = new XoroshiroRandomSource(a.seed);
            if (a.r >= 5.0) {
                core(level, a, random, a.big() ? a.r * 0.3 : 1.6);
            }
            if (a.big()) {
                tree(level, a, random);
            }
            if (random.nextFloat() < (a.big() ? 0.7f : 0.3f)) {
                rimeglass(level, a, random);
            }
        }
        return !here.isEmpty();
    }

    /** A rough ball of Nebulite Ore in Driftstone at the heart. */
    private static void core(WorldGenLevel level, DriftBelts.Asteroid a, RandomSource random, double radius) {
        BlockState ore = ModBlocks.NEBULITE_ORE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ir = (int) Math.ceil(radius + 1);
        int cx = (int) Math.floor(a.cx);
        int cy = (int) Math.floor(a.cy);
        int cz = (int) Math.floor(a.cz);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double wobble = Hashing.unit(Hashing.hash(a.seed, 3, dx, dy, dz)) * 1.2;
                    if (d > radius + wobble - 0.6) {
                        continue;
                    }
                    pos.set(cx + dx, cy + dy, cz + dz);
                    if (level.getBlockState(pos).is(ModBlocks.DRIFTSTONE.get()) && random.nextFloat() < 0.45f) {
                        level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /**
     * A petrified tree on the asteroid's crown: a leaning trunk (two blocks thick at the foot on the biggest
     * rocks) that forks into bare, upswept branches, each forking again. Wood blocks at the joints.
     */
    private static void tree(WorldGenLevel level, DriftBelts.Asteroid a, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos ground = null;
        // the highest Driftstone with air above, over the middle of the rock
        for (int[] d : new int[][] {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {2, 1}, {-1, 2}, {-2, -1}, {1, -2}}) {
            int cx = (int) Math.floor(a.cx) + d[0];
            int cz = (int) Math.floor(a.cz) + d[1];
            for (int y = (int) Math.ceil(a.cy + a.r * 1.4); y > a.cy; y--) {
                pos.set(cx, y, cz);
                if (level.getBlockState(pos).is(ModBlocks.DRIFTSTONE.get())) {
                    if (level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir()) {
                        BlockPos g = pos.above().immutable();
                        if (ground == null || g.getY() > ground.getY()) {
                            ground = g;
                        }
                    }
                    break;
                }
            }
        }
        if (ground == null) {
            return;
        }
        BlockState wood = ModBlocks.DRIFTWOOD_WOOD.get().defaultBlockState();
        int height = 5 + random.nextInt(4) + (a.r >= 11 ? 2 : 0);
        double lx = (random.nextDouble() - 0.5) * 0.35;
        double lz = (random.nextDouble() - 0.5) * 0.35;
        BlockPos top = ground;
        for (int i = 0; i < height; i++) {
            BlockPos p = BlockPos.containing(ground.getX() + 0.5 + lx * i, ground.getY() + i, ground.getZ() + 0.5 + lz * i);
            if (!level.getBlockState(p).isAir()) {
                break;
            }
            level.setBlock(p, i == 0 ? wood : log(Direction.Axis.Y), Block.UPDATE_CLIENTS);
            if (i < 2 && a.r >= 11) {
                // a buttressed foot
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos f = p.relative(d);
                    if (random.nextFloat() < 0.6f && level.getBlockState(f).isAir() && !level.getBlockState(f.below()).isAir()) {
                        level.setBlock(f, i == 0 ? wood : log(Direction.Axis.Y), Block.UPDATE_CLIENTS);
                    }
                }
            }
            top = p;
        }
        int branches = 2 + random.nextInt(2);
        double base = random.nextDouble() * Math.PI * 2;
        for (int b = 0; b < branches; b++) {
            double angle = base + b * (Math.PI * 2 / branches) + (random.nextDouble() - 0.5) * 0.8;
            branch(level, top.below(random.nextInt(2)), angle, 0.9, 3 + random.nextInt(2), 2, random);
        }
    }

    /** One branch: a few steps outward and upward, then two thinner forks. */
    private static void branch(WorldGenLevel level, BlockPos start, double angle, double rise, int length, int depth, RandomSource random) {
        double x = start.getX() + 0.5;
        double y = start.getY() + 0.5;
        double z = start.getZ() + 0.5;
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        BlockPos last = start;
        for (int i = 0; i < length; i++) {
            x += dx;
            z += dz;
            y += rise * (0.6 + random.nextDouble() * 0.6);
            BlockPos p = BlockPos.containing(x, y, z);
            if (p.equals(last)) {
                continue;
            }
            if (!level.getBlockState(p).isAir()) {
                return;
            }
            Direction.Axis axis = Math.abs(p.getY() - last.getY()) > 0 && Math.abs(p.getX() - last.getX()) + Math.abs(p.getZ() - last.getZ()) == 0
                    ? Direction.Axis.Y : (Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z);
            level.setBlock(p, log(axis), Block.UPDATE_CLIENTS);
            last = p;
        }
        if (depth > 0) {
            for (int k = -1; k <= 1; k += 2) {
                if (random.nextFloat() < 0.8f) {
                    branch(level, last, angle + k * (0.5 + random.nextDouble() * 0.4), rise * 1.1, Math.max(1, length - 1), depth - 1, random);
                }
            }
        }
    }

    private static BlockState log(Direction.Axis axis) {
        return ModBlocks.DRIFTWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /**
     * Clusters of Rimeglass crystals growing out of the rock: a main point two to four long with a couple of
     * shorter ones beside it. On rocks with a Nebulite core, a seam of the ore shows around the cluster's foot.
     */
    private static void rimeglass(WorldGenLevel level, DriftBelts.Asteroid a, RandomSource random) {
        BlockState glass = ModBlocks.RIMEGLASS.get().defaultBlockState();
        BlockState ore = ModBlocks.NEBULITE_ORE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int clusters = 1 + random.nextInt(a.big() ? 5 : 3);
        for (int c = 0; c < clusters; c++) {
            // walk from outside the rock toward the centre along a random direction to find its face
            double u = random.nextDouble() * 2 - 1;
            double t = random.nextDouble() * Math.PI * 2;
            double k = Math.sqrt(1 - u * u);
            double dx = k * Math.cos(t);
            double dy = u;
            double dz = k * Math.sin(t);
            BlockPos face = null;
            // at most 12 out, so a 4-block crystal stays within 16 of the centre (inside the feature's area)
            for (double d = Math.min(a.bound, 12.0); d > 0; d -= 0.5) {
                pos.set((int) Math.floor(a.cx + dx * d), (int) Math.floor(a.cy + dy * d), (int) Math.floor(a.cz + dz * d));
                if (level.getBlockState(pos).is(ModBlocks.DRIFTSTONE.get())) {
                    face = pos.immutable();
                    break;
                }
            }
            if (face == null) {
                continue;
            }
            Direction out = Direction.getNearest(dx, dy, dz);
            crystal(level, face, out, 2 + random.nextInt(3), glass);
            for (Direction side : Direction.values()) {
                if (side.getAxis() != out.getAxis() && random.nextFloat() < 0.35f) {
                    BlockPos root = face.relative(side);
                    if (level.getBlockState(root).is(ModBlocks.DRIFTSTONE.get())) {
                        crystal(level, root, out, 1 + random.nextInt(2), glass);
                        if (a.r >= 5.0 && random.nextFloat() < 0.5f) {
                            level.setBlock(root, ore, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
            if (a.r >= 5.0 && random.nextFloat() < 0.6f) {
                level.setBlock(face, ore, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void crystal(WorldGenLevel level, BlockPos root, Direction out, int length, BlockState glass) {
        BlockPos p = root;
        for (int i = 0; i < length; i++) {
            p = p.relative(out);
            if (!level.getBlockState(p).isAir()) {
                return;
            }
            level.setBlock(p, glass, Block.UPDATE_CLIENTS);
        }
    }
}
