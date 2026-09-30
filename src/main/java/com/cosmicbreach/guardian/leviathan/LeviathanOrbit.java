package com.cosmicbreach.guardian.leviathan;

import net.minecraft.world.phys.Vec3;

/**
 * The Leviathan's orbit round the central asteroid (Thalassine Leviathan design v1, "Movement"): a circle of
 * {@value LeviathanMoves#ORBIT_RADIUS} blocks between the core and the platforms, rising and falling
 * {@value LeviathanMoves#WAVE} blocks in a slow wave (twice a lap, its phase fixed for a fight), swum counter-clockwise
 * seen from above (the angle grows: (cos a, sin a) in x and z). Pure.
 */
public final class LeviathanOrbit {
    private LeviathanOrbit() {
    }

    /** The head's middle at angle {@code a} (radians) round {@code centre}. */
    public static Vec3 point(Vec3 centre, double a, double wavePhase) {
        return point(centre, a, wavePhase, LeviathanMoves.ORBIT_RADIUS);
    }

    /** A point of a circle of {@code radius} with the orbit's wave. */
    public static Vec3 point(Vec3 centre, double a, double wavePhase, double radius) {
        return new Vec3(centre.x + radius * Math.cos(a), centre.y + LeviathanMoves.WAVE * Math.sin(2.0 * a + wavePhase),
                centre.z + radius * Math.sin(a));
    }

    /** The angle after swimming {@code blocks} along the orbit from {@code a}. */
    public static double advance(double a, double blocks) {
        return a + blocks / LeviathanMoves.ORBIT_RADIUS;
    }

    /** The angle of {@code p} round {@code centre}. */
    public static double angleOf(Vec3 centre, Vec3 p) {
        return Math.atan2(p.z - centre.z, p.x - centre.x);
    }

    /** How far {@code to} is ahead of {@code from} along the swimming direction, in [0, 2 pi). */
    public static double ahead(double from, double to) {
        double d = (to - from) % (2.0 * Math.PI);
        return d < 0 ? d + 2.0 * Math.PI : d;
    }

    /** The flat unit direction of travel at angle {@code a}. */
    public static Vec3 heading(double a) {
        return new Vec3(-Math.sin(a), 0.0, Math.cos(a));
    }

    /** The flat unit direction out from the centre at angle {@code a}. */
    public static Vec3 outward(double a) {
        return new Vec3(Math.cos(a), 0.0, Math.sin(a));
    }

    /** Minecraft's yaw (degrees; 0 faces +Z, 90 faces -X) for a direction. */
    public static float yawOf(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    /** Minecraft's pitch (degrees, positive looks down) for a direction. */
    public static float pitchOf(Vec3 d) {
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        return (float) -Math.toDegrees(Math.atan2(d.y, flat));
    }
}
