package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.CosmicBreach;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A Refraction's beams for the clients that see the Colossus (S2C): {@link #CHARGING} while the red lines trace the
 * paths (resent at once whenever a crystal turns), {@link #FIRING} while the beams burn, {@link #OFF} after. Each
 * path is its points in order (the eye, then each node) and whether it ends at the core.
 */
public record RefractionPayload(int colossus, byte mode, long start, List<List<Vec3>> paths, List<Boolean> core)
        implements CustomPacketPayload {
    public static final byte OFF = 0;
    public static final byte CHARGING = 1;
    public static final byte FIRING = 2;

    public static final Type<RefractionPayload> TYPE = new Type<>(CosmicBreach.id("refraction"));
    public static final StreamCodec<FriendlyByteBuf, RefractionPayload> STREAM_CODEC = StreamCodec.of(RefractionPayload::write,
            RefractionPayload::read);

    private static void write(FriendlyByteBuf buf, RefractionPayload p) {
        ByteBufCodecs.VAR_INT.encode(buf, p.colossus());
        buf.writeByte(p.mode());
        buf.writeLong(p.start());
        ByteBufCodecs.VAR_INT.encode(buf, p.paths().size());
        for (int i = 0; i < p.paths().size(); i++) {
            List<Vec3> path = p.paths().get(i);
            ByteBufCodecs.VAR_INT.encode(buf, path.size());
            for (Vec3 v : path) {
                buf.writeDouble(v.x);
                buf.writeDouble(v.y);
                buf.writeDouble(v.z);
            }
            buf.writeBoolean(i < p.core().size() && p.core().get(i));
        }
    }

    private static RefractionPayload read(FriendlyByteBuf buf) {
        int colossus = ByteBufCodecs.VAR_INT.decode(buf);
        byte mode = buf.readByte();
        long start = buf.readLong();
        int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 8);
        List<List<Vec3>> paths = new ArrayList<>(n);
        List<Boolean> core = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int m = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 8);
            List<Vec3> path = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                path.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
            }
            paths.add(path);
            core.add(buf.readBoolean());
        }
        return new RefractionPayload(colossus, mode, start, paths, core);
    }

    @Override
    public Type<RefractionPayload> type() {
        return TYPE;
    }
}
