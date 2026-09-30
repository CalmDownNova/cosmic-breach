package com.cosmicbreach.structure.crypt.trap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One tile of a Crushing Gravity Plate's 5 by 5 sigil (GDD 6.4): a floor tile like any other until a careful player
 * sees its faint rings. {@link #PART} places it in the sigil ({@code (dx + 2) + 5 (dz + 2)} from the middle); the
 * plate's Gravity Piston hangs in the ceiling over the middle ({@link GravityPistonBlockEntity}).
 */
public class GravitySigilBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 24);
    public static final int MIDDLE = 12;

    public GravitySigilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, MIDDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public BlockState part(int dx, int dz) {
        return defaultBlockState().setValue(PART, (dx + 2) + 5 * (dz + 2));
    }

    /** The sigil's middle tile for the tile at {@code pos}. */
    public static BlockPos middle(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.offset(2 - part % 5, 0, 2 - part / 5);
    }
}
