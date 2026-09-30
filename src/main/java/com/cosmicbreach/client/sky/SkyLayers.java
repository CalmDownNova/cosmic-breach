package com.cosmicbreach.client.sky;

/**
 * Which layer's sky and fog the camera sees, as weights (Reach, Drift, Deep). The target comes from the
 * camera's height: a cross-fade across each Shear band (A: Y 300 to 320, B: Y 145 to 160). The shown
 * weights follow the target at most one full layer per {@link #FADE_SECONDS}, so falling through a band, or
 * a teleport, fades the sky and fog over about 3 seconds instead of cutting. Pure; one instance per client.
 */
public final class SkyLayers {
    /** Longest a full change of layer takes to fade in. */
    public static final double FADE_SECONDS = 3.0;
    /** Shear band A (the Reach above, the Drift below) and B (the Drift above, the Deep below). */
    static final double BAND_A_LOW = 300.0;
    static final double BAND_A_HIGH = 320.0;
    static final double BAND_B_LOW = 145.0;
    static final double BAND_B_HIGH = 160.0;

    private final double[] shown = {1.0, 0.0, 0.0};
    private boolean fresh = true;

    /** The weights for a camera at height {@code y}, with no smoothing. */
    public static double[] targetWeights(double y) {
        double reach = SkyModel.smoothstep(BAND_A_LOW, BAND_A_HIGH, y);
        double deep = 1.0 - SkyModel.smoothstep(BAND_B_LOW, BAND_B_HIGH, y);
        return new double[] {reach, Math.max(0.0, 1.0 - reach - deep), deep};
    }

    /** Moves the shown weights towards the target for height {@code y}, {@code dtSeconds} after the last call. */
    public double[] update(double y, double dtSeconds) {
        double[] target = targetWeights(y);
        if (fresh) {
            System.arraycopy(target, 0, shown, 0, 3);
            fresh = false;
            return shown;
        }
        double step = Math.max(0.0, dtSeconds) / FADE_SECONDS;
        // distance to go, measured as the largest change any one weight needs
        double far = 0.0;
        for (int i = 0; i < 3; i++) {
            far = Math.max(far, Math.abs(target[i] - shown[i]));
        }
        double k = far <= step ? 1.0 : step / far;
        for (int i = 0; i < 3; i++) {
            shown[i] += (target[i] - shown[i]) * k;
        }
        return shown;
    }

    /** Jumps straight to the target next time (entering Aetheria, a new level). */
    public void reset() {
        fresh = true;
    }

    public double[] shown() {
        return shown;
    }
}
