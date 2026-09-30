package com.cosmicbreach.gear.net;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server to the players near: one of the Driftweave's or the Choir Regalia's moments, to draw and hear.
 * {@code ownerId} is the wearer; {@code at}, {@code yaw}, {@code dir}, {@code move}, {@code ticks} and
 * {@code value} depend on the kind ({@link Kind}).
 */
public record SetFxPayload(int kind, int ownerId, Vec3 at, float yaw, Vec3 dir, ResourceLocation move, int ticks, float value)
        implements CustomPacketPayload {
    public static final Type<SetFxPayload> TYPE = new Type<>(CosmicBreach.id("set_fx"));
    public static final ResourceLocation NO_MOVE = CosmicBreach.id("none");

    public static final StreamCodec<FriendlyByteBuf, SetFxPayload> STREAM_CODEC = StreamCodec.of(SetFxPayload::write, SetFxPayload::read);

    /** What happened. */
    public static final class Kind {
        /** Drift began at {@code at} (the wearer's feet); ticks = how long it lasts. */
        public static final int DRIFT_START = 0;
        /** Drift ended at {@code at}. */
        public static final int DRIFT_END = 1;
        /** A perfect dodge left an Afterimage standing at {@code at} facing {@code yaw}, for {@code ticks}. */
        public static final int AFTERIMAGE = 2;
        /** The Afterimage repeats {@code move} (facing {@code yaw}); ticks = the move's length; value = repeats left. */
        public static final int AFTERIMAGE_REPEAT = 3;
        /** A copy struck at {@code at}, along {@code dir}; value = its Impact. */
        public static final int AFTERIMAGE_STRIKE = 4;
        /** The Afterimage is spent or ran out: it fades. */
        public static final int AFTERIMAGE_END = 5;
        /** A cast echoes: a ghost of the wearer replays {@code move} at {@code at} facing {@code yaw}; ticks = its length. */
        public static final int ECHO = 6;
        /** The echo of a Gravity Well: planted at {@code at}, value = radius, ticks = its pull. */
        public static final int ECHO_WELL = 7;
        /** That well collapses; value = the Collapse's radius. */
        public static final int ECHO_COLLAPSE = 8;
        /** The echo of a thrown sickle: from {@code at} along {@code dir}, value = blocks it flies, ticks = how long. */
        public static final int ECHO_THROW = 9;
        /** An echo's hit at {@code at}, along {@code dir}; value = its Impact. */
        public static final int ECHO_STRIKE = 10;
        /** The Hymn of Alignment: a ring at {@code at}, value = radius, ticks = how long it rings. */
        public static final int HYMN = 11;

        private Kind() {
        }
    }

    /** A moment with a place and a length only. */
    public static SetFxPayload at(int kind, int ownerId, Vec3 at, int ticks) {
        return new SetFxPayload(kind, ownerId, at, 0f, Vec3.ZERO, NO_MOVE, ticks, 0f);
    }

    private static void write(FriendlyByteBuf buf, SetFxPayload p) {
        buf.writeVarInt(p.kind);
        buf.writeVarInt(p.ownerId);
        buf.writeDouble(p.at.x);
        buf.writeDouble(p.at.y);
        buf.writeDouble(p.at.z);
        buf.writeFloat(p.yaw);
        buf.writeFloat((float) p.dir.x);
        buf.writeFloat((float) p.dir.y);
        buf.writeFloat((float) p.dir.z);
        buf.writeResourceLocation(p.move);
        buf.writeVarInt(p.ticks);
        buf.writeFloat(p.value);
    }

    private static SetFxPayload read(FriendlyByteBuf buf) {
        int kind = buf.readVarInt();
        int owner = buf.readVarInt();
        Vec3 at = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float yaw = buf.readFloat();
        Vec3 dir = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        ResourceLocation move = buf.readResourceLocation();
        int ticks = buf.readVarInt();
        float value = buf.readFloat();
        return new SetFxPayload(kind, owner, at, yaw, dir, move, ticks, value);
    }

    @Override
    public Type<SetFxPayload> type() {
        return TYPE;
    }
}
