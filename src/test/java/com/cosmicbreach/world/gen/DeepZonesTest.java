package com.cosmicbreach.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The Deep's zone field (Aetheria 1.2 design, section 6): shares, region size, the blended border, the Breach's ring. */
class DeepZonesTest {
    private static final long[] SALTS = {20260927L, 7L, -918273645L};

    @Test
    void sharesMatchTheDesignWithinFivePoints() {
        for (long salt : SALTS) {
            DeepZones zones = new DeepZones(salt);
            Random r = new Random(salt);
            int[] counts = new int[DeepZones.COUNT];
            int n = 200_000;
            for (int i = 0; i < n; i++) {
                double x = 2000 + r.nextDouble() * 60_000;
                double z = -30_000 + r.nextDouble() * 60_000;
                counts[zones.zoneAt(x, z)]++;
            }
            for (int k = 0; k < DeepZones.COUNT; k++) {
                double share = counts[k] / (double) n;
                assertEquals(DeepZones.SHARES[k], share, 0.05, "salt " + salt + " zone " + k + " share " + share);
            }
        }
    }

    @Test
    void weightsSumToOneAndChangeSmoothly() {
        DeepZones zones = new DeepZones(SALTS[0]);
        double[] a = new double[DeepZones.COUNT];
        double[] b = new double[DeepZones.COUNT];
        double worst = 0;
        int borders = 0;
        Random r = new Random(1);
        for (int i = 0; i < 40_000; i++) {
            double x = r.nextDouble() * 20_000 - 10_000;
            double z = r.nextDouble() * 20_000 - 10_000;
            zones.weights(x, z, a);
            zones.weights(x + 1, z, b);
            double sum = 0;
            for (int k = 0; k < DeepZones.COUNT; k++) {
                sum += a[k];
                assertTrue(a[k] >= 0 && a[k] <= 1);
                worst = Math.max(worst, Math.abs(a[k] - b[k]));
            }
            assertEquals(1.0, sum, 1e-9);
            if (DeepZones.dominant(a) != DeepZones.dominant(b)) {
                borders++;
                // at a change of dominant zone the two are about even: a blend, not a cut
                assertTrue(a[DeepZones.dominant(a)] < 0.6, "a cut at " + x + ", " + z);
            }
        }
        assertTrue(borders > 0, "the sample crossed no border");
        assertTrue(worst < 0.12, "weights jump by " + worst + " in one block");
    }

    @Test
    void theBorderIsAboutFortyEightBlocksWide() {
        // walk lines; at every change of dominant zone, measure how far the blend (no weight above 0.97) reaches
        DeepZones zones = new DeepZones(SALTS[1]);
        double[] w = new double[DeepZones.COUNT];
        Random r = new Random(2);
        double total = 0;
        int runs = 0;
        for (int line = 0; line < 60; line++) {
            double z = 3000 + r.nextDouble() * 20_000;
            int inBlend = 0;
            for (int x = 3000; x < 23_000; x++) {
                zones.weights(x, z, w);
                if (w[DeepZones.dominant(w)] < 0.97) {
                    inBlend++;
                } else if (inBlend > 0) {
                    total += inBlend;
                    runs++;
                    inBlend = 0;
                }
            }
        }
        double mean = total / runs;
        // straight across, the blend is 48 wide less the flat ends of the smoothstep; lines cross at angles, so wider
        assertTrue(mean > 25 && mean < 110, "mean blend crossing " + mean);
    }

    @Test
    void regionsAreAFewHundredBlocksAcross() {
        for (long salt : SALTS) {
            DeepZones zones = new DeepZones(salt);
            Random r = new Random(salt + 5);
            long length = 0;
            int regions = 0;
            for (int line = 0; line < 80; line++) {
                double z = 2000 + r.nextDouble() * 40_000;
                int run = 0;
                int last = -1;
                for (int x = 2000; x < 42_000; x += 2) {
                    int zone = zones.zoneAt(x, z);
                    if (zone != last && last >= 0) {
                        length += run;
                        regions++;
                        run = 0;
                    }
                    last = zone;
                    run += 2;
                }
            }
            double mean = length / (double) regions;
            assertTrue(mean >= 300 && mean <= 600, "salt " + salt + ": regions average " + mean + " blocks across a line");
        }
    }

    @Test
    void theBreachRingIsTheSpans() {
        DeepZones zones = new DeepZones(SALTS[0]);
        double[] w = new double[DeepZones.COUNT];
        for (int a = 0; a < 360; a += 3) {
            for (double r = 0; r <= DeepZones.HOME_RADIUS; r += 25) {
                zones.weights(r * Math.cos(Math.toRadians(a)), r * Math.sin(Math.toRadians(a)), w);
                assertEquals(1.0, w[DeepZones.SPANS], 1e-9);
            }
        }
    }

    @Test
    void pickFollowsTheWeights() {
        double[] w = {0.1, 0.6, 0.3, 0.0};
        assertEquals(0, DeepZones.pick(w, 0.05));
        assertEquals(1, DeepZones.pick(w, 0.5));
        assertEquals(2, DeepZones.pick(w, 0.95));
        assertEquals(1, DeepZones.dominant(w));
    }
}
