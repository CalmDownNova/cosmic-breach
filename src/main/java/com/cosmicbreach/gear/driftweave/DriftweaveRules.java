package com.cosmicbreach.gear.driftweave;

/**
 * The Driftweave's numbers (GDD 5.1), pure. Two pieces: +1 dash charge and dashes 20% longer. Four pieces,
 * Slipstream: a perfect dodge leaves an Afterimage for {@value #AFTERIMAGE_TICKS} ticks that repeats the next
 * {@value #AFTERIMAGE_REPEATS} attacks from where it stands at {@value #AFTERIMAGE_DAMAGE} of their damage. The set
 * ability, Drift: {@value #DRIFT_TICKS} ticks of personal 0.4x gravity, jumps 1.5x as high, free air dashes and
 * aerial attacks +20%, every 28 s.
 */
public final class DriftweaveRules {
    // two pieces
    public static final int DASH_CHARGES = 1;
    public static final double DASH_DISTANCE = 1.2;

    // four pieces, Slipstream
    public static final int AFTERIMAGE_TICKS = 60;
    public static final int AFTERIMAGE_REPEATS = 3;
    public static final double AFTERIMAGE_DAMAGE = 0.4;
    /** The copies' Impact, as a share of the move's: they push and stagger as lightly as they hurt. */
    public static final double AFTERIMAGE_IMPACT = 0.4;

    // the set ability, Drift
    public static final int DRIFT_COOLDOWN = 560;
    public static final int DRIFT_TICKS = 100;
    public static final double DRIFT_GRAVITY = 0.4;
    public static final double DRIFT_JUMP_HEIGHT = 1.5;
    public static final double AERIAL_DAMAGE = 1.2;

    /** Vanilla's jump (the jump strength attribute's default) and gravity, blocks a tick. */
    public static final double VANILLA_JUMP = 0.42;
    public static final double VANILLA_GRAVITY = 0.08;
    /** Vanilla keeps this share of a body's vertical speed each tick, after gravity (a float in the game). */
    public static final double AIR_DRAG = 0.98f;

    private static final double JUMP_MULTIPLIER = solveJumpMultiplier(VANILLA_JUMP, VANILLA_GRAVITY * DRIFT_GRAVITY, DRIFT_JUMP_HEIGHT);

    private DriftweaveRules() {
    }

    /**
     * How high a jump at {@code jump} blocks a tick rises under {@code gravity}, as vanilla moves a living body:
     * each tick it moves by its vertical speed, then loses the gravity and keeps 98% of what is left.
     */
    public static double jumpApex(double jump, double gravity) {
        double y = 0.0;
        double v = jump;
        double top = 0.0;
        for (int tick = 0; tick < 2000 && v > 0; tick++) {
            y += v;
            top = Math.max(top, y);
            v = (v - gravity) * AIR_DRAG;
        }
        return top;
    }

    /**
     * The jump strength multiplier that makes a jump under Drift's gravity rise {@link #DRIFT_JUMP_HEIGHT} times as
     * high as the same jump under that gravity alone (about sqrt(1.5): with no drag the height goes with the square
     * of the jump speed; the drag bends it a little).
     */
    public static double driftJumpMultiplier() {
        return JUMP_MULTIPLIER;
    }

    static double solveJumpMultiplier(double jump, double gravity, double heightRatio) {
        double target = heightRatio * jumpApex(jump, gravity);
        double lo = 1.0;
        double hi = 3.0;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            if (jumpApex(jump * mid, gravity) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    /** What a copy of a hit of {@code damage} deals. */
    public static double afterimageDamage(double damage) {
        return damage * AFTERIMAGE_DAMAGE;
    }

    /** True if an attack of this kind while airborne (or a plunge) takes Drift's +20%. */
    public static boolean aerial(boolean plunge, boolean onGround) {
        return plunge || !onGround;
    }
}
