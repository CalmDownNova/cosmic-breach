package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.structure.sanctum.SanctumArena.Ring;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Collapse (GDD 7.3): from 20% health the Breach widens under the arena. Every {@value HeliarchMoves#COLLAPSE_EVERY}
 * ticks one of the rim's eight segments cracks, shakes for {@value HeliarchMoves#SHAKE_TICKS} ticks and falls, going
 * round clockwise, and the outer ring's segment behind it follows half a step later; two minutes in, the arena ends
 * at the mid ring's edge (radius 16). Then the mid ring's segments go the same way, and four minutes in only the
 * dais is left. The round starts just past the Throne Stair, so the stair's two segments are the last of each ring.
 * Pure: times are ticks since the Collapse began, segments are W7's (0 to 7 clockwise from north).
 */
public final class CollapseSchedule {
    /** One segment's fate: it cracks at {@code crack} and falls at {@code fall}. */
    public record Crack(Ring ring, int segment, int crack, int fall) {
    }

    public enum State { STANDING, SHAKING, FALLEN }

    private final int first;
    private final List<Crack> cracks;

    /** The schedule for halls on {@code side} (-1 north, +1 south, as {@code SanctumLayout.side()}). */
    public CollapseSchedule(int side) {
        this.first = startSegment(side);
        List<Crack> list = new ArrayList<>();
        int step = HeliarchMoves.COLLAPSE_EVERY;
        for (int i = 0; i < SanctumLayout.SEGMENTS; i++) {
            int seg = (first + i) % SanctumLayout.SEGMENTS;
            list.add(new Crack(Ring.RIM, seg, i * step, i * step + HeliarchMoves.SHAKE_TICKS));
            list.add(new Crack(Ring.OUTER, seg, i * step + step / 2, i * step + step / 2 + HeliarchMoves.SHAKE_TICKS));
        }
        int base = SanctumLayout.SEGMENTS * step;
        for (int i = 0; i < SanctumLayout.SEGMENTS; i++) {
            int seg = (first + i) % SanctumLayout.SEGMENTS;
            list.add(new Crack(Ring.MID, seg, base + i * step, base + i * step + HeliarchMoves.SHAKE_TICKS));
        }
        this.cracks = Collections.unmodifiableList(list);
    }

    /** The first segment to go: just clockwise of the Throne Stair's two (7 and 0 in the north, 3 and 4 in the south). */
    public static int startSegment(int side) {
        return side < 0 ? 1 : 5;
    }

    public int first() {
        return first;
    }

    /** Every crack in order of time. */
    public List<Crack> cracks() {
        return cracks;
    }

    /** One segment's state {@code t} ticks into the Collapse (the dais never cracks). */
    /** When a segment cracks, in ticks into the Collapse, or -1 if it never does. */
    public int crackAt(Ring ring, int segment) {
        for (Crack c : cracks()) {
            if (c.ring() == ring && c.segment() == Math.floorMod(segment, 8)) {
                return c.crack();
            }
        }
        return -1;
    }

    public State state(Ring ring, int segment, long t) {
        for (Crack c : cracks) {
            if (c.ring() == ring && c.segment() == segment) {
                if (t >= c.fall()) {
                    return State.FALLEN;
                }
                return t >= c.crack() ? State.SHAKING : State.STANDING;
            }
        }
        return State.STANDING;
    }

    /** The cracks that start between {@code t0} (exclusive) and {@code t1} (inclusive). */
    public List<Crack> cracking(long t0, long t1) {
        List<Crack> out = new ArrayList<>();
        for (Crack c : cracks) {
            if (c.crack() > t0 && c.crack() <= t1) {
                out.add(c);
            }
        }
        return out;
    }

    /** The segments that fall between {@code t0} (exclusive) and {@code t1} (inclusive). */
    public List<Crack> falling(long t0, long t1) {
        List<Crack> out = new ArrayList<>();
        for (Crack c : cracks) {
            if (c.fall() > t0 && c.fall() <= t1) {
                out.add(c);
            }
        }
        return out;
    }

    /** How far out the floor still reaches all the way round {@code t} ticks in: 28, then 24, 16 and 8. */
    public double radiusLeft(long t) {
        Ring[] outward = {Ring.DAIS, Ring.MID, Ring.OUTER, Ring.RIM};
        double r = Ring.DAIS.outer;
        for (int i = 1; i < outward.length; i++) {
            for (int s = 0; s < SanctumLayout.SEGMENTS; s++) {
                if (state(outward[i], s, t) == State.FALLEN) {
                    return r;
                }
            }
            r = outward[i].outer;
        }
        return r;
    }

    /** True once nothing is left to fall. */
    public boolean done(long t) {
        return t >= cracks.get(cracks.size() - 1).fall();
    }
}
