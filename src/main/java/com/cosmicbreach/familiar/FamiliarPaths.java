package com.cosmicbreach.familiar;

import net.minecraft.world.phys.Vec3;

/**
 * Where a familiar wants to be, pure: the Emberwisp's orbit, the Prism Moth's loops over its owner's shoulders, the
 * Gravikin's perch and hops, and the step toward any of them. Offsets are from the owner's feet; yaw is Minecraft's
 * (degrees, 0 facing +Z).
 */
public final class FamiliarPaths {
    /** The Emberwisp goes round once in this many ticks. */
    public static final double WISP_PERIOD = 80.0;
    public static final double WISP_RADIUS = 1.15;
    /** Its orbit tilts: lowest in front of its owner (under the eyes' line), highest behind. */
    public static final double WISP_HEIGHT = 1.3;
    public static final double WISP_TILT = 0.3;
    /** The Prism Moth's loop takes this many ticks. */
    public static final double MOTH_PERIOD = 120.0;
    /** Fastest a flier moves, blocks a tick. */
    public static final double MAX_SPEED = 1.2;

    private FamiliarPaths() {
    }

    /** The unit vector a yaw faces, on the ground plane. */
    public static Vec3 facing(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
    }

    /** {@code local} (x to the owner's left, z ahead of them) turned into the world by the owner's yaw. */
    public static Vec3 turn(Vec3 local, float yaw) {
        double r = Math.toRadians(yaw);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return new Vec3(local.x * cos - local.z * sin, local.y, local.x * sin + local.z * cos);
    }

    /**
     * The Emberwisp's place at tick {@code t} (with the partial tick in it), from its owner's feet: a circle of radius
     * {@value #WISP_RADIUS} turning in the world, tilted so it dips under the eyes' line in front of its owner and
     * rises behind them, with a small bob.
     */
    public static Vec3 wispOrbit(double t, float ownerYaw) {
        double a = 2.0 * Math.PI * t / WISP_PERIOD;
        double x = WISP_RADIUS * Math.cos(a);
        double z = WISP_RADIUS * Math.sin(a);
        Vec3 ahead = facing(ownerYaw);
        double front = (x * ahead.x + z * ahead.z) / WISP_RADIUS; // 1 in front, -1 behind
        double y = WISP_HEIGHT - WISP_TILT * front + 0.08 * Math.sin(2.0 * Math.PI * t / 23.0);
        return new Vec3(x, y, z);
    }

    /**
     * The Prism Moth's place at tick {@code t}: a lazy figure of eight behind and over its owner's shoulders (out of the
     * first-person view), turned with its owner, with a flutter.
     */
    public static Vec3 mothLoop(double t, float ownerYaw) {
        double a = 2.0 * Math.PI * t / MOTH_PERIOD;
        double flutter = 0.06 * Math.sin(t * 1.9) + 0.03 * Math.sin(t * 3.7 + 1.0);
        Vec3 local = new Vec3(0.9 * Math.sin(a), 1.95 + 0.14 * Math.sin(3.0 * a) + flutter, -0.55 - 0.25 * Math.cos(2.0 * a));
        return turn(local, ownerYaw);
    }

    /** The Gravikin's favourite spot: a little behind its owner and to their right. */
    public static Vec3 perch(float ownerYaw) {
        return turn(new Vec3(-1.4, 0.0, -1.1), ownerYaw);
    }

    /** Ticks a hop of {@code distance} blocks takes: 8 to 20. */
    public static int hopTicks(double distance) {
        return (int) Math.max(8, Math.min(20, Math.round(6 + distance * 2.2)));
    }

    /** How high a hop of {@code distance} blocks rises over the higher of its two ends. */
    public static double hopApex(double distance) {
        return 0.6 + 0.12 * Math.min(distance, 12.0);
    }

    /** Where a hop from {@code from} to {@code to} is at {@code f} (0 to 1) of the way, rising {@code apex} over the higher end. */
    public static Vec3 hop(Vec3 from, Vec3 to, double apex, double f) {
        double u = Math.max(0.0, Math.min(1.0, f));
        double base = from.y + (to.y - from.y) * u;
        double top = Math.max(from.y, to.y) + apex;
        // a parabola through both ends that peaks at `top`: the rise over the straight line is largest mid-hop
        double lift = 4.0 * u * (1.0 - u) * (top - (from.y + to.y) / 2.0);
        return new Vec3(from.x + (to.x - from.x) * u, base + lift, from.z + (to.z - from.z) * u);
    }

    /** One tick's step from {@code at} toward {@code goal}: {@code gain} of the gap, never more than {@code max}. */
    public static Vec3 step(Vec3 at, Vec3 goal, double gain, double max) {
        Vec3 d = goal.subtract(at).scale(gain);
        double len = d.length();
        return len > max ? d.scale(max / len) : d;
    }
}
