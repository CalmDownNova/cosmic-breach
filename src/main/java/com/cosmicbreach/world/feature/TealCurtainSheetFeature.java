package com.cosmicbreach.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A sheet of the Hanging Wood's teal curtains (Aetheria 1.2): 3 to 10 strands side by side (sometimes two rows deep)
 * under a rock underside, each a different length, from 4 blocks to 40, a few gaps between, so they hang as a ragged
 * curtain rather than as single drips. Place it at an air block right under rock.
 */
public final class TealCurtainSheetFeature extends Feature<NoneFeatureConfiguration> {
    public TealCurtainSheetFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        BlockState curtain = ZoneBlocks.TEAL_CURTAIN.get().defaultBlockState();
        Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Direction across = along.getClockWise();
        int width = 3 + random.nextInt(8);
        int rows = random.nextFloat() < 0.5f ? 2 : 1;
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < width; i++) {
                if (random.nextFloat() < 0.2f) {
                    continue;
                }
                // find the underside over this strand: up to 4 blocks above the origin's height
                BlockPos top = null;
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos p = origin.relative(along, i).relative(across, row).above(dy);
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isFaceSturdy(level, p.above(), Direction.DOWN)) {
                        top = p;
                        break;
                    }
                }
                if (top == null) {
                    continue;
                }
                int length = random.nextFloat() < 0.25f ? 20 + random.nextInt(21) : 4 + random.nextInt(14);
                for (int k = 0; k < length; k++) {
                    pos.set(top.getX(), top.getY() - k, top.getZ());
                    if (!level.getBlockState(pos).isAir()) {
                        break;
                    }
                    level.setBlock(pos, curtain, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed > 0;
    }
}
