package com.cosmicbreach.sandbox;

import java.util.function.IntSupplier;

/**
 * When the sandbox sends Shardlings: a warm-up pair {@value #FIRST_DELAY} ticks (5 s) after it starts,
 * for a player still learning the keys, resting {@value #WARM_UP_PACE} times as long between attacks;
 * then a pack of 3 to 5 at the normal pace {@value #NEXT_DELAY} ticks (6 s) after each pack is gone. A
 * pack sent by hand ({@code /cosmicbreach wave}) counts too: the next one waits until it is gone.
 * Pure: the sandbox tells it how many of its Shardlings are alive each tick.
 */
public final class WaveSchedule {
    public static final int FIRST_DELAY = 100;
    public static final int NEXT_DELAY = 120;
    public static final int FIRST_SIZE = 2;
    public static final float WARM_UP_PACE = 1.5f;
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 5;

    private final IntSupplier nextSize;
    private int countdown = FIRST_DELAY;
    private boolean fighting;
    private int sent;

    /** {@code nextSize} rolls the size of every pack after the first; it is clamped to 3 to 5. */
    public WaveSchedule(IntSupplier nextSize) {
        this.nextSize = nextSize;
    }

    /** One tick with {@code alive} of the sandbox's Shardlings left. Returns the size of a pack to send now, or 0. */
    public int tick(int alive) {
        if (alive > 0) {
            fighting = true;
            return 0;
        }
        if (fighting) {
            fighting = false; // that pack is gone
            countdown = NEXT_DELAY;
        }
        if (--countdown > 0) {
            return 0;
        }
        fighting = true;
        sent++;
        return sent == 1 ? FIRST_SIZE : Math.max(MIN_SIZE, Math.min(MAX_SIZE, nextSize.getAsInt()));
    }

    /** Ticks until the next pack, while none is out. */
    public int countdown() {
        return fighting ? 0 : countdown;
    }

    /** Packs the schedule has sent. */
    public int sent() {
        return sent;
    }

    /** How slowly the {@code n}th pack sent (from 1) rests between attacks: the warm-up pair's pace, then 1. */
    public static float paceOf(int n) {
        return n == 1 ? WARM_UP_PACE : 1.0f;
    }
}
