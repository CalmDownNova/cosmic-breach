package com.cosmicbreach.structure.trap;

import com.cosmicbreach.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How long the emitter's thread is ({@link #length} blocks in front of it, 0 for the facing partner, which draws
 * nothing): the client draws the thread from here when a careful player is near.
 */
public class KineticEmitterBlockEntity extends BlockEntity {
    private int length;

    public KineticEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(StructureRegistry.KINETIC_EMITTER_ENTITY.get(), pos, state);
    }

    public int length() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("length", length);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        length = tag.getInt("length");
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
