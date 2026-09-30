package com.cosmicbreach.mount;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server to clients: a mount's moment for its visuals. {@link #BLINK}: a Phase Blink from {@code from} to {@code to}. */
public record MountFxPayload(int kind, int entityId, Vec3 from, Vec3 to) implements CustomPacketPayload {
    public static final int BLINK = 0;

    public static final Type<MountFxPayload> TYPE = new Type<>(CosmicBreach.id("mount_fx"));
    public static final StreamCodec<ByteBuf, MountFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MountFxPayload::kind,
            ByteBufCodecs.VAR_INT, MountFxPayload::entityId,
            MountActionPayload.POSITION, MountFxPayload::from,
            MountActionPayload.POSITION, MountFxPayload::to,
            MountFxPayload::new);

    @Override
    public Type<MountFxPayload> type() {
        return TYPE;
    }
}
