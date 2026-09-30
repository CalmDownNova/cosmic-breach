package com.cosmicbreach.entity.stalker;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Mask Shard (GDD 7.1: 5% from a Hollow Stalker, a trophy block): a broken piece of its porcelain mask, one eye slit
 * still faintly magenta, set upright on the floor and turned to face whoever placed it.
 */
public class MaskShardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MaskShardBlock> CODEC = simpleCodec(MaskShardBlock::new);
    private static final VoxelShape NORTH = Block.box(3, 0, 6, 13, 12, 10);
    private static final VoxelShape EAST = Block.box(6, 0, 3, 10, 12, 13);

    public MaskShardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH : EAST;
    }
}
