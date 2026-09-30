package com.cosmicbreach.progression.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to one player, right after its synced Attunement changed: it gained {@code amount} XP, so
 * the XP line under the hotbar shows the change. Logins and respawns sync the state without it, so
 * the line only appears when XP really comes in.
 */
public record XpGainedPayload(int amount) implements CustomPacketPayload {
    public static final Type<XpGainedPayload> TYPE = new Type<>(CosmicBreach.id("xp_gained"));
    public static final StreamCodec<ByteBuf, XpGainedPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(XpGainedPayload::new, XpGainedPayload::amount);

    @Override
    public Type<XpGainedPayload> type() {
        return TYPE;
    }
}
