package com.cosmicbreach.structure.crypt.trap;

/**
 * Crushing Gravity Plates (GDD 6.4). Stepping onto the 5 by 5 sigil makes gravity {@link #GRAVITY}x for
 * {@link #HEAVY_TICKS} ticks: no jumping, speed {@link #SPEED}x (-60%), dashes half their length. The plate's cycle
 * starts with the first step; on tick {@link #PISTON_TICK} of it the Gravity Piston slams down onto the sigil:
 * {@link #PISTON_DAMAGE} damage and a stagger to anyone still inside. Its tells, faint concentric rings on the
 * floor and a hum from the ceiling, reach careful players only (rings within {@link #REVEAL_RADIUS}, hum within
 * {@link #HUM_RADIUS} blocks). Pure.
 */
public final class GravityPlateRules {
    public static final int SIGIL = 5;
    public static final double GRAVITY = 3.0;
    public static final int HEAVY_TICKS = 60;
    public static final double SPEED = 0.4;
    public static final double DASH_SCALE = 0.5;
    public static final int PISTON_TICK = 40;
    public static final float PISTON_DAMAGE = 10.0f;
    /** The piston's drop, in ticks before it lands, and how long it rests down before rising. */
    public static final int DROP_TICKS = 3;
    public static final int HOLD_TICKS = 6;
    /** After a cycle ends, the plate waits this long before it can start another. */
    public static final int REARM_TICKS = 40;
    public static final double REVEAL_RADIUS = 4.0;
    public static final double HUM_RADIUS = 6.0;

    private GravityPlateRules() {
    }

    /** True while a player who stepped on at {@code stepped} is heavy at {@code now}. */
    public static boolean heavy(long stepped, long now) {
        return stepped >= 0 && now >= stepped && now < stepped + HEAVY_TICKS;
    }

    /** True if the piston lands on this tick of a cycle that started at {@code start}. */
    public static boolean slams(long start, long now) {
        return start >= 0 && now == start + PISTON_TICK;
    }

    /** True once a cycle that started at {@code start} is over (the plate is quiet again at {@link #rearmed}). */
    public static boolean over(long start, long now) {
        return start >= 0 && now >= start + HEAVY_TICKS;
    }

    /** True if a plate whose last cycle started at {@code start} can start a new one at {@code now}. */
    public static boolean rearmed(long start, long now) {
        return start < 0 || now >= start + HEAVY_TICKS + REARM_TICKS;
    }

    /**
     * The piston head's drop below the ceiling, 0 (up) to 1 (on the floor), at {@code t} ticks into a cycle: it
     * falls over the {@link #DROP_TICKS} ticks before {@link #PISTON_TICK}, rests {@link #HOLD_TICKS}, and rises
     * back over 10 ticks.
     */
    public static double drop(double t) {
        double land = PISTON_TICK;
        if (t < land - DROP_TICKS || t > land + HOLD_TICKS + 10) {
            return 0;
        }
        if (t < land) {
            double u = (t - (land - DROP_TICKS)) / DROP_TICKS;
            return u * u;
        }
        if (t <= land + HOLD_TICKS) {
            return 1;
        }
        return 1 - (t - land - HOLD_TICKS) / 10.0;
    }
}
