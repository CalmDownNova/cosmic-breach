package com.cosmicbreach.structure.crypt.trap;

/**
 * Starfall Chutes (GDD 6.4): slots in a corridor's ceiling that drop star-rocks on a timed loop, a timing gauntlet.
 * Each chute releases on a beat of Vesper's clock, every {@link Pattern#period} ticks at its own phase; the rock
 * falls {@link #FALL_TICKS} ticks and lands across the corridor's width, {@link #DAMAGE} damage to whoever is under
 * it. A glint shows at the aperture {@link #GLINT_LEAD} ticks before each release (to careful players, like every
 * tell). The chutes of one corridor follow a pattern. Pure.
 */
public final class ChuteRules {
    public static final int GLINT_LEAD = 20;
    public static final int FALL_TICKS = 8;
    public static final float DAMAGE = 8.0f;
    /** The glint is shown to careful players this close to the chute's strip of floor (horizontally). */
    public static final double REVEAL_RADIUS = 4.0;

    /** How a corridor's chutes (numbered from its entrance) keep time. */
    public enum Pattern {
        /** One after another down the corridor, a beat apart: follow the wave. */
        WAVE(48, new int[] {0, 12, 24}),
        /** The outer two together, the middle one on the off-bar. */
        ALTERNATE(48, new int[] {0, 24, 0}),
        /** Half a beat apart, back to front: a quick cascade toward you. */
        CASCADE(48, new int[] {12, 6, 0}),
        /** The first two together, then the last. */
        PAIRS(48, new int[] {0, 0, 24});

        public final int period;
        private final int[] phases;

        Pattern(int period, int[] phases) {
            this.period = period;
            this.phases = phases;
        }

        /** Phase in ticks of chute {@code index} (0 = nearest the corridor's entrance). */
        public int phase(int index) {
            return phases[Math.floorMod(index, phases.length)];
        }

        public static Pattern of(int ordinal) {
            return values()[Math.floorMod(ordinal, values().length)];
        }
    }

    private ChuteRules() {
    }

    /** True if a chute with this period and phase releases a rock on tick {@code now}. */
    public static boolean releases(long now, int period, int phase) {
        return Math.floorMod(now - phase, period) == 0;
    }

    /** True if its rock lands on tick {@code now}. */
    public static boolean lands(long now, int period, int phase) {
        return releases(now - FALL_TICKS, period, phase);
    }

    /** Ticks until the next release (0 on a release tick). */
    public static int untilRelease(long now, int period, int phase) {
        return Math.floorMod(phase - now, period);
    }

    /** True while the glint shows: the {@link #GLINT_LEAD} ticks before a release. */
    public static boolean glinting(long now, int period, int phase) {
        int until = untilRelease(now, period, phase);
        return until > 0 && until <= GLINT_LEAD;
    }

    /** Ticks since the last release (0 to period - 1). */
    public static int sinceRelease(long now, int period, int phase) {
        return Math.floorMod(now - phase, period);
    }

    /**
     * For tests: a player walking down a corridor at {@code speed} blocks a tick through strips {@code 1} block
     * deep at {@code positions} (blocks from the entrance), who may wait before each strip. Returns true if they get
     * through every strip without being under a landing rock (their body is {@code width} blocks across).
     */
    public static boolean walkable(Pattern pattern, int[] positions, double speed, double width) {
        for (int startTick = 0; startTick < pattern.period; startTick++) {
            if (walk(pattern, positions, speed, width, startTick)) {
                return true;
            }
        }
        return false;
    }

    private static boolean walk(Pattern pattern, int[] positions, double speed, double width, int startTick) {
        double x = 0;
        long t = startTick;
        for (int i = 0; i < positions.length; i++) {
            // walk up to the strip's near edge
            double near = positions[i] - width / 2;
            t += (long) Math.ceil(Math.max(0, near - x) / speed);
            x = Math.max(x, near);
            // wait for the next landing, cross just after it
            int crossTicks = (int) Math.ceil((1 + width) / speed);
            int wait = 0;
            while (wait < pattern.period && !clear(pattern, i, t + wait, crossTicks)) {
                wait++;
            }
            if (wait >= pattern.period) {
                return false;
            }
            t += wait + crossTicks;
            x = positions[i] + 1 + width / 2;
        }
        return true;
    }

    private static boolean clear(Pattern pattern, int chute, long from, int ticks) {
        for (int k = 0; k <= ticks; k++) {
            if (lands(from + k, pattern.period, pattern.phase(chute))) {
                return false;
            }
        }
        return true;
    }
}
