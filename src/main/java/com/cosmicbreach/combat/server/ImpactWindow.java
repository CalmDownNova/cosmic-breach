package com.cosmicbreach.combat.server;

import java.util.ArrayDeque;

/**
 * Impact taken within a rolling window of game ticks (GDD section 3.4: 3 s). An entry taken at tick
 * {@code t} counts while {@code now - t < windowTicks}. Reaching the threshold empties the window,
 * because a stagger resets the count. Pure, no world access.
 */
public final class ImpactWindow {
    private record Entry(long tick, double impact) {
    }

    private final int windowTicks;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private double total;

    public ImpactWindow(int windowTicks) {
        if (windowTicks < 1) {
            throw new IllegalArgumentException("window must be at least 1 tick, got " + windowTicks);
        }
        this.windowTicks = windowTicks;
    }

    /** Impact that still counts at {@code now}. */
    public double total(long now) {
        expire(now);
        return total;
    }

    /**
     * Adds {@code impact} taken at {@code now}. Returns true when the total inside the window reaches
     * {@code threshold}; the window is then emptied. No impact never breaks anything.
     */
    public boolean add(long now, double impact, double threshold) {
        expire(now);
        if (impact <= 0) {
            return false;
        }
        entries.addLast(new Entry(now, impact));
        total += impact;
        if (total >= threshold - 1e-9) {
            clear();
            return true;
        }
        return false;
    }

    public void clear() {
        entries.clear();
        total = 0;
    }

    private void expire(long now) {
        while (!entries.isEmpty() && now - entries.peekFirst().tick() >= windowTicks) {
            total -= entries.pollFirst().impact();
        }
        if (entries.isEmpty()) {
            total = 0; // no drift from repeated subtraction
        }
    }
}
