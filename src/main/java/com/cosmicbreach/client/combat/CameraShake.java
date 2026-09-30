package com.cosmicbreach.client.combat;

/**
 * Camera shake as trauma: hits add trauma (0 to 1), it drains {@value #DECAY_PER_SECOND} a second,
 * and the camera shakes by trauma squared, so small hits barely move it and big ones clearly do.
 * The shake is smooth noise on yaw, pitch and roll, a few degrees at most, scaled by the player's
 * screen shake setting. A kick ({@link #kick}) is the directional part: the view dips down fast and
 * comes back slower, for a heavy slam. Pure: the client feeds it ticks and reads offsets per frame.
 */
public final class CameraShake {
    public static final double DECAY_PER_SECOND = 1.5;
    private static final double DECAY_PER_TICK = DECAY_PER_SECOND / 20.0;
    /** Degrees at full trauma and full strength. */
    public static final float MAX_YAW = 3.0f;
    public static final float MAX_PITCH = 3.0f;
    public static final float MAX_ROLL = 2.0f;
    /** How fast the noise moves, in samples per second. */
    public static final double FREQUENCY = 16.0;
    /** Trauma one landed hit adds at most. */
    public static final double MAX_HIT_TRAUMA = 0.6;

    /** A kick dips for the first fifth of this and recovers over the rest. */
    public static final int KICK_TICKS = 8;

    private double trauma;
    private long ticks;
    private double kick;
    private int kickAge = KICK_TICKS;

    /** Trauma for landing a hit with this Impact. */
    public static double forLandedHit(double impact, boolean crit) {
        return clamp(impact / 40.0 + (crit ? 0.1 : 0.0), 0.0, MAX_HIT_TRAUMA);
    }

    /** Trauma for being hit with this Impact: a small shake. */
    public static double forTakenHit(double impact) {
        return clamp(0.2 + impact / 60.0, 0.0, 0.5);
    }

    public void addTrauma(double amount) {
        if (amount > 0) {
            trauma = Math.min(1.0, trauma + amount);
        }
    }

    /** Dips the view {@code degrees} down; a bigger kick replaces a smaller one still running. */
    public void kick(double degrees) {
        if (degrees > kickAt(0f)) {
            kick = degrees;
            kickAge = 0;
        }
    }

    /** The kick's pitch offset (degrees, down positive) a frame {@code partialTick} past the last tick. */
    public double kickAt(float partialTick) {
        double t = (kickAge + partialTick) / KICK_TICKS;
        if (kick <= 0.0 || t >= 1.0) {
            return 0.0;
        }
        return kick * (t < 0.2 ? t / 0.2 : Math.pow(1.0 - (t - 0.2) / 0.8, 2.0));
    }

    /** True while there is anything to add to the camera. */
    public boolean active() {
        return trauma > 0.0 || kickAge < KICK_TICKS;
    }

    /** One game tick passes. */
    public void tick() {
        ticks++;
        trauma = Math.max(0.0, trauma - DECAY_PER_TICK);
        if (kickAge < KICK_TICKS) {
            kickAge++;
        }
    }

    public double trauma() {
        return trauma;
    }

    /** Trauma part way to the next tick, so the fade is smooth between ticks. */
    public double traumaAt(float partialTick) {
        return Math.max(0.0, trauma - DECAY_PER_TICK * partialTick);
    }

    /** The shake amount: trauma squared, times the player's setting (0 to 1). */
    public double shake(float partialTick, double strength) {
        double t = traumaAt(partialTick);
        return t * t * clamp(strength, 0.0, 1.0);
    }

    /** Yaw, pitch and roll offsets in degrees for a frame {@code partialTick} past the last tick. */
    public float[] offsets(float partialTick, double strength) {
        double s = shake(partialTick, strength);
        float dip = (float) (kickAt(partialTick) * clamp(strength, 0.0, 1.0));
        if (s <= 0.0) {
            return new float[] {0f, dip, 0f};
        }
        double time = (ticks + partialTick) / 20.0 * FREQUENCY;
        return new float[] {
                (float) (MAX_YAW * s * noise(0x5EED1, time)),
                (float) (MAX_PITCH * s * noise(0x5EED2, time)) + dip,
                (float) (MAX_ROLL * s * noise(0x5EED3, time))
        };
    }

    public void reset() {
        trauma = 0.0;
        kick = 0.0;
        kickAge = KICK_TICKS;
    }

    /** Smooth value noise in [-1, 1]: random values at whole numbers, eased in between. */
    static double noise(int seed, double t) {
        long i = (long) Math.floor(t);
        double f = t - i;
        double a = lattice(seed, i);
        double b = lattice(seed, i + 1);
        double u = f * f * (3.0 - 2.0 * f);
        return a + (b - a) * u;
    }

    private static double lattice(int seed, long i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
