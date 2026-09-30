package com.cosmicbreach.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.ShearBand;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The terrain model's promises (GDD 2.1, 2.2): bands, the Breach, island sizes and links, belts, determinism. */
class AetheriaTerrainTest {
    private static final long SALT = 0x5EEDL;
    private final AetheriaTerrain t = AetheriaTerrain.forSalt(SALT);

    @Test
    void layersAndBands() {
        assertEquals(Layer.REACH, Layer.at(300));
        assertEquals(Layer.DRIFT, Layer.at(299.9));
        assertEquals(Layer.DRIFT, Layer.at(160));
        assertEquals(Layer.DEEP, Layer.at(159));
        assertEquals(ShearBand.A, ShearBand.at(300));
        assertEquals(ShearBand.A, ShearBand.at(319.9));
        assertEquals(null, ShearBand.at(320));
        assertEquals(ShearBand.B, ShearBand.at(145));
        assertEquals(null, ShearBand.at(160));
        assertEquals(Layer.DRIFT, ShearBand.A.guards);
        assertEquals(Layer.DEEP, ShearBand.B.guards);
    }

    @Test
    void theShearBandsAreOpenSky() {
        Random random = new Random(1);
        AetheriaTerrain.Column col = new AetheriaTerrain.Column();
        for (int n = 0; n < 1500; n++) {
            int x = random.nextInt(6000) - 3000;
            int z = random.nextInt(6000) - 3000;
            t.sampleColumn(x, z, col);
            for (ShearBand band : ShearBand.values()) {
                for (int y = band.minY - 1; y <= band.maxY; y++) {
                    assertTrue(t.blocks(col, x, y, z) <= 0, "rock in or touching band " + band + " at " + x + " " + y + " " + z);
                }
            }
            for (int y = 400; y < 480; y++) {
                assertTrue(t.blocks(col, x, y, z) <= 0, "terrain rock above the islands at " + x + " " + y + " " + z);
            }
        }
    }

    @Test
    void theBreachIsOpenThroughAllLayers() {
        AetheriaTerrain.Column col = new AetheriaTerrain.Column();
        for (int x = -140; x <= 140; x += 4) {
            for (int z = -140; z <= 140; z += 4) {
                double r = Math.hypot(x, z);
                t.sampleColumn(x, z, col);
                for (int y = 1; y < 480; y += 1) {
                    double radius = y >= 160 ? BreachShape.REACH_RADIUS : BreachShape.DEEP_RADIUS;
                    if (r < radius * 0.85) {
                        assertTrue(t.blocks(col, x, y, z) <= 0, "rock inside the Breach at " + x + " " + y + " " + z);
                    }
                }
            }
        }
    }

    @Test
    void islandsAreSizedAndPlacedPerTheGdd() {
        int sunfield = 0;
        int total = 0;
        for (int i = -12; i <= 12; i++) {
            for (int j = -12; j <= 12; j++) {
                ReachIslands.Isle isle = t.reach.isle(i, j);
                if (!isle.exists) {
                    continue;
                }
                total++;
                sunfield += isle.sunfield ? 1 : 0;
                assertTrue(isle.radius >= ReachIslands.MIN_RADIUS && isle.radius <= ReachIslands.MAX_RADIUS + 12, "radius " + isle.radius);
                assertTrue(isle.top >= 345 && isle.top <= 375, "top " + isle.top);
                assertTrue(isle.thickness >= 5 && isle.thickness <= 24, "thickness " + isle.thickness);
                assertTrue(isle.links().length >= 1, "island " + i + "," + j + " has no neck to any neighbour");
            }
        }
        double share = sunfield / (double) total;
        assertTrue(share > 0.15 && share < 0.35, "Sunfield share " + share);
    }

    /**
     * A player can walk from an island to the neighbour its neck leads to: a path over the terrain's top
     * surface where each step climbs at most 1 block (a jump) and drops at most 3 (no fall damage).
     */
    @Test
    void necksAreWalkable() {
        List<ReachIslands.Link> links = new ArrayList<>();
        List<ReachIslands.Isle[]> ends = new ArrayList<>();
        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                ReachIslands.Isle a = t.reach.isle(i, j);
                if (!a.exists) {
                    continue;
                }
                for (ReachIslands.Link l : a.links()) {
                    if (!links.contains(l)) {
                        links.add(l);
                        ReachIslands.Isle b = null;
                        for (int di = -2; di <= 2 && b == null; di++) {
                            for (int dj = -2; dj <= 2 && b == null; dj++) {
                                ReachIslands.Isle o = t.reach.isle(i + di, j + dj);
                                if (o != a && o.exists && (o.cx == l.ax && o.cz == l.az || o.cx == l.bx && o.cz == l.bz)) {
                                    b = o;
                                }
                            }
                        }
                        ends.add(new ReachIslands.Isle[] {a, b});
                    }
                }
            }
        }
        assertTrue(links.size() > 30, "only " + links.size() + " links");
        int walkable = 0;
        int tried = 0;
        StringBuilder failures = new StringBuilder();
        for (ReachIslands.Isle[] pair : ends) {
            // pairs next to the Breach may have their neck cut by its rim
            if (pair[1] == null || Math.hypot(pair[0].cx, pair[0].cz) < 260 || Math.hypot(pair[1].cx, pair[1].cz) < 260) {
                continue;
            }
            int[] from = inside(pair[0]);
            int[] to = inside(pair[1]);
            if (from == null || to == null) {
                continue;
            }
            tried++;
            if (path(from, to)) {
                walkable++;
            } else {
                failures.append(" ").append(pair[0].ci).append(',').append(pair[0].cj).append("->").append(pair[1].ci).append(',').append(pair[1].cj);
            }
        }
        assertTrue(tried > 30, "tried " + tried);
        assertTrue(walkable >= tried * 0.9, walkable + " of " + tried + " necks walkable;" + failures);
    }

    /** A world column well inside {@code isle} near its centre, or null. */
    private int[] inside(ReachIslands.Isle isle) {
        ReachIslands.Column col = new ReachIslands.Column();
        for (int r = 0; r < 60; r += 3) {
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                int x = (int) Math.floor(isle.cx + Math.cos(a) * r);
                int z = (int) Math.floor(isle.cz + Math.sin(a) * r);
                t.reach.sample(x + 0.5, z + 0.5, col);
                if (col.island && col.isle == isle && col.edge > 10) {
                    return new int[] {x, z};
                }
            }
        }
        return null;
    }

    /** A* over column tops: steps up at most 1, down at most 3. */
    private boolean path(int[] from, int[] to) {
        java.util.Map<Long, Integer> tops = new java.util.HashMap<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.PriorityQueue<long[]> open = new java.util.PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        open.add(new long[] {0, from[0], from[1], 0});
        int expanded = 0;
        while (!open.isEmpty() && expanded < 60000) {
            long[] n = open.poll();
            int x = (int) n[1];
            int z = (int) n[2];
            long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            if (!seen.add(key)) {
                continue;
            }
            expanded++;
            if (Math.abs(x - to[0]) <= 1 && Math.abs(z - to[1]) <= 1) {
                return true;
            }
            int top = top(tops, x, z);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0];
                int nz = z + d[1];
                int nt = top(tops, nx, nz);
                if (nt == Integer.MIN_VALUE || nt - top > 1 || top - nt > 3) {
                    continue;
                }
                long g = n[3] + 1;
                long h = (long) (Math.hypot(nx - to[0], nz - to[1]) * 1.2);
                open.add(new long[] {g + h, nx, nz, g});
            }
        }
        return false;
    }

    private int top(java.util.Map<Long, Integer> cache, int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Integer v = cache.get(key);
        if (v == null) {
            ReachIslands.Column col = new ReachIslands.Column();
            t.reach.sample(x + 0.5, z + 0.5, col);
            v = Integer.MIN_VALUE;
            if (col.island || col.neck) {
                for (int y = Math.min(420, col.maxY); y >= Math.max(ReachIslands.FLOOR_Y, col.minY); y--) {
                    if (t.reach.density(col, x, y, z) > 0) {
                        v = y;
                        break;
                    }
                }
            }
            cache.put(key, v);
        }
        return v;
    }

    @Test
    void beltsHoldAboutOneAsteroidPer40BlockCube() {
        int inBelt = 0;
        int filled = 0;
        for (int i = -60; i <= 60; i++) {
            for (int j = -60; j <= 60; j++) {
                double cx = (i + 0.5) * DriftBelts.CELL;
                double cz = (j + 0.5) * DriftBelts.CELL;
                DriftBelts.BeltPoint bp = t.drift.beltAt(cx, cz);
                if (bp.strength() < 0.8 || Math.hypot(cx, cz) < 200) {
                    continue;
                }
                int k = (int) Math.floor(bp.centreY() / DriftBelts.CELL);
                inBelt++;
                if (t.drift.asteroid(i, j, k) != null) {
                    filled++;
                }
            }
        }
        double ratio = filled / (double) inBelt;
        assertTrue(inBelt > 200, "belt cells " + inBelt);
        assertTrue(ratio > 0.6, "asteroids in belt cores: " + ratio);
        for (int i = -40; i <= 40; i++) {
            for (int j = -40; j <= 40; j++) {
                for (int k = 4; k <= 7; k++) {
                    DriftBelts.Asteroid a = t.drift.asteroid(i, j, k);
                    if (a != null) {
                        assertTrue(a.r >= 2 && a.r <= 15, "asteroid radius " + a.r);
                        assertTrue(a.cy - a.bound >= DriftBelts.FLOOR_Y - 0.5 && a.cy + a.bound <= DriftBelts.CEIL_Y + 0.5, "asteroid out of the Drift");
                    }
                }
            }
        }
    }

    @Test
    void sameSaltSameWorldOtherSaltOtherWorld() {
        AetheriaTerrain other = AetheriaTerrain.forSalt(SALT + 1);
        Random random = new Random(7);
        int differences = 0;
        for (int n = 0; n < 400; n++) {
            int x = random.nextInt(4000) - 2000;
            int y = 1 + random.nextInt(470);
            int z = random.nextInt(4000) - 2000;
            assertEquals(t.blocksAt(x, y, z), AetheriaTerrain.forSalt(SALT).blocksAt(x, y, z));
            if ((t.blocksAt(x, y, z) > 0) != (other.blocksAt(x, y, z) > 0)) {
                differences++;
            }
        }
        assertNotEquals(0, differences);
    }

    @Test
    void spiresFollowTheGdd() {
        List<SpireField.Spire> all = new ArrayList<>();
        t.spires.spiresTouching(-1500, -1500, 1500, 1500, all);
        assertTrue(all.size() > 300, "spires " + all.size());
        int leaning = 0;
        int snapped = 0;
        for (SpireField.Spire s : all) {
            assertTrue(s.height >= 20 && s.height <= 90, "height " + s.height);
            assertTrue(s.radius >= 2 && s.radius <= 7, "radius " + s.radius);
            assertTrue(s.groundY + s.standing <= 470, "spire top above 470");
            if (s.leanX != 0 || s.leanZ != 0) {
                leaning++;
                double angle = Math.toDegrees(Math.atan(Math.hypot(s.leanX, s.leanZ)));
                assertTrue(angle >= 4.9 && angle <= 20.1, "lean " + angle);
            }
            if (s.snapped) {
                snapped++;
            }
        }
        double lean = leaning / (double) all.size();
        double snap = snapped / (double) all.size();
        assertTrue(lean > 0.10 && lean < 0.20, "leaning share " + lean);
        assertTrue(snap > 0.05 && snap < 0.15, "snapped share " + snap);
    }
}
