package com.cosmicbreach.client.fx;

/**
 * How the charge's choir loop swells: volume and pitch against the charge's progress (0 at its start,
 * 1 at full charge). The volume rises fast at first and then eases, the pitch climbs evenly by about a
 * major third, so the rising choir peaks exactly when the charge is full. Pure.
 */
public final class ChargeRamp {
    public static final float VOLUME_START = 0.35f;
    public static final float VOLUME_FULL = 1.0f;
    public static final float PITCH_START = 0.9f;
    public static final float PITCH_FULL = 1.2f;

    private ChargeRamp() {
    }

    public static float volume(double progress) {
        return (float) (VOLUME_START + (VOLUME_FULL - VOLUME_START) * Math.pow(clamp(progress), 0.7));
    }

    public static float pitch(double progress) {
        return (float) (PITCH_START + (PITCH_FULL - PITCH_START) * clamp(progress));
    }

    private static double clamp(double progress) {
        return Math.max(0.0, Math.min(1.0, progress));
    }
}
