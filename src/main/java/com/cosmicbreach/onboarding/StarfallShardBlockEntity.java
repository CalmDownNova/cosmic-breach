package com.cosmicbreach.onboarding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The time a shard fell ({@link #fellAt}, game time; synced to clients), which the pillar renderer counts its
 * five minutes from. A shard placed any other way (a command) has no pillar.
 */
public class StarfallShardBlockEntity extends BlockEntity {
    /** How long the light pillar stands: five minutes. */
    public static final int PILLAR_TICKS = 5 * 60 * 20;
    private static final int HUM_EVERY = 80;
    private long fellAt = Long.MIN_VALUE;

    public StarfallShardBlockEntity(BlockPos pos, BlockState state) {
        super(OnboardingRegistry.STARFALL_SHARD_ENTITY.get(), pos, state);
    }

    public long fellAt() {
        return fellAt;
    }

    /** Marks the shard as fallen now: its pillar starts. Server side; synced to clients. */
    public void fell(long gameTime) {
        fellAt = gameTime;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** True while the pillar stands at {@code gameTime}. */
    public boolean pillarAt(long gameTime) {
        return fellAt != Long.MIN_VALUE && gameTime - fellAt >= 0 && gameTime - fellAt < PILLAR_TICKS;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, StarfallShardBlockEntity shard) {
        if ((level.getGameTime() + pos.hashCode()) % HUM_EVERY == 0) {
            level.playSound(null, pos, OnboardingRegistry.SHARD_HUM.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, StarfallShardBlockEntity shard) {
        if (level.random.nextInt(6) == 0) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double r = 0.2 + level.random.nextDouble() * 0.35;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.3 + level.random.nextDouble() * 0.6,
                    pos.getZ() + 0.5 + Math.sin(a) * r, 0.0, 0.015 + level.random.nextDouble() * 0.02, 0.0);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (fellAt != Long.MIN_VALUE) {
            tag.putLong("fell_at", fellAt);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fellAt = tag.contains("fell_at") ? tag.getLong("fell_at") : Long.MIN_VALUE;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
