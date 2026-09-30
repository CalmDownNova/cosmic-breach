package com.cosmicbreach.mount;

/**
 * The Drift Manta's numbers (GDD 8.1), pure.
 *
 * <p><b>Flight.</b> Inside the Drift it truly flies: {@value #FORWARD} blocks a tick forward and {@value #VERTICAL}
 * up or down (jump climbs; so does looking up while moving forward, and looking down dives). Outside the Drift it is
 * a glider that can't gain height: it sinks {@value #GLIDE_SINK} a tick at its forward speed and can only dive. The
 * Gale Fins make it {@value #GALE_FINS}x as fast. The body's velocity closes {@value #ACCEL} of the gap to what the
 * rider asks each tick, so it swims into its speed rather than snapping to it ({@link #step}).
 *
 * <p><b>Phase Blink</b> (the dash key): {@value #BLINK_DISTANCE} blocks along the rider's look (never upward outside
 * the Drift), {@value #BLINK_SHIELD} ticks when neither rider nor manta can be hurt, then {@value #BLINK_COOLDOWN}
 * ticks before the next (the Nebula Reins cut it by 30%).
 */
public final class MantaRules {
    public static final double HEALTH = 40.0;
    public static final double FORWARD = 0.35;
    public static final double VERTICAL = 0.25;
    /** Backward and sideways speeds, as shares of forward (as a horse's). */
    public static final double BACK_SHARE = 0.25;
    public static final double STRAFE_SHARE = 0.5;
    public static final double GALE_FINS = 1.15;
    public static final double ACCEL = 0.2;
    /** Pitch, degrees, that asks for the full climb or dive while moving forward. */
    public static final double FULL_CLIMB_PITCH = 35.0;
    public static final double GLIDE_SINK = 0.06;
    /** On the ground outside the Drift it can only shuffle, at this share of its speed. */
    public static final double GROUND_SHARE = 0.3;

    public static final double BLINK_DISTANCE = 8.0;
    public static final int BLINK_SHIELD = 6;
    public static final int BLINK_COOLDOWN = 120;
    /** The Nebula Reins: Phase Blink cooldown -30%. */
    public static final double REINS_COOLDOWN_SCALE = 0.7;

    /** A wild manta's cruising speed and how far it roams. */
    public static final double WILD_SPEED = 0.12;
    public static final double ROAM = 20.0;

    private MantaRules() {
    }

    /** How fast the rider may fly: 1, or the Gale Fins' 1.15. */
    public static double speedScale(boolean galeFins) {
        return galeFins ? GALE_FINS : 1.0;
    }

    /**
     * The velocity the rider asks for, {@code {x, y, z}} blocks a tick. {@code forward} and {@code strafe} are the
     * rider's movement inputs (-1 to 1; strafe positive to the left, as vanilla's), {@code yaw} and {@code pitch} the
     * rider's look in degrees (pitch positive looks down).
     */
    public static double[] target(double forward, double strafe, boolean jump, float yaw, float pitch, boolean inDrift,
                                  boolean onGround, boolean galeFins) {
        double scale = speedScale(galeFins);
        double f = forward < 0 ? forward * BACK_SHARE : forward;
        double s = strafe * STRAFE_SHARE;
        double speed = FORWARD * scale;
        if (!inDrift && onGround) {
            speed *= GROUND_SHARE;
        }
        double r = Math.toRadians(yaw);
        double sin = Math.sin(r);
        double cos = Math.cos(r);
        // vanilla's moveRelative: forward is (-sin, cos), strafe left is (cos, sin)
        double x = (-sin * f + cos * s) * speed;
        double z = (cos * f + sin * s) * speed;
        double climb = Math.max(-1.0, Math.min(1.0, -pitch / FULL_CLIMB_PITCH)) * Math.max(0.0, forward);
        if (jump) {
            climb = 1.0;
        }
        double y;
        if (inDrift) {
            y = climb * VERTICAL * scale;
        } else if (onGround) {
            y = 0.0;
        } else {
            y = climb < 0 ? -GLIDE_SINK + climb * (VERTICAL * scale - GLIDE_SINK) : -GLIDE_SINK;
        }
        return new double[] {x, y, z};
    }

    /** One tick toward {@code target}; outside the Drift the result never rises. */
    public static double[] step(double[] velocity, double[] target, boolean inDrift) {
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = velocity[i] + (target[i] - velocity[i]) * ACCEL;
        }
        if (!inDrift && out[1] > 0.0) {
            out[1] = 0.0;
        }
        return out;
    }

    // ------------------------------------------------------------------ Phase Blink

    /** Ticks from one blink to the next. */
    public static int blinkCooldown(boolean nebulaReins) {
        return nebulaReins ? (int) Math.round(BLINK_COOLDOWN * REINS_COOLDOWN_SCALE) : BLINK_COOLDOWN;
    }

    /** True if a blink may start at {@code now}, the last having started at {@code last}. */
    public static boolean blinkReady(long now, long last, boolean nebulaReins) {
        return now - last >= blinkCooldown(nebulaReins);
    }

    /** True while a blink started at {@code blinkAt} keeps rider and manta from harm (ticks 0 to 5 after it). */
    public static boolean shielded(long now, long blinkAt) {
        return now >= blinkAt && now - blinkAt < BLINK_SHIELD;
    }

    /**
     * The blink's direction, a unit vector {@code {x, y, z}} along the rider's look; outside the Drift it keeps no
     * upward part (level or down).
     */
    public static double[] blinkDirection(float yaw, float pitch, boolean inDrift) {
        double p = Math.toRadians(pitch);
        double r = Math.toRadians(yaw);
        double x = -Math.sin(r) * Math.cos(p);
        double y = -Math.sin(p);
        double z = Math.cos(r) * Math.cos(p);
        if (!inDrift && y > 0.0) {
            double h = Math.hypot(x, z);
            if (h < 1e-6) {
                x = -Math.sin(r);
                z = Math.cos(r);
                h = 1.0;
            }
            return new double[] {x / h, 0.0, z / h};
        }
        return new double[] {x, y, z};
    }
}
