package com.cosmicbreach.client.sky;

/**
 * The per-frame quads of the sky's bodies, as plain numbers (camera at the origin, world axes): Solenne's
 * sprite, the quad Thalassa is ray-traced on, Vesper's star and its two beams. Pure, so the offline preview
 * builds exactly the same geometry. Vertices are {@code x, y, z, u, v}; beams add a strip per beam.
 */
public final class SkyQuads {
    /** Distance of the bodies from the camera (inside the far plane even at render distance 2). */
    public static final float DISTANCE = 80f;
    /** Solenne's sprite reaches this many disc radii (the corona). */
    public static final double CORONA_RADII = 3.4;
    /** Thalassa's quad reaches past its rings' outer edge (planet radii). */
    public static final double RING_OUTER = 2.25;
    public static final double RING_INNER = 1.35;
    /** Vesper's sprite half-angle and the beam's reach (radians). */
    public static final double VESPER_HALF_ANGLE = Math.toRadians(2.2);
    public static final double BEAM_LENGTH = Math.toRadians(72.0);
    public static final int BEAM_SEGMENTS = 24;

    private SkyQuads() {
    }

    /** A quad facing the camera around unit direction {@code d}, spanning {@code halfAngle} each way. */
    public static float[] facing(float[] d, double halfAngle) {
        double[] n = {d[0], d[1], d[2]};
        double[] e1 = SkyModel.normalize(SkyModel.cross(n, Math.abs(n[1]) < 0.99 ? new double[] {0, 1, 0} : new double[] {1, 0, 0}));
        double[] e2 = SkyModel.cross(n, e1);
        double h = DISTANCE * Math.tan(halfAngle);
        float[] out = new float[20];
        double[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int k = 0; k < 4; k++) {
            double cx = corners[k][0] * h;
            double cy = corners[k][1] * h;
            out[k * 5] = (float) (n[0] * DISTANCE + e1[0] * cx + e2[0] * cy);
            out[k * 5 + 1] = (float) (n[1] * DISTANCE + e1[1] * cx + e2[1] * cy);
            out[k * 5 + 2] = (float) (n[2] * DISTANCE + e1[2] * cx + e2[2] * cy);
            out[k * 5 + 3] = (float) ((corners[k][0] + 1) * 0.5);
            out[k * 5 + 4] = (float) ((corners[k][1] + 1) * 0.5);
        }
        return out;
    }

    /** Solenne's sprite half-angle. */
    public static double sunHalfAngle(SkyFrame f) {
        return f.sunRadius * CORONA_RADII;
    }

    /** The disc's radius as a fraction of the sprite's half-size (GlowParams.x). */
    public static float sunDiscFraction(SkyFrame f) {
        return (float) (Math.tan(f.sunRadius) / Math.tan(sunHalfAngle(f)));
    }

    /** Thalassa's radius at {@link #DISTANCE}. */
    public static float planetRadius(SkyFrame f) {
        return (float) (DISTANCE * Math.sin(f.planetRadius));
    }

    /** Half-angle of Thalassa's quad: the rings' outer edge, with a margin. */
    public static double planetHalfAngle(SkyFrame f) {
        double reach = Math.min(0.97, RING_OUTER * Math.sin(f.planetRadius));
        return Math.asin(reach) * 1.06;
    }

    /**
     * One of Vesper's beams: a strip of {@link #BEAM_SEGMENTS} quads along the great circle leaving Vesper at
     * heading {@code heading}, widening like a cone. UV.x runs out from Vesper, UV.y across.
     */
    public static float[] beam(SkyFrame f, double heading) {
        double[] v = {f.vesperDir[0], f.vesperDir[1], f.vesperDir[2]};
        double c = Math.cos(heading);
        double s = Math.sin(heading);
        double[] b = {f.beamU[0] * c + f.beamV[0] * s, f.beamU[1] * c + f.beamV[1] * s, f.beamU[2] * c + f.beamV[2] * s};
        float[] out = new float[BEAM_SEGMENTS * 4 * 5];
        double[][] left = new double[BEAM_SEGMENTS + 1][];
        double[][] right = new double[BEAM_SEGMENTS + 1][];
        for (int i = 0; i <= BEAM_SEGMENTS; i++) {
            double t = (double) i / BEAM_SEGMENTS;
            double a = t * BEAM_LENGTH;
            double[] p = {v[0] * Math.cos(a) + b[0] * Math.sin(a), v[1] * Math.cos(a) + b[1] * Math.sin(a),
                    v[2] * Math.cos(a) + b[2] * Math.sin(a)};
            double[] tangent = {-v[0] * Math.sin(a) + b[0] * Math.cos(a), -v[1] * Math.sin(a) + b[1] * Math.cos(a),
                    -v[2] * Math.sin(a) + b[2] * Math.cos(a)};
            double[] side = SkyModel.normalize(SkyModel.cross(p, tangent));
            double w = DISTANCE * Math.toRadians(0.35 + 5.5 * t);
            left[i] = new double[] {p[0] * DISTANCE + side[0] * w, p[1] * DISTANCE + side[1] * w, p[2] * DISTANCE + side[2] * w};
            right[i] = new double[] {p[0] * DISTANCE - side[0] * w, p[1] * DISTANCE - side[1] * w, p[2] * DISTANCE - side[2] * w};
        }
        for (int i = 0; i < BEAM_SEGMENTS; i++) {
            double t0 = (double) i / BEAM_SEGMENTS;
            double t1 = (double) (i + 1) / BEAM_SEGMENTS;
            double[][] corner = {left[i], left[i + 1], right[i + 1], right[i]};
            double[] u = {t0, t1, t1, t0};
            double[] vv = {0, 0, 1, 1};
            for (int k = 0; k < 4; k++) {
                int o = (i * 4 + k) * 5;
                out[o] = (float) corner[k][0];
                out[o + 1] = (float) corner[k][1];
                out[o + 2] = (float) corner[k][2];
                out[o + 3] = (float) u[k];
                out[o + 4] = (float) vv[k];
            }
        }
        return out;
    }
}
