package com.cosmicbreach.guardian;

import com.cosmicbreach.CosmicBreach;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a guardian's boss bar shows beyond vanilla's bar (S2C, to the players who see the bar): the Break gauge
 * (0 to 1) and whether the guardian is Broken, a countdown (ticks left of {@code countdownMax}, 0 max for none),
 * the boss music to loop (a sound event id, empty for none), and {@code gone} when the bar is taken away.
 */
public record GuardianBarPayload(UUID bar, float gauge, boolean broken, int countdown, int countdownMax, String music,
                                 boolean gone) implements CustomPacketPayload {
    public static final Type<GuardianBarPayload> TYPE = new Type<>(CosmicBreach.id("guardian_bar"));
    public static final StreamCodec<FriendlyByteBuf, GuardianBarPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, p.bar());
                buf.writeFloat(p.gauge());
                buf.writeBoolean(p.broken());
                ByteBufCodecs.VAR_INT.encode(buf, p.countdown());
                ByteBufCodecs.VAR_INT.encode(buf, p.countdownMax());
                buf.writeUtf(p.music(), 256);
                buf.writeBoolean(p.gone());
            },
            buf -> new GuardianBarPayload(UUIDUtil.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readBoolean(),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readUtf(256), buf.readBoolean()));

    @Override
    public Type<GuardianBarPayload> type() {
        return TYPE;
    }
}
