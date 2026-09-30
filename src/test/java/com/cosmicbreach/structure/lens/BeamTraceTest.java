package com.cosmicbreach.structure.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The beam's rules (GDD 6.2): mirrors, splitters, filters, Umbral blocks, ports, loops and the segment cap. */
class BeamTraceTest {
    /** A 5 by 5 grid, empty but for the source at the west edge's middle, facing east. */
    private static int[] grid() {
        int[] cells = new int[25];
        cells[2 * 5] = Lens.source(Lens.EAST);
        return cells;
    }

    private static int[] ports() {
        return new int[20];
    }

    private static int receptor(int color) {
        return Lens.PORT_RECEPTOR + color;
    }

    @Test
    void mirrorsReflectLikePlates() {
        // a plate at 45 x t degrees clockwise from east-west; reflect the heading vector off it
        for (int t = 0; t < 4; t++) {
            double a = -Math.PI / 4 * t; // clockwise seen from above, with z pointing south
            double px = Math.cos(a);
            double pz = -Math.sin(a);
            double nx = -pz;
            double nz = px;
            for (int d = 0; d < 4; d++) {
                double dx = Lens.DX[d];
                double dz = Lens.DZ[d];
                double dot = dx * nx + dz * nz;
                double rx = dx - 2 * dot * nx;
                double rz = dz - 2 * dot * nz;
                int out = Lens.mirrorOut(t, d);
                assertEquals(Lens.DX[out], Math.round(rx), "turn " + t + " heading " + d);
                assertEquals(Lens.DZ[out], Math.round(rz), "turn " + t + " heading " + d);
            }
        }
    }

    @Test
    void oneClockwisePressTurnsThePlateAnEighth() {
        // two presses are a quarter turn: the same as rotating every heading a quarter clockwise
        for (int t = 0; t < 4; t++) {
            for (int d = 0; d < 4; d++) {
                assertEquals(Lens.cw(Lens.mirrorOut(t, d)), Lens.mirrorOut((t + 2) & 3, Lens.cw(d)));
            }
        }
        assertEquals(1, Lens.turnDistance(3, 0));
        assertEquals(2, Lens.turnDistance(1, 3));
    }

    @Test
    void straightLightReachesAReceptorOfItsColour() {
        int[] ports = ports();
        ports[Lens.EAST * 5 + 2] = receptor(Lens.WHITE);
        BeamTrace.Result r = BeamTrace.trace(5, grid(), ports);
        assertTrue(r.solves(ports));
        assertEquals(1, r.count());
        assertEquals(5, r.x1(0));
        assertEquals(2, r.z1(0));
        ports[Lens.EAST * 5 + 2] = receptor(Lens.GOLD);
        assertFalse(BeamTrace.trace(5, grid(), ports).solves(ports), "white light does not light a gold receptor");
    }

    @Test
    void filtersTintAndUmbralBlocksAbsorb() {
        int[] ports = ports();
        ports[Lens.EAST * 5 + 2] = receptor(Lens.TEAL);
        int[] cells = grid();
        cells[2 * 5 + 2] = Lens.filter(Lens.TEAL, false);
        BeamTrace.Result r = BeamTrace.trace(5, cells, ports);
        assertTrue(r.solves(ports));
        assertEquals(2, r.count());
        assertEquals(Lens.WHITE, r.color(0));
        assertEquals(Lens.TEAL, r.color(1));
        cells[2 * 5 + 3] = Lens.umbral();
        r = BeamTrace.trace(5, cells, ports);
        assertFalse(r.solves(ports));
        assertEquals(2f, r.closest()[Lens.EAST * 5 + 2], 1e-6, "the beam stops at the block, two cells short of the wall");
    }

    @Test
    void mirrorsTurnPassAndSendBack() {
        int[] ports = ports();
        ports[Lens.NORTH * 5 + 2] = receptor(Lens.WHITE);
        int[] cells = grid();
        cells[2 * 5 + 2] = Lens.mirror(3, false); // "/" turns east-bound light north
        assertTrue(BeamTrace.trace(5, cells, ports).solves(ports));
        cells[2 * 5 + 2] = Lens.mirror(0, false); // along the plate: passes
        BeamTrace.Result r = BeamTrace.trace(5, cells, ports);
        assertEquals(1 << (Lens.EAST * 5 + 2), r.reached());
        cells[2 * 5 + 2] = Lens.mirror(2, false); // square on: back to the source, where it ends
        r = BeamTrace.trace(5, cells, ports);
        assertEquals(0, r.reached());
        assertEquals(1, r.count());
    }

    @Test
    void splittersSplitFromTheirOneSide() {
        int[] ports = ports();
        ports[Lens.NORTH * 5 + 2] = receptor(Lens.WHITE);
        ports[Lens.SOUTH * 5 + 2] = receptor(Lens.WHITE);
        int[] cells = grid();
        cells[2 * 5 + 2] = Lens.splitter(Lens.EAST);
        BeamTrace.Result r = BeamTrace.trace(5, cells, ports);
        assertTrue(r.solves(ports));
        assertEquals(3, r.count());
        cells[2 * 5 + 2] = Lens.splitter(Lens.NORTH);
        assertEquals(0, BeamTrace.trace(5, cells, ports).reached(), "from any other side the prism absorbs");
    }

    @Test
    void theEyeAndLoopsAndTheCap() {
        int[] ports = ports();
        ports[Lens.EAST * 5 + 2] = Lens.PORT_EYE;
        assertTrue(BeamTrace.trace(5, grid(), ports).eye());
        assertTrue((BeamTrace.litMask(5, grid(), ports, new boolean[400]) & 1 << 31) != 0);
        // dense random grids of mirrors and splitters: every trace ends, within the segment cap
        java.util.Random random = new java.util.Random(7);
        int capped = 0;
        for (int k = 0; k < 2000; k++) {
            int[] cells = new int[49];
            for (int i = 0; i < 49; i++) {
                int roll = random.nextInt(10);
                cells[i] = roll < 4 ? Lens.mirror(random.nextInt(4), false) : roll < 7 ? Lens.splitter(random.nextInt(4))
                        : roll < 8 ? Lens.filter(random.nextInt(4), false) : 0;
            }
            cells[3 * 7] = Lens.source(Lens.EAST);
            BeamTrace.Result r = BeamTrace.trace(7, cells, new int[28]);
            assertTrue(r.count() <= Lens.MAX_SEGMENTS);
            capped += r.truncated() ? 1 : 0;
            int fast = BeamTrace.litMask(7, cells, new int[28], new boolean[49 * 16]);
            assertEquals(0, fast & ~(1 << 31));
        }
        assertTrue(capped < 2000, "not every dense grid hits the cap");
    }

    @Test
    void closestApproachShrinksAsTheBeamComesNearer() {
        int[] ports = ports();
        int target = Lens.NORTH * 5 + 4;
        ports[target] = receptor(Lens.WHITE);
        int[] cells = grid();
        cells[2 * 5 + 1] = Lens.mirror(3, false); // turns north at x = 1: three cells from x = 4
        float far = BeamTrace.trace(5, cells, ports).closest()[target];
        cells[2 * 5 + 1] = 0;
        cells[2 * 5 + 3] = Lens.mirror(3, false); // turns north at x = 3: one cell away
        float near = BeamTrace.trace(5, cells, ports).closest()[target];
        assertEquals(3f, far, 1e-6);
        assertEquals(1f, near, 1e-6);
    }
}
