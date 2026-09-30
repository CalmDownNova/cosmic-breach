package com.cosmicbreach.guardian.heliarch;

/**
 * Corona Sweep (GDD 7.3): {@value HeliarchMoves#SWEEP_TELL} ticks of a glowing band on the floor between radius 10 and
 * 16, then a knee-high wall of solar fire runs once round the band, clockwise from the Heliarch's facing, in
 * {@value HeliarchMoves#SWEEP_TICKS} ticks. A player in the band whose feet are under
 * {@value HeliarchMoves#SWEEP_CLEAR} over the floor when the wall passes them takes 20: jump it, or stand inside 10
 * or outside 16. Pure: {@code t} counts from the wall's start.
 */
public final class CoronaSweep {
    private CoronaSweep() {
    }

    /** The wall's compass angle {@code t} ticks after it starts. */
    public static double wallAngle(double start, double t) {
        double a = (start + 360.0 * Math.max(0.0, Math.min(1.0, t / HeliarchMoves.SWEEP_TICKS))) % 360.0;
        return a < 0 ? a + 360.0 : a;
    }

    /** True if (x, z) is in the band (a player's edge counts). */
    public static boolean inBand(double x, double z) {
        double r = HeliarchArena.radiusOf(x, z);
        return r >= HeliarchMoves.SWEEP_INNER - 0.3 && r <= HeliarchMoves.SWEEP_OUTER + 0.3;
    }

    /** True if the wall passed the compass angle {@code angle} between {@code t0} (exclusive) and {@code t1} (inclusive). */
    public static boolean passes(double angle, double start, double t0, double t1) {
        double along = (angle - start) % 360.0;
        if (along < 0) {
            along += 360.0;
        }
        if (along < 1e-6 || along > 360.0 - 1e-6) {
            along = 0.0;
        }
        double s0 = t0 < 0 ? -1e-6 : 360.0 * Math.min(1.0, t0 / HeliarchMoves.SWEEP_TICKS);
        double s1 = 360.0 * Math.min(1.0, t1 / HeliarchMoves.SWEEP_TICKS);
        return along > s0 && along <= s1;
    }

    /** True if the wall strikes a player at (x, z), feet {@code feet} over the floor, as it moves from t0 to t1. */
    public static boolean strikes(double x, double z, double feet, double start, double t0, double t1) {
        return inBand(x, z) && feet < HeliarchMoves.SWEEP_CLEAR && passes(HeliarchArena.angleOf(x, z), start, t0, t1);
    }
}
