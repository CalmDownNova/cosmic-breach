package com.cosmicbreach.client.sky;

/**
 * The aurora's ribbons over the Reach: three curtains hanging in the northern sky (world frame, not
 * celestial: they belong to the air), each a strip of quads from its hem up to its top, wavy along its
 * length. UV.x runs along a ribbon (the shader scrolls its rays along it), UV.y from the hem (0) to the top
 * (1); the vertex alpha fades each ribbon's ends. Pure data, like {@link SkyStars}.
 */
public final class SkyAurora {
    public static final float RADIUS = 84f;
    private static final int SEGMENTS = 56;

    /** Per ribbon: centre azimuth (degrees from north towards east), span, hem elevation, height, waviness phase. */
    private static final double[][] RIBBONS = {
            {-18.0, 118.0, 13.0, 21.0, 0.0},
            {26.0, 96.0, 21.0, 16.0, 2.1},
            {-52.0, 70.0, 27.0, 13.0, 4.4}};

    private SkyAurora() {
    }

    public static SkyStars.Mesh ribbons() {
        int quads = RIBBONS.length * SEGMENTS;
        float[] vertices = new float[quads * 4 * 5];
        int[] colors = new int[quads * 4];
        int q = 0;
        for (double[] rb : RIBBONS) {
            double centre = Math.toRadians(rb[0]);
            double span = Math.toRadians(rb[1]);
            double hem = Math.toRadians(rb[2]);
            double height = Math.toRadians(rb[3]);
            double phase = rb[4];
            double along = 0.0;
            double[] prevBottom = null;
            double[][] bottoms = new double[SEGMENTS + 1][];
            double[][] tops = new double[SEGMENTS + 1][];
            double[] us = new double[SEGMENTS + 1];
            float[] fades = new float[SEGMENTS + 1];
            for (int i = 0; i <= SEGMENTS; i++) {
                double s = (double) i / SEGMENTS;
                double az = centre + (s - 0.5) * span;
                double wave = Math.sin(s * 9.0 + phase) * 0.6 + Math.sin(s * 17.0 + phase * 2.3) * 0.4;
                double e = hem + Math.toRadians(2.2) * wave;
                double lean = Math.toRadians(3.0) * Math.sin(s * 6.0 + phase * 1.7);   // the top drifts sideways: folds
                bottoms[i] = direction(az, e);
                tops[i] = direction(az + lean, e + height * (0.85 + 0.15 * Math.sin(s * 5.0 + phase)));
                if (prevBottom != null) {
                    along += SkyModel.angle(prevBottom, bottoms[i]) / Math.toRadians(10.0);
                }
                prevBottom = bottoms[i];
                us[i] = along;
                fades[i] = (float) (SkyModel.smoothstep(0.0, 0.16, s) * SkyModel.smoothstep(1.0, 0.84, s));
            }
            for (int i = 0; i < SEGMENTS; i++) {
                double[][] corner = {bottoms[i], bottoms[i + 1], tops[i + 1], tops[i]};
                double[] cu = {us[i], us[i + 1], us[i + 1], us[i]};
                double[] cv = {0.0, 0.0, 1.0, 1.0};
                float[] fade = {fades[i], fades[i + 1], fades[i + 1], fades[i]};
                for (int k = 0; k < 4; k++) {
                    int v = q * 4 + k;
                    vertices[v * 5] = (float) (corner[k][0] * RADIUS);
                    vertices[v * 5 + 1] = (float) (corner[k][1] * RADIUS);
                    vertices[v * 5 + 2] = (float) (corner[k][2] * RADIUS);
                    vertices[v * 5 + 3] = (float) cu[k];
                    vertices[v * 5 + 4] = (float) cv[k];
                    int alpha = Math.round(fade[k] * 255f);
                    colors[v] = alpha << 24 | 0xFFFFFF;
                }
                q++;
            }
        }
        return new SkyStars.Mesh(vertices, colors, quads);
    }

    /** A world direction: azimuth from north (-Z) towards east (+X), elevation up. */
    static double[] direction(double azimuth, double elevation) {
        double c = Math.cos(elevation);
        return new double[] {Math.sin(azimuth) * c, Math.sin(elevation), -Math.cos(azimuth) * c};
    }
}
