package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptRegistry;
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

/** The Void Pocket's sigil, in the middle of its floor: the pocket's memory ({@link VoidPocketBlockEntity}). */
public class VoidPocketBlock extends BaseEntityBlock {
    public static final MapCodec<VoidPocketBlock> CODEC = simpleCodec(VoidPocketBlock::new);

    public VoidPocketBlock(Properties properties) {
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
        return new VoidPocketBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, CryptRegistry.VOID_POCKET_ENTITY.get(), VoidPocketBlockEntity::serverTick);
    }
}
