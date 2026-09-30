package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.guardian.GuardianAltarBlock;
import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.guardian.LairGuardian;
import com.cosmicbreach.world.LayerAttunement;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Leviathan's altar (Thalassine Leviathan design v1, "Awakening"): a Rimeglass bell on the platform nearest the
 * Rift's entrance. Ringing it (using it empty-handed) wakes the sleeping Leviathan for a player not yet attuned to the
 * Deep once the lair is armed; anyone wakes it with a Guardian Echo after the 20-minute cooldown (the altar's own
 * rule). Otherwise the bell tells the lair's state. It rings every time.
 */
public class RiftBellBlock extends GuardianAltarBlock {
    public static final MapCodec<RiftBellBlock> BELL_CODEC = simpleCodec(RiftBellBlock::new);
    /** Times a bell has woken its Leviathan, and times one was rung (tests). */
    private static int wakes;
    private static int rings;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(7, 2, 7, 9, 5, 9),
            Block.box(3, 5, 3, 13, 14, 13), Block.box(5, 14, 5, 11, 16, 11));

    public RiftBellBlock(Properties properties) {
        super("leviathan", properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return BELL_CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar && player instanceof ServerPlayer sp) {
            level.playSound(null, pos, LeviathanRegistry.BELL.get(), SoundSource.BLOCKS, 3.0f, 1.0f);
            rings++;
            sp.displayClientMessage(ring(altar, (ServerLevel) level, sp), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Times a bell has woken its Leviathan since the game started (tests). */
    public static int wakes() {
        return wakes;
    }

    /** Times a bell was rung since the game started (tests). */
    public static int rings() {
        return rings;
    }

    /** Rings the bell for {@code player}: wakes the Leviathan if it may, and says what happened. */
    static Component ring(GuardianAltarBlockEntity altar, ServerLevel level, ServerPlayer player) {
        LairGuardian g = altar.current(level);
        boolean attuned = LayerAttunement.has(player, altar.type().attunes());
        if (altar.armed() && g != null && g.dormant()) {
            if (attuned) {
                return Component.translatable("message.cosmicbreach.rift_bell.attuned");
            }
            if (g instanceof ThalassineLeviathan l && !l.fairGame(player)) {
                return altar.status();
            }
            g.awaken(player, false);
            wakes++;
            return Component.translatable("message.cosmicbreach.rift_bell.wakes");
        }
        return altar.status();
    }
}
