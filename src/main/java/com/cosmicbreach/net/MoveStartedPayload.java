package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to everyone tracking a player except that player: it started a move (for its animation). */
public record MoveStartedPayload(int entityId, ResourceLocation moveId) implements CustomPacketPayload {
    public static final Type<MoveStartedPayload> TYPE = new Type<>(CosmicBreach.id("move_started"));
    public static final StreamCodec<ByteBuf, MoveStartedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MoveStartedPayload::entityId,
            ResourceLocation.STREAM_CODEC, MoveStartedPayload::moveId,
            MoveStartedPayload::new);

    @Override
    public Type<MoveStartedPayload> type() {
        return TYPE;
    }
}
