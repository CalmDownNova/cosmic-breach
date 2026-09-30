package com.cosmicbreach.world;

/**
 * The three layers of Aetheria (GDD 2.1), stacked by height around the Breach at X 0, Z 0. Each layer is a
 * biome band ({@link #at}); the rock of each layer sits inside its band, clear of the two Shear bands
 * ({@link ShearBand}), which are open sky.
 *
 * <pre>
 *   479 +--------------------------------+
 *       | Upper Reach: islands 322..381, |  biome band Y 300 and up
 *       | spires up to 470               |
 *   320 +--------------------------------+
 *       | Shear band A (300..319)        |
 *   300 +--------------------------------+
 *       | The Drift: asteroids 162..298  |  biome band Y 160..299, gravity 0.4x
 *   160 +--------------------------------+
 *       | Shear band B (145..159)        |
 *   145 +--------------------------------+
 *       | The Deep: rock 1..143          |  biome band below Y 160
 *     0 +--------------------------------+  the void below kills
 * </pre>
 */
public enum Layer {
    REACH(300, 480, 322, 470),
    DRIFT(160, 300, 162, 298),
    DEEP(0, 160, 1, 143);

    /** Lowest Y of this layer's biome band. */
    public final int bandMinY;
    /** First Y above this layer's biome band. */
    public final int bandMaxY;
    /** Lowest Y generated rock can reach in this layer. */
    public final int rockMinY;
    /** Highest Y generated rock can reach in this layer (spires included for the Reach). */
    public final int rockMaxY;

    Layer(int bandMinY, int bandMaxY, int rockMinY, int rockMaxY) {
        this.bandMinY = bandMinY;
        this.bandMaxY = bandMaxY;
        this.rockMinY = rockMinY;
        this.rockMaxY = rockMaxY;
    }

    /** The layer whose biome band holds {@code y}. */
    public static Layer at(double y) {
        if (y >= REACH.bandMinY) {
            return REACH;
        }
        return y >= DRIFT.bandMinY ? DRIFT : DEEP;
    }

    /** The layer below this one, or null for the Deep. */
    public Layer below() {
        return switch (this) {
            case REACH -> DRIFT;
            case DRIFT -> DEEP;
            case DEEP -> null;
        };
    }
}
