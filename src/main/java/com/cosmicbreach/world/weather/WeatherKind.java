package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.Layer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * The four cosmic weather events (GDD 2.5): where each can happen, how long its warning and the event
 * itself last. How often each comes is the server config's ({@link WeatherConfig}).
 */
public enum WeatherKind {
    /** The Reach: Solenne flares; the open sky burns (Scorch), Resonance gain doubles. */
    FLARE(EnumSet.of(Layer.REACH), 15, 90),
    /** The Reach and the Drift: meteors fall near every player, each leaving a Meteorite. */
    SHOWER(EnumSet.of(Layer.REACH, Layer.DRIFT), 10, 60),
    /** The Drift: gravity 0.15x, one current carries everything, no fall damage. */
    TIDE(EnumSet.of(Layer.DRIFT), 10, 45),
    /** The Deep: Vesper falls silent; the Hollow stir and Arcane abilities cost less. */
    SURGE(EnumSet.of(Layer.DEEP), 20, 120);

    private final Set<Layer> layers;
    /** Warning length in ticks. */
    public final int warningTicks;
    /** The event's length in ticks. */
    public final int activeTicks;

    WeatherKind(Set<Layer> layers, int warningSeconds, int activeSeconds) {
        this.layers = layers;
        this.warningTicks = warningSeconds * 20;
        this.activeTicks = activeSeconds * 20;
    }

    /** True if this event can happen in {@code layer}. */
    public boolean canHappenIn(Layer layer) {
        return layers.contains(layer);
    }

    /** The layer this event happens in when none is named: the caller's own if it can, else its first. */
    public Layer homeLayer(@Nullable Layer preferred) {
        if (preferred != null && canHappenIn(preferred)) {
            return preferred;
        }
        return layers.iterator().next();
    }

    /** The command and save name: flare, shower, tide, surge. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable WeatherKind byId(String id) {
        for (WeatherKind kind : values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
