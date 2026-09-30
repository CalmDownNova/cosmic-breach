package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.guardian.GuardianAltarBlock;
import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.guardian.LairGuardian;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Silent Nave's altar (Unsung design v1): a cracked basalt lectern at the apse's entrance holding a hymnal of blank
 * pages. It is the framework's guardian altar (the lair's cooldown, the Guardian Echo), and one thing more: a player
 * not yet attuned to the Breach Sanctum who opens the hymnal wakes the Unsung. Everyone else reads the lair's state.
 * {@link #FACING} is the side the reader stands on.
 */
public class HymnalAltarBlock extends GuardianAltarBlock {
    public static final MapCodec<HymnalAltarBlock> CODEC = simpleCodec(HymnalAltarBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(5, 2, 5, 11, 12, 11),
            Block.box(1, 12, 1, 15, 15, 15));

    public HymnalAltarBlock(Properties properties) {
        super("unsung", properties);
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Opening the hymnal: an unattuned player wakes the Unsung (when the lair is armed); anyone else reads its state. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level instanceof ServerLevel server && player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar) {
            if (wake(server, altar, sp)) {
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(altar.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean wake(ServerLevel level, GuardianAltarBlockEntity altar, ServerPlayer player) {
        if (!altar.armed() || com.cosmicbreach.world.LayerAttunement.hasSanctum(player) || !Unsung.fairGame(player)) {
            return false;
        }
        LairGuardian g = altar.current(level);
        if (g == null) {
            g = altar.spawnGuardian(level);
        }
        if (g == null || !g.dormant()) {
            return false;
        }
        level.playSound(null, altar.getBlockPos(), UnsungRegistry.HYMNAL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.cosmicbreach.hymnal.opened"), true);
        g.awaken(player, false);
        return true;
    }
}
