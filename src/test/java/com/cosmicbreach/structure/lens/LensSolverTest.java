package com.cosmicbreach.structure.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The solver's minimum is the true minimum: checked against a plain breadth-first search over every layout (every
 * press, every loose piece to every empty pedestal, every knock) on small random grids.
 */
class LensSolverTest {
    @Test
    void aSingleWrongMirrorCostsItsPresses() {
        int[] cells = new int[25];
        int[] ports = new int[20];
        cells[2 * 5] = Lens.source(Lens.EAST);
        cells[2 * 5 + 2] = Lens.mirror(1, false); // "\" sends the light south; "/" (two presses away) north
        ports[Lens.NORTH * 5 + 2] = Lens.PORT_RECEPTOR;
        assertEquals(2, LensSolver.solve(5, cells, ports, 10).moves());
        cells[2 * 5 + 2] = Lens.mirror(0, false);
        assertEquals(1, LensSolver.solve(5, cells, ports, 10).moves());
        // an Umbral block in the way costs one knock more
        cells[2 * 5 + 1] = Lens.umbral();
        assertEquals(2, LensSolver.solve(5, cells, ports, 10).moves());
        assertEquals(Boolean.FALSE, LensSolver.solvableWithin(5, cells, ports, 1));
        assertEquals(Boolean.TRUE, LensSolver.solvableWithin(5, cells, ports, 2));
    }

    @Test
    void aLooseMirrorCarriedIntoPlaceCostsOneMove() {
        int[] cells = new int[25];
        int[] ports = new int[20];
        cells[2 * 5] = Lens.source(Lens.EAST);
        cells[4 * 5 + 4] = Lens.mirror(3, true); // "/" already right, but in the corner
        ports[Lens.NORTH * 5 + 3] = Lens.PORT_RECEPTOR;
        LensSolver.Answer a = LensSolver.solve(5, cells, ports, 10);
        assertEquals(1, a.moves());
        assertTrue(BeamTrace.trace(5, a.layout(), ports).solves(ports));
    }

    @Test
    void matchesPlainBreadthFirstSearchOnSmallGrids() {
        Random random = new Random(20260928);
        int checked = 0;
        int solvedSomewhere = 0;
        for (int k = 0; k < 1500; k++) {
            int n = 4;
            int[] cells = new int[n * n];
            int[] ports = new int[4 * n];
            int side = random.nextInt(4);
            int idx = random.nextInt(n);
            int sx = side == Lens.EAST ? n - 1 : side == Lens.WEST ? 0 : idx;
            int sz = side == Lens.SOUTH ? n - 1 : side == Lens.NORTH ? 0 : idx;
            cells[sz * n + sx] = Lens.source(Lens.opposite(side));
            int receptors = 1 + random.nextInt(2);
            for (int r = 0; r < receptors; r++) {
                ports[random.nextInt(4 * n)] = Lens.PORT_RECEPTOR + (random.nextInt(3) == 0 ? Lens.GOLD : Lens.WHITE);
            }
            put(cells, random, Lens.mirror(random.nextInt(4), false));
            put(cells, random, Lens.mirror(random.nextInt(4), false));
            if (random.nextBoolean()) {
                put(cells, random, Lens.splitter(random.nextInt(4)));
            }
            put(cells, random, Lens.mirror(random.nextInt(4), true));
            if (random.nextBoolean()) {
                put(cells, random, Lens.filter(Lens.GOLD, random.nextBoolean()));
            }
            put(cells, random, Lens.umbral());
            int brute = brute(n, cells, ports, 4);
            LensSolver.Answer a = LensSolver.solve(n, cells, ports, 4);
            assertTrue(a.known());
            assertEquals(brute, a.moves(), "grid " + k + ":\n" + new LensPuzzle(n, cells, ports, cells, -1, 0, false));
            if (a.moves() >= 0) {
                assertNotNull(a.layout());
                assertTrue(BeamTrace.trace(n, a.layout(), ports).solves(ports));
                assertEquals(a.moves(), LensPuzzle.movesBetween(cells, a.layout(), n), "the layout found costs what was claimed");
                solvedSomewhere++;
            }
            checked++;
        }
        assertEquals(1500, checked);
        assertTrue(solvedSomewhere > 100, "enough of the random grids have a solution within 4 moves: " + solvedSomewhere);
    }

    private static void put(int[] cells, Random random, int piece) {
        for (int tries = 0; tries < 100; tries++) {
            int i = random.nextInt(cells.length);
            if (cells[i] == 0) {
                cells[i] = piece;
                return;
            }
        }
    }

    /** The fewest moves by plain breadth-first search over layouts, or -1 if more than {@code limit}. */
    static int brute(int n, int[] start, int[] ports, int limit) {
        if (BeamTrace.trace(n, start, ports).solves(ports)) {
            return 0;
        }
        Set<List<Integer>> seen = new HashSet<>();
        seen.add(key(start));
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        frontier.add(start);
        for (int depth = 1; depth <= limit; depth++) {
            ArrayDeque<int[]> next = new ArrayDeque<>();
            for (int[] s : frontier) {
                for (int[] t : moves(n, s)) {
                    if (!seen.add(key(t))) {
                        continue;
                    }
                    if (BeamTrace.trace(n, t, ports).solves(ports)) {
                        return depth;
                    }
                    next.add(t);
                }
            }
            frontier = next;
        }
        return -1;
    }

    private static List<Integer> key(int[] cells) {
        List<Integer> k = new ArrayList<>(cells.length);
        for (int c : cells) {
            k.add(c);
        }
        return k;
    }

    private static List<int[]> moves(int n, int[] s) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < s.length; i++) {
            int c = s[i];
            if (Lens.rotatable(c)) {
                for (int step : new int[] {1, 3}) {
                    int[] t = s.clone();
                    t[i] = Lens.withTurn(c, Lens.turn(c) + step);
                    out.add(t);
                }
            }
            if (Lens.loose(c)) {
                for (int j = 0; j < s.length; j++) {
                    if (s[j] == 0) {
                        int[] t = s.clone();
                        t[j] = c;
                        t[i] = 0;
                        out.add(t);
                    }
                }
            }
            if (Lens.kind(c) == Lens.UMBRAL) {
                for (int d = 0; d < 4; d++) {
                    int x = i % n + Lens.DX[d];
                    int z = i / n + Lens.DZ[d];
                    if (x >= 0 && x < n && z >= 0 && z < n && s[z * n + x] == 0) {
                        int[] t = s.clone();
                        t[z * n + x] = c;
                        t[i] = 0;
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    @Test
    void movesBetweenMatchesPiecesTheCheapestWay() {
        int[] a = new int[9];
        int[] b = new int[9];
        a[0] = Lens.mirror(0, true);
        a[8] = Lens.mirror(2, true);
        b[0] = Lens.mirror(2, true);
        b[8] = Lens.mirror(0, true);
        assertEquals(2, LensPuzzle.movesBetween(a, b, 3), "swapping two loose mirrors: carry both, no presses");
        a[4] = Lens.umbral();
        b[1] = Lens.umbral();
        assertEquals(3, LensPuzzle.movesBetween(a, b, 3));
        b[2] = Lens.mirror(0, false);
        assertEquals(-1, LensPuzzle.movesBetween(a, b, 3), "a fixed piece can't appear");
        assertEquals(0, LensPuzzle.movesBetween(a, a.clone(), 3));
        assertTrue(Arrays.equals(a, a.clone()));
    }
}
