package com.cosmicbreach.structure.array;

import com.cosmicbreach.structure.StructureRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A Lens Array's core: a floor tile with a sun sigil under the grid's middle pedestal. Its block entity
 * ({@link LensCoreBlockEntity}) holds the puzzle, runs it and draws its beams.
 */
public class LensCoreBlock extends BaseEntityBlock {
    public static final MapCodec<LensCoreBlock> CODEC = simpleCodec(LensCoreBlock::new);

    public LensCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LensCoreBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, StructureRegistry.LENS_CORE_ENTITY.get(), LensCoreBlockEntity::serverTick);
    }
}
