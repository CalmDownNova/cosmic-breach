package com.cosmicbreach.world.gen;

/**
 * Measures the Deep from the terrain model: whether a column has ground to stand on, and how much of an area does
 * (its walkable coverage). The tests and the zones scenario use the same rule, so their figures compare.
 *
 * <p>A column is walkable when some rock block in it, at Y {@link #MIN_Y} or higher, has two blocks of air above it:
 * a player can stand there. The lowest blocks (the shards at the feet of pillars, just above the void) do not count.
 */
public final class DeepSurvey {
    public static final int MIN_Y = 24;

    private DeepSurvey() {
    }

    /** True if column (x, z), already sampled into {@code col}, has ground to stand on in the Deep. */
    public static boolean walkable(AetheriaTerrain t, AetheriaTerrain.Column col, int x, int z) {
        if (col.deep.empty()) {
            return false;
        }
        int top = Math.min(DeepSpans.CEIL_Y, col.deep.maxY);
        int bottom = Math.max(MIN_Y, col.deep.minY);
        boolean air1 = true;
        boolean air2 = true;
        for (int y = top; y >= bottom; y--) {
            boolean rock = t.blocks(col, x, y, z) > 0;
            if (rock && air1 && air2) {
                return true;
            }
            air2 = air1;
            air1 = !rock;
        }
        return false;
    }

    /** The share of columns walkable on a {@code step} grid over a square, skipping the Breach. */
    public static double coverage(AetheriaTerrain t, int x0, int z0, int size, int step) {
        AetheriaTerrain.Column col = new AetheriaTerrain.Column();
        long n = 0;
        long hit = 0;
        for (int x = x0; x < x0 + size; x += step) {
            for (int z = z0; z < z0 + size; z += step) {
                if (Math.hypot(x, z) < BreachShape.DEEP_RADIUS + 40) {
                    continue;
                }
                t.sampleColumn(x, z, col);
                n++;
                if (walkable(t, col, x, z)) {
                    hit++;
                }
            }
        }
        return n == 0 ? 0 : hit / (double) n;
    }
}
