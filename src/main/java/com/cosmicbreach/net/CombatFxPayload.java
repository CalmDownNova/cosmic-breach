package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to clients: a combat moment on an entity, for effects. {@code offset} is where it happened,
 * relative to the entity's position (the parry's meeting point; zero when the entity itself is the
 * place), sent as floats; {@code value} is a size where a kind has one (a plunge's fall in blocks).
 */
public record CombatFxPayload(int entityId, Kind kind, Vec3 offset, float value) implements CustomPacketPayload {
    /** New kinds go at the end: the ordinal is the wire byte. */
    public enum Kind {
        /** A parry caught an attack (to trackers and the parrying player); the offset is where the blades met. */
        PARRY,
        /** A dash's i-frames dodged an attack in the perfect window (to trackers and the player). */
        PERFECT_DODGE,
        /** A player dashed (to trackers; the dasher predicted it). */
        DASH,
        /** An entity's poise broke (to trackers and the entity). */
        STAGGER,
        /** A player raised a parry (to trackers; the parrying player predicted it). */
        PARRY_START,
        /** A player started holding a charge (to trackers; the charging player predicted it). */
        CHARGE_START,
        /** A player's charge ended without a release: let go early, cancelled, swapped away (to trackers). */
        CHARGE_STOP,
        /** A player's plunge landed (to trackers; the plunging player predicted it); the value is the fall. */
        PLUNGE_LANDED;

        private static final Kind[] BY_ID = values();

        public byte id() {
            return (byte) ordinal();
        }

        /** An unknown byte fails the packet rather than guessing. */
        public static Kind byId(byte id) {
            if (id < 0 || id >= BY_ID.length) {
                throw new IllegalArgumentException("unknown combat fx kind " + id);
            }
            return BY_ID[id];
        }
    }

    public static final Type<CombatFxPayload> TYPE = new Type<>(CosmicBreach.id("combat_fx"));
    /** The offset as three floats: it is small, so floats keep it exact enough. */
    private static final StreamCodec<ByteBuf, Vec3> OFFSET_CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeFloat((float) v.x);
                buf.writeFloat((float) v.y);
                buf.writeFloat((float) v.z);
            },
            buf -> new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
    public static final StreamCodec<ByteBuf, CombatFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatFxPayload::entityId,
            ByteBufCodecs.BYTE.map(Kind::byId, Kind::id), CombatFxPayload::kind,
            OFFSET_CODEC, CombatFxPayload::offset,
            ByteBufCodecs.FLOAT, CombatFxPayload::value,
            CombatFxPayload::new);

    /** A moment that happened at the entity itself and has no size. */
    public CombatFxPayload(int entityId, Kind kind) {
        this(entityId, kind, Vec3.ZERO, 0f);
    }

    @Override
    public Type<CombatFxPayload> type() {
        return TYPE;
    }
}
