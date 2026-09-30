package com.cosmicbreach.structure.array;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A filter lens: tints light passing it, any way, {@link #COLOR}. A {@link #LOOSE} one can be carried. */
public class FilterBlock extends PuzzleBlock {
    public static final EnumProperty<Tint> COLOR = EnumProperty.create("color", Tint.class, t -> t != Tint.WHITE);
    public static final BooleanProperty LOOSE = MirrorBlock.LOOSE;

    public FilterBlock(Properties properties) {
        super(properties, PIECE);
        registerDefaultState(stateDefinition.any().setValue(COLOR, Tint.GOLD).setValue(LOOSE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, LOOSE);
    }
}
