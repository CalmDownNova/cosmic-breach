package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * One layer's weather as both sides see it: the event (null when calm), its phase, when the phase started
 * and ends (game ticks of the level, so each side eases its visuals on its own clock), and the event's
 * heading in radians (the Gravity Tide's current, the direction meteors come from). The server builds it
 * from {@link WeatherSchedule} and sends it on every change; rules on both sides read the phase.
 */
public record LayerWeather(@Nullable WeatherKind kind, Phase phase, long phaseStart, long phaseEnd, float heading) {
    public static final LayerWeather CALM = new LayerWeather(null, Phase.IDLE, 0L, 0L, 0f);

    public static final StreamCodec<ByteBuf, LayerWeather> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LayerWeather decode(ByteBuf buf) {
            int kind = buf.readByte();
            int phase = buf.readByte();
            long start = buf.readLong();
            long end = buf.readLong();
            float heading = buf.readFloat();
            WeatherKind[] kinds = WeatherKind.values();
            Phase[] phases = Phase.values();
            if (kind < 0 || kind >= kinds.length || phase <= 0 || phase >= phases.length) {
                return CALM;
            }
            return new LayerWeather(kinds[kind], phases[phase], start, end, heading);
        }

        @Override
        public void encode(ByteBuf buf, LayerWeather w) {
            buf.writeByte(w.kind == null ? -1 : w.kind.ordinal());
            buf.writeByte(w.phase.ordinal());
            buf.writeLong(w.phaseStart);
            buf.writeLong(w.phaseEnd);
            buf.writeFloat(w.heading);
        }
    };

    /** A snapshot of {@code now} at {@code gameTime}. */
    public static LayerWeather of(WeatherSchedule.Now now, long gameTime) {
        if (now.phase() == Phase.IDLE || now.kind() == null) {
            return CALM;
        }
        long start = gameTime - now.elapsed();
        return new LayerWeather(now.kind(), now.phase(), start, start + now.length(), now.heading());
    }

    public boolean is(WeatherKind k, Phase p) {
        return kind == k && phase == p;
    }

    /** True in {@code k}'s warning or the event itself. */
    public boolean any(WeatherKind k) {
        return kind == k && phase != Phase.IDLE;
    }

    public boolean calm() {
        return phase == Phase.IDLE;
    }

    /** How far through the phase at {@code time} (game ticks, partial ticks welcome): 0 to 1. */
    public double progress(double time) {
        if (phase == Phase.IDLE || phaseEnd <= phaseStart) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (time - phaseStart) / (double) (phaseEnd - phaseStart)));
    }

    /** Ticks left in the phase at {@code gameTime}. */
    public long ticksLeft(long gameTime) {
        return phase == Phase.IDLE ? 0L : Math.max(0L, phaseEnd - gameTime);
    }

    /** The heading as a unit vector on the ground: x east, z south (Minecraft's axes). */
    public double headingX() {
        return Math.cos(heading);
    }

    public double headingZ() {
        return Math.sin(heading);
    }
}
