package com.cosmicbreach.world.gen;

/**
 * The Deep's zones (Aetheria 1.2): a low-frequency field that splits the layer into regions of four profiles, the
 * Spans (the pillars and arches of 1.0), the Lichen Gardens, the Hanging Wood and the Shattered Field. Each region is
 * one of a few hundred blocks; neighbouring regions blend over {@link #BORDER} blocks.
 *
 * <p>How: Voronoi cells over a jittered {@link #CELL}-block grid (the sample point domain warped first so borders
 * wander), each cell's site drawing its zone by hash with the shares in {@link #SHARES}. A zone's weight at a point
 * falls from equal (on the bisector between the nearest site and that zone's nearest site) to nothing
 * {@link #BORDER}/2 blocks into the nearest site's side. Near the Breach the field is the Spans alone
 * ({@link #HOME_RADIUS}), so the way down and the centre's siting keep the geometry they were built for.
 *
 * <p>Pure and thread safe: a function of the salt and the point.
 */
public final class DeepZones {
    public static final int SPANS = 0;
    public static final int GARDENS = 1;
    public static final int HANGING = 2;
    public static final int SHATTERED = 3;
    public static final int COUNT = 4;
    /** Share of the layer each zone takes, in zone order. */
    public static final double[] SHARES = {0.25, 0.30, 0.25, 0.20};
    public static final double CELL = 384.0;
    public static final double BORDER = 48.0;
    /** Within this radius of the Breach's axis the field is the Spans alone; it blends out over {@link #BORDER}. */
    public static final double HOME_RADIUS = 300.0;
    private static final double JITTER = 0.4;
    private static final double WARP = 40.0;
    private static final double WARP_SCALE = 220.0;
    private static final int TAG_SITE = 41;

    private final long salt;
    private final SeededNoise warpX;
    private final SeededNoise warpZ;

    public DeepZones(long salt) {
        this.salt = salt;
        this.warpX = new SeededNoise(salt + 411);
        this.warpZ = new SeededNoise(salt + 412);
    }

    /** The zone a site's hash value u in [0, 1) draws. */
    static int zoneOf(double u) {
        double acc = 0;
        for (int z = 0; z < COUNT - 1; z++) {
            acc += SHARES[z];
            if (u < acc) {
                return z;
            }
        }
        return COUNT - 1;
    }

    /** Each zone's weight at (x, z), summing to 1, written into {@code out} (length {@link #COUNT}); returns out. */
    public double[] weights(double x, double z, double[] out) {
        double home = 1.0 - TMath.smoothstep(HOME_RADIUS, HOME_RADIUS + BORDER, Math.hypot(x, z));
        if (home >= 1.0) {
            java.util.Arrays.fill(out, 0.0);
            out[SPANS] = 1.0;
            return out;
        }
        double qx = x + WARP * warpX.at(x / WARP_SCALE, z / WARP_SCALE);
        double qz = z + WARP * warpZ.at(x / WARP_SCALE + 31.7, z / WARP_SCALE - 12.9);
        int ci = (int) Math.floor(qx / CELL);
        int cj = (int) Math.floor(qz / CELL);
        // the nearest site of each zone, among the 5 by 5 cells around
        double[] best = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                long h = Hashing.hash(salt, TAG_SITE, ci + di, cj + dj, 0);
                double sx = (ci + di + 0.5 + (Hashing.unit(h, 0) * 2 - 1) * JITTER) * CELL;
                double sz = (cj + dj + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * JITTER) * CELL;
                int zone = zoneOf(Hashing.unit(h, 2));
                best[zone] = Math.min(best[zone], TMath.sq(sx - qx) + TMath.sq(sz - qz));
            }
        }
        int near = 0;
        for (int k = 1; k < COUNT; k++) {
            if (best[k] < best[near]) {
                near = k;
            }
        }
        double sum = 0;
        for (int k = 0; k < COUNT; k++) {
            double w;
            if (k == near) {
                w = 1.0;
            } else if (best[k] == Double.MAX_VALUE) {
                w = 0.0;
            } else {
                // half the difference of the distances: the distance to the bisector when crossing straight, a
                // little less at a slant; continuous everywhere, since each zone's nearest distance is
                double b = (Math.sqrt(best[k]) - Math.sqrt(best[near])) / 2.0;
                w = 1.0 - TMath.smoothstep(0.0, BORDER / 2, b);
            }
            out[k] = w;
            sum += w;
        }
        for (int k = 0; k < COUNT; k++) {
            out[k] = out[k] / sum * (1.0 - home) + (k == SPANS ? home : 0.0);
        }
        return out;
    }

    /** The zone with the most weight at (x, z): the biome's zone. */
    public int zoneAt(double x, double z) {
        return dominant(weights(x, z, new double[COUNT]));
    }

    /** The index of the largest weight. */
    public static int dominant(double[] w) {
        int best = 0;
        for (int k = 1; k < w.length; k++) {
            if (w[k] > w[best]) {
                best = k;
            }
        }
        return best;
    }

    /** A zone drawn with the weights as odds by {@code u} in [0, 1): the dominant zone away from any border. */
    public static int pick(double[] w, double u) {
        double acc = 0;
        for (int k = 0; k < w.length; k++) {
            acc += w[k];
            if (u < acc) {
                return k;
            }
        }
        return dominant(w);
    }
}
