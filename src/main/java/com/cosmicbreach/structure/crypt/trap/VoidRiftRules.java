package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.trap.KineticRules;
import com.cosmicbreach.structure.trap.MotionWindow;

/**
 * Void Rift tiles (GDD 6.4): they punish standing still. A cluster is a 3 by 3 patch of floor. A tick counts only when a
 * player is truly standing on it ({@link #standing}): feet on one of its tiles, not dashing, and moving slower than a
 * quarter of walking pace averaged over the last three ticks ({@link MotionWindow}), so neither a walk, a sprint, a
 * sneak nor a dash across ever counts, even when a busy server bunches their movement. {@link #STAND_TICKS} such ticks
 * spring it; on tick {@link #OPEN_AT}, counted from the first of them, the whole patch vanishes for {@link #OPEN_TICKS}
 * ticks, dropping whoever is on it into the Void Pocket below. Stepping off the cluster starts the count again. Its tell,
 * hairline cracks with a violet shimmer, shows only to careful players within {@link #REVEAL_RADIUS} blocks. Pure.
 */
public final class VoidRiftRules {
    public static final int SIZE = 3;
    public static final int STAND_TICKS = 6;
    public static final int OPEN_AT = 16;
    public static final int OPEN_TICKS = 60;
    /** After closing, the patch can't spring again for this long. */
    public static final int REARM_TICKS = 20;
    public static final double REVEAL_RADIUS = 4.0;
    /** Slower than this, averaged over three ticks (blocks per tick), a player on the cluster is standing: a quarter of walking pace. */
    public static final double STILL_SPEED = KineticRules.WALK_SPEED / 4;

    private VoidRiftRules() {
    }

    /** True if a tick on the cluster counts: feet on it, not dashing, and barely moving over the last three ticks. */
    public static boolean standing(boolean onGround, boolean dashing, double averageSpeed) {
        return onGround && !dashing && averageSpeed < STILL_SPEED;
    }

    /** The count after a tick: standing on the cluster adds one, moving on it keeps it, being off it starts it again. */
    public static int count(int count, boolean onCluster, boolean standing) {
        if (!onCluster) {
            return 0;
        }
        return standing ? count + 1 : count;
    }

    /** True if a player who has stood {@code count} ticks springs the cluster. */
    public static boolean springs(int count) {
        return count >= STAND_TICKS;
    }

    /** Ticks from springing (the tick {@link #springs} first held) to the patch opening. */
    public static int openDelay() {
        return OPEN_AT - (STAND_TICKS - 1);
    }

    /** For tests: {@link #crossing(double[], int[], boolean[])} with one movement packet for each tick that moved. */
    public static int crossing(double[] moved, boolean[] dashing) {
        return crossing(moved, new int[0], dashing);
    }

    /**
     * For tests: a player crossing the patch in a straight line, {@code moved[t]} blocks on server tick t carried by
     * {@code packets[t]} movement packets (a busy server's bunch is a tick with none, then one with several; an empty
     * array means one packet for each tick that moved), with a dash on the ticks {@code dashing[t]} marks. Returns the
     * tick (from the first tick stood on it) the patch opens under them, or -1 if it never springs or they are off it
     * by then.
     */
    public static int crossing(double[] moved, int[] packets, boolean[] dashing) {
        MotionWindow window = new MotionWindow();
        window.endTick(0, 0, 0);
        double at = 0;
        int count = 0;
        int firstStood = -1;
        int springTick = -1;
        for (int t = 0; t < 400; t++) {
            double step = t < moved.length ? moved[t] : 0;
            at += step;
            window.endTick(at, 0, packets.length == 0 ? (step > 0 ? 1 : 0) : t < packets.length ? packets[t] : 0);
            boolean on = at < SIZE;
            if (springTick < 0) {
                boolean still = standing(true, t < dashing.length && dashing[t], window.average());
                count = count(count, on, still);
                if (still && on && firstStood < 0) {
                    firstStood = t;
                }
                if (springs(count)) {
                    springTick = t;
                }
                if (!on) {
                    return -1;
                }
            }
            if (springTick >= 0 && t == springTick + openDelay()) {
                return on ? t - firstStood : -1;
            }
        }
        return -1;
    }

    /** For tests: {@code ticks} ticks of {@code speed} blocks each. */
    public static double[] steady(double speed, int ticks) {
        double[] out = new double[ticks];
        java.util.Arrays.fill(out, speed);
        return out;
    }
}
