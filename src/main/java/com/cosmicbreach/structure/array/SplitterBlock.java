package com.cosmicbreach.structure.array;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * A splitter prism: light heading {@link #FACING} leaves as two beams, 90 degrees either side; from any other
 * side the prism drinks it. Its point faces the light it takes. Use turns it a quarter clockwise, sneak-use back.
 */
public class SplitterBlock extends PuzzleBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SplitterBlock(Properties properties) {
        super(properties, PIECE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
