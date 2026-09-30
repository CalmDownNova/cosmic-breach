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

/**
 * A Gravity Piston (GDD 6.4): set in the ceiling over a Crushing Gravity Plate's sigil, it looks like the ceiling
 * round it. Its block entity runs the plate ({@link GravityPistonBlockEntity}); the client draws the 5 by 5 head
 * when it slams.
 */
public class GravityPistonBlock extends BaseEntityBlock {
    public static final MapCodec<GravityPistonBlock> CODEC = simpleCodec(GravityPistonBlock::new);

    public GravityPistonBlock(Properties properties) {
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
        return new GravityPistonBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, CryptRegistry.GRAVITY_PISTON_ENTITY.get(), GravityPistonBlockEntity::serverTick);
    }
}
