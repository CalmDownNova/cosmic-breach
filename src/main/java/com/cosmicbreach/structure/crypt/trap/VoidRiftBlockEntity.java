package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Void Rift cluster's timer, in its middle tile (GDD 6.4, {@link VoidRiftRules}). {@link CryptTraps} reports each
 * tick a player is on one of its tiles, and whether they are truly standing; the sixth standing tick springs it (a
 * crack, heard by all), on tick 16 from the first of them the nine tiles open for 60 ticks (the fall), then close and
 * re-arm. A tick off the cluster starts a player's count again. Nothing ticks while it is armed and untouched.
 */
public class VoidRiftBlockEntity extends BlockEntity {
    private final Map<UUID, long[]> standing = new HashMap<>();
    private long openAt = -1;
    private long closeAt = -1;
    private long armedAt = -1;
    /** For tests: {first tick stood, sprung, opened, closed}. */
    private final long[] last = {-1, -1, -1, -1};

    public VoidRiftBlockEntity(BlockPos pos, BlockState state) {
        super(CryptRegistry.VOID_RIFT_ENTITY.get(), pos, state);
    }

    public boolean open() {
        return getBlockState().getValue(VoidRiftTileBlock.OPEN);
    }

    public long[] timeline() {
        return last.clone();
    }

    /** The cluster's 3 by 3 footprint, a block tall (for the renderer and tests). */
    public AABB patch() {
        return new AABB(worldPosition).inflate(1, 0, 1);
    }

    /**
     * {@code player} is on one of this cluster's tiles on tick {@code now}; {@code still}: truly standing
     * ({@link VoidRiftRules#standing}). A player not reported on the tick before has stepped off: their count starts again.
     */
    void stand(ServerLevel level, ServerPlayer player, boolean still, long now) {
        if (openAt >= 0 || closeAt >= 0 || now < armedAt) {
            return;
        }
        long[] s = standing.computeIfAbsent(player.getUUID(), id -> new long[] {0, Long.MIN_VALUE});
        // not on the cluster the tick before: they stepped off, and their count starts again
        int before = VoidRiftRules.count((int) s[0], s[1] == now - 1, false);
        int count = VoidRiftRules.count(before, true, still);
        if (before == 0 && count == 1) {
            last[0] = now;
        }
        s[0] = count;
        s[1] = now;
        if (VoidRiftRules.springs(count)) {
            spring(level, now);
        }
    }

    /** Springs the patch now: it opens {@link VoidRiftRules#openDelay} ticks later. */
    public void spring(ServerLevel level, long now) {
        if (openAt >= 0 || closeAt >= 0) {
            return;
        }
        openAt = now + VoidRiftRules.openDelay();
        last[1] = now;
        standing.clear();
        level.playSound(null, worldPosition.above(), CryptRegistry.RIFT_CRACK.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VoidRiftBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.openAt < 0 && be.closeAt < 0) {
            return;
        }
        long now = level.getGameTime();
        if (be.openAt >= 0 && now >= be.openAt) {
            be.openAt = -1;
            be.closeAt = now + VoidRiftRules.OPEN_TICKS;
            be.last[2] = now;
            be.setOpen(server, true);
            server.playSound(null, pos, CryptRegistry.RIFT_FALL.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 40, 1.0, 0.2, 1.0, 0.02);
        } else if (be.closeAt >= 0 && now >= be.closeAt) {
            be.closeAt = -1;
            be.last[3] = now;
            be.armedAt = now + VoidRiftRules.REARM_TICKS;
            be.setOpen(server, false);
        }
    }

    private void setOpen(ServerLevel level, boolean open) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = worldPosition.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof VoidRiftTileBlock && VoidRiftTileBlock.middle(p, s).equals(worldPosition)
                        && s.getValue(VoidRiftTileBlock.OPEN) != open) {
                    level.setBlock(p, s.setValue(VoidRiftTileBlock.OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("open_at", openAt);
        tag.putLong("close_at", closeAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        openAt = tag.contains("open_at") ? tag.getLong("open_at") : -1;
        closeAt = tag.contains("close_at") ? tag.getLong("close_at") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
