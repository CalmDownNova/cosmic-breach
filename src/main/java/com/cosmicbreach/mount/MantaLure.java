package com.cosmicbreach.mount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a wild stingray waits for a player holding a Resonance Chime (1.1 design section 4), pure: just past the edge of
 * the rock the player stands on, at their height, in the direction where that edge is nearest, so stepping to the edge
 * puts it within reach. Looks {@value #SEARCH} blocks out in {@value #DIRECTIONS} directions. {@link #edgeSpots} lists
 * every direction's spot, nearest the player first, for a stingray whose way to the nearest is blocked.
 */
public final class MantaLure {
    /** A player holding the Chime lures stingrays this far away. */
    public static final double RANGE = 24.0;
    public static final int SEARCH = 8;
    public static final int DIRECTIONS = 16;

    /** True if the block at (x, y, z) has collision. */
    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    private MantaLure() {
    }

    /** The spot (a stingray's feet) for a player standing in block (px, py, pz), or null if no edge with room is near. */
    public static @Nullable Vec3 edgeSpot(Solid solid, int px, int py, int pz) {
        List<Vec3> spots = edgeSpots(solid, px, py, pz);
        return spots.isEmpty() ? null : spots.get(0);
    }

    /**
     * Every direction's spot (see {@link #edgeSpot}) for a player standing in block (px, py, pz): the one nearest the middle
     * of their block first, and of two equally near the one in the earlier direction. Empty if no edge with room is near.
     */
    public static List<Vec3> edgeSpots(Solid solid, int px, int py, int pz) {
        record Found(double dist, Vec3 spot) {
        }
        List<Found> found = new ArrayList<>();
        for (int k = 0; k < DIRECTIONS; k++) {
            double a = k * 2.0 * Math.PI / DIRECTIONS;
            double cx = Math.cos(a);
            double cz = Math.sin(a);
            for (int d = 1; d <= SEARCH; d++) {
                int x = px + (int) Math.round(cx * d);
                int z = pz + (int) Math.round(cz * d);
                if (solid.at(x, py - 1, z) || solid.at(x, py - 2, z)) {
                    continue; // still rock underfoot
                }
                if (roomy(solid, x, py, z)) {
                    Vec3 spot = new Vec3(x + 0.5 + cx * 0.6, py + 0.4, z + 0.5 + cz * 0.6);
                    found.add(new Found(Math.hypot(spot.x - (px + 0.5), spot.z - (pz + 0.5)), spot));
                }
                break; // the edge in this direction
            }
        }
        found.sort(Comparator.comparingDouble(Found::dist)); // stable: equally near keeps its direction order
        return found.stream().map(Found::spot).toList();
    }

    /** True if a stingray's body fits round column (x, z) at height y. */
    static boolean roomy(Solid solid, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (solid.at(x + dx, y, z + dz) || solid.at(x + dx, y + 1, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
