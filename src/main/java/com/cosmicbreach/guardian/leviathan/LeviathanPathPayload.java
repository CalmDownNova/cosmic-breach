package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.CosmicBreach;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A Breach Dive's path, sent to the players near a Leviathan when it chooses the dive (S2C): its points, the tick the
 * dust wake starts ({@code start}; the head follows {@value LeviathanMoves#DIVE_TELL} ticks later), the dive's speed, and
 * where it lands (its target's feet, where the client rings the ground). The client draws the whole path for
 * {@value LeviathanMoves#WAKE_SHOW} ticks from {@code start}, fades it over {@value LeviathanMoves#WAKE_FADE} more
 * ({@link LeviathanMoves#wakeAlpha}) and then shows nothing.
 */
public record LeviathanPathPayload(int entity, long start, float speed, Vec3 target, List<Vec3> points) implements CustomPacketPayload {
    public static final Type<LeviathanPathPayload> TYPE = new Type<>(CosmicBreach.id("leviathan_path"));
    public static final int MAX_POINTS = 512;
    public static final StreamCodec<FriendlyByteBuf, LeviathanPathPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.entity());
                buf.writeLong(p.start());
                buf.writeFloat(p.speed());
                buf.writeDouble(p.target().x);
                buf.writeDouble(p.target().y);
                buf.writeDouble(p.target().z);
                int n = Math.min(MAX_POINTS, p.points().size());
                ByteBufCodecs.VAR_INT.encode(buf, n);
                for (int i = 0; i < n; i++) {
                    Vec3 v = p.points().get(i);
                    buf.writeFloat((float) v.x);
                    buf.writeFloat((float) v.y);
                    buf.writeFloat((float) v.z);
                }
            },
            buf -> {
                int entity = ByteBufCodecs.VAR_INT.decode(buf);
                long start = buf.readLong();
                float speed = buf.readFloat();
                Vec3 target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int n = Math.min(MAX_POINTS, ByteBufCodecs.VAR_INT.decode(buf));
                List<Vec3> points = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    points.add(new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
                }
                return new LeviathanPathPayload(entity, start, speed, target, points);
            });

    @Override
    public Type<LeviathanPathPayload> type() {
        return TYPE;
    }
}
