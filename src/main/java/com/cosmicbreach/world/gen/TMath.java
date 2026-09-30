package com.cosmicbreach.world.gen;

/** Small shaping functions for the terrain model. */
final class TMath {
    private TMath() {
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 0 below {@code e0}, 1 above {@code e1}, a smooth S between. Works with {@code e0 > e1} (falling). */
    static double smoothstep(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** A smooth minimum of a and b, rounding the corner over about {@code k}. */
    static double smin(double a, double b, double k) {
        double h = clamp(0.5 + 0.5 * (b - a) / k, 0, 1);
        return lerp(b, a, h) - k * h * (1 - h);
    }

    static double sq(double v) {
        return v * v;
    }
}
