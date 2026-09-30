package com.cosmicbreach.client.sky;

import java.util.Random;

/**
 * The star field, built once from a fixed seed: 600 bright stars, 4,000 faint ones (a third of them crowded
 * along the Reach nebula's band, like a galaxy's plane), and three globular clusters (a few hundred small
 * stars packed round a soft glowing heart). Every star is a quad on a sphere of radius {@link #RADIUS} in the
 * celestial frame, facing the centre. Pure data (no Minecraft types): {@link SkyGeometry} uploads it, and
 * {@code SkyModelDump} (src/test) can write it for the offline preview.
 *
 * <p>Per vertex: position (3 floats), corner UV (2 floats), colour (brightness times tint) and in the alpha
 * the star's twinkle phase.
 */
public final class SkyStars {
    public static final int BRIGHT = 600;
    public static final int FAINT = 4000;
    public static final int CLUSTERS = 3;
    public static final int CLUSTER_MEMBERS = 220;
    public static final float RADIUS = 90f;
    private static final long SEED = 0xAE7E_2026L;

    /** Interleaved vertex data: x, y, z, u, v per vertex; and a packed ARGB colour per vertex. */
    public record Mesh(float[] vertices, int[] colors, int quads) {}

    private SkyStars() {
    }

    public static Mesh field() {
        Random r = new Random(SEED);
        Builder b = new Builder(BRIGHT + FAINT);
        double[] band = SkyModel.normalize(new double[] {0.35, 0.80, -0.48});
        for (int i = 0; i < BRIGHT; i++) {
            double[] d = randomDirection(r);
            double m = Math.pow(r.nextDouble(), 2.2);          // most bright stars are modest, a few blaze
            float size = (float) (0.28 + 0.34 * m);
            float bright = (float) (0.55 + 0.45 * m);
            b.star(d, size, tint(r), bright, r.nextFloat());
        }
        for (int i = 0; i < FAINT; i++) {
            double[] d = randomDirection(r);
            if (i % 3 == 0) {
                // pull towards the band's plane
                double k = d[0] * band[0] + d[1] * band[1] + d[2] * band[2];
                double squeeze = 0.12 + 0.2 * r.nextDouble();
                d = SkyModel.normalize(new double[] {d[0] - band[0] * k * (1 - squeeze), d[1] - band[1] * k * (1 - squeeze),
                        d[2] - band[2] * k * (1 - squeeze)});
            }
            float size = (float) (0.17 + 0.07 * r.nextDouble());
            float bright = (float) (0.12 + 0.3 * Math.pow(r.nextDouble(), 1.6));
            b.star(d, size, tint(r), bright, r.nextFloat());
        }
        return b.build();
    }

    public static Mesh clusters() {
        Random r = new Random(SEED ^ 0x5151L);
        Builder b = new Builder(CLUSTERS * (CLUSTER_MEMBERS + 2));
        double[][] centres = clusterCentres();
        double[] spread = {1.1, 0.8, 1.35};
        for (int c = 0; c < CLUSTERS; c++) {
            double[] n = centres[c];
            double[] e1 = SkyModel.normalize(SkyModel.cross(n, Math.abs(n[1]) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0}));
            double[] e2 = SkyModel.cross(n, e1);
            double sigma = Math.toRadians(spread[c]);
            // the heart: two soft glows, wide and faint, then small and brighter
            b.star(n, 4.2f, new float[] {1.0f, 0.93f, 0.78f}, 0.10f, 0.5f);
            b.star(n, 1.6f, new float[] {1.0f, 0.95f, 0.85f}, 0.16f, 0.25f);
            for (int i = 0; i < CLUSTER_MEMBERS; i++) {
                double gx = r.nextGaussian() * sigma * (0.4 + 0.6 * r.nextDouble());
                double gy = r.nextGaussian() * sigma * (0.4 + 0.6 * r.nextDouble());
                double[] d = SkyModel.normalize(new double[] {n[0] + e1[0] * gx + e2[0] * gy, n[1] + e1[1] * gx + e2[1] * gy,
                        n[2] + e1[2] * gx + e2[2] * gy});
                double core = Math.exp(-(gx * gx + gy * gy) / (2 * sigma * sigma));
                float size = (float) (0.15 + 0.1 * r.nextDouble());
                float bright = (float) ((0.18 + 0.4 * r.nextDouble()) * (0.55 + 0.45 * core));
                float[] tint = r.nextDouble() < 0.3 ? new float[] {1.0f, 0.86f, 0.66f} : new float[] {1.0f, 0.97f, 0.9f};
                b.star(d, size, tint, bright, r.nextFloat());
            }
        }
        return b.build();
    }

    /** The clusters' centres in the celestial frame: up in the north so they are often above the horizon. */
    static double[][] clusterCentres() {
        double[] p = SkyModel.pole();
        return new double[][] {
                tilt(p, 32.0, 40.0),
                tilt(p, 48.0, 165.0),
                tilt(p, 61.0, 285.0)};
    }

    /** {@code pole} tilted {@code away} degrees towards the heading {@code around} degrees. */
    private static double[] tilt(double[] pole, double away, double around) {
        double[] e1 = SkyModel.normalize(SkyModel.cross(pole, new double[] {0, 1, 0.0001}));
        double[] e2 = SkyModel.cross(pole, e1);
        double a = Math.toRadians(away);
        double h = Math.toRadians(around);
        double s = Math.sin(a);
        return SkyModel.normalize(new double[] {
                pole[0] * Math.cos(a) + (e1[0] * Math.cos(h) + e2[0] * Math.sin(h)) * s,
                pole[1] * Math.cos(a) + (e1[1] * Math.cos(h) + e2[1] * Math.sin(h)) * s,
                pole[2] * Math.cos(a) + (e1[2] * Math.cos(h) + e2[2] * Math.sin(h)) * s});
    }

    private static double[] randomDirection(Random r) {
        double z = r.nextDouble() * 2.0 - 1.0;
        double a = r.nextDouble() * Math.PI * 2.0;
        double s = Math.sqrt(1.0 - z * z);
        return new double[] {s * Math.cos(a), z, s * Math.sin(a)};
    }

    private static float[] tint(Random r) {
        double t = r.nextDouble();
        if (t < 0.15) {
            return new float[] {0.76f, 0.86f, 1.0f};
        }
        if (t < 0.6) {
            return new float[] {1.0f, 1.0f, 1.0f};
        }
        if (t < 0.85) {
            return new float[] {1.0f, 0.94f, 0.8f};
        }
        return new float[] {1.0f, 0.8f, 0.6f};
    }

    private static final class Builder {
        private final float[] vertices;
        private final int[] colors;
        private int quads;

        Builder(int capacity) {
            vertices = new float[capacity * 4 * 5];
            colors = new int[capacity * 4];
        }

        void star(double[] d, float size, float[] tint, float bright, float phase) {
            double[] e1 = SkyModel.normalize(SkyModel.cross(d, Math.abs(d[1]) < 0.99 ? new double[] {0, 1, 0} : new double[] {1, 0, 0}));
            double[] e2 = SkyModel.cross(d, e1);
            int argb = (Math.round(phase * 255f) & 0xFF) << 24
                    | channel(tint[0] * bright) << 16 | channel(tint[1] * bright) << 8 | channel(tint[2] * bright);
            float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int k = 0; k < 4; k++) {
                int v = quads * 4 + k;
                double cx = corners[k][0] * size;
                double cy = corners[k][1] * size;
                vertices[v * 5] = (float) (d[0] * RADIUS + e1[0] * cx + e2[0] * cy);
                vertices[v * 5 + 1] = (float) (d[1] * RADIUS + e1[1] * cx + e2[1] * cy);
                vertices[v * 5 + 2] = (float) (d[2] * RADIUS + e1[2] * cx + e2[2] * cy);
                vertices[v * 5 + 3] = (corners[k][0] + 1) * 0.5f;
                vertices[v * 5 + 4] = (corners[k][1] + 1) * 0.5f;
                colors[v] = argb;
            }
            quads++;
        }

        private static int channel(float v) {
            return Math.max(0, Math.min(255, Math.round(v * 255f)));
        }

        Mesh build() {
            return new Mesh(vertices, colors, quads);
        }
    }
}
