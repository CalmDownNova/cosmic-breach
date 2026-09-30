package com.cosmicbreach.net;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the target's trackers (and the target) plus the attacker: a hit landed. {@code position}
 * is the point on the target nearest the swing, {@code direction} the swing's horizontal direction,
 * {@code hitstop} the ticks the attacker's animation holds.
 */
public record HitFxPayload(int attackerId, int targetId, Vec3 position, Vec3 direction, float impact,
                           boolean crit, int hitstop) implements CustomPacketPayload {
    public static final Type<HitFxPayload> TYPE = new Type<>(CosmicBreach.id("hit_fx"));
    public static final StreamCodec<FriendlyByteBuf, HitFxPayload> STREAM_CODEC =
            CustomPacketPayload.codec(HitFxPayload::write, HitFxPayload::read);

    private static HitFxPayload read(FriendlyByteBuf buf) {
        int attacker = buf.readVarInt();
        int target = buf.readVarInt();
        Vec3 position = buf.readVec3();
        Vec3 direction = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        float impact = buf.readFloat();
        boolean crit = buf.readBoolean();
        int hitstop = buf.readVarInt();
        return new HitFxPayload(attacker, target, position, direction, impact, crit, hitstop);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(attackerId);
        buf.writeVarInt(targetId);
        buf.writeVec3(position);
        buf.writeFloat((float) direction.x);
        buf.writeFloat((float) direction.y);
        buf.writeFloat((float) direction.z);
        buf.writeFloat(impact);
        buf.writeBoolean(crit);
        buf.writeVarInt(hitstop);
    }

    @Override
    public Type<HitFxPayload> type() {
        return TYPE;
    }
}
