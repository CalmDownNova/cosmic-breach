package com.cosmicbreach.world.weather;

import com.cosmicbreach.CosmicBreach;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Cosmic weather's packets, server to client. */
public final class WeatherNet {
    private WeatherNet() {
    }

    /**
     * The weather of all three layers and the Drift currents' salt: sent to everyone in Aetheria whenever a
     * layer's weather changes, and to a player arriving there.
     */
    public record Sync(long currentSalt, List<LayerWeather> layers) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(CosmicBreach.id("weather_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Sync::currentSalt,
                LayerWeather.STREAM_CODEC.apply(ByteBufCodecs.list(3)), Sync::layers,
                Sync::new);

        public Sync {
            layers = List.copyOf(layers);
            if (layers.size() != 3) {
                List<LayerWeather> padded = new ArrayList<>(layers);
                while (padded.size() < 3) {
                    padded.add(LayerWeather.CALM);
                }
                layers = List.copyOf(padded.subList(0, 3));
            }
        }

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /**
     * A meteor is coming down at ({@code x}, {@code y}, {@code z}) (the ground's top face) in {@code delay}
     * ticks: the client draws the red circle of {@code radius}, then the falling meteor from the
     * {@code heading} side, then the impact. The server plays the whistle and the impact and deals the damage.
     */
    public record Meteor(double x, double y, double z, int delay, float radius, float heading) implements CustomPacketPayload {
        public static final Type<Meteor> TYPE = new Type<>(CosmicBreach.id("weather_meteor"));
        public static final StreamCodec<ByteBuf, Meteor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Meteor::x,
                ByteBufCodecs.DOUBLE, Meteor::y,
                ByteBufCodecs.DOUBLE, Meteor::z,
                ByteBufCodecs.VAR_INT, Meteor::delay,
                ByteBufCodecs.FLOAT, Meteor::radius,
                ByteBufCodecs.FLOAT, Meteor::heading,
                Meteor::new);

        @Override
        public Type<Meteor> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        // lambdas calling the client classes, so a dedicated server never loads them
        event.registrar("1")
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC,
                        (payload, context) -> com.cosmicbreach.client.weather.WeatherClient.onSync(payload))
                .playToClient(Meteor.TYPE, Meteor.STREAM_CODEC,
                        (payload, context) -> com.cosmicbreach.client.weather.MeteorFx.onMeteor(payload));
    }
}
