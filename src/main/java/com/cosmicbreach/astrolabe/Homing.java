package com.cosmicbreach.astrolabe;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The Astrolabe's soft homing (GDD 4.2): a bolt flies {@value #SPEED} blocks a tick for {@value #RANGE} blocks and
 * locks onto the enemy nearest its heading within a {@value #CONE} degree cone ({@link #acquire}); it keeps that lock
 * while the enemy stays within {@value #KEEP} degrees ({@link #keeps}), and turns toward it at most {@value #TURN}
 * degrees a tick ({@link #steer}). Soft and short on purpose: you aim with the camera and the bolts forgive a little.
 * Pure.
 */
public final class Homing {
    public static final double SPEED = 2.0;
    public static final double RANGE = 24.0;
    public static final double CONE = 10.0;
    public static final double KEEP = 40.0;
    public static final double TURN = 8.0;

    private Homing() {
    }

    /** Degrees between two directions (0 for a zero vector). */
    public static double angle(Vec3 a, Vec3 b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1e-9 || lb < 1e-9) {
            return 0.0;
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b) / (la * lb)))));
    }

    /**
     * The target a bolt at {@code pos} heading along {@code heading} locks onto: of the {@code centres} within
     * {@code range}, the one within {@code cone} degrees of the heading at the smallest angle (a tie to the nearer);
     * -1 if none.
     */
    public static int acquire(Vec3 pos, Vec3 heading, List<Vec3> centres, double cone, double range) {
        int best = -1;
        double bestAngle = Double.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < centres.size(); i++) {
            Vec3 to = centres.get(i).subtract(pos);
            double d = to.length();
            if (d > range || d < 1e-6) {
                continue;
            }
            double a = angle(heading, to);
            if (a > cone) {
                continue;
            }
            if (a < bestAngle - 1e-9 || (Math.abs(a - bestAngle) <= 1e-9 && d < bestDistance)) {
                best = i;
                bestAngle = a;
                bestDistance = d;
            }
        }
        return best;
    }

    /** True while a locked target stays worth chasing: within {@code keep} degrees of the heading and {@code range}. */
    public static boolean keeps(Vec3 pos, Vec3 heading, Vec3 centre, double keep, double range) {
        Vec3 to = centre.subtract(pos);
        double d = to.length();
        return d <= range && (d < 1e-6 || angle(heading, to) <= keep);
    }

    /**
     * {@code velocity} turned toward {@code toTarget} by at most {@code maxTurnDeg} degrees, its speed kept: straight
     * onto the target when it is within the turn, else along the arc between the two directions.
     */
    public static Vec3 steer(Vec3 velocity, Vec3 toTarget, double maxTurnDeg) {
        double speed = velocity.length();
        if (speed < 1e-9 || toTarget.lengthSqr() < 1e-12) {
            return velocity;
        }
        Vec3 u = velocity.scale(1.0 / speed);
        Vec3 w = toTarget.normalize();
        double theta = angle(u, w);
        if (theta <= maxTurnDeg) {
            return w.scale(speed);
        }
        Vec3 p = w.subtract(u.scale(u.dot(w)));
        if (p.lengthSqr() < 1e-12) { // straight behind: turn about any axis square to the flight
            p = Math.abs(u.y) < 0.9 ? u.cross(new Vec3(0, 1, 0)) : u.cross(new Vec3(1, 0, 0));
        }
        p = p.normalize();
        double r = Math.toRadians(maxTurnDeg);
        return u.scale(Math.cos(r)).add(p.scale(Math.sin(r))).scale(speed);
    }

    /** {@code look} turned {@code degrees} about the vertical (a fan's side bolts). */
    public static Vec3 fan(Vec3 look, double degrees) {
        double r = Math.toRadians(degrees);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new Vec3(look.x * c + look.z * s, look.y, -look.x * s + look.z * c);
    }
}
