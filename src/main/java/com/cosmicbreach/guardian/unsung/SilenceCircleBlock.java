package com.cosmicbreach.guardian.unsung;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * An inlay of a circle of silence in the choir floor (Unsung design v1): dark Rift Glass that lights white
 * ({@link #LIT}, light 8) through a Harmonize warning to show where the choir's chord can't reach. Part of the lair.
 */
public class SilenceCircleBlock extends Block {
    public static final MapCodec<SilenceCircleBlock> CODEC = simpleCodec(SilenceCircleBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public SilenceCircleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
