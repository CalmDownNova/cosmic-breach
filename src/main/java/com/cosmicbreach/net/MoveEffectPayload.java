package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server to clients: a moment of a move effect that only the server decides (where a Gravity Well
 * was planted, when it collapsed and how hard, where a crater opened), for its visuals. {@code entityId}
 * is the player whose move it is, {@code effect} the effect's id, {@code stage} the effect's own
 * numbering of its moments, {@code at} a world position, {@code value} and {@code ticks} a size and a
 * duration where the moment has them.
 */
public record MoveEffectPayload(int entityId, ResourceLocation effect, int stage, Vec3 at, float value, int ticks)
        implements CustomPacketPayload {
    public static final Type<MoveEffectPayload> TYPE = new Type<>(CosmicBreach.id("move_effect"));
    private static final StreamCodec<ByteBuf, Vec3> POSITION = StreamCodec.of(
            (buf, v) -> {
                buf.writeDouble(v.x);
                buf.writeDouble(v.y);
                buf.writeDouble(v.z);
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    public static final StreamCodec<ByteBuf, MoveEffectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MoveEffectPayload::entityId,
            ResourceLocation.STREAM_CODEC, MoveEffectPayload::effect,
            ByteBufCodecs.VAR_INT, MoveEffectPayload::stage,
            POSITION, MoveEffectPayload::at,
            ByteBufCodecs.FLOAT, MoveEffectPayload::value,
            ByteBufCodecs.VAR_INT, MoveEffectPayload::ticks,
            MoveEffectPayload::new);

    @Override
    public Type<MoveEffectPayload> type() {
        return TYPE;
    }
}
