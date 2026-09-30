package com.cosmicbreach.onboarding;

/**
 * The Landing's plan (GDD 1.3): a 6 by 6 platform of Starfall Stone Bricks, flush with the island's surface,
 * round an open 4 by 4 return ring (12 lit frames round the 2 by 2 Breach). Offsets are from the ring's
 * origin, the Breach's north-west block: the ring's border runs from -1 to 2, the platform from -2 to 3. The
 * arrival column falls on the platform's west edge beside the Breach's north-west block, two blocks from the
 * hole: a player drifting down on Slow Falling lands on the bricks, never in the Breach.
 */
public final class LandingLayout {
    /** The platform runs from {@link #MIN} to {@link #MAX} on both axes. */
    public static final int MIN = -2;
    public static final int MAX = 3;
    /** Where the arrival column is, relative to the ring's origin. */
    public static final int ARRIVAL_DX = -2;
    public static final int ARRIVAL_DZ = 0;

    public enum Part { BRICK, FRAME, BREACH, NONE }

    private LandingLayout() {
    }

    /** The ring's origin for an arrival column at (x, z): {x, z}. */
    public static int[] originFor(int arrivalX, int arrivalZ) {
        return new int[] {arrivalX - ARRIVAL_DX, arrivalZ - ARRIVAL_DZ};
    }

    /** The arrival column for a Landing whose ring's origin is (x, z): {x, z}. */
    public static int[] arrivalFor(int originX, int originZ) {
        return new int[] {originX + ARRIVAL_DX, originZ + ARRIVAL_DZ};
    }

    /** What stands at (dx, dz) from the ring's origin, on the platform's level. */
    public static Part partAt(int dx, int dz) {
        if (dx < MIN || dx > MAX || dz < MIN || dz > MAX) {
            return Part.NONE;
        }
        if (dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1) {
            return Part.BREACH;
        }
        if (dx >= -1 && dx <= 2 && dz >= -1 && dz <= 2) {
            return Part.FRAME;
        }
        return Part.BRICK;
    }
}
