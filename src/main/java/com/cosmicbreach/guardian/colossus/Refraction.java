package com.cosmicbreach.guardian.colossus;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Refraction's rules (Prism Colossus design v1, "Refraction, the signature"), pure.
 *
 * <p>Six crown crystals stand round the arena, numbered clockwise as seen from above (crystal {@code i + 1}
 * is the next one clockwise). Each points at one target: one of the five other crystals, or the Colossus's
 * core ({@link #CORE}). Turning a crystal moves its target one step clockwise through the cycle
 * {@code i+1, i+2, i+3, i+4, i+5, core} and round again, so {@link #stepsToCore} hits bring it to the core.
 *
 * <p>The beam leaves the eye, strikes the lit crystal and bounces on along the targets: the lit crystal, its
 * target, that crystal's target, and there it dies out ({@link #PATH_CRYSTALS} crystals at most). A target that
 * is the core ends the path there: the beam turned back into the Colossus.
 *
 * <p>The Colossus aims a lit crystal at one of the three crystals that leave the core 1 to 3 steps away
 * ({@link #aimCandidates}), whichever sends the beam across the most players, and never along a path that ends
 * at its own core. Players turn lit crystals during the charge; after the beam, crystals left pointing at the
 * core settle back to the opposite crystal ({@link #settle}), so the next Refraction starts from a clean ring.
 */
public final class Refraction {
    public static final int CRYSTALS = 6;
    /** The target index that means the Colossus's core. */
    public static final int CORE = CRYSTALS;
    /** A beam visits at most this many crystals. */
    public static final int PATH_CRYSTALS = 3;
    /** The Colossus leaves the core at least this many turns away from a lit crystal's target... */
    public static final int MIN_CORE_STEPS = 1;
    /** ...and at most this many. */
    public static final int MAX_CORE_STEPS = 3;

    private Refraction() {
    }

    /** True for a target crystal {@code crystal} may point at: another crystal, or the core. */
    public static boolean validTarget(int crystal, int target) {
        return target == CORE || (target >= 0 && target < CRYSTALS && target != crystal);
    }

    /** The crystal opposite {@code crystal} across the arena: every crystal's resting target. */
    public static int opposite(int crystal) {
        return (crystal + CRYSTALS / 2) % CRYSTALS;
    }

    /** The target after one clockwise turn of {@code crystal}, pointing at {@code target} now. */
    public static int turn(int crystal, int target) {
        if (target == CORE) {
            return (crystal + 1) % CRYSTALS;
        }
        int next = (target + 1) % CRYSTALS;
        return next == crystal ? CORE : next;
    }

    /** How many clockwise turns bring {@code crystal}'s beam from {@code target} to the core (0 if it is there). */
    public static int stepsToCore(int crystal, int target) {
        if (target == CORE) {
            return 0;
        }
        return Math.floorMod(crystal - target, CRYSTALS);
    }

    /**
     * The targets the Colossus may give a lit crystal: those with the core 1 to 3 turns away, nearest first
     * (the crystal just counter-clockwise, then two, then the opposite one).
     */
    public static int[] aimCandidates(int crystal) {
        int[] out = new int[MAX_CORE_STEPS - MIN_CORE_STEPS + 1];
        for (int steps = MIN_CORE_STEPS; steps <= MAX_CORE_STEPS; steps++) {
            out[steps - MIN_CORE_STEPS] = Math.floorMod(crystal - steps, CRYSTALS);
        }
        return out;
    }

    /**
     * The nodes the beam visits from lit crystal {@code lit}: crystal indices in order, ending with {@link #CORE}
     * if it reaches the core. Always starts with {@code lit}; at most {@link #PATH_CRYSTALS} crystals.
     */
    public static int[] path(int lit, int[] targets) {
        int[] out = new int[PATH_CRYSTALS];
        int n = 0;
        int at = lit;
        out[n++] = at;
        while (n < PATH_CRYSTALS) {
            int next = targets[at];
            out[n++] = next;
            if (next == CORE) {
                break;
            }
            at = next;
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    /** True if the path ends at the core. */
    public static boolean endsAtCore(int[] path) {
        return path.length > 0 && path[path.length - 1] == CORE;
    }

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
     * into {@code targets}. Crystals whose best path crosses the most players win; ties go to {@code pick}.
     * Returns the lit crystals, best first.
     */
    public static int[] light(int count, int[] targets, ToIntFunction<int[]> crossed, IntUnaryOperator pick) {
        int n = Math.max(1, Math.min(count, CRYSTALS));
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
            targets[chosen] = aims[chosen];
            lit[k] = chosen;
        }
        return lit;
    }

    /** After a Refraction: every crystal left pointing at the core turns back to the opposite crystal. */
    public static void settle(int[] targets) {
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == CORE || !validTarget(i, targets[i])) {
                targets[i] = opposite(i);
            }
        }
    }

    /** Every crystal at rest: pointing at the opposite one. */
    public static int[] restingTargets() {
        int[] out = new int[CRYSTALS];
        settle(fill(out));
        return out;
    }

    private static int[] fill(int[] targets) {
        Arrays.fill(targets, CORE);
        return targets;
    }
}
