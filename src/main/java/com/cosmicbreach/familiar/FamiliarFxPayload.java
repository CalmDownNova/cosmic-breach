package com.cosmicbreach.familiar;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to clients: a familiar's moment, for its look and sound on the client. {@code a} and {@code b} are entity ids
 * (-1 for none), {@code at} a world position, {@code value} a number where the moment has one.
 */
public record FamiliarFxPayload(int kind, int a, int b, Vec3 at, int value) implements CustomPacketPayload {
    /** A familiar appeared: a = the familiar, at its place, value its kind. */
    public static final int SUMMON = 0;
    /** A familiar went back into its lantern: at its place, value its kind. */
    public static final int DISMISS = 1;
    /** A familiar died: at its place, value its kind. */
    public static final int DEATH = 2;
    /** A familiar teleported home: a = the familiar, at where it left. */
    public static final int TELEPORT = 3;
    /** The Emberwisp threw Scorch: a = the wisp, b = the target. */
    public static final int SCORCH = 4;
    /** Kindled flared on a player: a = the player, b = the wisp. */
    public static final int KINDLED = 5;
    /** The Gravikin taunted: a = the Gravikin, value how many answered. */
    public static final int TAUNT = 6;
    /** The Prism Moth threw a glint: a = the moth, b = the target, value the target's stacks now. */
    public static final int GLINT = 7;
    /** Refract on a target changed: a = the target, value its stacks now (0: gone, consumed or run out). */
    public static final int REFRACT = 8;
    /** An ability consumed three Refract stacks: a = the target, at the hit. */
    public static final int REFRACT_BREAK = 9;
    /** The Prism Moth took a status off its owner: a = the owner, b = the moth. */
    public static final int CLEANSE = 10;
    /** A Star Egg hatched: at the egg, value the kind. */
    public static final int HATCH = 11;
    /** The Gravikin landed a hop: a = the Gravikin, at its feet. */
    public static final int LAND = 12;
    /** A familiar's hit landed: a = the familiar, b = the target, at the hit. */
    public static final int STRIKE = 13;

    public static final Type<FamiliarFxPayload> TYPE = new Type<>(CosmicBreach.id("familiar_fx"));
    private static final StreamCodec<ByteBuf, Vec3> POSITION = StreamCodec.of(
            (buf, v) -> {
                buf.writeDouble(v.x);
                buf.writeDouble(v.y);
                buf.writeDouble(v.z);
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    public static final StreamCodec<ByteBuf, FamiliarFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FamiliarFxPayload::kind,
            ByteBufCodecs.INT, FamiliarFxPayload::a,
            ByteBufCodecs.INT, FamiliarFxPayload::b,
            POSITION, FamiliarFxPayload::at,
            ByteBufCodecs.VAR_INT, FamiliarFxPayload::value,
            FamiliarFxPayload::new);

    @Override
    public Type<FamiliarFxPayload> type() {
        return TYPE;
    }
}
