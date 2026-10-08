package com.cosmicbreach.client.gyre;

/**
 * How a Gyre Knight's blade rings are drawn by distance (1.1 design section 8, render audit bug B). Up close a line keeps
 * a pixel minimum so the rings read against a pale sky; beyond {@value #PIXEL_RANGE} blocks the stroke stops growing, so
 * it shrinks on screen with the ring and the rings keep their holes; between {@value #FADE_START} and {@value #FADE_END}
 * blocks they fade out, after which the knight's own glow marks it.
 */
public final class RingFade {
    public static final double PIXEL_RANGE = 16.0;
    public static final double FADE_START = 40.0;
    public static final double FADE_END = 56.0;

    private RingFade() {
    }

    /** A ring line's width in blocks at {@code distance}: {@code pixels} on a 720-line screen out to 16 blocks, never under {@code min}. */
    public static double width(double distance, double pixels, double min) {
        return Math.max(min, Math.min(distance, PIXEL_RANGE) * 1.4 / 720.0 * pixels);
    }

    /** The rings' opacity at {@code distance}: 1 to 40 blocks, a smooth step down to 0 at 56. */
    public static float alpha(double distance) {
        if (distance <= FADE_START) {
            return 1.0f;
        }
        if (distance >= FADE_END) {
            return 0.0f;
        }
        double t = (distance - FADE_START) / (FADE_END - FADE_START);
        return (float) (1.0 - t * t * (3.0 - 2.0 * t));
    }
}
