package com.cosmicbreach.guardian.heliarch;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One block of a monolith (GDD 7.3): at the Hollowing each plate of the halo slams into the mid ring and stands up as a
 * slab 3 blocks wide and 4 tall, cover from the eclipse. Each block knows its column and row, so the plate's face is
 * painted across all twelve ({@code tools/art/gen_heliarch.py}); {@link #AXIS} is the way the slab runs. Its integrity
 * pips are drawn by the client from the Heliarch's state. Unbreakable; only the beams and the fight's end take it away.
 */
public class MonolithBlock extends Block {
    public static final MapCodec<MonolithBlock> CODEC = simpleCodec(MonolithBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 3);

    public MonolithBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(COLUMN, 0).setValue(ROW, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, COLUMN, ROW);
    }

    /** The state for a monolith's block at {@code column} (0 to 2 along the slab) and {@code row} (0 at the floor). */
    public BlockState cell(boolean alongX, int column, int row) {
        return defaultBlockState().setValue(AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z).setValue(COLUMN, column).setValue(ROW, row);
    }
}
