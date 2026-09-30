package com.cosmicbreach.guardian;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The shapes a guardian's telegraphs draw on the floor, and the tests for who stands in them, so the warning a
 * player sees and the hit the server deals are the same shape (GDD 4.1: the telegraph is the hitbox). Pure.
 *
 * <ul>
 *   <li>A <b>circle</b> (the gold ring under a slam): a player whose box reaches within the radius.</li>
 *   <li>A <b>band</b> (the white band of a sweep): an annulus from an inner to an outer radius across an arc
 *       centred on a facing; a player whose middle stands in it.</li>
 *   <li>A <b>line</b> (a beam): a segment with a radius; a player whose box it touches.</li>
 * </ul>
 * Yaws are Minecraft's: 0 faces south (+Z), 90 west (-X).
 */
public final class Telegraphs {
    /** Floor shapes reach this far above the floor they are drawn on (a jump doesn't clear them). */
    public static final double FLOOR_REACH_UP = 2.5;
    public static final double FLOOR_REACH_DOWN = 0.5;

    private Telegraphs() {
    }

    /** True if {@code box} reaches into the circle of {@code radius} round {@code centre} (on the floor at centre.y). */
    public static boolean inCircle(AABB box, Vec3 centre, double radius) {
        if (box.maxY < centre.y - FLOOR_REACH_DOWN || box.minY > centre.y + FLOOR_REACH_UP) {
            return false;
        }
        double nx = Math.max(box.minX, Math.min(centre.x, box.maxX));
        double nz = Math.max(box.minZ, Math.min(centre.z, box.maxZ));
        return (nx - centre.x) * (nx - centre.x) + (nz - centre.z) * (nz - centre.z) <= radius * radius;
    }

    /**
     * True if the middle of {@code box} stands in the band from {@code inner} to {@code outer} blocks round
     * {@code centre}, within {@code arcDegrees / 2} of {@code facingYaw}. The band's radii grow by half the box's
     * width, so a player whose edge is in the band counts.
     */
    public static boolean inBand(AABB box, Vec3 centre, double inner, double outer, float facingYaw, double arcDegrees) {
        if (box.maxY < centre.y - FLOOR_REACH_DOWN || box.minY > centre.y + FLOOR_REACH_UP) {
            return false;
        }
        Vec3 mid = box.getCenter();
        double d = Math.hypot(mid.x - centre.x, mid.z - centre.z);
        double half = (box.maxX - box.minX) / 2.0;
        if (d < inner - half || d > outer + half) {
            return false;
        }
        return Math.abs(angleFrom(centre, mid, facingYaw)) <= arcDegrees / 2.0;
    }

    /** The signed angle in degrees (-180 to 180, positive clockwise seen from above) from {@code facingYaw} to {@code point}. */
    public static double angleFrom(Vec3 centre, Vec3 point, float facingYaw) {
        double yaw = Math.toDegrees(Math.atan2(point.z - centre.z, point.x - centre.x)) - 90.0;
        return wrap(yaw - facingYaw);
    }

    /** The flat unit vector a Minecraft yaw faces (0 south, 90 west). */
    public static Vec3 forward(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
    }

    /** An angle in degrees brought into (-180, 180]. */
    public static double wrap(double degrees) {
        double a = degrees % 360.0;
        if (a > 180.0) {
            a -= 360.0;
        } else if (a <= -180.0) {
            a += 360.0;
        }
        return a;
    }

    /** True if the segment from {@code a} to {@code b}, {@code radius} thick, touches {@code box}. */
    public static boolean onLine(AABB box, Vec3 a, Vec3 b, double radius) {
        AABB grown = box.inflate(radius);
        return grown.contains(a) || grown.contains(b) || grown.clip(a, b).isPresent();
    }

    /** The point of segment {@code a}-{@code b} nearest {@code p}. */
    public static Vec3 closestOnSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-9) {
            return a;
        }
        double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
        return a.add(ab.scale(t));
    }

    /**
     * The flat direction that pushes a player at {@code p} out of the line {@code a}-{@code b}: away from its
     * nearest point, or square to the line when standing right on it.
     */
    public static Vec3 outOfLine(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 near = closestOnSegment(p, a, b);
        Vec3 away = new Vec3(p.x - near.x, 0.0, p.z - near.z);
        if (away.lengthSqr() > 1e-6) {
            return away.normalize();
        }
        Vec3 along = new Vec3(b.x - a.x, 0.0, b.z - a.z);
        if (along.lengthSqr() < 1e-9) {
            return new Vec3(1.0, 0.0, 0.0);
        }
        along = along.normalize();
        return new Vec3(-along.z, 0.0, along.x);
    }
}
