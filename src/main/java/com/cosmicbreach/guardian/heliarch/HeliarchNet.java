package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Heliarch's messages to clients that its synced state can't carry: a line it speaks, a volley of Solar Rain, a
 * segment of the floor about to fall (the client keeps its blocks to draw them falling), and the world's seal over the
 * Breach after the first kill. The one-shot effects of the fight itself go as entity events.
 */
public final class HeliarchNet {
    private HeliarchNet() {
    }

    /** The Heliarch says {@code line}. */
    public record Speak(int line) implements CustomPacketPayload {
        public static final Type<Speak> TYPE = new Type<>(CosmicBreach.id("heliarch_speak"));
        public static final StreamCodec<ByteBuf, Speak> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeByte(p.line),
                buf -> new Speak(buf.readByte()));

        @Override
        public Type<Speak> type() {
            return TYPE;
        }
    }

    /** Solar Rain: gold circles at {@code circles} (floor level), the impacts at game time {@code land}. */
    public record Rain(List<Vec3> circles, long land) implements CustomPacketPayload {
        public static final Type<Rain> TYPE = new Type<>(CosmicBreach.id("heliarch_rain"));
        public static final StreamCodec<ByteBuf, Rain> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.circles.size());
            for (Vec3 c : p.circles) {
                buf.writeFloat((float) c.x);
                buf.writeFloat((float) c.y);
                buf.writeFloat((float) c.z);
            }
            buf.writeLong(p.land);
        }, buf -> {
            int n = buf.readByte();
            List<Vec3> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
            }
            return new Rain(List.copyOf(list), buf.readLong());
        });

        @Override
        public Type<Rain> type() {
            return TYPE;
        }
    }

    /** A segment of ring {@code ring} (SanctumArena.Ring ordinal) falls now ({@code restore}: it rises back). */
    public record Fall(int ring, int segment, boolean restore) implements CustomPacketPayload {
        public static final Type<Fall> TYPE = new Type<>(CosmicBreach.id("heliarch_fall"));
        public static final StreamCodec<ByteBuf, Fall> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.ring);
            buf.writeByte(p.segment);
            buf.writeBoolean(p.restore);
        }, buf -> new Fall(buf.readByte(), buf.readByte(), buf.readBoolean()));

        @Override
        public Type<Fall> type() {
            return TYPE;
        }
    }

    /** Whether the Breach is sealed in this world ({@code forming}: it was sealed just now). */
    public record Seal(boolean sealed, boolean forming) implements CustomPacketPayload {
        public static final Type<Seal> TYPE = new Type<>(CosmicBreach.id("heliarch_seal"));
        public static final StreamCodec<ByteBuf, Seal> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.sealed);
            buf.writeBoolean(p.forming);
        }, buf -> new Seal(buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<Seal> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // lambdas calling the client class, so a dedicated server never loads it
        registrar.playToClient(Speak.TYPE, Speak.STREAM_CODEC,
                (p, context) -> com.cosmicbreach.client.guardian.heliarch.HeliarchClient.onSpeak(p.line()));
        registrar.playToClient(Rain.TYPE, Rain.STREAM_CODEC,
                (p, context) -> com.cosmicbreach.client.guardian.heliarch.HeliarchClient.onRain(p.circles(), p.land()));
        registrar.playToClient(Fall.TYPE, Fall.STREAM_CODEC,
                (p, context) -> com.cosmicbreach.client.guardian.heliarch.HeliarchClient.onFall(p.ring(), p.segment(), p.restore()));
        registrar.playToClient(Seal.TYPE, Seal.STREAM_CODEC,
                (p, context) -> com.cosmicbreach.client.guardian.heliarch.HeliarchClient.onSeal(p.sealed(), p.forming()));
    }

    public static void speak(Collection<ServerPlayer> to, HeliarchLine line) {
        for (ServerPlayer p : to) {
            PacketDistributor.sendToPlayer(p, new Speak(line.ordinal()));
        }
    }

    public static void rain(Collection<ServerPlayer> to, List<Vec3> circles, long land) {
        Rain r = new Rain(List.copyOf(circles), land);
        for (ServerPlayer p : to) {
            PacketDistributor.sendToPlayer(p, r);
        }
    }

    public static void fall(Collection<ServerPlayer> to, int ring, int segment, boolean restore) {
        Fall f = new Fall(ring, segment, restore);
        for (ServerPlayer p : to) {
            PacketDistributor.sendToPlayer(p, f);
        }
    }

    public static void seal(ServerPlayer to, boolean sealed, boolean forming) {
        PacketDistributor.sendToPlayer(to, new Seal(sealed, forming));
    }
}
