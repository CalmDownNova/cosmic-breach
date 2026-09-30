package com.cosmicbreach.structure.trap;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A Kinetic Tripwire's wall emitter (GDD 6.4): a Driftstone block with a cyan slit, {@link #FACING} along its
 * thread. Two face each other across a corridor; when someone rushes the thread between them, both fire. It can
 * be mined (hard, iron and up): disarming a trap is a fair answer to one.
 */
public class KineticEmitterBlock extends BaseEntityBlock {
    public static final MapCodec<KineticEmitterBlock> CODEC = simpleCodec(KineticEmitterBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public KineticEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticEmitterBlockEntity(pos, state);
    }
}
