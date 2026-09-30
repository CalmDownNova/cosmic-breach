package com.cosmicbreach.guardian.unsung;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Silent Nave's apse, the choir floor, as the fight sees it (Unsung design v1), shared by the structure that
 * builds it, the Unsung that fights on it and the client that draws its telegraphs. Pure (no level).
 *
 * <p>The apse's centre is the block corner {@code (x, z)}; {@code floorY} is the first block of air above the floor.
 * The choir floor is the disc of block columns whose centres lie within {@link #FLOOR_RADIUS}; the dais at its centre
 * is one block high out to {@link #DAIS_RADIUS}, with a ring of slabs as its step. The eight circles of silence
 * (radius {@link #CIRCLE_RADIUS}, Rift Glass inlays) sit on a ring of radius {@link #RING_RADIUS}, circle {@code k} at
 * {@code 22.5 + 45 k} degrees clockwise from east seen from above (+X east, +Z south). The masks circle the dais in a
 * triangle of radius {@link UnsungMoves#ORBIT_RADIUS}.
 */
public record ChoirArena(int x, int floorY, int z) {
    public static final double FLOOR_RADIUS = 16.5;
    /** How far above the floor a player still counts as on the choir floor. */
    public static final double INSIDE_HEIGHT = 12.0;
    public static final double DAIS_RADIUS = 4.5;
    public static final double STEP_RADIUS = 5.5;
    public static final double RING_RADIUS = 10.0;
    public static final double CIRCLE_RADIUS = 2.0;
    /** A circle's blocks: columns whose centres lie this close to its centre. */
    public static final double CIRCLE_BLOCKS = 2.25;
    public static final int CIRCLES = HarmonizeRules.CIRCLES;

    public static ChoirArena at(BlockPos centre) {
        return new ChoirArena(centre.getX(), centre.getY(), centre.getZ());
    }

    public BlockPos centreBlock() {
        return new BlockPos(x, floorY, z);
    }

    /** The centre on the floor. */
    public Vec3 centre() {
        return new Vec3(x, floorY, z);
    }

    /** Horizontal distance from the centre. */
    public double distance(double px, double pz) {
        return Math.hypot(px - x, pz - z);
    }

    /** True if {@code p} (a player's feet) is on the choir floor: inside the disc, not below the floor nor far above. */
    public boolean onFloor(Vec3 p) {
        return distance(p.x, p.z) <= FLOOR_RADIUS && p.y >= floorY - 1.0 && p.y <= floorY + INSIDE_HEIGHT;
    }

    /** True if the block column {@code (bx, bz)} is part of the choir floor. */
    public boolean floorColumn(int bx, int bz) {
        return distance(bx + 0.5, bz + 0.5) <= FLOOR_RADIUS;
    }

    /** True if the column is under the dais (one block high). */
    public boolean daisColumn(int bx, int bz) {
        return distance(bx + 0.5, bz + 0.5) <= DAIS_RADIUS;
    }

    /** True if the column is the dais's step (a slab). */
    public boolean stepColumn(int bx, int bz) {
        double d = distance(bx + 0.5, bz + 0.5);
        return d > DAIS_RADIUS && d <= STEP_RADIUS;
    }

    /** The ground's height under {@code (px, pz)}: the dais's top, its step, or the floor. */
    public double groundY(double px, double pz) {
        double d = distance(px, pz);
        if (d <= DAIS_RADIUS) {
            return floorY + 1.0;
        }
        return d <= STEP_RADIUS ? floorY + 0.5 : floorY;
    }

    // ------------------------------------------------------------------ the circles of silence

    /** Circle {@code k}'s angle, radians clockwise from east seen from above. */
    public static double circleAngle(int k) {
        return Math.toRadians(22.5 + 45.0 * Math.floorMod(k, CIRCLES));
    }

    /** Circle {@code k}'s centre on the floor. */
    public Vec3 circleCentre(int k) {
        double a = circleAngle(k);
        return new Vec3(x + RING_RADIUS * Math.cos(a), floorY, z + RING_RADIUS * Math.sin(a));
    }

    /** The circle whose inlay holds block column {@code (bx, bz)}, or -1. */
    public int circleColumn(int bx, int bz) {
        for (int k = 0; k < CIRCLES; k++) {
            Vec3 c = circleCentre(k);
            if (Math.hypot(bx + 0.5 - c.x, bz + 0.5 - c.z) <= CIRCLE_BLOCKS) {
                return k;
            }
        }
        return -1;
    }

    /** The circle {@code (px, pz)} stands in (with half a player's width), or -1. */
    public int circleAt(double px, double pz) {
        for (int k = 0; k < CIRCLES; k++) {
            Vec3 c = circleCentre(k);
            if (Math.hypot(px - c.x, pz - c.z) <= CIRCLE_RADIUS + HarmonizeRules.PLAYER_HALF) {
                return k;
            }
        }
        return -1;
    }

    /** The lit circles as {@link HarmonizeRules.Circle}s. */
    public java.util.List<HarmonizeRules.Circle> circles(int[] lit) {
        java.util.List<HarmonizeRules.Circle> out = new java.util.ArrayList<>();
        for (int k : lit) {
            Vec3 c = circleCentre(k);
            out.add(new HarmonizeRules.Circle(k, c.x, c.z, CIRCLE_RADIUS));
        }
        return out;
    }

    // ------------------------------------------------------------------ the masks

    /** Where {@code voice}'s face floats at {@code time} (ticks, smooth) circling the dais, before any dip or rise. */
    public Vec3 orbit(Voice voice, double time) {
        double a = Math.PI * 2.0 * time / UnsungMoves.ORBIT_TICKS + voice.ordinal() * Math.PI * 2.0 / 3.0;
        return new Vec3(x + UnsungMoves.ORBIT_RADIUS * Math.cos(a), floorY + UnsungMoves.FACE_HEIGHT,
                z + UnsungMoves.ORBIT_RADIUS * Math.sin(a));
    }

    /** Where a mask whose face is at {@code face} has its box's feet. */
    public static Vec3 feetForFace(Vec3 face) {
        return face.subtract(0, UnsungMoves.FACE_UP, 0);
    }

    /** The box of a mask whose face is at {@code face}. */
    public static AABB maskBox(Vec3 face) {
        Vec3 f = feetForFace(face);
        double h = UnsungMoves.MASK_WIDTH / 2.0;
        return new AABB(f.x - h, f.y, f.z - h, f.x + h, f.y + UnsungMoves.MASK_HEIGHT, f.z + h);
    }

    /** The yaw (Minecraft's: 0 faces south, 90 west) that faces from {@code from} toward {@code to}. */
    public static float yawToward(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }

    // ------------------------------------------------------------------ the Sweeping Wave

    /** The wave's front, in blocks from the centre, {@code t} ticks after it leaves the dais. */
    public static double waveFront(double t) {
        return Math.max(0.0, t) * UnsungMoves.WAVE_SPEED;
    }

    /**
     * True if the wave's front passed over a player this tick (from {@code before} to {@code after} blocks) and caught
     * them: their box reaches the front ({@code half} is half their width) and their feet are under the knee-high
     * wave over the ground there.
     */
    public boolean waveCatches(double px, double pz, double feetY, double half, double before, double after) {
        double d = distance(px, pz);
        if (d + half < before || d - half > after) {
            return false;
        }
        return feetY < groundY(px, pz) + UnsungMoves.WAVE_HEIGHT;
    }
}
