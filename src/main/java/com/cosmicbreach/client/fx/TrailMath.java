package com.cosmicbreach.client.fx;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure parts of a slash trail: which animation ticks of a move draw one, how it fades behind the
 * blade, a smooth curve through the blade's positions frame by frame, and, for a trail drawn on the
 * screen, how far to bow a nearly straight stroke into a crescent and how it fades at the view's edge.
 * Pure, so it is tested.
 */
public final class TrailMath {
    /** A trail reaches this many animation ticks behind the blade. */
    public static final double TRAIL_TICKS = 2.4;
    /** After the swing, the trail's tail catches up with its head over this many game ticks. */
    public static final double FADE_TICKS = 4.0;
    /** A stroke on screen bowed less than this (its deepest point off the chord, over the chord) gets bowed to it. */
    public static final double MIN_BOW = 0.14;
    /** The screen-edge fade: it starts this far out (1 is the edge) or just past the blade, whichever is further out. */
    public static final double EDGE_FADE_FROM = 0.72;
    public static final double EDGE_FADE_OVER = 0.16;

    /** A point on the screen. */
    public record Point2(double x, double y) {
    }

    private TrailMath() {
    }

    /**
     * The animation ticks whose blade draws the trail for a move with this startup and these active
     * ticks: from the last tick of the wind-up, when the blade sets off, to one tick after the active
     * ones, the follow-through. {@code [from, to]}.
     */
    public static double[] window(int startup, int active) {
        return new double[] {Math.max(0, startup - 1), startup + Math.max(1, active) + 1};
    }

    /**
     * How strongly a point {@code age} animation ticks behind the blade shows: 1 at the blade, falling to
     * 0 at {@code length} behind it, a little faster near the tail.
     */
    public static double ageStrength(double age, double length) {
        if (length <= 0 || age >= length) {
            return 0.0;
        }
        double t = 1.0 - Math.max(0.0, age) / length;
        return Math.pow(t, 0.9);
    }

    /**
     * The length the trail fades over: its full length, or less when the swing began more recently
     * than that, so the oldest point always fades to nothing instead of ending in a hard edge.
     */
    public static double fadeLength(double length, double head, double windowStart) {
        return Math.max(1e-3, Math.min(length, head - windowStart));
    }

    /**
     * A smooth curve through {@code points} (centripetal Catmull-Rom, so a fast arc sampled sparsely stays
     * round without loops), about one point per {@code spacing} blocks and at least one per span. The
     * first and last points are kept; returns the parameter (index, fractional) of every point too.
     */
    public static Curve smooth(List<Vec3> points, double spacing) {
        List<Vec3> out = new ArrayList<>();
        List<Double> at = new ArrayList<>();
        int n = points.size();
        if (n == 0) {
            return new Curve(out, at);
        }
        out.add(points.get(0));
        at.add(0.0);
        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = points.get(Math.max(0, i - 1));
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);
            Vec3 p3 = points.get(Math.min(n - 1, i + 2));
            int steps = Math.max(1, (int) Math.ceil(p1.distanceTo(p2) / Math.max(1e-3, spacing)));
            steps = Math.min(steps, 24);
            for (int s = 1; s <= steps; s++) {
                double u = s / (double) steps;
                out.add(s == steps ? p2 : centripetal(p0, p1, p2, p3, u));
                at.add(i + u);
            }
        }
        return new Curve(out, at);
    }

    /** Points along a curve and where each lies between the original points (index plus fraction). */
    public record Curve(List<Vec3> points, List<Double> at) {
    }

    /** The point {@code u} (0 to 1) of the way from {@code p1} to {@code p2} on a centripetal Catmull-Rom spline. */
    public static Vec3 centripetal(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u) {
        double t0 = 0.0;
        double t1 = t0 + knot(p0, p1);
        double t2 = t1 + knot(p1, p2);
        double t3 = t2 + knot(p2, p3);
        if (t2 - t1 < 1e-9) {
            return p1;
        }
        double t = t1 + (t2 - t1) * u;
        Vec3 a1 = mix(p0, p1, t0, t1, t);
        Vec3 a2 = mix(p1, p2, t1, t2, t);
        Vec3 a3 = mix(p2, p3, t2, t3, t);
        Vec3 b1 = mix(a1, a2, t0, t2, t);
        Vec3 b2 = mix(a2, a3, t1, t3, t);
        return mix(b1, b2, t1, t2, t);
    }

    // ------------------------------------------------------------------ on the screen

    /**
     * How far to push each point of a stroke on screen sideways (along the chord's left normal, in the
     * same units) so that the stroke bows by at least {@link #MIN_BOW} of its chord: nothing for a stroke
     * already that curved; otherwise a half sine, zero at both ends, the way it already leans (or to the
     * chord's left when it is straight). Points run from the tail to the blade.
     */
    public static double[] bow(List<Point2> points) {
        int n = points.size();
        double[] out = new double[n];
        if (n < 3) {
            return out;
        }
        Point2 a = points.get(0);
        Point2 b = points.get(n - 1);
        double cx = b.x() - a.x();
        double cy = b.y() - a.y();
        double chord = Math.sqrt(cx * cx + cy * cy);
        if (chord < 1e-6) {
            return out;
        }
        // The chord's left normal, and the point furthest from the chord along it.
        double nx = -cy / chord;
        double ny = cx / chord;
        double deviation = 0.0;
        for (Point2 p : points) {
            double d = (p.x() - a.x()) * nx + (p.y() - a.y()) * ny;
            if (Math.abs(d) > Math.abs(deviation)) {
                deviation = d;
            }
        }
        double have = Math.abs(deviation) / chord;
        if (have >= MIN_BOW) {
            return out;
        }
        double side = have > 0.02 ? Math.signum(deviation) : 1.0;
        double amount = (MIN_BOW - have) * chord * side;
        // Arc length along the stroke, for the half sine.
        double[] s = new double[n];
        for (int i = 1; i < n; i++) {
            Point2 p = points.get(i);
            Point2 q = points.get(i - 1);
            s[i] = s[i - 1] + Math.hypot(p.x() - q.x(), p.y() - q.y());
        }
        double total = s[n - 1];
        if (total < 1e-9) {
            return out;
        }
        for (int i = 0; i < n; i++) {
            out[i] = amount * Math.sin(Math.PI * s[i] / total);
        }
        return out;
    }

    /**
     * How much of a point at {@code edge} (its distance toward the view's edge, 1 being the edge: the
     * larger of |x| over the half width and |y| over the half height) shows, for a trail whose blade is at
     * {@code bladeEdge}: all of it within {@link #EDGE_FADE_FROM} (or just past the blade, if that is
     * further out), then fading to nothing over {@link #EDGE_FADE_OVER}. So a trail ends inside the view
     * unless its blade has left it.
     */
    public static double edgeFade(double edge, double bladeEdge) {
        double from = Math.max(EDGE_FADE_FROM, bladeEdge + 0.02);
        double t = (edge - from) / EDGE_FADE_OVER;
        if (t <= 0) {
            return 1.0;
        }
        if (t >= 1) {
            return 0.0;
        }
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }

    private static double knot(Vec3 a, Vec3 b) {
        return Math.max(1e-6, Math.sqrt(a.distanceTo(b)));
    }

    private static Vec3 mix(Vec3 a, Vec3 b, double ta, double tb, double t) {
        if (tb - ta < 1e-9) {
            return b;
        }
        double w = (t - ta) / (tb - ta);
        return a.scale(1.0 - w).add(b.scale(w));
    }
}
