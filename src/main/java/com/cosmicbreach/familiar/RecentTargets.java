package com.cosmicbreach.familiar;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * The last few creatures an owner hit (or that hit the owner), newest first, by entity id and game time; pure. "An
 * enemy you hit recently" is the newest one still valid within the window. Holds {@value #SIZE}.
 */
public final class RecentTargets {
    public static final int SIZE = 4;

    private final int[] ids = new int[SIZE];
    private final long[] times = new long[SIZE];
    private int size;

    /** {@code id} was hit (or hit back) at {@code now}: to the front, once. */
    public void hit(int id, long now) {
        int at = -1;
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                at = i;
                break;
            }
        }
        int shift = at >= 0 ? at : Math.min(size, SIZE - 1);
        for (int i = shift; i > 0; i--) {
            ids[i] = ids[i - 1];
            times[i] = times[i - 1];
        }
        ids[0] = id;
        times[0] = now;
        if (at < 0 && size < SIZE) {
            size++;
        }
    }

    /** The newest id hit within {@code window} ticks of {@code now} that {@code valid} accepts. */
    public OptionalInt latest(long now, int window, IntPredicate valid) {
        for (int i = 0; i < size; i++) {
            if (now - times[i] <= window && valid.test(ids[i])) {
                return OptionalInt.of(ids[i]);
            }
        }
        return OptionalInt.empty();
    }

    /** When {@code id} was last hit, or {@link Long#MIN_VALUE}. */
    public long lastHit(int id) {
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                return times[i];
            }
        }
        return Long.MIN_VALUE;
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
    }
}
