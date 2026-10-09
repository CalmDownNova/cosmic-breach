package com.cosmicbreach.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A strand of the Hanging Wood's teal curtains (Aetheria 1.2): glowing lichen hanging in long strands from rock, block
 * under block. It hangs from a solid underside or from the strand above it, and falls away (drops nothing) when that
 * is gone. No collision: players and mantas pass through.
 */
public class TealCurtainBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public TealCurtainBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.is(this) || above.isFaceSturdy(level, pos.above(), Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos,
            BlockPos neighbourPos) {
        return direction == Direction.UP && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
}
