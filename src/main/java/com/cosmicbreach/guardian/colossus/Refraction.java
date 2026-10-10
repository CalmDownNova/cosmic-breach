package com.cosmicbreach.guardian.colossus;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Refraction's rules (Prism Colossus design v1, "Refraction, the signature"; reworked in 1.2.1), pure.
 *
 * <p>Six crown crystals stand round the arena, numbered clockwise as seen from above (crystal {@code i + 1}
 * is the next one clockwise). The Colossus's body stands in the middle: a vertical cylinder of
 * {@link CrownArena#BODY_RADIUS} round the centre. A beam leg that would pass through the body ends there instead:
 * the beam is turned back into the Colossus ({@link #CORE}). Only a leg to the opposite crystal crosses the body (the
 * others pass at least 7 blocks clear of it), so the direction toward the opposite crystal <em>is</em> the core: a
 * crystal points at one of four other crystals ({@code i+1, i+2, i+4, i+5}) or at the core.
 *
 * <p>Turning a crystal moves its target one step clockwise through {@link #cycle}: {@code i+1, i+2, core, i+4, i+5}
 * and round again (its pointer sweeps one way), so {@link #stepsToCore} hits bring it to the core.
 *
 * <p>The beam leaves the eye, strikes the lit crystal and bounces on along the targets: the lit crystal, its
 * target, that crystal's target, and there it dies out ({@link #PATH_CRYSTALS} crystals at most). A target that
 * is the core (or a leg through the body) ends the path there: the beam turned back into the Colossus.
 *
 * <p>At rest every crystal points two steps round ({@link #resting}), so no resting leg crosses the body. The
 * Colossus aims a lit crystal at one of the three targets that leave the core 1 to 3 turns away
 * ({@link #aimCandidates}), whichever sends the beam across the most players, and never along a path that ends
 * at its own body. Players turn lit crystals during the charge; after the beam every crystal settles back to rest
 * ({@link #settle}), so the next Refraction starts from a clean ring.
 */
public final class Refraction {
    public static final int CRYSTALS = 6;
    /** The target index that means the Colossus's core (its body). */
    public static final int CORE = CRYSTALS;
    /** A beam visits at most this many crystals. */
    public static final int PATH_CRYSTALS = 3;
    /** The Colossus leaves the core at least this many turns away from a lit crystal's target... */
    public static final int MIN_CORE_STEPS = 1;
    /** ...and at most this many. */
    public static final int MAX_CORE_STEPS = 3;
    /** A resting crystal points this many crystals round, clockwise. */
    public static final int REST_STEPS = 2;

    private Refraction() {
    }

    // ------------------------------------------------------------------ the body

    /** Crystal {@code k}'s ideal spot, {x, z} from the centre (+X east, +Z south; clockwise from east seen from above). */
    static double[] ringPoint(int k) {
        double a = Math.toRadians(60.0 * Math.floorMod(k, CRYSTALS));
        return new double[] {CrownArena.CRYSTAL_RADIUS * Math.cos(a), CrownArena.CRYSTAL_RADIUS * Math.sin(a)};
    }

    /** True if the flat segment from {@code (ax, az)} to {@code (bx, bz)} passes within {@code radius} of the origin. */
    public static boolean segmentNearCentre(double ax, double az, double bx, double bz, double radius) {
        double dx = bx - ax;
        double dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double u = len2 < 1e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, -(ax * dx + az * dz) / len2));
        double px = ax + dx * u;
        double pz = az + dz * u;
        return px * px + pz * pz < radius * radius;
    }

    /** True if a beam from crystal {@code a} to crystal {@code b} would pass through the Colossus's body. */
    public static boolean crossesBody(int a, int b) {
        double[] pa = ringPoint(a);
        double[] pb = ringPoint(b);
        return segmentNearCentre(pa[0], pa[1], pb[0], pb[1], CrownArena.BODY_RADIUS);
    }

    // ------------------------------------------------------------------ targets and turning

    /** True for a target crystal {@code crystal} may point at: another crystal clear of the body, or the core. */
    public static boolean validTarget(int crystal, int target) {
        return target == CORE || (target >= 0 && target < CRYSTALS && target != crystal && !crossesBody(crystal, target));
    }

    /** The crystal opposite {@code crystal} across the arena (behind the Colossus's body from it). */
    public static int opposite(int crystal) {
        return (crystal + CRYSTALS / 2) % CRYSTALS;
    }

    /** Where {@code crystal} points at rest: two crystals round, clockwise. */
    public static int resting(int crystal) {
        return (crystal + REST_STEPS) % CRYSTALS;
    }

    /**
     * {@code crystal}'s targets in turning order, starting just clockwise of it: each other crystal in ring order, the one
     * behind the body replaced by the core ({@code i+1, i+2, core, i+4, i+5}).
     */
    public static int[] cycle(int crystal) {
        int[] out = new int[CRYSTALS - 1];
        int n = 0;
        boolean core = false;
        for (int k = 1; k < CRYSTALS; k++) {
            int t = (crystal + k) % CRYSTALS;
            if (crossesBody(crystal, t)) {
                if (!core) {
                    out[n++] = CORE;
                    core = true;
                }
            } else {
                out[n++] = t;
            }
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    private static int indexIn(int[] cycle, int target) {
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /** The target after one clockwise turn of {@code crystal}, pointing at {@code target} now (an invalid one turns to rest). */
    public static int turn(int crystal, int target) {
        int[] c = cycle(crystal);
        int i = indexIn(c, target);
        return i < 0 ? resting(crystal) : c[(i + 1) % c.length];
    }

    /** How many clockwise turns bring {@code crystal}'s beam from {@code target} to the core (0 if it is there). */
    public static int stepsToCore(int crystal, int target) {
        int[] c = cycle(crystal);
        int i = indexIn(c, target);
        int core = indexIn(c, CORE);
        return i < 0 ? c.length : Math.floorMod(core - i, c.length);
    }

    /**
     * The targets the Colossus may give a lit crystal: those with the core 1 to 3 turns away, nearest first
     * (two round, then the next crystal clockwise, then the next counter-clockwise).
     */
    public static int[] aimCandidates(int crystal) {
        int[] c = cycle(crystal);
        int[] out = new int[MAX_CORE_STEPS - MIN_CORE_STEPS + 1];
        int n = 0;
        for (int steps = MIN_CORE_STEPS; steps <= MAX_CORE_STEPS; steps++) {
            for (int t : c) {
                if (t != CORE && stepsToCore(crystal, t) == steps) {
                    out[n++] = t;
                }
            }
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    // ------------------------------------------------------------------ the beam's path

    /**
     * The nodes the beam visits from lit crystal {@code lit}: crystal indices in order, ending with {@link #CORE}
     * if it is turned back into the body (a target on the core, or a leg that would cross the body). Always starts with
     * {@code lit}; at most {@link #PATH_CRYSTALS} crystals.
     */
    public static int[] path(int lit, int[] targets) {
        int[] out = new int[PATH_CRYSTALS];
        int n = 0;
        int at = lit;
        out[n++] = at;
        while (n < PATH_CRYSTALS) {
            int next = targets[at];
            if (next != CORE && next >= 0 && next < CRYSTALS && next != at && crossesBody(at, next)) {
                next = CORE; // the leg would pass through the body: it ends there
            }
            if (next != CORE && (next < 0 || next >= CRYSTALS || next == at)) {
                break; // no target (never set up): the beam dies out here
            }
            out[n++] = next;
            if (next == CORE) {
                break;
            }
            at = next;
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    /** True if the path ends at the core (turned back into the body). */
    public static boolean endsAtCore(int[] path) {
        return path.length > 0 && path[path.length - 1] == CORE;
    }

    // ------------------------------------------------------------------ the Colossus's aim

    /**
     * The target the Colossus gives lit crystal {@code lit}: among {@link #aimCandidates}, the one whose path
     * crosses the most players ({@code crossed} scores a path; any int, higher is better), never one whose path ends at
     * the core.
     * Ties go to {@code pick} (given the number of tied choices, it returns an index into them). If every
     * candidate ended at the core (the ring was left unsettled), the one farthest from the core.
     */
    public static int chooseTarget(int lit, int[] targets, ToIntFunction<int[]> crossed, IntUnaryOperator pick) {
        int[] candidates = aimCandidates(lit);
        int[] trial = targets.clone();
        int best = Integer.MIN_VALUE;
        int[] tied = new int[candidates.length];
        int ties = 0;
        for (int candidate : candidates) {
            trial[lit] = candidate;
            int[] p = path(lit, trial);
            if (endsAtCore(p)) {
                continue;
            }
            int score = crossed.applyAsInt(p);
            if (score > best) {
                best = score;
                ties = 0;
            }
            if (score == best) {
                tied[ties++] = candidate;
            }
        }
        if (ties == 0) {
            return candidates[candidates.length - 1];
        }
        return tied[Math.floorMod(pick.applyAsInt(ties), ties)];
    }

    /**
     * Picks {@code count} distinct crystals to light and aims each ({@link #chooseTarget}), writing their targets
     * into {@code targets}. Crystals whose best path crosses the most players win; ties go to {@code pick}. A crystal
     * opposite one already lit is never lit with it (the Colossus faces between its lit crystals, so neither beam leaves
     * its eye backwards). Returns the lit crystals, best first.
     */
    public static int[] light(int count, int[] targets, ToIntFunction<int[]> crossed, IntUnaryOperator pick) {
        int n = Math.max(1, Math.min(count, CRYSTALS / 2));
        int[] lit = new int[n];
        boolean[] used = new boolean[CRYSTALS];
        for (int k = 0; k < n; k++) {
            int best = Integer.MIN_VALUE;
            int[] tied = new int[CRYSTALS];
            int ties = 0;
            int[] aims = new int[CRYSTALS];
            for (int i = 0; i < CRYSTALS; i++) {
                if (used[i]) {
                    continue;
                }
                int aim = chooseTarget(i, targets, crossed, pick);
                int[] trial = targets.clone();
                trial[i] = aim;
                int score = crossed.applyAsInt(path(i, trial));
                aims[i] = aim;
                if (score > best) {
                    best = score;
                    ties = 0;
                }
                if (score == best) {
                    tied[ties++] = i;
                }
            }
            int chosen = tied[Math.floorMod(pick.applyAsInt(ties), ties)];
            used[chosen] = true;
            used[opposite(chosen)] = true;
            targets[chosen] = aims[chosen];
            lit[k] = chosen;
        }
        return lit;
    }

    /** After a Refraction (and before one): every crystal turns back to rest, two round. */
    public static void settle(int[] targets) {
        for (int i = 0; i < targets.length; i++) {
            targets[i] = resting(i);
        }
    }

    /** Every crystal at rest: pointing two crystals round, clockwise. */
    public static int[] restingTargets() {
        int[] out = new int[CRYSTALS];
        settle(out);
        return out;
    }
}
