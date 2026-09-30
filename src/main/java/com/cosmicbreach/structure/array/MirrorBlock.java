package com.cosmicbreach.structure.array;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A mirror on its pedestal, silvered on both faces. {@link #TURN} is the puzzle core's turn: 0 lies east to
 * west, each step 45 degrees clockwise seen from above (1 runs north-west to south-east, 2 north to south, 3
 * south-west to north-east). Use turns it one step clockwise, sneak-use one back. A {@link #LOOSE} one (gold
 * rim) can be lifted by hand and set on any empty pedestal.
 */
public class MirrorBlock extends PuzzleBlock {
    public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);
    public static final BooleanProperty LOOSE = BooleanProperty.create("loose");

    public MirrorBlock(Properties properties) {
        super(properties, PIECE);
        registerDefaultState(stateDefinition.any().setValue(TURN, 0).setValue(LOOSE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TURN, LOOSE);
    }
}
