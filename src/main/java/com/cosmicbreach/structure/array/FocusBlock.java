package com.cosmicbreach.structure.array;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** The focus under the Sun Aperture: it catches the falling sunbeam and sends it across the grid, {@link #FACING}. */
public class FocusBlock extends PuzzleBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public FocusBlock(Properties properties) {
        super(properties, PIECE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
