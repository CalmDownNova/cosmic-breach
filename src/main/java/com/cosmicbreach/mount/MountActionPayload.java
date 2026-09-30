package com.cosmicbreach.mount;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Client to server: a rider's mount did its move. {@link #BLINK}: the rider's client blinked its Drift Manta from
 * {@code from} to {@code to} (the client moves the vehicle, as vanilla has it move every vehicle); the server checks
 * the cooldown and the distance, then starts the cooldown and the shield, or sends the manta back.
 */
public record MountActionPayload(int action, Vec3 from, Vec3 to) implements CustomPacketPayload {
    public static final int BLINK = 0;

    public static final Type<MountActionPayload> TYPE = new Type<>(CosmicBreach.id("mount_action"));
    static final StreamCodec<ByteBuf, Vec3> POSITION = StreamCodec.of(
            (buf, v) -> {
                buf.writeDouble(v.x);
                buf.writeDouble(v.y);
                buf.writeDouble(v.z);
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    public static final StreamCodec<ByteBuf, MountActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MountActionPayload::action,
            POSITION, MountActionPayload::from,
            POSITION, MountActionPayload::to,
            MountActionPayload::new);

    @Override
    public Type<MountActionPayload> type() {
        return TYPE;
    }
}
