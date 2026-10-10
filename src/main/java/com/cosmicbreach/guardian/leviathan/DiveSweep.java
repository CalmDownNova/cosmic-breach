package com.cosmicbreach.guardian.leviathan;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Breach Dive hit detection along a step. At {@value LeviathanMoves#DIVE_SPEED} blocks a tick a point check at the end of
 * each tick could skip a thin target, so a hit is the swept path of the head's (or a segment's) middle over the tick,
 * sampled every {@value #SAMPLE} blocks, coming within reach of the player's box. Pure.
 */
public final class DiveSweep {
    /** Samples along a step are at most this far apart (a fraction of the shortest reach, 2.5). */
    public static final double SAMPLE = 0.5;

    private DiveSweep() {
    }

    /** True if any point of the segment {@code from} to {@code to} is within {@code reach} of {@code box}. */
    public static boolean reaches(AABB box, Vec3 from, Vec3 to, double reach) {
        double len = from.distanceTo(to);
        int n = Math.max(1, (int) Math.ceil(len / SAMPLE));
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            if (near(box, from.add(to.subtract(from).scale(f)), reach)) {
                return true;
            }
        }
        return false;
    }

    /** True if {@code p} is within {@code reach} of the box. */
    public static boolean near(AABB box, Vec3 p, double reach) {
        double nx = Math.max(box.minX, Math.min(p.x, box.maxX));
        double ny = Math.max(box.minY, Math.min(p.y, box.maxY));
        double nz = Math.max(box.minZ, Math.min(p.z, box.maxZ));
        return (nx - p.x) * (nx - p.x) + (ny - p.y) * (ny - p.y) + (nz - p.z) * (nz - p.z) <= reach * reach;
    }
}
