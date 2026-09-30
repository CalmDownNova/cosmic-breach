package com.cosmicbreach.onboarding;

import java.util.ArrayList;
import java.util.List;

/**
 * The Breach Ring's shape (GDD 1.3, 4 by 4 since 2026-09-28): 12 Breach Frames flat on the ground forming the
 * border of a 4 by 4 square, its 2 by 2 middle empty, two blocks of open air above each middle block, in the
 * Overworld. A ring is named by its <em>origin</em>: the north-west block of the middle (lowest x and z). Pure:
 * the world is behind {@link View}, so the rule and its hints are unit tested. {@link #find} works out which
 * ring a clicked frame belongs to.
 */
public final class BreachRing {
    /**
     * The frames' offsets from the origin (dx, dz): the north row west to east, the two sides, the south row.
     * The border runs from -1 to 2 on both axes.
     */
    public static final int[][] RING = {
            {-1, -1}, {0, -1}, {1, -1}, {2, -1},
            {-1, 0}, {2, 0},
            {-1, 1}, {2, 1},
            {-1, 2}, {0, 2}, {1, 2}, {2, 2}};
    /** The middle's offsets from the origin: north-west, north-east, south-west, south-east. */
    public static final int[][] MIDDLE = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};

    /** What is wrong with a ring, in the order the player should fix it. */
    public enum Problem { NONE, NOT_OVERWORLD, ALREADY_OPEN, MISSING_FRAMES, CENTRE_BLOCKED, NO_HEADROOM }

    /** Where a frame sits on the border. */
    public enum Edge { NORTH, SOUTH, WEST, EAST }

    /** The world around a ring. */
    public interface View {
        boolean frame(int x, int y, int z);

        /** Air, or something a player walks through that the Breach may replace (grass, snow). */
        boolean open(int x, int y, int z);

        boolean breach(int x, int y, int z);
    }

    /**
     * A check's outcome for the ring whose origin is ({@code x}, {@code y}, {@code z}). {@code missing} holds the
     * offsets (dx, dz) of the missing frames; {@code shifted} those of them that sit one block too high
     * ({@code +1}) or too low ({@code -1}) instead, as (dx, dz, dy).
     */
    public record Result(Problem problem, int x, int y, int z, List<int[]> missing, List<int[]> shifted) {
        public boolean ok() {
            return problem == Problem.NONE;
        }

        public int frames() {
            return RING.length - missing.size();
        }
    }

    private BreachRing() {
    }

    /** Checks the ring whose origin is (x, y, z). */
    public static Result check(View view, boolean overworld, int x, int y, int z) {
        List<int[]> missing = new ArrayList<>();
        List<int[]> shifted = new ArrayList<>();
        for (int[] o : RING) {
            if (!view.frame(x + o[0], y, z + o[1])) {
                missing.add(o);
                if (view.frame(x + o[0], y + 1, z + o[1])) {
                    shifted.add(new int[] {o[0], o[1], 1});
                } else if (view.frame(x + o[0], y - 1, z + o[1])) {
                    shifted.add(new int[] {o[0], o[1], -1});
                }
            }
        }
        boolean open = false;
        boolean clear = true;
        boolean headroom = true;
        for (int[] m : MIDDLE) {
            open |= view.breach(x + m[0], y, z + m[1]);
            clear &= view.open(x + m[0], y, z + m[1]);
            headroom &= view.open(x + m[0], y + 1, z + m[1]) && view.open(x + m[0], y + 2, z + m[1]);
        }
        Problem problem;
        if (!overworld) {
            problem = Problem.NOT_OVERWORLD;
        } else if (open) {
            problem = Problem.ALREADY_OPEN;
        } else if (!missing.isEmpty()) {
            problem = Problem.MISSING_FRAMES;
        } else if (!clear) {
            problem = Problem.CENTRE_BLOCKED;
        } else if (!headroom) {
            problem = Problem.NO_HEADROOM;
        } else {
            problem = Problem.NONE;
        }
        return new Result(problem, x, y, z, List.copyOf(missing), List.copyOf(shifted));
    }

    /**
     * The ring a frame at (x, y, z) most likely belongs to: all 12 origins it could be part of are checked, and
     * the best is returned (a valid or already open ring first, then the one with the most frames in place,
     * then one whose middle is clear).
     */
    public static Result find(View view, boolean overworld, int x, int y, int z) {
        Result best = null;
        for (int[] o : RING) {
            Result r = check(view, overworld, x - o[0], y, z - o[1]);
            if (best == null || rank(r) > rank(best)) {
                best = r;
            }
        }
        return best;
    }

    private static int rank(Result r) {
        int score = r.frames() * 10;
        if (r.problem() == Problem.NONE || r.problem() == Problem.ALREADY_OPEN) {
            score += 1000;
        }
        // a middle holding frames or blocks belongs to some other shape, not this ring
        if (r.problem() == Problem.CENTRE_BLOCKED) {
            score -= 1;
        }
        return score;
    }

    /** True if the offset (dx, dz) is one of the border's four corners. */
    public static boolean corner(int dx, int dz) {
        return (dx == -1 || dx == 2) && (dz == -1 || dz == 2);
    }

    /** The edge a side frame (not a corner) at (dx, dz) sits on. */
    public static Edge edge(int dx, int dz) {
        if (dz == -1) {
            return Edge.NORTH;
        }
        if (dz == 2) {
            return Edge.SOUTH;
        }
        return dx == -1 ? Edge.WEST : Edge.EAST;
    }

    /** The compass name of a ring offset, for hints: "north-west corner", "east side". */
    public static String place(int dx, int dz) {
        String ns = dz < 0 ? "north" : dz > 1 ? "south" : "";
        String ew = dx < 0 ? "west" : dx > 1 ? "east" : "";
        if (!ns.isEmpty() && !ew.isEmpty()) {
            return ns + "-" + ew + " corner";
        }
        return (ns.isEmpty() ? ew : ns) + " side";
    }
}
