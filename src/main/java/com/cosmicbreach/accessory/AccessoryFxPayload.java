package com.cosmicbreach.accessory;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to clients: a moment of an accessory, for its visuals. {@code entityId} is the wearer, {@code at} a world
 * position, {@code value} and {@code ticks} a size and a duration where the moment has them.
 */
public record AccessoryFxPayload(int kind, int entityId, Vec3 at, float value, int ticks) implements CustomPacketPayload {
    /** The Heart's nova: at the wearer's middle, value its radius. */
    public static final int NOVA = 0;
    /** The Gravity Loop's well opened: at its middle on the ground, value its radius, ticks how long it pulls. */
    public static final int WELL = 1;
    /** The Lens's black hole: at the attacker's middle, value its radius, ticks how long it pulls. */
    public static final int BLACK_HOLE = 2;
    /** The Hourglass slowed the room: at the wearer's feet, value its radius, ticks how many it slowed. */
    public static final int SLOW = 3;
    /** A Halo shard broke on a projectile: where. */
    public static final int SHARD_BROKEN = 4;
    /** A Halo shard cut something: where. */
    public static final int SHARD_CUT = 5;
    /** A Halo shard grew back: at the wearer's middle, ticks which shard. */
    public static final int SHARD_REGROWN = 6;
    /** The Leechstar Signet drank: at the wearer's chest, value the health it gave. */
    public static final int LEECH = 7;

    public static final Type<AccessoryFxPayload> TYPE = new Type<>(CosmicBreach.id("accessory_fx"));
    private static final StreamCodec<ByteBuf, Vec3> POSITION = StreamCodec.of(
            (buf, v) -> {
                buf.writeDouble(v.x);
                buf.writeDouble(v.y);
                buf.writeDouble(v.z);
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    public static final StreamCodec<ByteBuf, AccessoryFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AccessoryFxPayload::kind,
            ByteBufCodecs.VAR_INT, AccessoryFxPayload::entityId,
            POSITION, AccessoryFxPayload::at,
            ByteBufCodecs.FLOAT, AccessoryFxPayload::value,
            ByteBufCodecs.VAR_INT, AccessoryFxPayload::ticks,
            AccessoryFxPayload::new);

    @Override
    public Type<AccessoryFxPayload> type() {
        return TYPE;
    }
}
