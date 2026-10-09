package com.cosmicbreach.world.gen;

import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The Deep's generation cost and coverage, offline: column set-up plus every block's density over Y 1 to 143, the work
 * the chunk generator does for the layer. Runs only with {@code CB_BENCH} set: {@code CB_BENCH=1 ./gradlew test --tests
 * '*DeepBenchmark*'}; prints nanoseconds per column (best of five passes over the same 64 chunks) and the coverage.
 */
class DeepBenchmark {
    @Test
    void run() {
        Assumptions.assumeTrue(System.getenv("CB_BENCH") != null);
        AetheriaTerrain t = AetheriaTerrain.forSalt(20260927L);
        double best = Double.MAX_VALUE;
        double sink = 0;
        for (int pass = 0; pass < 6; pass++) {
            AetheriaTerrain.Column col = new AetheriaTerrain.Column();
            long t0 = System.nanoTime();
            int columns = 0;
            for (int c = 0; c < 64; c++) {
                int bx = 3000 + (c % 8) * 16 * 23;
                int bz = -2500 + (c / 8) * 16 * 19;
                for (int x = bx; x < bx + 16; x++) {
                    for (int z = bz; z < bz + 16; z++) {
                        t.deep.sample(x, z, col.deep);
                        for (int y = 1; y <= DeepSpans.CEIL_Y; y++) {
                            sink += t.blocks(col, x, y, z);
                        }
                        columns++;
                    }
                }
            }
            double ns = (System.nanoTime() - t0) / (double) columns;
            if (pass > 0) {
                best = Math.min(best, ns);
            }
        }
        double cover = DeepSurvey.coverage(t, -3000, -3000, 6000, 8);
        System.out.println(String.format(Locale.ROOT, "DEEP_BENCH ns_per_column=%.0f coverage=%.4f sink=%.1f", best, cover, sink));
    }
}
