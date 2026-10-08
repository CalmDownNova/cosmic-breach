package com.cosmicbreach.provision;

import com.cosmicbreach.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Fallen Driftwood (1.1): a petrified log the Drift's currents carried up, lying on the Reach's ground. Three to six
 * Driftwood logs end to end along X or Z, only where every one of them rests on solid ground with air above, and half the
 * time a one or two block stump standing beside one end. Breakable by hand: the Reach's first planks, sticks, crafting
 * table and fuel. The origin is the air block above the surface.
 */
public final class FallenDriftwoodFeature extends Feature<NoneFeatureConfiguration> {
    public FallenDriftwoodFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        int length = 3 + random.nextInt(4);
        BlockPos start = origin.relative(along.getOpposite(), length / 2);
        List<BlockPos> log = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            BlockPos p = start.relative(along, i);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                    || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                return false;
            }
            log.add(p);
        }
        BlockState lying = ModBlocks.DRIFTWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        for (BlockPos p : log) {
            level.setBlock(p, lying, Block.UPDATE_CLIENTS);
        }
        if (random.nextBoolean()) {
            Direction side = axis == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
            BlockPos stump = (random.nextBoolean() ? log.get(0) : log.get(log.size() - 1)).relative(side, 2);
            int height = 1 + random.nextInt(2);
            if (level.getBlockState(stump.below()).isFaceSturdy(level, stump.below(), Direction.UP)) {
                BlockState standing = ModBlocks.DRIFTWOOD_LOG.get().defaultBlockState();
                for (int h = 0; h < height && level.getBlockState(stump.above(h)).isAir(); h++) {
                    level.setBlock(stump.above(h), standing, Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }
}
