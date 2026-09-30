package com.cosmicbreach.onboarding;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The way in's packets, server to client. Handlers call into the client package only when they run. */
public final class OnboardingNet {
    private OnboardingNet() {
    }

    /**
     * A Starfall's streak: from {@code from} high in the sky to {@code to} (the top of the ground it hits) over
     * {@code ticks}. Sent to every client within 256 blocks of the landing, which draws it on its own, so it
     * does not depend on entity tracking range.
     */
    public record Streak(Vec3 from, Vec3 to, int ticks) implements CustomPacketPayload {
        public static final Type<Streak> TYPE = new Type<>(CosmicBreach.id("starfall_streak"));
        public static final StreamCodec<ByteBuf, Streak> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeDouble(s.from.x).writeDouble(s.from.y).writeDouble(s.from.z);
                    buf.writeDouble(s.to.x).writeDouble(s.to.y).writeDouble(s.to.z);
                    buf.writeShort(s.ticks);
                },
                buf -> new Streak(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readShort()));

        @Override
        public Type<Streak> type() {
            return TYPE;
        }
    }

    /**
     * The receiving player steps into a Breach: pull them up for {@code ticks} while the screen fades to
     * white; the server moves them when the pull ends, and the white clears once the new place has loaded.
     */
    public record FallUpStart(int ticks) implements CustomPacketPayload {
        public static final Type<FallUpStart> TYPE = new Type<>(CosmicBreach.id("fall_up"));
        public static final StreamCodec<ByteBuf, FallUpStart> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> buf.writeShort(s.ticks), buf -> new FallUpStart(buf.readShort()));

        @Override
        public Type<FallUpStart> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Streak.TYPE, Streak.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.onboarding.OnboardingClient.onStreak(payload));
        registrar.playToClient(FallUpStart.TYPE, FallUpStart.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.onboarding.OnboardingClient.onFallUp(payload));
    }
}
