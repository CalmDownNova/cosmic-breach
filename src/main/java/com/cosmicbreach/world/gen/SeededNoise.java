package com.cosmicbreach.world.gen;

import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * One seeded simplex noise (vanilla's {@link SimplexNoise}, immutable and thread safe) with a few helpers.
 * Values are roughly in [-1, 1]; coordinates are divided by the wavelength by the caller.
 */
public final class SeededNoise {
    private final SimplexNoise simplex;

    public SeededNoise(long seed) {
        this.simplex = new SimplexNoise(new XoroshiroRandomSource(seed));
    }

    public double at(double x, double z) {
        return simplex.getValue(x, z);
    }

    public double at(double x, double y, double z) {
        return simplex.getValue(x, y, z);
    }

    /** Fractal sum of {@code octaves} octaves (each half the size and half the weight), scaled to about [-1, 1]. */
    public double fbm(double x, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * simplex.getValue(x * f + i * 17.31, z * f - i * 9.17);
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return sum / norm;
    }

    /** Ridged noise: 1 on the noise's zero lines, falling to 0 away from them. */
    public double ridged(double x, double z) {
        return 1.0 - Math.abs(simplex.getValue(x, z));
    }
}
