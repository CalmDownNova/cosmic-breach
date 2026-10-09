package com.cosmicbreach.world.feature;

import com.cosmicbreach.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A giant umbral cap, the Lichen Gardens' tree (Aetheria 1.2): a short, thick pale stem (one block across for the small
 * ones, two by two for the big) under a wide glowing violet cap, flat-topped and drooping at the rim (radius 3 to 7),
 * its underside grown with magenta and teal Neon Lichen. 5 to 12 blocks tall in all. Place it at an air block standing
 * on the Deep's stone; it needs the room (the stem's column and the cap's disc must be air).
 */
public final class GiantUmbralCapFeature extends Feature<NoneFeatureConfiguration> {
    public GiantUmbralCapFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (!level.getBlockState(origin.below()).is(ZoneBlocks.DEEP_STONE) || !level.getBlockState(origin).isAir()) {
            return false;
        }
        // sizes from small to giant
        float size = random.nextFloat();
        boolean big = size < 0.5f;
        boolean small = size > 0.8f;
        int stem = big ? 5 + random.nextInt(5) : small ? 2 + random.nextInt(2) : 3 + random.nextInt(3);
        int radius = big ? 5 + random.nextInt(3) : small ? 2 : 3 + random.nextInt(2);
        int thick = big ? 2 : 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y <= stem + 2; y++) {
            for (int dx = 0; dx < thick; dx++) {
                for (int dz = 0; dz < thick; dz++) {
                    if (!level.getBlockState(pos.setWithOffset(origin, dx, y, dz)).isAir()) {
                        return false;
                    }
                }
            }
        }
        double cx = origin.getX() + thick / 2.0;
        double cz = origin.getZ() + thick / 2.0;
        int capY = origin.getY() + stem;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                if (Math.hypot(dx + 0.5 - thick / 2.0, dz + 0.5 - thick / 2.0) <= radius
                        && !level.getBlockState(pos.set(origin.getX() + dx, capY + 1, origin.getZ() + dz)).isAir()) {
                    return false;
                }
            }
        }
        BlockState stemState = ZoneBlocks.UMBRAL_STEM.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int y = 0; y < stem; y++) {
            for (int dx = 0; dx < thick; dx++) {
                for (int dz = 0; dz < thick; dz++) {
                    level.setBlock(pos.setWithOffset(origin, dx, y, dz), stemState, Block.UPDATE_CLIENTS);
                }
            }
        }
        // a root flare at the foot of the big ones
        if (big) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int k = 0; k < 2; k++) {
                    BlockPos p = origin.relative(d, d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 2 : 1)
                            .relative(d.getClockWise(), k);
                    if (random.nextFloat() < 0.6f && level.getBlockState(p).isAir() && level.getBlockState(p.below()).is(ZoneBlocks.DEEP_STONE)) {
                        level.setBlock(p, stemState, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        BlockState cap = ZoneBlocks.GIANT_UMBRAL_CAP.get().defaultBlockState();
        // the cap: a broad flat top one block thick, a second layer in the middle, the rim drooping one block
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                double d = Math.hypot(origin.getX() + dx + 0.5 - cx, origin.getZ() + dz + 0.5 - cz);
                if (d > radius + 0.3) {
                    continue;
                }
                setIfFree(level, pos.set(origin.getX() + dx, capY, origin.getZ() + dz), cap);
                // a dome: rising toward the middle
                if (d < radius * 0.7) {
                    setIfFree(level, pos.set(origin.getX() + dx, capY + 1, origin.getZ() + dz), cap);
                }
                if (big && d < radius * 0.4) {
                    setIfFree(level, pos.set(origin.getX() + dx, capY + 2, origin.getZ() + dz), cap);
                }
                if (d > radius - 1.0) {
                    setIfFree(level, pos.set(origin.getX() + dx, capY - 1, origin.getZ() + dz), cap);
                }
            }
        }
        // glowing gills under the cap
        Block[] lichens = {ModBlocks.MAGENTA_NEON_LICHEN.get(), ModBlocks.TEAL_NEON_LICHEN.get()};
        Block gill = lichens[random.nextInt(2)];
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (random.nextFloat() > 0.7f) {
                    continue;
                }
                for (int dy = -1; dy >= -2; dy--) {
                    BlockPos p = new BlockPos(origin.getX() + dx, capY + dy, origin.getZ() + dz);
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).is(ZoneBlocks.GIANT_UMBRAL_CAP.get())) {
                        Block b = random.nextFloat() < 0.8f ? gill : lichens[random.nextInt(2)];
                        level.setBlock(p, b.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true), Block.UPDATE_CLIENTS);
                        break;
                    }
                }
            }
        }
        return true;
    }

    private static void setIfFree(WorldGenLevel level, BlockPos pos, BlockState state) {
        BlockState here = level.getBlockState(pos);
        if (here.isAir() || here.canBeReplaced()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
