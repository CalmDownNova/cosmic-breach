package com.cosmicbreach.familiar;

/**
 * A familiar's repeating special ("every 3 s"), pure: ready once {@code period} ticks have passed since it last went
 * off, and it waits, ready, until there is something to use it on (a Scorch with no enemy hit lately doesn't spend
 * its turn). New cadences are ready at once.
 */
public final class Cadence {
    private final int period;
    private long last;
    private boolean fired;

    public Cadence(int period) {
        this.period = period;
    }

    public int period() {
        return period;
    }

    public boolean ready(long now) {
        return !fired || now - last >= period;
    }

    public void fire(long now) {
        last = now;
        fired = true;
    }

    /** Ticks until it is ready (0 when it is). */
    public int left(long now) {
        return fired ? (int) Math.max(0L, period - (now - last)) : 0;
    }

    /** When it last went off, or {@link Long#MIN_VALUE} if never. */
    public long last() {
        return fired ? last : Long.MIN_VALUE;
    }

    /** Ready at once again. */
    public void reset() {
        fired = false;
    }
}
