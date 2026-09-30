package com.cosmicbreach.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.onboarding.BreachRing.Problem;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BreachRingTest {
    /** A small world: frames, solid blocks and Breaches at listed positions, air everywhere else. */
    private static final class World implements BreachRing.View {
        final Set<Long> frames = new HashSet<>();
        final Set<Long> solid = new HashSet<>();
        final Set<Long> breaches = new HashSet<>();

        static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
        }

        /** A whole 4 by 4 ring whose origin (the middle's north-west block) is (x, y, z). */
        World ring(int x, int y, int z) {
            for (int[] o : BreachRing.RING) {
                frames.add(key(x + o[0], y, z + o[1]));
            }
            return this;
        }

        @Override
        public boolean frame(int x, int y, int z) {
            return frames.contains(key(x, y, z));
        }

        @Override
        public boolean open(int x, int y, int z) {
            long k = key(x, y, z);
            return !frames.contains(k) && !solid.contains(k) && !breaches.contains(k);
        }

        @Override
        public boolean breach(int x, int y, int z) {
            return breaches.contains(key(x, y, z));
        }
    }

    @Test
    void theRingIsTwelveFramesRoundA2By2Middle() {
        assertEquals(12, BreachRing.RING.length);
        assertEquals(4, BreachRing.MIDDLE.length);
        Set<String> seen = new HashSet<>();
        for (int[] o : BreachRing.RING) {
            // every frame on the border of the 4 by 4 square from -1 to 2, none in the middle
            assertTrue(o[0] == -1 || o[0] == 2 || o[1] == -1 || o[1] == 2, o[0] + "," + o[1]);
            assertTrue(o[0] >= -1 && o[0] <= 2 && o[1] >= -1 && o[1] <= 2);
            assertTrue(seen.add(o[0] + "," + o[1]), "twice: " + o[0] + "," + o[1]);
        }
        int corners = 0;
        for (int[] o : BreachRing.RING) {
            corners += BreachRing.corner(o[0], o[1]) ? 1 : 0;
        }
        assertEquals(4, corners);
    }

    @Test
    void twelveFramesRoundAnEmptyMiddleWithHeadroomIsARing() {
        World w = new World().ring(10, 64, 10);
        BreachRing.Result r = BreachRing.check(w, true, 10, 64, 10);
        assertEquals(Problem.NONE, r.problem());
        assertEquals(12, r.frames());
    }

    @Test
    void onlyTheOverworld() {
        World w = new World().ring(10, 64, 10);
        assertEquals(Problem.NOT_OVERWORLD, BreachRing.check(w, false, 10, 64, 10).problem());
    }

    @Test
    void missingFramesAreNamedCornersByNameSidesBySide() {
        World w = new World().ring(0, 64, 0);
        w.frames.remove(World.key(2, 64, -1));
        w.frames.remove(World.key(-1, 64, 0));
        w.frames.remove(World.key(-1, 64, 1));
        BreachRing.Result r = BreachRing.check(w, true, 0, 64, 0);
        assertEquals(Problem.MISSING_FRAMES, r.problem());
        assertEquals(3, r.missing().size());
        assertTrue(r.missing().stream().anyMatch(o -> BreachRing.corner(o[0], o[1])
                && BreachRing.place(o[0], o[1]).equals("north-east corner")));
        assertTrue(r.missing().stream().filter(o -> !BreachRing.corner(o[0], o[1]))
                .allMatch(o -> BreachRing.edge(o[0], o[1]) == BreachRing.Edge.WEST));
        assertEquals("west side", BreachRing.place(-1, 1));
        assertEquals("south side", BreachRing.place(1, 2));
        assertEquals("south-east corner", BreachRing.place(2, 2));
        assertEquals(BreachRing.Edge.NORTH, BreachRing.edge(1, -1));
        assertEquals(BreachRing.Edge.EAST, BreachRing.edge(2, 0));
    }

    @Test
    void aFrameOneBlockTooHighIsCaught() {
        World w = new World().ring(0, 64, 0);
        w.frames.remove(World.key(0, 64, 2));
        w.frames.add(World.key(0, 65, 2));
        BreachRing.Result r = BreachRing.check(w, true, 0, 64, 0);
        assertEquals(Problem.MISSING_FRAMES, r.problem());
        assertEquals(1, r.shifted().size());
        assertEquals(1, r.shifted().get(0)[2]);
    }

    @Test
    void allFourMiddleBlocksMustBeEmptyWithTwoBlocksOfAirAboveEach() {
        for (int[] m : BreachRing.MIDDLE) {
            World w = new World().ring(0, 64, 0);
            w.solid.add(World.key(m[0], 64, m[1]));
            assertEquals(Problem.CENTRE_BLOCKED, BreachRing.check(w, true, 0, 64, 0).problem(), "middle " + m[0] + "," + m[1]);
            w.solid.clear();
            w.solid.add(World.key(m[0], 66, m[1]));
            assertEquals(Problem.NO_HEADROOM, BreachRing.check(w, true, 0, 64, 0).problem(), "headroom " + m[0] + "," + m[1]);
            w.solid.clear();
            w.solid.add(World.key(m[0], 67, m[1]));
            assertEquals(Problem.NONE, BreachRing.check(w, true, 0, 64, 0).problem());
        }
    }

    @Test
    void anOpenBreachSaysSo() {
        World w = new World().ring(0, 64, 0);
        w.breaches.add(World.key(1, 64, 1));
        assertEquals(Problem.ALREADY_OPEN, BreachRing.check(w, true, 0, 64, 0).problem());
    }

    @Test
    void anyOfTheTwelveFramesFindsTheRing() {
        World w = new World().ring(5, 70, -3);
        for (int[] o : BreachRing.RING) {
            BreachRing.Result r = BreachRing.find(w, true, 5 + o[0], 70, -3 + o[1]);
            assertEquals(Problem.NONE, r.problem(), "clicked " + o[0] + "," + o[1]);
            assertEquals(5, r.x());
            assertEquals(-3, r.z());
        }
    }

    @Test
    void anIncompleteRingStillFindsItsOriginForTheHint() {
        World w = new World().ring(0, 64, 0);
        w.frames.remove(World.key(2, 64, -1));
        // the missing corner's frame put down one block too far east
        w.frames.add(World.key(3, 64, -1));
        for (int[] o : BreachRing.RING) {
            if (o[0] == 2 && o[1] == -1) {
                continue;
            }
            BreachRing.Result r = BreachRing.find(w, true, o[0], 64, o[1]);
            assertEquals(Problem.MISSING_FRAMES, r.problem(), "clicked " + o[0] + "," + o[1]);
            assertEquals(0, r.x());
            assertEquals(0, r.z());
            assertEquals(1, r.missing().size());
            assertEquals("north-east corner", BreachRing.place(r.missing().get(0)[0], r.missing().get(0)[1]));
        }
    }

    @Test
    void twoRingsSideBySideStayApart() {
        World w = new World().ring(0, 64, 0).ring(5, 64, 0);
        BreachRing.Result a = BreachRing.find(w, true, 2, 64, 1);
        BreachRing.Result b = BreachRing.find(w, true, 4, 64, 1);
        assertEquals(0, a.x());
        assertEquals(5, b.x());
        assertTrue(a.ok() && b.ok());
        assertFalse(BreachRing.check(w, true, 3, 64, 0).ok());
    }
}
