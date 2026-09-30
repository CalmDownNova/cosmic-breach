package com.cosmicbreach.structure.sanctum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.structure.sanctum.SanctumLayout.Kind;
import com.cosmicbreach.structure.sanctum.SanctumLayout.Wing;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.BreachShape;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The Breach Sanctum's shape (GDD 6.1, 7.3): it fits inside the Breach's chasm in every world, its causeway reaches a
 * Deep platform on a walkable ramp, the arena has the GDD's rings, segments and pillars, and the rooms leave the two
 * puzzles the room they need.
 */
class SanctumLayoutTest {
    private static final int WORLDS = 160;

    private static SanctumLayout layout(int side) {
        return new SanctumLayout(side, 40, 100, side * 185, 7L);
    }

    @Test
    void everyWorldFindsAPlatformAndTheSanctumFitsInsideTheChasm() {
        int[] sides = new int[3];
        double longest = 0;
        double steepest = 0;
        for (int i = 0; i < WORLDS; i++) {
            long salt = 0x5A17C0DEL * (i + 1) ^ (i * 0x9E3779B97F4A7C15L);
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            SanctumSite site = SanctumSite.choose(t);
            assertFalse(site.fallback, "world " + i + ": no Deep platform met the causeway's rules");
            SanctumLayout l = site.layout(salt);
            sides[site.side + 1]++;
            longest = Math.max(longest, l.causewayLength());
            steepest = Math.max(steepest, l.causewaySlope());
            assertTrue(l.causewaySlope() <= SanctumSite.MAX_SLOPE + 1e-9, "a walkable ramp");
            assertTrue(l.fitsReferenceRange(), "world " + i + ": every chunk within 8 of the start chunk");
            // the end stands on the platform's rock
            assertTrue(t.blocksAt(site.endX, site.endY - 1, site.endZ) > 0, "world " + i + ": the causeway ends on rock");
            // the arena and the halls lie well inside the chasm: nothing of the terrain within them
            for (int x = -56; x <= 50; x += 3) {
                for (int d = -30; d <= 102; d += 3) {
                    int z = l.z(d);
                    if (!l.touches(x, z) || l.causewayOffset(x, z) < 3.0) {
                        continue;
                    }
                    for (int y = 40; y <= 110; y += 4) {
                        Kind k = l.kind(x, y, z);
                        if (k == Kind.KEEP || k == Kind.AIR) {
                            continue;
                        }
                        assertTrue(Math.hypot(x, z) < BreachShape.DEEP_RADIUS - 40, "inside the chasm at " + x + " " + z);
                        assertTrue(t.blocksAt(x, y, z) <= 0, "world " + i + ": terrain inside the Sanctum at " + x + " " + y + " " + z);
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "sanctum sites over %d worlds: halls north %d, south %d; longest causeway %.1f, steepest %.3f%n",
                WORLDS, sides[0], sides[2], longest, steepest);
    }

    @Test
    void theArenaHasTheGddsRingsSegmentsAndPillars() {
        SanctumLayout l = layout(-1);
        int[][] perRingSegment = new int[4][8];
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                int ring = SanctumLayout.ring(x, z);
                double r = Math.hypot(x, z);
                if (r <= 28.0) {
                    assertTrue(ring >= 0, "the disc reaches radius 28");
                }
                if (ring < 0) {
                    continue;
                }
                int seg = SanctumLayout.segment(x, z);
                assertTrue(seg >= 0 && seg < 8);
                for (int y = SanctumLayout.keelBottom(ring); y <= SanctumLayout.FLOOR_Y; y++) {
                    Kind k = l.kind(x, y, z);
                    assertTrue(k != Kind.KEEP && k != Kind.AIR, "the disc is solid at " + x + " " + y + " " + z + ": " + k);
                    assertTrue(SanctumLayout.discBlock(x, y, z));
                    perRingSegment[ring][seg]++;
                }
                if (x != 0 || z != 0) {
                    Kind above = l.kind(x, SanctumLayout.ARENA_Y, z);
                    assertTrue(above == Kind.AIR || (SanctumLayout.pillarAt(x, z) >= 0 && above == Kind.GILT), "open floor");
                }
            }
        }
        // the rings as the GDD has them
        assertEquals(0, SanctumLayout.ring(0, 8));
        assertEquals(1, SanctumLayout.ring(0, 9));
        assertEquals(1, SanctumLayout.ring(16, 0));
        assertEquals(2, SanctumLayout.ring(17, 0));
        assertEquals(2, SanctumLayout.ring(0, -24));
        assertEquals(3, SanctumLayout.ring(0, -25));
        assertEquals(3, SanctumLayout.ring(28, 0));
        assertEquals(-1, SanctumLayout.ring(29, 0));
        // eight segments per ring, of equal size, clockwise from north
        for (int ring = 0; ring < 4; ring++) {
            for (int s = 0; s < 8; s++) {
                assertTrue(perRingSegment[ring][s] > 0, "ring " + ring + " segment " + s);
                assertEquals(perRingSegment[ring][0], perRingSegment[ring][s], 0.15 * perRingSegment[ring][0], "segments of a ring are alike");
            }
        }
        assertEquals(0, SanctumLayout.segment(1, -20), "north-north-east is segment 0");
        assertEquals(2, SanctumLayout.segment(20, 1), "east-south-east is segment 2");
        assertEquals(6, SanctumLayout.segment(-20, -1), "west-north-west is segment 6");
        // each ring is one step deeper toward the middle: a segment falls as a wedge
        assertTrue(SanctumLayout.keelBottom(3) > SanctumLayout.keelBottom(2) && SanctumLayout.keelBottom(2) > SanctumLayout.keelBottom(1)
                && SanctumLayout.keelBottom(1) > SanctumLayout.keelBottom(0));
        // eight pillars at radius 22, one in the middle of each outer segment, standing on the floor to Y 78
        Set<Integer> segments = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            int px = SanctumLayout.PILLAR_X[i];
            int pz = SanctumLayout.PILLAR_Z[i];
            assertEquals(22.0, Math.hypot(px, pz), 0.75, "pillar " + i + " at radius 22");
            assertEquals(2, SanctumLayout.ring(px, pz), "on the outer ring");
            segments.add(SanctumLayout.segment(px, pz));
            for (int y = SanctumLayout.ARENA_Y; y <= SanctumLayout.PILLAR_TOP; y++) {
                Kind k = l.kind(px, y, pz);
                assertTrue(k == Kind.PILLAR || k == Kind.GILT || k == Kind.EMBER, "pillar " + i + " solid at " + y);
            }
            int foot = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (SanctumLayout.pillarAt(px + dx, pz + dz) == i) {
                        foot++;
                    }
                }
            }
            assertEquals(13, foot, "a round footprint 5 across");
        }
        assertEquals(8, segments.size(), "one pillar per segment");
        assertEquals(Kind.THRONE, l.kind(0, SanctumLayout.ARENA_Y, 0));
    }

    @Test
    void theStairClimbsFromTheRimToTheHallsAndTheSealClosesItsHead() {
        for (int side : new int[] {-1, 1}) {
            SanctumLayout l = layout(side);
            for (int k = 0; k < SanctumLayout.STAIR_STEPS; k++) {
                int d = SanctumLayout.STAIR_D0 + k;
                for (int x = -3; x <= 3; x++) {
                    assertEquals(Kind.STAIR, l.kind(x, SanctumLayout.ARENA_Y + k, l.z(d)), "step " + k);
                    for (int h = 1; h <= 4; h++) {
                        assertEquals(Kind.AIR, l.kind(x, SanctumLayout.ARENA_Y + k + h, l.z(d)), "headroom over step " + k);
                    }
                }
            }
            // the first step leaves the rim, the last meets the landing
            assertEquals(3, SanctumLayout.ring(0, l.z(SanctumLayout.STAIR_D0 - 1)));
            assertEquals(Kind.IVORY, l.kind(0, SanctumLayout.HALL_FLOOR, l.z(SanctumLayout.LANDING_D)));
            for (int[] b : l.sealBlocks()) {
                assertEquals(Kind.SEAL, l.kind(b[0], b[1], b[2]));
            }
            assertEquals(Kind.LOCK_WEST, kindAt(l, l.lock(Wing.WEST)));
            assertEquals(Kind.LOCK_EAST, kindAt(l, l.lock(Wing.EAST)));
            assertTrue(l.lock(Wing.WEST)[0] < 0 && l.lock(Wing.EAST)[0] > 0, "west is west whichever side the halls are");
            for (int[] b : l.gateBlocks()) {
                assertEquals(Kind.GATE, l.kind(b[0], b[1], b[2]));
            }
        }
    }

    @Test
    void theWingsLeaveTheirPuzzlesTheRoomTheyNeed() {
        SanctumLayout l = layout(1);
        int[] core = l.lensCore();
        // the 7 by 7 grid and its sockets: floor under, air over the pedestal row up to the ceiling, the ceiling solid
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int x = core[0] + dx;
                int z = core[2] + dz;
                assertTrue(solid(l.kind(x, core[1], z)), "floor under the grid");
                for (int y = core[1] + 1; y < SanctumLayout.LENS_CEIL_Y; y++) {
                    assertEquals(Kind.AIR, l.kind(x, y, z), "clear over the grid at " + dx + " " + (y - core[1]) + " " + dz);
                }
                assertTrue(solid(l.kind(x, SanctumLayout.LENS_CEIL_Y, z)), "the ceiling the aperture sits in");
            }
        }
        assertEquals(Kind.VAULT_WEST, kindAt(l, l.vault(Wing.WEST)));
        // the Choir Floor: clear to 8 blocks and 5 high
        int[] c = l.conductor();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 64) {
                    continue;
                }
                assertTrue(solid(l.kind(c[0] + dx, c[1] - 1, c[2] + dz)), "the Choir's floor");
                for (int h = 0; h < 5; h++) {
                    assertEquals(Kind.AIR, l.kind(c[0] + dx, c[1] + h, c[2] + dz), "clear over the Choir Floor");
                }
            }
        }
        assertEquals(Kind.VAULT_EAST, kindAt(l, l.vault(Wing.EAST)));
        // the routes run over floors through open air
        for (Wing w : Wing.values()) {
            for (double[] p : l.toWing(w)) {
                int x = (int) Math.floor(p[0]);
                int z = (int) Math.floor(p[2]);
                assertTrue(solid(l.kind(x, SanctumLayout.HALL_FLOOR, z)), "floor under the route at " + x + " " + z);
                assertEquals(Kind.AIR, l.kind(x, SanctumLayout.HALL_Y + 1, z), w + " route clear at " + x + " " + z);
            }
        }
    }

    @Test
    void theCausewayRampsInHalfBlocks() {
        SanctumLayout l = new SanctumLayout(-1, 30, 110, -190, 1L);
        int last = -1;
        for (double[] p : l.approach()) {
            int x = (int) Math.floor(p[0]);
            int z = (int) Math.floor(p[2]);
            int feet2 = l.causewayFeet2(x, z);
            if (feet2 < 0) {
                continue;
            }
            if (last >= 0) {
                assertTrue(Math.abs(feet2 - last) <= 16, "no cliff along the causeway");
            }
            last = feet2;
        }
        // neighbouring columns differ by at most half a block
        for (int x = -10; x <= 40; x++) {
            for (int z = -200; z <= -100; z++) {
                int a = l.causewayFeet2(x, z);
                if (a < 0) {
                    continue;
                }
                int b = l.causewayFeet2(x + 1, z);
                int c = l.causewayFeet2(x, z + 1);
                assertTrue(b < 0 || Math.abs(a - b) <= 1, "a walkable ramp across x at " + x + " " + z);
                assertTrue(c < 0 || Math.abs(a - c) <= 1, "a walkable ramp across z at " + x + " " + z);
            }
        }
        assertNotEquals(Kind.KEEP, l.kind(30, 109, -190), "the causeway reaches the platform");
    }

    @Test
    void theWholeLayoutIsCheapToAsk() {
        SanctumLayout l = new SanctumLayout(1, -38, 88, 164, 3L);
        int[] b = l.bounds();
        long t0 = System.nanoTime();
        int built = 0;
        long asked = 0;
        for (int x = b[0]; x <= b[3]; x++) {
            for (int z = b[2]; z <= b[5]; z++) {
                if (!l.touches(x, z)) {
                    continue;
                }
                for (int y = b[1]; y <= b[4]; y++) {
                    asked++;
                    if (l.kind(x, y, z) != Kind.KEEP) {
                        built++;
                    }
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf(Locale.ROOT, "sanctum layout: %d blocks written of %d asked, %.1f ms; bounds %d x %d x %d%n", built, asked, ms,
                b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1);
        assertTrue(built > 50_000, "a building, not a sketch");
        assertTrue(ms < 5_000, "the whole Sanctum is asked in well under a few seconds");
    }

    private static Kind kindAt(SanctumLayout l, int[] p) {
        return l.kind(p[0], p[1], p[2]);
    }

    private static boolean solid(Kind k) {
        return k != Kind.KEEP && k != Kind.AIR;
    }
}
