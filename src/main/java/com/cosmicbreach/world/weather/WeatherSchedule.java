package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.Layer;
import java.util.Arrays;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * The weather timetable of one world (GDD 2.5), pure logic so it is unit-tested: one event per layer at a
 * time, each a warning then the event, then calm. Every place an event can happen is a {@link Slot} (the
 * Meteor Shower has two, the Reach's and the Drift's) with its own countdown, drawn from the config's
 * interval. A slot counts down only while its layer is calm, so events never queue up; when an event ends,
 * the layer's other slots wait at least {@link #GAP_TICKS}. The owner ticks it only while someone is in
 * Aetheria, so the intervals are play time there.
 */
public final class WeatherSchedule {
    /** Where an event is in its life. */
    public enum Phase { IDLE, WARNING, ACTIVE }

    /** A place an event can happen: its layer and kind. */
    public enum Slot {
        REACH_FLARE(Layer.REACH, WeatherKind.FLARE),
        REACH_SHOWER(Layer.REACH, WeatherKind.SHOWER),
        DRIFT_SHOWER(Layer.DRIFT, WeatherKind.SHOWER),
        DRIFT_TIDE(Layer.DRIFT, WeatherKind.TIDE),
        DEEP_SURGE(Layer.DEEP, WeatherKind.SURGE);

        public final Layer layer;
        public final WeatherKind kind;

        Slot(Layer layer, WeatherKind kind) {
            this.layer = layer;
            this.kind = kind;
        }

        public static @Nullable Slot of(Layer layer, WeatherKind kind) {
            for (Slot slot : values()) {
                if (slot.layer == layer && slot.kind == kind) {
                    return slot;
                }
            }
            return null;
        }

        public String id() {
            return layer.name().toLowerCase(java.util.Locale.ROOT) + "_" + kind.id();
        }
    }

    /** Draws the calm time before a slot's next event, in ticks. */
    @FunctionalInterface
    public interface Intervals {
        int ticks(Slot slot, boolean heliarchFallen, RandomSource random);
    }

    /** One layer's weather: what, which phase, how far through it, and the event's heading (radians). */
    public static final class Now {
        private @Nullable WeatherKind kind;
        private Phase phase = Phase.IDLE;
        private int elapsed;
        private int length;
        private float heading;

        public @Nullable WeatherKind kind() {
            return kind;
        }

        public Phase phase() {
            return phase;
        }

        /** Ticks spent in this phase. */
        public int elapsed() {
            return elapsed;
        }

        /** This phase's length in ticks (0 while calm). */
        public int length() {
            return length;
        }

        public float heading() {
            return heading;
        }

        public boolean is(WeatherKind k, Phase p) {
            return kind == k && phase == p;
        }

        void set(@Nullable WeatherKind kind, Phase phase, int elapsed, int length, float heading) {
            this.kind = phase == Phase.IDLE ? null : kind;
            this.phase = kind == null ? Phase.IDLE : phase;
            this.elapsed = Math.max(0, elapsed);
            this.length = this.phase == Phase.IDLE ? 0 : Math.max(1, length);
            this.heading = heading;
        }
    }

    /** Calm time a layer keeps after an event before its next one may start: 2 minutes. */
    public static final int GAP_TICKS = 2 * 60 * 20;

    private final Now[] layers = {new Now(), new Now(), new Now()};
    /** Ticks until each slot's next event; negative means not drawn yet. */
    private final int[] countdown = new int[Slot.values().length];
    private boolean heliarchFallen;

    public WeatherSchedule() {
        Arrays.fill(countdown, -1);
    }

    public Now now(Layer layer) {
        return layers[layer.ordinal()];
    }

    /** Ticks until {@code slot}'s next event (counting only calm time in its layer); -1 before the first draw. */
    public int countdown(Slot slot) {
        return countdown[slot.ordinal()];
    }

    public void setCountdown(Slot slot, int ticks) {
        countdown[slot.ordinal()] = ticks;
    }

    public boolean heliarchFallen() {
        return heliarchFallen;
    }

    /** Sets the Heliarch flag as saved (loading); {@link #heliarchFell} is the event. */
    public void setHeliarchFallen(boolean fallen) {
        this.heliarchFallen = fallen;
    }

    /**
     * The Heliarch's first death: Eclipse Surges come half as often from now on, starting with the countdown
     * already running (it doubles). Later calls change nothing.
     */
    public void heliarchFell() {
        if (heliarchFallen) {
            return;
        }
        heliarchFallen = true;
        int i = Slot.DEEP_SURGE.ordinal();
        if (countdown[i] > 0) {
            countdown[i] *= 2;
        }
    }

    /**
     * One tick of weather: phases run on, calm layers count their slots down and start the first that is
     * due (only when {@code natural}; otherwise only started events run). Returns a bit per layer
     * ({@code 1 << layer.ordinal()}) whose weather changed.
     */
    public int tick(Intervals intervals, RandomSource random, boolean natural) {
        int changed = 0;
        for (Slot slot : Slot.values()) {
            if (countdown[slot.ordinal()] < 0) {
                countdown[slot.ordinal()] = Math.max(1, intervals.ticks(slot, heliarchFallen, random));
            }
        }
        for (Layer layer : Layer.values()) {
            Now now = now(layer);
            if (now.phase != Phase.IDLE) {
                now.elapsed++;
                if (now.elapsed >= now.length) {
                    if (now.phase == Phase.WARNING) {
                        now.set(now.kind, Phase.ACTIVE, 0, now.kind.activeTicks, now.heading);
                    } else {
                        calm(layer);
                    }
                    changed |= 1 << layer.ordinal();
                }
                continue;
            }
            if (!natural) {
                continue;
            }
            Slot due = null;
            for (Slot slot : Slot.values()) {
                if (slot.layer != layer) {
                    continue;
                }
                int i = slot.ordinal();
                if (countdown[i] > 0) {
                    countdown[i]--;
                }
                if (countdown[i] == 0 && due == null) {
                    due = slot;
                }
            }
            if (due != null) {
                begin(due, randomHeading(random), intervals, random);
                changed |= 1 << layer.ordinal();
            }
        }
        return changed;
    }

    /**
     * Starts {@code kind} in {@code layer} now, with its warning, replacing whatever that layer had. The
     * slot's countdown is drawn again, so a forced event doesn't bring a natural one right behind it.
     */
    public void start(Layer layer, WeatherKind kind, float heading, Intervals intervals, RandomSource random) {
        Slot slot = Slot.of(layer, kind);
        if (slot == null) {
            throw new IllegalArgumentException(kind.id() + " can't happen in " + layer);
        }
        begin(slot, heading, intervals, random);
    }

    /** Skips the rest of {@code layer}'s warning: its event starts on the next tick. False if it had none. */
    public boolean skipWarning(Layer layer) {
        Now now = now(layer);
        if (now.phase != Phase.WARNING) {
            return false;
        }
        now.elapsed = Math.max(now.elapsed, now.length - 1);
        return true;
    }

    /** Ends {@code layer}'s event now, if it has one. True if it had one. */
    public boolean clear(Layer layer) {
        if (now(layer).phase == Phase.IDLE) {
            return false;
        }
        calm(layer);
        return true;
    }

    private void begin(Slot slot, float heading, Intervals intervals, RandomSource random) {
        now(slot.layer).set(slot.kind, Phase.WARNING, 0, slot.kind.warningTicks, heading);
        countdown[slot.ordinal()] = Math.max(1, intervals.ticks(slot, heliarchFallen, random));
    }

    private void calm(Layer layer) {
        now(layer).set(null, Phase.IDLE, 0, 0, 0f);
        for (Slot slot : Slot.values()) {
            if (slot.layer == layer) {
                countdown[slot.ordinal()] = Math.max(countdown[slot.ordinal()], GAP_TICKS);
            }
        }
    }

    private static float randomHeading(RandomSource random) {
        return (float) (random.nextDouble() * Math.PI * 2.0);
    }
}
