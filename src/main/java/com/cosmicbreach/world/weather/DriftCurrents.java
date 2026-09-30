package com.cosmicbreach.world.weather;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The Drift's currents (GDD 2.2): long straight tubes of moving dust between the asteroids that carry
 * anything inside them {@value #PUSH} blocks a tick along their heading. They are pure arithmetic on a salt
 * made from the world seed ({@link #salt}), so the server and every client (which gets the salt with the
 * weather sync, never the seed) find the same tubes without sending any of them.
 *
 * <p>The Drift is cut into {@value #CELL}-block cells; about two cells in three hold one tube: centred in the
 * cell, 80 to 160 blocks long, 5 to 8 blocks in radius, level, at Y 180 to 280 (inside the Drift's band).
 * During a Gravity Tide every tube turns to the Tide's heading.
 */
public final class DriftCurrents {
    /** Cell size in blocks. */
    public static final int CELL = 192;
    /** How far a current carries things each tick, in blocks. */
    public static final double PUSH = 0.05;
    /** Share of cells that hold a current. */
    static final double CHANCE = 0.68;
    public static final int MIN_Y = 180;
    public static final int MAX_Y = 280;

    /** One current: its centre, heading (radians, x east and z south) and size. */
    public record Zone(double x, double y, double z, float heading, double halfLength, double radius) {
        public double dirX() {
            return Math.cos(heading);
        }

        public double dirZ() {
            return Math.sin(heading);
        }

        /** Distance along the axis from the centre to the point's projection, clamped to the tube. */
        public double along(double px, double pz) {
            double t = (px - x) * dirX() + (pz - z) * dirZ();
            return Math.max(-halfLength, Math.min(halfLength, t));
        }

        /** Squared distance from the point to the tube's axis segment. */
        public double distanceSqr(double px, double py, double pz) {
            double t = along(px, pz);
            double cx = x + dirX() * t;
            double cz = z + dirZ() * t;
            double dx = px - cx;
            double dy = py - y;
            double dz = pz - cz;
            return dx * dx + dy * dy + dz * dz;
        }

        public boolean contains(double px, double py, double pz) {
            return distanceSqr(px, py, pz) <= radius * radius;
        }
    }

    private DriftCurrents() {
    }

    /** The currents' salt for a world seed: mixed so the seed can't be read back from it. */
    public static long salt(long worldSeed) {
        return mix(mix(worldSeed ^ 0x6C1D5E7A3B29F04DL) + 0x2545F4914F6CDD1DL);
    }

    /** The current of cell ({@code cx}, {@code cz}), or null if that cell has none. */
    public static @Nullable Zone zone(long salt, int cx, int cz) {
        long h = mix(salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL));
        if (unit(h) >= CHANCE) {
            return null;
        }
        h = mix(h);
        double x = cx * (double) CELL + 32 + unit(h) * (CELL - 64);
        h = mix(h);
        double z = cz * (double) CELL + 32 + unit(h) * (CELL - 64);
        h = mix(h);
        double y = MIN_Y + unit(h) * (MAX_Y - MIN_Y);
        h = mix(h);
        float heading = (float) (unit(h) * Math.PI * 2.0);
        h = mix(h);
        double halfLength = 40 + unit(h) * 40;
        h = mix(h);
        double radius = 5 + unit(h) * 3;
        return new Zone(x, y, z, heading, halfLength, radius);
    }

    /** The current holding the point, or null. */
    public static @Nullable Zone at(long salt, double x, double y, double z) {
        if (y < MIN_Y - 10 || y > MAX_Y + 10) {
            return null;
        }
        int cx = Math.floorDiv((int) Math.floor(x), CELL);
        int cz = Math.floorDiv((int) Math.floor(z), CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Zone zone = zone(salt, cx + dx, cz + dz);
                if (zone != null && zone.contains(x, y, z)) {
                    return zone;
                }
            }
        }
        return null;
    }

    /** Currents whose tube comes within {@code range} blocks of (x, z) on the ground plane. */
    public static List<Zone> near(long salt, double x, double z, double range) {
        List<Zone> out = new ArrayList<>();
        int reach = 1 + (int) Math.ceil(range / CELL);
        int cx = Math.floorDiv((int) Math.floor(x), CELL);
        int cz = Math.floorDiv((int) Math.floor(z), CELL);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                Zone zone = zone(salt, cx + dx, cz + dz);
                if (zone == null) {
                    continue;
                }
                double t = zone.along(x, z);
                double px = zone.x + zone.dirX() * t - x;
                double pz = zone.z + zone.dirZ() * t - z;
                double r = range + zone.radius;
                if (px * px + pz * pz <= r * r) {
                    out.add(zone);
                }
            }
        }
        return out;
    }

    /**
     * The push at a point, blocks per tick as {x, z}: {@value #PUSH} along its current's heading, or along
     * {@code tideHeading} when a Tide runs (NaN when none); {0, 0} outside every current.
     */
    public static double[] push(long salt, double x, double y, double z, double tideHeading) {
        Zone zone = at(salt, x, y, z);
        if (zone == null) {
            return new double[] {0.0, 0.0};
        }
        double heading = Double.isNaN(tideHeading) ? zone.heading : tideHeading;
        return new double[] {Math.cos(heading) * PUSH, Math.sin(heading) * PUSH};
    }

    /** 0 (inclusive) to 1 (exclusive) from the top 53 bits. */
    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** SplitMix64's finaliser. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
