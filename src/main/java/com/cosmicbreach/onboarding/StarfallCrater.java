package com.cosmicbreach.onboarding;

import java.util.ArrayList;
import java.util.List;

/**
 * The small crater a Starfall leaves (GDD 1.3): a bowl of radius 2 or 3, one or two blocks deep in the
 * middle, lined with Starfall Stone, with the shard standing on the lining at its centre. Offsets are from
 * the ground block the shard landed on ({@code dy} 0 is that block's level). Pure; {@link Starfalls} applies
 * it to the world, removing only what the crater tag allows and never touching block entities.
 */
public final class StarfallCrater {
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 3;
    /** Plants and snow this far above the ground are cleared off the bowl. */
    public static final int CLEAR_ABOVE = 3;
    private static final double EDGE = 0.35;

    public enum Kind {
        /** Dug out (ground) or swept clear (plants above it). */
        CLEAR,
        /** The bowl's surface: Starfall Stone. */
        LINING,
        /** Where the shard stands. */
        SHARD
    }

    public record Cell(int dx, int dy, int dz, Kind kind) {}

    private StarfallCrater() {
    }

    /** How deep the bowl is at (dx, dz), in blocks dug below the ground; -1 outside the crater. */
    public static int depth(int radius, int dx, int dz) {
        double reach = radius + EDGE;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > reach) {
            return -1;
        }
        int deepest = radius - 1;
        double k = d / reach;
        return (int) Math.floor(deepest * (1.0 - k * k) + 0.5);
    }

    /** The cells of a crater of {@code radius} (clamped to 2 to 3). */
    public static List<Cell> cells(int radius) {
        int r = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        List<Cell> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int depth = depth(r, dx, dz);
                if (depth < 0) {
                    continue;
                }
                for (int dy = CLEAR_ABOVE; dy > -depth; dy--) {
                    boolean shard = dx == 0 && dz == 0 && dy == -depth + 1;
                    out.add(new Cell(dx, dy, dz, shard ? Kind.SHARD : Kind.CLEAR));
                }
                out.add(new Cell(dx, -depth, dz, Kind.LINING));
            }
        }
        return out;
    }

    /** Where the shard stands, relative to the ground block. */
    public static Cell shard(int radius) {
        int r = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        return new Cell(0, -depth(r, 0, 0) + 1, 0, Kind.SHARD);
    }
}
