package com.cosmicbreach.structure.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * 10,000 generated puzzles per size and difficulty (GDD 6.2): every one solvable (its solution lights every
 * receptor, costs what it says, and is reachable from the start), none solvable in fewer than the difficulty's
 * minimum moves (proven by {@link LensSolver} again here), a fair start (the Eye dark, not already solved), and
 * the same seed always the same puzzle. Runs on every core.
 */
class LensGeneratorTest {
    static final int PER_DIFFICULTY = 10_000;

    @Test
    void easy5() {
        check(LensDifficulty.EASY_5);
    }

    @Test
    void medium5() {
        check(LensDifficulty.MEDIUM_5);
    }

    @Test
    void hard5() {
        check(LensDifficulty.HARD_5);
    }

    @Test
    void easy7() {
        check(LensDifficulty.EASY_7);
    }

    @Test
    void medium7() {
        check(LensDifficulty.MEDIUM_7);
    }

    @Test
    void hard7() {
        check(LensDifficulty.HARD_7);
    }

    private static void check(LensDifficulty diff) {
        AtomicInteger templates = new AtomicInteger();
        List<String> problems = Collections.synchronizedList(new ArrayList<>());
        long t0 = System.nanoTime();
        LongStream.range(0, PER_DIFFICULTY).parallel().forEach(seed -> {
            LensPuzzle p = LensGenerator.generate(seed * 0x9E3779B97F4A7C15L + diff.name().hashCode(), diff);
            if (p.template()) {
                templates.incrementAndGet();
            }
            String problem = problem(p, diff);
            if (problem != null) {
                problems.add("seed " + seed + ": " + problem + "\n" + p);
            }
        });
        double seconds = (System.nanoTime() - t0) / 1e9;
        System.out.printf("[lens] %s: %d puzzles in %.1f s, %d handmade fallbacks%n", diff.name(), PER_DIFFICULTY, seconds, templates.get());
        assertTrue(problems.isEmpty(), problems.size() + " bad puzzles, first: " + (problems.isEmpty() ? "" : problems.get(0)));
        assertTrue(templates.get() < PER_DIFFICULTY / 50, "fallbacks stay rare: " + templates.get());
        // the same seed, the same puzzle
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(LensGenerator.generate(seed, diff), LensGenerator.generate(seed, diff));
        }
    }

    /** What is wrong with {@code p}, or null. */
    static String problem(LensPuzzle p, LensDifficulty diff) {
        int n = p.size();
        if (n != diff.size()) {
            return "size " + n;
        }
        int receptors = p.receptorCount();
        if (receptors < 1 || receptors > 3) {
            return receptors + " receptors";
        }
        BeamTrace.Result solved = BeamTrace.trace(n, p.solution(), p.ports());
        if (!solved.solves(p.ports())) {
            return "the solution does not light every receptor";
        }
        if (solved.eye()) {
            return "the solution wakes the Warden Eye";
        }
        BeamTrace.Result start = BeamTrace.trace(n, p.start(), p.ports());
        if (start.eye()) {
            return "the start wakes the Warden Eye";
        }
        if (start.solves(p.ports())) {
            return "already solved";
        }
        int moves = LensPuzzle.movesBetween(p.start(), p.solution(), n);
        if (moves < 0 || moves != p.solutionMoves()) {
            return "solution moves " + moves + " but the puzzle says " + p.solutionMoves();
        }
        if (p.minMoves() < diff.minMoves()) {
            return "claims a minimum of " + p.minMoves();
        }
        Boolean easier = LensSolver.solvableWithin(n, p.start(), p.ports(), diff.minMoves() - 1);
        if (easier == null) {
            return "the solver gave up";
        }
        if (easier) {
            return "solvable in fewer than " + diff.minMoves() + " moves";
        }
        return null;
    }
}
