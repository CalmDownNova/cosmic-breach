package com.cosmicbreach.familiar;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the familiar key (H). Held: summon or dismiss ({@link #TOGGLE}); tapped: the next mode ({@link #CYCLE}). */
public record FamiliarKeyPayload(int action) implements CustomPacketPayload {
    public static final int TOGGLE = 0;
    public static final int CYCLE = 1;

    public static final Type<FamiliarKeyPayload> TYPE = new Type<>(CosmicBreach.id("familiar_key"));
    public static final StreamCodec<ByteBuf, FamiliarKeyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FamiliarKeyPayload::action, FamiliarKeyPayload::new);

    @Override
    public Type<FamiliarKeyPayload> type() {
        return TYPE;
    }
}
