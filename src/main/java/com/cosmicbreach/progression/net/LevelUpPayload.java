package com.cosmicbreach.progression.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the player and everyone tracking it: it reached Attunement {@code level} ({@code gained}
 * levels at once). Everyone sees the burst of star glints; the player also sees the message.
 */
public record LevelUpPayload(int entityId, int level, int gained) implements CustomPacketPayload {
    public static final Type<LevelUpPayload> TYPE = new Type<>(CosmicBreach.id("level_up"));
    public static final StreamCodec<ByteBuf, LevelUpPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LevelUpPayload::entityId,
            ByteBufCodecs.VAR_INT, LevelUpPayload::level,
            ByteBufCodecs.VAR_INT, LevelUpPayload::gained,
            LevelUpPayload::new);

    @Override
    public Type<LevelUpPayload> type() {
        return TYPE;
    }
}
