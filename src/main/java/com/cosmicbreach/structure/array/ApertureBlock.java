package com.cosmicbreach.structure.array;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The Sun Aperture in the ceiling, over the focus. {@link #OPEN} once the room has woken: a sunbeam falls from it. */
public class ApertureBlock extends PuzzleBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public ApertureBlock(Properties properties) {
        super(properties, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0));
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }
}
