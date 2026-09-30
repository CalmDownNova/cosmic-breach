package com.cosmicbreach.guardian.leviathan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * A path the Leviathan's head swims (a dive, the rise from its sleep, the swim into a Moorage, the sink at its death), as
 * points with their running length so it can be walked at a speed. Built from control points with a Catmull-Rom spline
 * (it passes through every control point). Pure and immutable; the same on both sides for the same points.
 */
public final class Polyline {
    private final Vec3[] points;
    private final double[] lengths;

    public Polyline(List<Vec3> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a path needs a point");
        }
        this.points = points.toArray(new Vec3[0]);
        this.lengths = new double[this.points.length];
        for (int i = 1; i < this.points.length; i++) {
            lengths[i] = lengths[i - 1] + this.points[i].distanceTo(this.points[i - 1]);
        }
    }

    /**
     * A Catmull-Rom spline through {@code control}, from the second point to the second to last (the first and last only
     * shape the ends), sampled every span into {@code perSpan} pieces.
     */
    public static Polyline catmullRom(List<Vec3> control, int perSpan) {
        if (control.size() < 4) {
            throw new IllegalArgumentException("a spline needs 4 control points, got " + control.size());
        }
        List<Vec3> out = new ArrayList<>();
        for (int i = 1; i < control.size() - 2; i++) {
            Vec3 p0 = control.get(i - 1);
            Vec3 p1 = control.get(i);
            Vec3 p2 = control.get(i + 1);
            Vec3 p3 = control.get(i + 2);
            for (int k = 0; k < perSpan; k++) {
                out.add(spline(p0, p1, p2, p3, k / (double) perSpan));
            }
        }
        out.add(control.get(control.size() - 2));
        return new Polyline(out);
    }

    private static Vec3 spline(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(cr(p0.x, p1.x, p2.x, p3.x, t, t2, t3), cr(p0.y, p1.y, p2.y, p3.y, t, t2, t3), cr(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    private static double cr(double a, double b, double c, double d, double t, double t2, double t3) {
        return 0.5 * (2.0 * b + (-a + c) * t + (2.0 * a - 5.0 * b + 4.0 * c - d) * t2 + (-a + 3.0 * b - 3.0 * c + d) * t3);
    }

    public double length() {
        return lengths[lengths.length - 1];
    }

    public int size() {
        return points.length;
    }

    public Vec3 point(int i) {
        return points[i];
    }

    public Vec3 start() {
        return points[0];
    }

    public Vec3 end() {
        return points[points.length - 1];
    }

    /** The point {@code s} blocks along the path (clamped to its ends). */
    public Vec3 at(double s) {
        if (s <= 0 || points.length == 1) {
            return points[0];
        }
        if (s >= length()) {
            return end();
        }
        int lo = 0;
        int hi = lengths.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (lengths[mid] <= s) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double span = lengths[hi] - lengths[lo];
        double f = span < 1e-9 ? 0.0 : (s - lengths[lo]) / span;
        return points[lo].add(points[hi].subtract(points[lo]).scale(f));
    }

    /** The unit direction of the path at {@code s}. */
    public Vec3 direction(double s) {
        double a = Math.max(0.0, Math.min(length() - 0.5, s - 0.25));
        Vec3 d = at(a + 0.5).subtract(at(a));
        return d.lengthSqr() < 1e-12 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** The points, for the payload that shows the path to clients. */
    public List<Vec3> points() {
        return List.of(points);
    }
}
