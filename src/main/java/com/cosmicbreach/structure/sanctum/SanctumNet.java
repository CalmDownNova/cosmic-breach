package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Sanctum's two messages to a client: its own pass through the Gate's veil, and the sky's flicker at the throne. */
public final class SanctumNet {
    private SanctumNet() {
    }

    /** Whether the receiving player walks through the Gate's veil (their client simulates their movement). */
    public record Pass(boolean pass) implements CustomPacketPayload {
        public static final Type<Pass> TYPE = new Type<>(CosmicBreach.id("sanctum_pass"));
        public static final StreamCodec<ByteBuf, Pass> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.pass), buf -> new Pass(buf.readBoolean()));

        @Override
        public Type<Pass> type() {
            return TYPE;
        }
    }

    /** The sky flickers for {@code ticks}: a Heart on the throne that nothing answers yet. */
    public record Flicker(int ticks) implements CustomPacketPayload {
        public static final Type<Flicker> TYPE = new Type<>(CosmicBreach.id("sanctum_flicker"));
        public static final StreamCodec<ByteBuf, Flicker> STREAM_CODEC = StreamCodec.of(
                (buf, f) -> buf.writeShort(f.ticks), buf -> new Flicker(buf.readShort()));

        @Override
        public Type<Flicker> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // lambdas calling the client class, so a dedicated server never loads it
        registrar.playToClient(Pass.TYPE, Pass.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.sanctum.SanctumClient.onPass(payload.pass()));
        registrar.playToClient(Flicker.TYPE, Flicker.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.sanctum.SanctumClient.onFlicker(payload.ticks()));
    }

    public static void sendPass(ServerPlayer player, boolean pass) {
        PacketDistributor.sendToPlayer(player, new Pass(pass));
    }

    public static void sendFlicker(ServerPlayer player, int ticks) {
        PacketDistributor.sendToPlayer(player, new Flicker(ticks));
    }
}
