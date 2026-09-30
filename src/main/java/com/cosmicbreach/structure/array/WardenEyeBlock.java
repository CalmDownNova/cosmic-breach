package com.cosmicbreach.structure.array;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** The Warden Eye on the wall: light sent into it wakes it ({@link #AWAKE}) and it calls two Shardlings. */
public class WardenEyeBlock extends PuzzleBlock {
    public static final BooleanProperty AWAKE = BooleanProperty.create("awake");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public WardenEyeBlock(Properties properties) {
        super(properties, Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0));
        registerDefaultState(stateDefinition.any().setValue(AWAKE, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AWAKE, FACING);
    }
}
