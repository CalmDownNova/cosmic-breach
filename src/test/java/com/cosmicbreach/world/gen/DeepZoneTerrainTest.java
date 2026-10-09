package com.cosmicbreach.world.gen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The Deep's terrain per zone (Aetheria 1.2 design, section 6): the Breach stays open, ground per zone, the zones' shapes. */
class DeepZoneTerrainTest {
    private static final long[] SALTS = {20260927L, 7L, -918273645L};

    @Test
    void noRockWithinTheBreach() {
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            for (int x = -160; x <= 160; x += 3) {
                for (int z = -160; z <= 160; z += 3) {
                    if (t.breach.outside(x + 0.5, z + 0.5, BreachShape.DEEP_RADIUS) >= 0) {
                        continue;
                    }
                    t.sampleColumn(x, z, col);
                    for (int y = 1; y <= DeepSpans.CEIL_Y; y++) {
                        assertTrue(t.blocks(col, x, y, z) <= 0, "rock in the Breach at " + x + " " + y + " " + z + " (salt " + salt + ")");
                    }
                }
            }
        }
    }

    @Test
    void walkableCoverageRisesToThirtyToFortyPercent() {
        StringBuilder report = new StringBuilder();
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            long[] n = new long[DeepZones.COUNT];
            long[] hit = new long[DeepZones.COUNT];
            for (int x = -4000; x < 4000; x += 9) {
                for (int z = -4000; z < 4000; z += 9) {
                    if (Math.hypot(x, z) < BreachShape.DEEP_RADIUS + 40) {
                        continue;
                    }
                    int zone = t.zones.zoneAt(x + 0.5, z + 0.5);
                    t.sampleColumn(x, z, col);
                    n[zone]++;
                    if (DeepSurvey.walkable(t, col, x, z)) {
                        hit[zone]++;
                    }
                }
            }
            long all = 0;
            long allHit = 0;
            double[] share = new double[DeepZones.COUNT];
            for (int k = 0; k < DeepZones.COUNT; k++) {
                all += n[k];
                allHit += hit[k];
                share[k] = hit[k] / (double) n[k];
            }
            double overall = allHit / (double) all;
            report.append(String.format(Locale.ROOT, "salt %d: overall %.3f, spans %.3f, gardens %.3f, hanging %.3f, shattered %.3f%n",
                    salt, overall, share[0], share[1], share[2], share[3]));
            assertTrue(overall >= 0.30 && overall <= 0.40, report.toString());
            assertTrue(share[DeepZones.GARDENS] > 0.6, report.toString());
            assertTrue(share[DeepZones.SPANS] > 0.1 && share[DeepZones.HANGING] > 0.08 && share[DeepZones.SHATTERED] > 0.15, report.toString());
        }
        System.out.print("DEEP_COVERAGE " + report);
    }

    @Test
    void everyPillarStandsWholeWhateverItsZone() {
        // the ground at each pillar's middle is where the model says, also beside a border
        for (long salt : SALTS) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            int[] checked = new int[DeepZones.COUNT];
            for (int ci = -40; ci < 40; ci++) {
                for (int cj = -40; cj < 40; cj++) {
                    DeepSpans.Pillar p = t.deep.pillar(ci, cj);
                    int x = (int) Math.floor(p.cx);
                    int z = (int) Math.floor(p.cz);
                    if (!p.exists || DeepSpans.snapped(p) || (p.zone == DeepZones.SPANS && t.deep.scar(x + 0.5, z + 0.5))) {
                        continue;
                    }
                    t.sampleColumn(x, z, col);
                    int surface = -1;
                    for (int y = (int) Math.ceil(p.top) + 3; y > p.top - 6; y--) {
                        if (t.blocks(col, x, y, z) > 0) {
                            surface = y + 1;
                            break;
                        }
                    }
                    // higher ground over a pillar is another zone's rock (a garden slab, a mass) mingling at a border
                    boolean covered = col.deep.gardenCover > -3 || col.deep.pillars.size() > 1 || !col.deep.chunks.isEmpty()
                            || !col.deep.roots.isEmpty() || !col.deep.spans.isEmpty();
                    assertTrue(surface >= p.top - 3 && (surface <= p.top + 3 || covered),
                            "zone " + p.zone + " pillar " + ci + "," + cj + " top " + p.top + " surface " + surface + " (salt " + salt + ")");
                    checked[p.zone]++;
                }
            }
            for (int k = 0; k < DeepZones.COUNT; k++) {
                assertTrue(checked[k] > 50, "salt " + salt + " zone " + k + " has only " + checked[k] + " pillars");
            }
        }
    }

    @Test
    void theShatteredFieldIsHoppable() {
        // every floating chunk has another within 12 blocks (rim to rim, across), so a stag can hop between them
        AetheriaTerrain t = AetheriaTerrain.forSalt(SALTS[0]);
        int chunks = 0;
        int cells = 0;
        int lonely = 0;
        for (int ci = -60; ci < 60; ci++) {
            for (int cj = -60; cj < 60; cj++) {
                DeepSpans.Pillar p = t.deep.pillar(ci, cj);
                if (p.zone != DeepZones.SHATTERED) {
                    continue;
                }
                cells++;
                for (DeepSpans.Chunk a : p.satellites()) {
                    assertTrue(a.top >= 50 && a.top <= 135 && a.r >= 1.5 && a.r <= 10.5);
                    double nearest = Double.MAX_VALUE;
                    for (int di = -1; di <= 1; di++) {
                        for (int dj = -1; dj <= 1; dj++) {
                            DeepSpans.Pillar q = t.deep.pillar(ci + di, cj + dj);
                            if (q.exists && q.zone != DeepZones.GARDENS) {
                                nearest = Math.min(nearest, Math.hypot(a.x - q.cx, a.z - q.cz) - a.r - q.platformR);
                            }
                            for (DeepSpans.Chunk b : q.satellites()) {
                                if (b != a) {
                                    nearest = Math.min(nearest, Math.hypot(a.x - b.x, a.z - b.z) - a.r - b.r);
                                }
                            }
                        }
                    }
                    if (nearest > 12.0 + 1e-9) {
                        lonely++;
                    }
                    chunks++;
                }
            }
        }
        assertTrue(chunks > 1000 && chunks > cells * 6, chunks + " chunks in " + cells + " cells");
        assertTrue(lonely < chunks * 0.02, lonely + " of " + chunks + " chunks have nothing within 12 blocks");
    }

    @Test
    void theHangingWoodHangsFromTheCeilingTowardYForty() {
        AetheriaTerrain t = AetheriaTerrain.forSalt(SALTS[1]);
        int roots = 0;
        double lowest = Double.MAX_VALUE;
        for (int ci = -60; ci < 60; ci++) {
            for (int cj = -60; cj < 60; cj++) {
                DeepSpans.Pillar p = t.deep.pillar(ci, cj);
                if (p.zone != DeepZones.HANGING || !p.exists) {
                    continue;
                }
                assertTrue(p.top >= 100 && p.top <= 134, "mass top " + p.top);
                for (DeepSpans.Root r : p.roots) {
                    assertTrue(r.y1 >= 40 - 1e-9 && r.y0 <= 136, "root from " + r.y0 + " to " + r.y1);
                    lowest = Math.min(lowest, r.y1);
                    roots++;
                }
            }
        }
        assertTrue(roots > 1000 && lowest < 45, roots + " roots, lowest tip " + lowest);
    }
}
