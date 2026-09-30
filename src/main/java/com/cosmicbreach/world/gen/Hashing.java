package com.cosmicbreach.world.gen;

/** Stateless 64-bit hashing for placing things on grids: the same inputs always give the same values. */
public final class Hashing {
    private Hashing() {
    }

    /** SplitMix64's finalizer: a good avalanche of all 64 bits. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** A hash of a salt, a tag and up to three grid coordinates. */
    public static long hash(long salt, int tag, int a, int b, int c) {
        long h = salt ^ (tag * 0x9E3779B97F4A7C15L);
        h = mix(h + a * 0xC2B2AE3D27D4EB4FL);
        h = mix(h + b * 0x165667B19E3779F9L);
        h = mix(h + c * 0xD6E8FEB86659FD93L);
        return h;
    }

    /** A value in [0, 1) from the high bits of {@code h}. */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * The {@code n}th value in [0, 1) derived from {@code h}: {@code unit(h)} is value 0, and each further
     * index remixes, so one hash yields as many independent values as a placement needs.
     */
    public static double unit(long h, int n) {
        return unit(mix(h + n * 0x9E3779B97F4A7C15L));
    }
}
