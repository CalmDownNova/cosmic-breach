package com.cosmicbreach.structure.vault;

import com.cosmicbreach.structure.StructureRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A structure's vault: sealed until its Lens Array is solved ({@link #READY}), then it opens once for each player,
 * like the vanilla Vault. {@code tier} picks its loot ({@link VaultBlockEntity#lootTable}).
 */
public class VaultBlock extends BaseEntityBlock {
    public static final BooleanProperty READY = BooleanProperty.create("ready");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);

    private final int tier;
    private final MapCodec<VaultBlock> codec;

    public VaultBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        this.codec = simpleCodec(p -> new VaultBlock(p, tier));
        registerDefaultState(stateDefinition.any().setValue(READY, false).setValue(FACING, Direction.NORTH));
    }

    public int tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(READY, FACING);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            vault.open(server, sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(READY) || random.nextInt(3) != 0 || !(level.getBlockEntity(pos) instanceof VaultBlockEntity vault)
                || vault.openedByLocal()) {
            return;
        }
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultBlockEntity(pos, state);
    }

    public static boolean isVault(BlockState state) {
        return state.is(StructureRegistry.RELIQUARY_VAULT.get()) || state.is(StructureRegistry.OBSERVATORY_VAULT.get());
    }
}
