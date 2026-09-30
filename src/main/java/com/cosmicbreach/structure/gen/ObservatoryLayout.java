package com.cosmicbreach.structure.gen;

import com.cosmicbreach.structure.lens.LensDifficulty;
import java.util.Random;
import net.minecraft.core.Direction;

/**
 * A Gyre Observatory's plan (GDD 6.1), all from its seed: a Driftstone tower grown through an asteroid of radius
 * {@link #asteroid}, its root spike coming out below, {@link #floors} floors (5 to 8) above the asteroid's
 * flattened top joined by gravity lifts, a Kinetic Tripwire across every middle floor between its arrival and
 * departure lifts, and on top the telescope chamber: a wide round room under a glass dome with the 7 by 7 Lens
 * Array (medium or hard: 2 or 3 receptors, filters, splitters), the vault, and the Gyre Knight's post.
 *
 * <p>Coordinates are relative to the asteroid's centre.
 */
public record ObservatoryLayout(int asteroid, int floors, Direction entry, Direction lift, LensDifficulty difficulty, long puzzleSeed) {
    public static final int STOREY = 7;
    public static final double TOWER = 9.0;
    public static final double TOWER_INSIDE = 8.0;
    public static final double CHAMBER = 13.0;
    public static final double CHAMBER_INSIDE = 12.0;
    /** The telescope chamber's pedestals to its ceiling. */
    public static final int CEILING = 8;
    public static final int DOME = 13;

    /** A plan whose tower fits {@code headroom} blocks above the asteroid's centre (5 floors at least). */
    public static ObservatoryLayout of(long seed, int asteroid, int headroom) {
        Random r = new Random((seed ^ 0x0B5E7A7EL) * 0x9E3779B97F4A7C15L);
        int fit = (headroom - asteroid - 20) / STOREY; // height() = asteroid + 7 floors + 20
        int floors = Math.max(5, Math.min(Math.min(8, fit), 5 + r.nextInt(4)));
        Direction entry = Direction.from2DDataValue(r.nextInt(4));
        Direction lift = Direction.from2DDataValue(r.nextInt(4));
        LensDifficulty diff = r.nextInt(3) == 0 ? LensDifficulty.HARD_7 : LensDifficulty.MEDIUM_7;
        return new ObservatoryLayout(asteroid, floors, entry, lift, diff, r.nextLong());
    }

    /** Air bottom of the entrance floor: on the asteroid's flattened top. */
    public int base() {
        return asteroid - 2;
    }

    /** Air bottom of floor {@code k}. */
    public int floorY(int k) {
        return base() + STOREY * k;
    }

    /** The telescope chamber's floor index. */
    public int top() {
        return floors - 1;
    }

    /** Where floor {@code k}'s lift up leaves from: alternate sides of the lift axis; the last one in the walkway. */
    public Direction liftSide(int k) {
        return k % 2 == 0 ? lift : lift.getOpposite();
    }

    public int liftRadius(int k) {
        return k == floors - 2 ? 7 : 5;
    }

    /**
     * Where floor {@code k}'s lift column stands, relative to the axis: {@link #liftRadius} out on its side, and the
     * last one (up into the telescope chamber) one block round, so it comes up in the walkway between the grid and
     * its sockets, clear of the pedestal in the middle of that side.
     */
    public int liftX(int k) {
        Direction side = liftSide(k);
        return side.getStepX() * liftRadius(k) + (k == floors - 2 ? side.getClockWise().getStepX() : 0);
    }

    public int liftZ(int k) {
        Direction side = liftSide(k);
        return side.getStepZ() * liftRadius(k) + (k == floors - 2 ? side.getClockWise().getStepZ() : 0);
    }

    /** Height above the asteroid's centre of the dome's top and telescope. */
    public int height() {
        return floorY(top()) + CEILING + DOME + 8;
    }

    /** Depth below the asteroid's centre of the root's tip. */
    public int depth() {
        return asteroid + 10;
    }
}
