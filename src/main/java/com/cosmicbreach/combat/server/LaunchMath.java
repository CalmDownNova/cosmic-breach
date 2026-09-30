package com.cosmicbreach.combat.server;

/**
 * Vertical launch numbers for Minecraft's air physics. A living entity in the air moves by its
 * velocity, then its vertical velocity becomes {@code (vy - gravity) x 0.98}. Pure, no world access.
 */
public final class LaunchMath {
    public static final double AIR_DRAG = 0.98;
    private static final int MAX_TICKS = 400;

    private LaunchMath() {
    }

    /** The highest point above the start reached with upward speed {@code v0} (blocks per tick). */
    public static double apex(double v0, double gravity) {
        double y = 0;
        double best = 0;
        double v = v0;
        for (int i = 0; i < MAX_TICKS && v > 0; i++) {
            y += v;
            best = Math.max(best, y);
            v = (v - gravity) * AIR_DRAG;
        }
        return best;
    }

    /** The upward speed that peaks {@code height} blocks above the start. Needs gravity above 0. */
    public static double velocityForHeight(double height, double gravity) {
        if (height <= 0) {
            return 0;
        }
        if (gravity <= 0) {
            throw new IllegalArgumentException("a launch needs gravity, got " + gravity);
        }
        double lo = 0;
        double hi = 1;
        while (apex(hi, gravity) < height && hi < 64) {
            hi *= 2;
        }
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (apex(mid, gravity) < height) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }
}
