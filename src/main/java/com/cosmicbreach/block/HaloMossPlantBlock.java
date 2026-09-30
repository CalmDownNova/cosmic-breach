package com.cosmicbreach.block;

import com.cosmicbreach.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrowingPlantBodyBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The body of a Halo Moss strand: every block of it except the tip ({@link HaloMossBlock}). */
public class HaloMossPlantBlock extends GrowingPlantBodyBlock {
    public static final MapCodec<HaloMossPlantBlock> CODEC = simpleCodec(HaloMossPlantBlock::new);
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    public HaloMossPlantBlock(Properties properties) {
        super(properties, Direction.DOWN, SHAPE, false);
    }

    @Override
    public MapCodec<HaloMossPlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return ModBlocks.HALO_MOSS.get();
    }
}
