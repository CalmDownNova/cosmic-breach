package com.cosmicbreach.world.gen;

/**
 * What it takes to cross an open gap between two pads in the Drift (Aetheria 1.1 Design, section 2), from the audit's figures
 * for level 2 (gravity 0.4x, jump 1.3x, apex 4.1 blocks): a running jump on foot, then the air dash outside the low gravity
 * window, then what only the window (the Driftweave's Drift) or a mount can cross. {@link DriftBelts} lays its stepping stones
 * by this and the layout test reports its gaps by it.
 *
 * <p>A gap is the open ground between the edges of the two pads, and the hop is taken from the higher pad: a player who cannot
 * climb a step (the apex is 4.1) drops across instead, and a hop down reaches further than a level one.
 *
 * <p>The numbers here freeze with 1.1. Changing one changes which stepping stones generate, in chunks generated after the change
 * only (a seam in every world generated before it), and the table is only as good as the game numbers it was measured for
 * (gravity, the low gravity jump, the air dash): {@code DriftReachTest} pins those and compares the table with a tick simulation
 * of them, so retuning one fails a test instead of leaving the stones on the old physics.
 */
public final class DriftReach {
    /** What a gap needs. */
    public enum Crossing {
        /** A running jump crosses it. */
        JUMP,
        /** Too wide to jump, within reach of the air dash outside the window. */
        DASH,
        /** Wider still: the window's free dashes or a mount (a stingray always crosses it). */
        MOUNT
    }

    /** What two timed air dashes add to a running jump outside the window: the audit's 12.7 blocks against 10.4 on the level. */
    public static final double DASH_BONUS = 2.3;

    /** The audit's running jump, by how far the landing pad lies above the takeoff (negative: below): rise, then reach. */
    private static final double[][] RUNNING_JUMP = {{4.1, 0.0}, {3.0, 7.7}, {0.0, 10.4}, {-5.0, 12.9}, {-10.0, 14.9}, {-20.0, 18.3}};
    /** Past the last figure a block more of fall buys this much reach. */
    private static final double FALL_SLOPE = 0.34;

    /**
     * A stepping stone is there to make a crossing easy: it is laid only where each hop it leaves is within this share of the
     * running jump's reach. The audit's figures are the best a player can do, with a full run and the takeoff at the very edge.
     */
    public static final double COMFORT = 0.8;

    private DriftReach() {
    }

    /** The edge to edge reach of a running jump that lands {@code rise} blocks above where it starts (negative: below). */
    public static double jump(double rise) {
        if (rise >= RUNNING_JUMP[0][0]) {
            return 0.0;
        }
        for (int i = 0; i + 1 < RUNNING_JUMP.length; i++) {
            double[] hi = RUNNING_JUMP[i];
            double[] lo = RUNNING_JUMP[i + 1];
            if (rise >= lo[0]) {
                return lo[1] + (rise - lo[0]) / (hi[0] - lo[0]) * (hi[1] - lo[1]);
            }
        }
        double[] last = RUNNING_JUMP[RUNNING_JUMP.length - 1];
        return last[1] + FALL_SLOPE * (last[0] - rise);
    }

    /** What an open gap of {@code gap} blocks needs between two pads whose tops are {@code drop} apart (taken from the higher). */
    public static Crossing crossing(double gap, double drop) {
        double reach = jump(-Math.abs(drop));
        if (gap <= reach) {
            return Crossing.JUMP;
        }
        return gap <= reach + DASH_BONUS ? Crossing.DASH : Crossing.MOUNT;
    }

    /** Whether a hop of {@code gap} blocks between two pads whose tops are {@code drop} apart leaves room to spare ({@link #COMFORT}). */
    public static boolean comfortable(double gap, double drop) {
        return gap <= COMFORT * jump(-Math.abs(drop));
    }
}
