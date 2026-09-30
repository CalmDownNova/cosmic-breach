package com.cosmicbreach.guardian.colossus;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The stump of the Crown Spire's central pillar, where the Prism Colossus stands fused. Lit while a Colossus stands
 * on it; after a kill it dims until the lair's cooldown is over and a new statue grows. Indestructible.
 */
public class CrownPillarBlock extends Block {
    public static final MapCodec<CrownPillarBlock> CODEC = simpleCodec(CrownPillarBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public CrownPillarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
