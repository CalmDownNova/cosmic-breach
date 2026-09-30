package com.cosmicbreach.structure.choir;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Conductor's upper half: its hooded head and the arm that holds the baton (the baton is drawn, it moves). */
public class ConductorTopBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 14.0, 13.0);

    public ConductorTopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
