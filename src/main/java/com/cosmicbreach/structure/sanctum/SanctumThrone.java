package com.cosmicbreach.structure.sanctum;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Regent's Throne and the summon (GDD 7.3). Setting a Dying Star Heart on the throne consumes it and calls
 * {@link #onHeartPlaced}, which hands the moment to the {@link Summoner} the boss task registers
 * ({@link #setSummoner}). Until one is registered (or when it declines), the throne hums, the sky over the players near it
 * flickers, and after {@value #ANSWER_TICKS} ticks a subtitle says nothing answers yet and the Heart comes back.
 */
public final class SanctumThrone {
    /** How long the throne holds a Heart nothing answers. */
    public static final int ANSWER_TICKS = 60;
    /** Players this close see the sky flicker. */
    public static final double FLICKER_RADIUS = 96.0;

    /** What the boss task registers: begins a summon at the throne. */
    @FunctionalInterface
    public interface Summoner {
        /**
         * {@code player} has set a Heart (consumed) on the throne at {@code throne}, whose state now shows it. Return
         * true to take it from here (the Heart is spent), false to hand it back (say why to the player yourself).
         */
        boolean summon(ServerLevel level, BlockPos throne, ServerPlayer player);
    }

    private record Pending(ResourceKey<Level> level, BlockPos pos, UUID player, long returnAt) {
    }

    private static volatile @Nullable Summoner summoner;
    private static final List<Pending> PENDING = new ArrayList<>();
    private static int placed;
    private static int returned;

    private SanctumThrone() {
    }

    public static void setSummoner(@Nullable Summoner s) {
        summoner = s;
    }

    public static @Nullable Summoner summoner() {
        return summoner;
    }

    /** A Heart was set on the throne at {@code pos} (already taken from {@code player}'s hand). */
    public static void onHeartPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        placed++;
        setHeart(level, pos, true);
        Summoner s = summoner;
        if (s != null) {
            if (s.summon(level, pos, player)) {
                return;
            }
            setHeart(level, pos, false);
            returnHeart(level, pos, player);
            return;
        }
        // nothing answers yet
        level.playSound(null, pos, SanctumRegistry.THRONE_HUM.get(), SoundSource.BLOCKS, 3.0f, 1.0f);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.5, 0.4, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 20, 1.5, 1.5, 1.5, 0.05);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= FLICKER_RADIUS * FLICKER_RADIUS) {
                SanctumNet.sendFlicker(p, ANSWER_TICKS);
            }
        }
        synchronized (PENDING) {
            PENDING.add(new Pending(level.dimension(), pos.immutable(), player.getUUID(), level.getGameTime() + ANSWER_TICKS));
        }
    }

    public static void tick(MinecraftServer server) {
        List<Pending> due = new ArrayList<>();
        synchronized (PENDING) {
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                ServerLevel level = server.getLevel(p.level());
                if (level == null || level.getGameTime() >= p.returnAt()) {
                    it.remove();
                    if (level != null) {
                        due.add(p);
                    }
                }
            }
        }
        for (Pending p : due) {
            ServerLevel level = server.getLevel(p.level());
            ServerPlayer player = server.getPlayerList().getPlayer(p.player());
            setHeart(level, p.pos(), false);
            returnHeart(level, p.pos(), player);
            for (ServerPlayer near : level.players()) {
                if (near.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY(), p.pos().getZ() + 0.5) <= 48 * 48) {
                    Sanctums.subtitle(near, Component.translatable("cosmicbreach.sanctum.throne.silent"));
                }
            }
        }
    }

    /** Gives a Heart back: into {@code player}'s inventory if they are near and have room, else onto the dais before the throne. */
    public static void returnHeart(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        returned++;
        ItemStack heart = new ItemStack(SanctumRegistry.DYING_STAR_HEART.get());
        if (player != null && player.isAlive() && player.level() == level
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 64 * 64 && player.getInventory().add(heart)) {
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f);
            return;
        }
        BlockState s = level.getBlockState(pos);
        Direction f = s.hasProperty(SanctumThroneBlock.FACING) ? s.getValue(SanctumThroneBlock.FACING) : Direction.NORTH;
        ItemEntity e = new ItemEntity(level, pos.getX() + 0.5 + f.getStepX() * 1.2, pos.getY() + 0.6, pos.getZ() + 0.5 + f.getStepZ() * 1.2,
                heart);
        e.setDefaultPickUpDelay();
        level.addFreshEntity(e);
    }

    private static void setHeart(ServerLevel level, BlockPos pos, boolean heart) {
        BlockState s = level.getBlockState(pos);
        if (s.is(SanctumRegistry.SANCTUM_THRONE.get()) && s.getValue(SanctumThroneBlock.HEART) != heart) {
            level.setBlock(pos, s.setValue(SanctumThroneBlock.HEART, heart), Block.UPDATE_ALL);
        }
    }

    /** "placed returned" since the server started (for tests). */
    public static String counters() {
        return placed + " " + returned;
    }

    public static void reset() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        placed = 0;
        returned = 0;
    }
}
