package com.cosmicbreach.guardian.leviathan;

/**
 * A Moorage's clock (Thalassine Leviathan design v1, "Phases"): at half health, and again at a fifth, the Leviathan swims
 * into the centre and coils round the central asteroid for {@value #COILED} ticks, holding still, its armor plates lifted
 * and its four song glands glowing (x2 damage). It shudders every {@value #SHUDDER_EVERY} ticks, each shudder told
 * {@value #SHUDDER_TELL} ticks ahead by a ripple of light running along its body. Then it tears free (a roar and a burst of
 * silver dust, {@value #TEAR_FREE} ticks) and swims back to its orbit. A Moorage before the last closes its armor plates
 * over the glands once it has lost a fifth of its health ({@link #CLOSE_AFTER}): its health holds there and it only keeps
 * its coil till it tears free, so a strong player can't carry the first Moorage past the second line and skip phase 2.
 * Ticks here count from when the coil settles. Pure.
 */
public final class Moorage {
    public static final int COILED = 300;
    public static final int SHUDDER_EVERY = 80;
    public static final int SHUDDER_TELL = 16;
    public static final int TEAR_FREE = 30;
    /** A Moorage before the last closes its plates once it has lost this fraction of its health since it began. */
    public static final double CLOSE_AFTER = 0.2;
    /** The Driftwood bridges grow out from the nearest platforms over this long. */
    public static final int BRIDGE_GROW = 30;

    private Moorage() {
    }

    /** True on the tick a shudder lands ({@code t} ticks into the coil): 80, 160 and 240. */
    public static boolean shudders(int t) {
        return t > 0 && t < COILED && t % SHUDDER_EVERY == 0;
    }

    /** True on the tick a shudder's ripple starts. */
    public static boolean rippleStarts(int t) {
        return shudders(t + SHUDDER_TELL);
    }

    /** How far the ripple has run along the body (0 at the head, 1 at the tail), or -1 outside a ripple. */
    public static double ripple(double t) {
        double next = Math.ceil(t / SHUDDER_EVERY) * SHUDDER_EVERY;
        if (next <= 0 || next >= COILED) {
            return -1;
        }
        double into = t - (next - SHUDDER_TELL);
        return into < 0 ? -1 : Math.min(1.0, into / SHUDDER_TELL);
    }

    /** Ticks into the swim in before the swell is over and a line may sound (the swell is the Moorage's own warning). */
    public static final int SWELL = 30;

    /**
     * How long it is quiet {@code t} ticks into the swim in with {@code swimLeft} ticks of swimming left: none while the swell
     * sounds, then the rest of the swim and the coil's first {@value #SHUDDER_EVERY} minus {@value #SHUDDER_TELL} ticks, up to the
     * first ripple. Her opening line is said here.
     */
    public static int swimInQuietTicks(long t, long swimLeft) {
        if (t < SWELL) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE / 2, swimLeft + SHUDDER_EVERY - SHUDDER_TELL);
    }

    /** True if a swim in of {@code swimTicks} ticks leaves room, from the end of the swell, for words that end {@code speechTicks} into a take. */
    public static boolean swimInFits(int swimTicks, int speechTicks) {
        return swimInQuietTicks(SWELL, swimTicks - SWELL) >= speechTicks + com.cosmicbreach.voice.boss.VoiceDirector.QUIET_MARGIN;
    }

    /** How long it is quiet {@code c} ticks into the coil: up to the next ripple's tell, none from it through the shudder. */
    public static int coilQuietTicks(int c) {
        int q = 0;
        while (c + q < COILED && ripple(c + q) < 0) {
            q++;
        }
        return q;
    }

    /** The number of shudders in one Moorage. */
    public static int shudderCount() {
        int n = 0;
        for (int t = 1; t < COILED; t++) {
            if (shudders(t)) {
                n++;
            }
        }
        return n;
    }

    /** True once the coil has held its time: it tears free. */
    public static boolean tearsFree(int t) {
        return t >= COILED;
    }

    /**
     * The health line of the next Moorage after {@code done} of them (a fraction of its health), or -1 when both are
     * done.
     */
    public static double nextLine(int done) {
        return done < LeviathanMoves.MOORAGE_AT.length ? LeviathanMoves.MOORAGE_AT[done] : -1.0;
    }

    /**
     * Health a hit leaves during Moorage {@code started} (1 for the first) that began at {@code startHealth}: one before
     * the last can't lose more than {@link #CLOSE_AFTER} of {@code max}, and is held there.
     */
    public static float hold(float startHealth, float health, float max, int started) {
        if (started <= 0 || started >= LeviathanMoves.MOORAGE_AT.length) {
            return health;
        }
        return Math.max(health, startHealth - (float) (CLOSE_AFTER * max));
    }

    /** True once Moorage {@code started} (not the last) has lost its fifth: its plates close over the glands. */
    public static boolean platesClose(float startHealth, float health, float max, int started) {
        return started > 0 && started < LeviathanMoves.MOORAGE_AT.length && health <= startHealth - CLOSE_AFTER * max + 1e-3;
    }

    /**
     * Health can't pass a Moorage line in one hit: a hit that would is held at the line, and the Moorage begins. Returns
     * the health to keep ({@code health} itself when no line is crossed).
     */
    public static float clamp(float before, float health, float max, int done) {
        double line = nextLine(done);
        if (line < 0 || health >= before) {
            return health;
        }
        float at = (float) (max * line);
        return before > at && health <= at ? at : health;
    }
}
