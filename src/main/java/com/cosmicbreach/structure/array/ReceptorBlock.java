package com.cosmicbreach.structure.array;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A receptor crystal on the wall, facing the grid: it wants light of {@link #COLOR} and is {@link #LIT} by it. */
public class ReceptorBlock extends PuzzleBlock {
    public static final EnumProperty<Tint> COLOR = EnumProperty.create("color", Tint.class);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ReceptorBlock(Properties properties) {
        super(properties, Block.box(3.0, 0.0, 3.0, 13.0, 15.0, 13.0));
        registerDefaultState(stateDefinition.any().setValue(COLOR, Tint.WHITE).setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, LIT, FACING);
    }
}
