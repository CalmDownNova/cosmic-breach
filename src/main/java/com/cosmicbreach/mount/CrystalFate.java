package com.cosmicbreach.mount;

/**
 * What becomes of the mount inside a Stable Crystal when the crystal's item is lost (quality review, Important 1, and its
 * re-review), pure. A held mount is never deleted with its item. An item that falls out of the world, or is killed, or is
 * destroyed, is handed to its owner: into their pack at once if they are online and alive, else owed to them until they next
 * log in or respawn (the ledger the guardians' rewards use). A killed or destroyed item whose owner is not about sets its mount
 * down where it was instead, if there is room. Only an item with no owner on record (never one that holds a tamed mount) is
 * held where it is, lifted to just above the line where the world deletes items.
 */
public final class CrystalFate {
    /** An item is out of the world this far under its floor (the world discards it 64 under). */
    public static final int FALL_MARGIN = 8;

    public enum Fate {
        /** The crystal goes to its owner: into their pack now if they are about, else owed to them until they are. */
        HAND_OVER,
        /** The item is lifted to just above the line where the world deletes items and stops there. */
        HOLD,
        /** The mount is set down where the item was. */
        FREE_HERE,
        /** Nothing can be done: the one way a mount is lost. */
        LOST
    }

    private CrystalFate() {
    }

    /**
     * True if a crystal may go inside an item that carries other items (a shulker box, a bundle). A full one may not: the guard
     * protects an item entity whose own stack is a full crystal, and a box is an ordinary item, which the void, a despawn or
     * a kill deletes with everything inside it. An empty crystal has nothing to lose.
     */
    public static boolean fitsInsideItems(boolean holdsMount) {
        return !holdsMount;
    }

    /** True if an item at {@code y} has fallen out of a world whose floor is {@code minBuildHeight}. */
    public static boolean fellOut(double y, int minBuildHeight) {
        return y < minBuildHeight - FALL_MARGIN;
    }

    /** Where a held item is lifted to: the edge of the margin, so it is not "out of the world" again and the world never deletes it. */
    public static double holdHeight(int minBuildHeight) {
        return minBuildHeight - FALL_MARGIN;
    }

    /** An item that fell out of the world and still exists: {@code ownerKnown} is whether its mount's owner is on record. */
    public static Fate whenFallen(boolean ownerKnown) {
        return ownerKnown ? Fate.HAND_OVER : Fate.HOLD;
    }

    /**
     * An item that was killed or destroyed and is going: its owner is given the crystal if they are about
     * ({@code ownerHere}); else its mount is set down where it was if there is room ({@code roomHere}); else the crystal is
     * owed to its owner ({@code ownerKnown}); else the mount is lost.
     */
    public static Fate whenKilled(boolean ownerHere, boolean roomHere, boolean ownerKnown) {
        if (ownerHere) {
            return Fate.HAND_OVER;
        }
        if (roomHere) {
            return Fate.FREE_HERE;
        }
        return ownerKnown ? Fate.HAND_OVER : Fate.LOST;
    }
}
