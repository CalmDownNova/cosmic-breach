package com.cosmicbreach.combat.server;

/**
 * Caps how many combat inputs one player may send per server tick; the rest are dropped. A real
 * client sends at most a handful (a press and a release of each button), so the cap only bites on
 * a flood. Pure, no world access.
 */
public final class InputRateLimiter {
    private final int perTick;
    private int used;
    private int dropped;

    public InputRateLimiter(int perTick) {
        if (perTick < 1) {
            throw new IllegalArgumentException("need at least 1 input per tick, got " + perTick);
        }
        this.perTick = perTick;
    }

    /** True if one more input fits into this tick (and counts it). */
    public boolean tryAcquire() {
        if (used >= perTick) {
            dropped++;
            return false;
        }
        used++;
        return true;
    }

    /** Inputs dropped since the last {@link #nextTick}. */
    public int dropped() {
        return dropped;
    }

    /** A new server tick starts: the budget refills. */
    public void nextTick() {
        used = 0;
        dropped = 0;
    }
}
