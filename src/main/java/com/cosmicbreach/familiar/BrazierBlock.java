package com.cosmicbreach.familiar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Brazier of Solenne (GDD 8.2, Forge I): set a Star Egg in its bowl and it hatches after 10 minutes of loaded time
 * (at once when a Solar Flare burns on the open bowl). The hatchling's Familiar Lantern waits in the bowl until it is
 * taken; use the brazier with an empty hand (or anything but an egg) to take out what it holds. {@link #LIT} while it
 * warms an egg.
 */
public class BrazierBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<BrazierBlock> CODEC = simpleCodec(BrazierBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),     // the foot
            Block.box(5, 2, 5, 11, 8, 11),     // the stem
            Block.box(1, 8, 1, 15, 12, 15));   // the bowl

    public BrazierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BrazierBlockEntity brazier)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(FamiliarRegistry.STAR_EGG.get()) && brazier.held().isEmpty()) {
            if (!level.isClientSide()) {
                brazier.setEgg(stack.copyWithCount(1));
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BrazierBlockEntity brazier) || brazier.held().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack out = brazier.take();
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BrazierBlockEntity brazier && !brazier.held().isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, brazier.take());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrazierBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityTicker<BrazierBlockEntity> ticker = level.isClientSide() ? BrazierBlockEntity::clientTick : BrazierBlockEntity::serverTick;
        return createTickerHelper(type, FamiliarRegistry.BRAZIER_ENTITY.get(), ticker);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (state.getValue(LIT)) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.8,
                        z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.01 + random.nextDouble() * 0.02, 0.0);
            }
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 1.1,
                        z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.015, 0.0);
            }
        } else if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.78,
                    z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.005, 0.0);
        }
    }
}
