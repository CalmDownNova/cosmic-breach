package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A Starfall Chute's aperture in a corridor's ceiling (GDD 6.4): three in a row across the corridor ({@link #AXIS}),
 * the middle one ({@link #MIDDLE}) keeping time for all three ({@link StarfallChuteBlockEntity}).
 */
public class StarfallChuteBlock extends BaseEntityBlock {
    public static final MapCodec<StarfallChuteBlock> CODEC = simpleCodec(StarfallChuteBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty MIDDLE = BooleanProperty.create("middle");

    public StarfallChuteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(MIDDLE, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, MIDDLE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MIDDLE) ? new StarfallChuteBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || !state.getValue(MIDDLE) ? null
                : createTickerHelper(type, CryptRegistry.STARFALL_CHUTE_ENTITY.get(), StarfallChuteBlockEntity::serverTick);
    }
}
