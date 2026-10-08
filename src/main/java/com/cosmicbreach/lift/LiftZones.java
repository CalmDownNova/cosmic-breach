package com.cosmicbreach.lift;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to one client: the lift zones near them, the Rifts by centre and layout seed (the client rebuilds each layout), and the
 * rising currents beside the shrines (1.1 design section 5).
 */
public record LiftZones(List<Rift> rifts, List<AscentCurrent.Current> currents) implements CustomPacketPayload {
    /** A Leviathan Rift: its arena centre and the seed its layout was built from. */
    public record Rift(BlockPos centre, long seed) {
        public static final StreamCodec<ByteBuf, Rift> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Rift::centre,
                ByteBufCodecs.VAR_LONG, Rift::seed,
                Rift::new);
    }

    public static final Type<LiftZones> TYPE = new Type<>(CosmicBreach.id("lift_zones"));
    public static final StreamCodec<ByteBuf, LiftZones> STREAM_CODEC = StreamCodec.composite(
            Rift.STREAM_CODEC.apply(ByteBufCodecs.list()), LiftZones::rifts,
            AscentCurrent.Current.STREAM_CODEC.apply(ByteBufCodecs.list()), LiftZones::currents,
            LiftZones::new);

    @Override
    public Type<LiftZones> type() {
        return TYPE;
    }
}
