package com.cosmicbreach.guardian.unsung;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Rift Glass lit from inside by Neon Lichen, the Silent Nave's apse windows (Unsung design v1): magenta or teal, bright
 * while the lair sleeps ({@link #LIT}, light 7), dimmed one by one as the Unsung wakes so the fight is lit by the
 * singers, and lit again when it ends. Part of the lair: it can't be broken.
 */
public class LichenWindowBlock extends Block {
    public static final MapCodec<LichenWindowBlock> CODEC = simpleCodec(LichenWindowBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final EnumProperty<Hue> HUE = EnumProperty.create("hue", Hue.class);

    /** The lichen's colour. */
    public enum Hue implements StringRepresentable {
        MAGENTA, TEAL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public LichenWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(HUE, Hue.MAGENTA));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, HUE);
    }
}
