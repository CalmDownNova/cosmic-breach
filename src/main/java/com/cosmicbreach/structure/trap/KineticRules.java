package com.cosmicbreach.structure.trap;

/**
 * The Kinetic Tripwire's numbers (GDD 6.4, "careful movement reveals, reckless movement triggers"): a thread
 * crossed faster than walking pace fires {@link #BOLTS} bolts from the wall emitters, {@link #BOLT_INTERVAL}
 * ticks apart, each dealing 4 x (speed / walking speed), at most 12. Walking through is safe. Sneaking or walking
 * within {@link #REVEAL_RADIUS} blocks shows the thread; sprinting or dashing shows nothing. Pure, for tests.
 */
public final class KineticRules {
    /** Vanilla walking pace, blocks per tick (4.317 blocks a second). */
    public static final double WALK_SPEED = 0.21585;
    /** Faster than this many times walking pace springs a thread: sprinting (1.3) does, walking (1.0) doesn't. */
    public static final double TRIGGER_RATIO = 1.15;
    public static final double DAMAGE_PER_RATIO = 4.0;
    public static final double MAX_DAMAGE = 12.0;
    public static final int BOLTS = 3;
    public static final int BOLT_INTERVAL = 2;
    public static final double REVEAL_RADIUS = 4.0;
    /** A position change bigger than this in one tick is a teleport, not a run. */
    public static final double TELEPORT = 3.0;

    private KineticRules() {
    }

    /** Speed as a multiple of walking pace. */
    public static double ratio(double blocksPerTick) {
        return blocksPerTick / WALK_SPEED;
    }

    /** True if crossing a thread at this speed (blocks per tick, horizontal) fires it. */
    public static boolean triggers(double blocksPerTick) {
        return blocksPerTick < TELEPORT && ratio(blocksPerTick) > TRIGGER_RATIO;
    }

    /** Each bolt's damage for a crossing at this speed. */
    public static float damage(double blocksPerTick) {
        return (float) Math.min(MAX_DAMAGE, DAMAGE_PER_RATIO * ratio(blocksPerTick));
    }

    /** True if a player moving like this sees threads near them: not sprinting, and at walking pace or slower. */
    public static boolean careful(boolean sprinting, double blocksPerTick) {
        return !sprinting && ratio(blocksPerTick) <= TRIGGER_RATIO;
    }

    /** The tick offsets of the bolts after a crossing. */
    public static int boltTick(int bolt) {
        return bolt * BOLT_INTERVAL;
    }
}
