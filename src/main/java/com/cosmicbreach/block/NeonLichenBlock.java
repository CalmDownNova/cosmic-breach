package com.cosmicbreach.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Neon Lichen, the Deep's bioluminescence (GDD 2.2): glow lichen's behaviour (a face on any side of a
 * block, spreads with bone meal, waterloggable, light 7 while it has a face) in magenta or teal.
 * A copy of {@code GlowLichenBlock} rather than a subclass, because glow lichen only merges a second
 * face into itself when the held item is glow lichen. Worldgen can use {@code multiface_growth}.
 */
public class NeonLichenBlock extends MultifaceBlock implements BonemealableBlock, SimpleWaterloggedBlock {
    public static final MapCodec<NeonLichenBlock> CODEC = simpleCodec(NeonLichenBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public NeonLichenBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    public MapCodec<NeonLichenBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.getItemInHand().is(asItem()) || super.canBeReplaced(state, useContext);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return Direction.stream().anyMatch(d -> spreader.canSpreadInAnyDirection(state, level, pos, d.getOpposite()));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        spreader.spreadFromRandomFaceTowardRandomDirection(state, level, pos, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getFluidState().isEmpty();
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return spreader;
    }
}
