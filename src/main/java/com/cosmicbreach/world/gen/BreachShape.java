package com.cosmicbreach.world.gen;

/**
 * The Breach (GDD 2.1, 2.2): a vertical opening at X 0, Z 0 through all three layers, about 200 blocks
 * across in the Reach and the Drift and about 300 in the Deep. Its rim wobbles a little so the cut reads
 * as torn rock rather than a drawn circle.
 */
public final class BreachShape {
    public static final double REACH_RADIUS = 100;
    public static final double DRIFT_RADIUS = 100;
    public static final double DEEP_RADIUS = 150;

    private final SeededNoise wobble;

    public BreachShape(long salt) {
        this.wobble = new SeededNoise(salt ^ 0x42524541434BL);
    }

    /**
     * How far (x, z) lies outside the rim of a Breach of {@code radius}: negative inside it. Exact near the
     * rim; far from it (where only the sign matters) the wobble is skipped.
     */
    public double outside(double x, double z, double radius) {
        double d = Math.sqrt(x * x + z * z);
        if (d < radius * 0.7 || d > radius * 1.4) {
            return d - radius;
        }
        double angle = Math.atan2(z, x);
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double wob = 0.09 * wobble.at(c * 1.7, s * 1.7) + 0.035 * wobble.at(c * 5.3 + 21.0, s * 5.3 - 17.0);
        return d - radius * (1.0 + wob);
    }
}
