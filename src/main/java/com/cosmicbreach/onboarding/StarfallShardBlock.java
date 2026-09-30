package com.cosmicbreach.onboarding;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A fallen Starfall Shard (GDD 1.3): a glowing crystal (light 15) in its crater that breaks by hand at once
 * and drops the Starfall Shard item. Its block entity keeps the time it fell, for the light pillar its
 * renderer draws for five minutes, and hums.
 */
public class StarfallShardBlock extends BaseEntityBlock {
    public static final MapCodec<StarfallShardBlock> CODEC = simpleCodec(StarfallShardBlock::new);
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 14.0, 13.0);

    public StarfallShardBlock(Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarfallShardBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, OnboardingRegistry.STARFALL_SHARD_ENTITY.get(),
                level.isClientSide ? StarfallShardBlockEntity::clientTick : StarfallShardBlockEntity::serverTick);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(OnboardingRegistry.STARFALL_SHARD.get());
    }
}
