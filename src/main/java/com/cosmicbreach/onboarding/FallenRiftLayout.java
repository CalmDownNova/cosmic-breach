package com.cosmicbreach.onboarding;

import net.minecraft.util.Mth;

/**
 * A Fallen Rift's plan (GDD 1.3): a meteor crater of radius 8 to 12, a bowl about half its radius deep with
 * a raised lip, a half-buried 4 by 4 Breach Ring at the bottom (8 of its 12 frames flush with the floor, the
 * gaps and the 2 by 2 middle left open, so one craft of frames finishes it where it lies) and a chest sunk
 * into the floor beside it. The bowl is centred on the ring's middle (the corner its four middle blocks
 * share), one block south-east of the ring's origin. Everything here is a function of the rift's seed and a
 * position, so every chunk the crater crosses draws the same crater. Pure.
 */
public final class FallenRiftLayout {
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 12;
    /** Frames the ring keeps, of its 12. */
    public static final int FRAMES = 8;
    /** Round the ring the floor is flat at the ring's level, this far from its middle: the ring and a row round it. */
    public static final double FLAT_RADIUS = 2.9;
    /** The lip rises this far past the radius. */
    public static final int LIP = 2;

    private FallenRiftLayout() {
    }

    public static int radius(long seed) {
        return MIN_RADIUS + (int) Math.floorMod(mix(seed, 1), MAX_RADIUS - MIN_RADIUS + 1);
    }

    /** The bowl's depth at the middle. */
    public static int depth(int radius) {
        return Math.max(3, Math.round(radius * 0.42f));
    }

    /**
     * The floor's offset from the rim level at distance {@code d} from the middle: {@code -depth} at the
     * middle rising to 0 at the radius, then a lip up to +1 just past the rim that falls back to 0 at
     * radius + {@link #LIP}. {@code Integer.MIN_VALUE} beyond that.
     */
    public static int floorOffset(int radius, double d) {
        if (d > radius + LIP) {
            return Integer.MIN_VALUE;
        }
        if (d <= radius) {
            double k = d / radius;
            return -(int) Math.round(depth(radius) * (1.0 - k * k));
        }
        return d - radius < LIP * 0.6 ? 1 : 0;
    }

    /** Which of {@link BreachRing#RING}'s frames are still there: exactly {@link #FRAMES} of the 12. */
    public static boolean[] framesPresent(long seed) {
        boolean[] present = new boolean[BreachRing.RING.length];
        int[] order = new int[BreachRing.RING.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        long s = mix(seed, 2);
        for (int i = order.length - 1; i > 0; i--) {
            int j = (int) Math.floorMod(s, i + 1);
            s = mix(s, i);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        for (int i = 0; i < FRAMES; i++) {
            present[order[i]] = true;
        }
        return present;
    }

    /**
     * The chest's offset from the ring's origin (dx, dz): just outside the flat floor round the ring, beside the
     * middle of one of its four sides.
     */
    public static int[] chest(long seed) {
        return switch ((int) Math.floorMod(mix(seed, 3), 4)) {
            case 0 -> new int[] {4, 1};
            case 1 -> new int[] {-3, 0};
            case 2 -> new int[] {0, 4};
            default -> new int[] {1, -3};
        };
    }

    /** True if (dx, dz) from the ring's origin is on the round flat floor about the ring. */
    public static boolean flat(int dx, int dz) {
        return fromMiddle(dx, dz) <= FLAT_RADIUS;
    }

    /** The distance from the bowl's centre (the ring's middle) to the block at (dx, dz) from the origin. */
    public static double fromMiddle(int dx, int dz) {
        return Math.hypot(dx - 0.5, dz - 0.5);
    }

    /** A stable number from 0 to 1 for the block at (x, y, z) of this rift, for scattering materials. */
    public static double noise(long seed, int x, int y, int z) {
        long h = mix(seed ^ (x * 0x9E3779B97F4A7C15L), y * 31L + z * 0x632BE59BD9B4E019L);
        return (h >>> 11) * 0x1.0p-53;
    }

    static long mix(long seed, long salt) {
        long z = seed + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Distance helper for callers working in blocks. */
    public static double distance(int dx, int dz) {
        return Mth.sqrt(dx * dx + dz * dz);
    }

    /** The floor's offset from the rim level at (dx, dz) from the ring's origin (flat round the ring). */
    public static int floorAt(int radius, int dx, int dz) {
        return flat(dx, dz) ? -depth(radius) : floorOffset(radius, fromMiddle(dx, dz));
    }
}
