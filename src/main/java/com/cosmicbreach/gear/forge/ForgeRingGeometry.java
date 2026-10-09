package com.cosmicbreach.gear.forge;

/**
 * The shape of the Forge's orbit rings (GDD 3.5), kept free of rendering so a test can walk every tier. A ring is a circle
 * of its radius, tilted about X then about Z; its centre sits above the block's floor. A tilted ring dips by
 * {@code radius * sqrt(1 - ny^2)} below its centre (ny: how upright its normal stays), and the outer rings used to dip into
 * the anvil's base. {@link #ring} lifts the centre of any ring that would come within {@link #CLEARANCE} of the base top and
 * eases the tilt of one that would need a lift higher than {@link #MAX_CENTRE_Y}.
 */
public final class ForgeRingGeometry {
    /** Top of the anvil's base above the block's floor (the 4 pixel slab). */
    public static final double BASE_TOP = 4.0 / 16.0;
    /** Every ring's lowest point stays at least this far above the base top. */
    public static final double CLEARANCE = 0.1;
    /** The orbit centre height when nothing needs lifting, above the block's floor. */
    public static final double CENTRE_Y = 0.9;
    /** No ring's centre rises above this; a steeper ring has its tilt eased instead. */
    public static final double MAX_CENTRE_Y = 1.3;
    public static final int TIERS = 4;

    /** Radius, tilt about X, tilt about Z (degrees), turn speed (degrees a tick) and width of each ring, inner first. */
    private static final double[][] SPEC = {
            {0.72, 12, -8, 1.6, 0.04},
            {0.90, -24, 18, -1.1, 0.038},
            {1.08, 38, 30, 0.8, 0.036},
            {1.26, -52, -34, -0.6, 0.036},
    };

    /** One ring as drawn: {@code centreY} above the block's floor, tilts after any easing. */
    public record Ring(double radius, double tiltX, double tiltZ, double spinPerTick, double width, double centreY) {
        /** How far the ring dips below its centre. */
        public double drop() {
            return ForgeRingGeometry.drop(radius, tiltX, tiltZ);
        }

        /** A point of the ring relative to the block's floor centre, at angle {@code a} (radians) and {@code scale} of its radius. */
        public double[] point(double a, double scale) {
            double x = Math.cos(a) * radius * scale;
            double z = Math.sin(a) * radius * scale;
            double ax = Math.toRadians(tiltX);
            double y1 = -z * Math.sin(ax);
            double z1 = z * Math.cos(ax);
            double bz = Math.toRadians(tiltZ);
            double x2 = x * Math.cos(bz) - y1 * Math.sin(bz);
            double y2 = x * Math.sin(bz) + y1 * Math.cos(bz);
            return new double[]{x2, y2 + centreY, z1};
        }

        /** The lowest y of the ring above the block's floor, over a full turn. */
        public double lowestY() {
            return centreY - drop();
        }
    }

    private ForgeRingGeometry() {
    }

    /** The dip below the centre of a ring with these tilts: the radius times the sine of the angle between its normal and straight up. */
    static double drop(double radius, double tiltXDeg, double tiltZDeg) {
        double ny = Math.cos(Math.toRadians(tiltXDeg)) * Math.cos(Math.toRadians(tiltZDeg));
        return radius * Math.sqrt(Math.max(0.0, 1.0 - ny * ny));
    }

    /** Ring {@code index} (0 for tier I). */
    public static Ring ring(int index) {
        double[] s = SPEC[index];
        double tx = s[1];
        double tz = s[2];
        double maxDrop = MAX_CENTRE_Y - BASE_TOP - CLEARANCE;
        if (drop(s[0], tx, tz) > maxDrop) {
            double lo = 0.0;
            double hi = 1.0;
            for (int i = 0; i < 40; i++) { // the largest share of the tilt that still fits
                double mid = (lo + hi) / 2;
                if (drop(s[0], tx * mid, tz * mid) > maxDrop) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            tx *= lo;
            tz *= lo;
        }
        double centre = Math.max(CENTRE_Y, drop(s[0], tx, tz) + BASE_TOP + CLEARANCE);
        return new Ring(s[0], tx, tz, s[3], s[4], centre);
    }

    /** The rings a Forge of {@code tier} shows. */
    public static int ringsFor(int tier) {
        return Math.max(0, Math.min(TIERS, tier));
    }
}
