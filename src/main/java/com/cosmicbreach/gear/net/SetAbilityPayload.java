package com.cosmicbreach.gear.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the Set Ability key was pressed. The server decides everything (aim, cooldown, pieces). */
public record SetAbilityPayload() implements CustomPacketPayload {
    public static final SetAbilityPayload INSTANCE = new SetAbilityPayload();
    public static final Type<SetAbilityPayload> TYPE = new Type<>(CosmicBreach.id("set_ability"));
    public static final StreamCodec<ByteBuf, SetAbilityPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<SetAbilityPayload> type() {
        return TYPE;
    }
}
