package com.cosmicbreach.gear.regalia;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The Choir Regalia's numbers (GDD 5.1), pure. Two pieces: out of combat Resonance drifts to 50% of max instead of
 * 30%, and abilities cost 10% less. Four pieces, Harmonics: every third ability cast within 10 s echoes 10 ticks
 * later at 60% power, free. The set ability, the Hymn of Alignment: a ring of radius 6 for 120 ticks; allies
 * inside get +30 Haste and double Resonance gain, enemies inside are Aligned and take 10% more from abilities.
 */
public final class RegaliaRules {
    // two pieces
    public static final double DRIFT_TARGET = 0.5;
    public static final double ABILITY_COST = 0.9;

    // four pieces, Harmonics
    public static final int ECHO_EVERY = 3;
    public static final int ECHO_WINDOW = 200;
    public static final int ECHO_DELAY = 10;
    public static final double ECHO_POWER = 0.6;

    // the set ability, the Hymn of Alignment
    public static final int HYMN_COOLDOWN = 800;
    public static final int HYMN_TICKS = 120;
    public static final double HYMN_RADIUS = 6.0;
    /** A body counts as inside while its feet are this far below the ring or above it. */
    public static final double HYMN_BELOW = 2.0;
    public static final double HYMN_ABOVE = 4.0;
    public static final double HYMN_HASTE = 30.0;
    public static final double HYMN_RESONANCE_GAIN = 2.0;
    public static final double ALIGNED_ABILITY_DAMAGE = 1.1;
    /** Aligned lingers this long after leaving the ring (renewed every tick inside). */
    public static final int ALIGNED_TICKS = 10;

    private RegaliaRules() {
    }

    /** True if a body with feet at (dx, dy, dz) from the ring's centre stands inside it. */
    public static boolean insideRing(double dx, double dy, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius && dy >= -HYMN_BELOW && dy <= HYMN_ABOVE;
    }

    /**
     * Harmonics' count: the casts of the last 10 s. A cast that makes the third within the window echoes, and the
     * count starts over (the echo itself doesn't count).
     */
    public static final class Harmonics {
        private final Deque<Long> casts = new ArrayDeque<>();

        /** A cast at {@code now}: true if it echoes. */
        public boolean cast(long now) {
            prune(now);
            casts.addLast(now);
            if (casts.size() >= ECHO_EVERY) {
                casts.clear();
                return true;
            }
            return false;
        }

        /** Drops casts older than the window. */
        public void prune(long now) {
            while (!casts.isEmpty() && now - casts.peekFirst() >= ECHO_WINDOW) {
                casts.removeFirst();
            }
        }

        /** Casts counted at {@code now}. */
        public int count(long now) {
            prune(now);
            return casts.size();
        }

        /** True if the next cast at {@code now} would echo. */
        public boolean primed(long now) {
            return count(now) >= ECHO_EVERY - 1;
        }

        /** When the count next drops (its oldest cast leaves the window), or {@code Long.MAX_VALUE} when empty. */
        public long nextDrop() {
            return casts.isEmpty() ? Long.MAX_VALUE : casts.peekFirst() + ECHO_WINDOW;
        }

        public void clear() {
            casts.clear();
        }
    }
}
