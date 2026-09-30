package com.cosmicbreach.block;

import com.cosmicbreach.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.NetherVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Halo Moss, the tip of a strand hanging under the Reach's islands (GDD 2.2). It grows downward like
 * weeping vines: the tip is this block, everything above it is {@link HaloMossPlantBlock}. Worldgen can
 * place strands with a {@code block_column} feature (body blocks, then this tip, direction down).
 */
public class HaloMossBlock extends GrowingPlantHeadBlock {
    public static final MapCodec<HaloMossBlock> CODEC = simpleCodec(HaloMossBlock::new);
    private static final VoxelShape SHAPE = Block.box(4.0, 9.0, 4.0, 12.0, 16.0, 12.0);

    public HaloMossBlock(Properties properties) {
        super(properties, Direction.DOWN, SHAPE, false, 0.1);
    }

    @Override
    public MapCodec<HaloMossBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getBlocksToGrowWhenBonemealed(RandomSource random) {
        return NetherVines.getBlocksToGrowWhenBonemealed(random);
    }

    @Override
    protected Block getBodyBlock() {
        return ModBlocks.HALO_MOSS_PLANT.get();
    }

    @Override
    protected boolean canGrowInto(BlockState state) {
        return NetherVines.isValidGrowthState(state);
    }
}
