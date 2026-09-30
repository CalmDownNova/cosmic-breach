package com.cosmicbreach.progression.net;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.progression.Allocation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: spend these points (one visit to the allocation screen). The server checks everything. */
public record AllocatePointsPayload(Allocation add) implements CustomPacketPayload {
    public static final Type<AllocatePointsPayload> TYPE = new Type<>(CosmicBreach.id("allocate_points"));
    public static final StreamCodec<ByteBuf, AllocatePointsPayload> STREAM_CODEC =
            Allocation.STREAM_CODEC.map(AllocatePointsPayload::new, AllocatePointsPayload::add);

    @Override
    public Type<AllocatePointsPayload> type() {
        return TYPE;
    }
}
