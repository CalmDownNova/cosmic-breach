package com.cosmicbreach.guardian;

import com.cosmicbreach.combat.server.ImpactWindow;

/**
 * A guardian's Break gauge (GDD 3.4: guardians Break for 100 ticks when their poise fills). Impact adds up over a
 * rolling {@value #WINDOW_TICKS} ticks, a little longer than a creature's 3 s: steady hits alone drain away before they
 * fill it, a parry and the hits after it sometimes do, and a beam sent back into the core (100 of the Colossus's 150)
 * with the hits around it almost always does. Reaching the capacity Breaks it and empties the gauge. Pure.
 *
 * <p>(Measured with a scripted fight: at 10 s every parry turned into a Break, which made the fight a third as long
 * as its design.)
 */
public final class BreakGauge {
    /** 5 seconds. */
    public static final int WINDOW_TICKS = 100;
    public static final int BREAK_TICKS = 100;

    private final double capacity;
    private final ImpactWindow window;

    public BreakGauge(double capacity) {
        this(capacity, WINDOW_TICKS);
    }

    /**
     * A gauge whose Impact drains after {@code windowTicks} instead of {@value #WINDOW_TICKS} (a slow giant with a big
     * gauge, such as the Leviathan's 300 over 20 s, would never fill in 5).
     */
    public BreakGauge(double capacity, int windowTicks) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (windowTicks <= 0) {
            throw new IllegalArgumentException("window must be positive, got " + windowTicks);
        }
        this.capacity = capacity;
        this.window = new ImpactWindow(windowTicks);
    }

    /** Adds {@code impact} at {@code now}; true if that filled the gauge (a Break; the gauge empties). */
    public boolean add(long now, double impact) {
        return window.add(now, impact, capacity);
    }

    /** Impact in the gauge at {@code now}. */
    public double total(long now) {
        return window.total(now);
    }

    /** How full it is, 0 to 1. */
    public double fraction(long now) {
        return Math.min(1.0, window.total(now) / capacity);
    }

    public double capacity() {
        return capacity;
    }

    public void clear() {
        window.clear();
    }
}
