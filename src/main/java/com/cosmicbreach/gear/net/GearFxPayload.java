package com.cosmicbreach.gear.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players near: a set effect happened {@code at}, so draw it. {@code entityId} is who made it,
 * {@code value} and {@code ticks} depend on the kind (see {@link Kind}).
 */
public record GearFxPayload(int kind, int entityId, Vec3 at, float value, int ticks) implements CustomPacketPayload {
    public static final Type<GearFxPayload> TYPE = new Type<>(CosmicBreach.id("gear_fx"));
    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);
    public static final StreamCodec<ByteBuf, GearFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GearFxPayload::kind,
            ByteBufCodecs.VAR_INT, GearFxPayload::entityId,
            VEC3, GearFxPayload::at,
            ByteBufCodecs.FLOAT, GearFxPayload::value,
            ByteBufCodecs.VAR_INT, GearFxPayload::ticks,
            GearFxPayload::new);

    /** What happened. */
    public static final class Kind {
        /** Heavy Landing's shockwave at the feet; value = blocks fallen. */
        public static final int SHOCKWAVE = 0;
        /** Meteor Call marked a spot; value = radius, ticks = until the meteor lands. */
        public static final int METEOR_MARK = 1;
        /** The meteor landed; value = radius, ticks = how long the crater burns. */
        public static final int METEOR_IMPACT = 2;

        private Kind() {
        }
    }

    @Override
    public Type<GearFxPayload> type() {
        return TYPE;
    }
}
