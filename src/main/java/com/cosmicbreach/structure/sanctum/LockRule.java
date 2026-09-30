package com.cosmicbreach.structure.sanctum;

/**
 * The Eclipse Locks and the Throne Stair (GDD 6.1), pure: each wing's solve lights its lock for good, and the stair
 * opens the moment both are lit, once. Nothing ever puts a lock out.
 *
 * @param west  the Lens of Solenne's lock is lit
 * @param east  the Choir of the Unsung's lock is lit
 * @param stair the Throne Stair is open
 */
public record LockRule(boolean west, boolean east, boolean stair) {
    public static final LockRule SEALED = new LockRule(false, false, false);
    /** Ticks between the second lock lighting and the seal dissolving. */
    public static final int STAIR_DELAY = 40;

    /** After {@code wing}'s puzzle is solved. */
    public LockRule light(SanctumLayout.Wing wing) {
        boolean w = west || wing == SanctumLayout.Wing.WEST;
        boolean e = east || wing == SanctumLayout.Wing.EAST;
        return new LockRule(w, e, stair || (w && e));
    }

    public boolean lit(SanctumLayout.Wing wing) {
        return wing == SanctumLayout.Wing.WEST ? west : east;
    }

    /** True if lighting {@code wing} now is what opens the stair. */
    public boolean opensStair(SanctumLayout.Wing wing) {
        return !stair && light(wing).stair;
    }

    /** How many locks burn. */
    public int count() {
        return (west ? 1 : 0) + (east ? 1 : 0);
    }
}
