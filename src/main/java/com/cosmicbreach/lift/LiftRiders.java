package com.cosmicbreach.lift;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server to every client in the Drift: the entity ids of the players the rescue lift holds right now (sent when the list
 * changes), so each client draws a luminous trail behind each of them that is being carried up: a rescue is seen across the arena,
 * not only by the rider.
 */
public record LiftRiders(List<Integer> ids) implements CustomPacketPayload {
    public static final Type<LiftRiders> TYPE = new Type<>(CosmicBreach.id("lift_riders"));
    public static final StreamCodec<ByteBuf, LiftRiders> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), LiftRiders::ids,
            LiftRiders::new);

    /**
     * Whether a trail is drawn behind a player: the lift has caught them and they are not sneaking (a sneaking rider is lowered,
     * and nothing climbs behind them). The rule each client applies to every player the server lists and to its own ride; the list
     * itself holds everyone the lift holds, so that a rider's sneak taps never have to be sent.
     */
    public static boolean carried(boolean riding, boolean sneaking) {
        return riding && !sneaking;
    }

    /**
     * What a player who has just arrived in the Drift is sent: the list of who is carried now, even when it is empty (a client that
     * left the Drift while someone was carried still holds the old list, and an empty one is what clears it), or null for a player
     * already in, who hears of changes as they happen.
     */
    public static @Nullable LiftRiders forArrival(boolean joining, List<Integer> carried) {
        return joining ? new LiftRiders(List.copyOf(carried)) : null;
    }

    @Override
    public Type<LiftRiders> type() {
        return TYPE;
    }
}
