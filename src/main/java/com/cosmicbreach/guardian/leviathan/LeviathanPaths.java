package com.cosmicbreach.guardian.leviathan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The paths the Leviathan's head swims off its orbit (Thalassine Leviathan design v1), each a {@link Polyline} through a
 * few control points round the lair's centre, and the orbit angle where it rejoins the orbit (NaN when it doesn't).
 * Pure: the server swims them, the client draws the dust wake from the same points.
 */
public final class LeviathanPaths {
    /** A path and the orbit angle it ends on (NaN: it ends off the orbit). */
    public record Swim(Polyline path, double endAngle) {
        public boolean endsOnOrbit() {
            return !Double.isNaN(endAngle);
        }
    }

    private LeviathanPaths() {
    }

    static Vec3 polar(Vec3 centre, double a, double r, double y) {
        return new Vec3(centre.x + r * Math.cos(a), y, centre.z + r * Math.sin(a));
    }

    private static Vec3 orbit(Vec3 centre, double a, double wave) {
        return LeviathanOrbit.point(centre, a, wave);
    }

    /** The horizontal distance of {@code p} from the centre's axis. */
    public static double radius(Vec3 centre, Vec3 p) {
        return Math.hypot(p.x - centre.x, p.z - centre.z);
    }

    /** How high over the target's feet the dive's head passes: its body skims the platform, clear of the rock. */
    public static final double DIVE_OVER = 2.3;

    /**
     * The Breach Dive: from the orbit at {@code startAngle} out along its swimming direction through where the target
     * stands (its head's middle {@value #DIVE_OVER} over the target's feet, so the whole body passes through, skimming the
     * platform), level on out past the platform's outer rim ({@code clearOut} from the axis), then down under the
     * platforms in a loop and back up into the orbit a little under a radian further on.
     */
    public static Swim dive(Vec3 centre, double wave, double startAngle, Vec3 targetFeet, double clearOut) {
        double aT = startAngle + LeviathanOrbit.ahead(startAngle, LeviathanOrbit.angleOf(centre, targetFeet));
        double rT = Math.max(24.0, Math.min(44.0, radius(centre, targetFeet)));
        double yT = targetFeet.y + DIVE_OVER;
        double rOut = Math.max(rT + 5.0, clearOut);
        Vec3 start = orbit(centre, startAngle, wave);
        List<Vec3> c = new ArrayList<>();
        c.add(orbit(centre, startAngle - 0.18, wave));
        c.add(start);
        c.add(polar(centre, (startAngle + aT) / 2.0, (LeviathanMoves.ORBIT_RADIUS + rT) / 2.0, start.y + (yT - start.y) * 0.55));
        c.add(polar(centre, aT, rT, yT));
        c.add(polar(centre, aT + 0.1, (rT + rOut) / 2.0, yT + 0.3));
        c.add(polar(centre, aT + 0.2, rOut, yT));
        c.add(polar(centre, aT + 0.34, rOut + 4.0, yT - 9.0));
        c.add(polar(centre, aT + 0.62, 27.0, yT - 17.0));
        double end = aT + 1.0;
        c.add(orbit(centre, end, wave));
        c.add(orbit(centre, end + 0.18, wave));
        return new Swim(Polyline.catmullRom(c, 12), end);
    }

    /** The target's angle ahead of {@code headAngle} along the orbit, in [0, 2 pi). */
    public static double targetAhead(Vec3 centre, double headAngle, Vec3 target) {
        return LeviathanOrbit.ahead(headAngle, LeviathanOrbit.angleOf(centre, target));
    }

    /** True if a dive may start at a target that far ahead (so it never cuts across the core). */
    public static boolean diveWindow(double ahead) {
        return ahead >= LeviathanMoves.DIVE_AHEAD_MIN && ahead <= LeviathanMoves.DIVE_AHEAD_MAX;
    }

    /**
     * Its rise from sleep: from where its head rests on the bowl floor one turn up round the central asteroid, widening
     * from the floor ring to the orbit, into the orbit.
     */
    public static Swim rise(Vec3 centre, double wave, Vec3 sleepingHead, double sleepAngle) {
        double r0 = radius(centre, sleepingHead);
        List<Vec3> c = new ArrayList<>();
        c.add(polar(centre, sleepAngle - 0.3, r0, sleepingHead.y));
        for (int k = 0; k <= 8; k++) {
            double f = k / 8.0;
            double a = sleepAngle + f * 2.0 * Math.PI;
            double e = f * f * (3.0 - 2.0 * f);
            double r = r0 + (LeviathanMoves.ORBIT_RADIUS - r0) * Math.min(1.0, f * 1.4);
            double y = sleepingHead.y + (orbit(centre, a, wave).y - sleepingHead.y) * e;
            c.add(k == 8 ? orbit(centre, a, wave) : polar(centre, a, r, y));
        }
        double end = sleepAngle + 2.0 * Math.PI;
        c.add(orbit(centre, end + 0.25, wave));
        return new Swim(Polyline.catmullRom(c, 10), end);
    }

    /** Where a sleeping Leviathan's body lies: a ring on the bowl floor, head first, {@code length} blocks of it. */
    public static List<Vec3> sleepingBody(Vec3 centre, double headAngle, double radius, double y, double length) {
        List<Vec3> out = new ArrayList<>();
        int n = (int) Math.ceil(length / 0.5);
        for (int i = 0; i <= n; i++) {
            double a = headAngle - i * 0.5 / radius;
            out.add(polar(centre, a, radius, y));
        }
        return out;
    }

    /**
     * The swim into a Moorage: on round its orbit (so it passes the platforms on the way) until it can turn in and lay the
     * middle of its coil at {@code coilMid} (an angle round the axis: the platform its target stands on, so that platform's
     * bridge lands mid-coil), then spiralling in to the coil round the central asteroid and along it far enough that the
     * whole body lies on the coil. The coil's spine sinks toward the tail so its back is level ({@link #coilSpineY}), the
     * walkable ring. Ends off the orbit, its head resting at {@link #coilHeadAngle}.
     */
    public static Swim moorage(Vec3 centre, double wave, Vec3 head, double headAngle, double coilMid) {
        List<Vec3> c = new ArrayList<>();
        Vec3 back = polar(centre, headAngle - 0.2, radius(centre, head), head.y);
        c.add(back);
        c.add(head);
        double arc = coilArc();
        double coilStart = coilStart(headAngle, coilMid);
        for (double a = headAngle + 0.5; a < coilStart - 0.9; a += 0.5) {
            c.add(LeviathanOrbit.point(centre, a, wave));
        }
        double a0 = coilStart - 0.45;
        c.add(polar(centre, a0, (radius(centre, head) + LeviathanMoves.COIL_RADIUS) / 2.0, centre.y + 2.0));
        int steps = 10;
        for (int k = 0; k <= steps; k++) {
            double a = coilStart + arc * k / steps;
            double behindHead = (arc - arc * k / steps) * LeviathanMoves.COIL_RADIUS;
            c.add(polar(centre, a, LeviathanMoves.COIL_RADIUS, coilSpineY(centre, behindHead)));
        }
        c.add(polar(centre, coilStart + arc + 0.15, LeviathanMoves.COIL_RADIUS, coilSpineY(centre, 0)));
        return new Swim(Polyline.catmullRom(c, 10), Double.NaN);
    }

    /** The arc of the coil the head swims along: long enough for the whole body (the tail's fan included). */
    public static double coilArc() {
        return (LeviathanMoves.FOLLOW[LeviathanMoves.FOLLOW.length - 1] + 8.0) / LeviathanMoves.COIL_RADIUS;
    }

    /** Where the head joins the coil: the first angle at least 0.9 ahead of {@code headAngle} that puts the coil's middle at {@code coilMid}. */
    public static double coilStart(double headAngle, double coilMid) {
        double wanted = coilMid - (coilArc() - LeviathanMoves.FOLLOW[1] / LeviathanMoves.COIL_RADIUS);
        return headAngle + 0.9 + LeviathanOrbit.ahead(headAngle + 0.9, wanted);
    }

    /** The angle the head rests at in a Moorage started at {@code headAngle} to coil round {@code coilMid}. */
    public static double coilHeadAngle(double headAngle, double coilMid) {
        return coilStart(headAngle, coilMid) + coilArc();
    }

    /** The coil's spine height {@code behindHead} blocks of body behind the head: its back is level at the coil top. */
    public static double coilSpineY(Vec3 centre, double behindHead) {
        return Math.floor(centre.y + LeviathanMoves.COIL_TOP) - halfHeight(behindHead);
    }

    /** Half the body's height {@code behindHead} blocks behind the head's middle (the model's round cross-section). */
    public static double halfHeight(double behindHead) {
        float[] h = LeviathanMoves.PART_HEIGHT;
        double[] half = new double[h.length];
        for (int i = 0; i < h.length; i++) {
            half[i] = h[i] / 2.0;
        }
        return alongBody(half, behindHead);
    }

    /** Half the body's width {@code behindHead} blocks behind the head's middle (it tapers to the tail). */
    public static double halfWidth(double behindHead) {
        return alongBody(LeviathanMoves.HALF_WIDTH, behindHead);
    }

    /** {@code values} (one at the head's middle and one at each follower's) read {@code behindHead} blocks along the body. */
    private static double alongBody(double[] values, double behindHead) {
        double[] at = {0.0, LeviathanMoves.FOLLOW[0], LeviathanMoves.FOLLOW[1], LeviathanMoves.FOLLOW[2], LeviathanMoves.FOLLOW[3],
                LeviathanMoves.FOLLOW[4]};
        if (behindHead <= 0) {
            return values[0];
        }
        for (int i = 1; i < at.length; i++) {
            if (behindHead <= at[i]) {
                double f = (behindHead - at[i - 1]) / (at[i] - at[i - 1]);
                return values[i - 1] + (values[i] - values[i - 1]) * f;
            }
        }
        return values[values.length - 1];
    }

    /** Tearing free: from the coil out and up into the orbit ahead. */
    public static Swim tearFree(Vec3 centre, double wave, Vec3 head, double headAngle) {
        List<Vec3> c = new ArrayList<>();
        c.add(polar(centre, headAngle - 0.2, radius(centre, head), head.y));
        c.add(head);
        c.add(polar(centre, headAngle + 0.4, (LeviathanMoves.COIL_RADIUS + LeviathanMoves.ORBIT_RADIUS) / 2.0, head.y + 2.0));
        double end = headAngle + 0.9;
        c.add(orbit(centre, end, wave));
        c.add(orbit(centre, end + 0.2, wave));
        return new Swim(Polyline.catmullRom(c, 10), end);
    }

    /** A Break: it drifts nose-down, its head sinking level with the nearest platform ({@code platformTop} its top). */
    public static Swim droop(Vec3 centre, Vec3 head, double headAngle, double platformTop) {
        List<Vec3> c = new ArrayList<>();
        c.add(polar(centre, headAngle - 0.15, radius(centre, head), head.y + 0.5));
        c.add(head);
        c.add(polar(centre, headAngle + 0.12, 21.6, (head.y + platformTop + 1.4) / 2.0 - 0.5));
        c.add(polar(centre, headAngle + 0.22, 21.9, platformTop + 1.4));
        c.add(polar(centre, headAngle + 0.26, 22.0, platformTop + 1.2));
        return new Swim(Polyline.catmullRom(c, 10), Double.NaN);
    }

    /** Back up from a Break into the orbit. */
    public static Swim recover(Vec3 centre, double wave, Vec3 head, double headAngle) {
        List<Vec3> c = new ArrayList<>();
        c.add(polar(centre, headAngle - 0.1, radius(centre, head), head.y - 0.4));
        c.add(head);
        double end = headAngle + 0.45;
        c.add(orbit(centre, end, wave));
        c.add(orbit(centre, end + 0.2, wave));
        return new Swim(Polyline.catmullRom(c, 10), end);
    }

    /** Its death: it goes still and sinks straight down onto the lower shell ({@code floorY} under the head). */
    public static Swim sink(Vec3 head, Vec3 facing, double floorY) {
        List<Vec3> c = new ArrayList<>();
        Vec3 flat = new Vec3(facing.x, 0, facing.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        c.add(head.subtract(flat.scale(0.5)));
        c.add(head);
        double drop = Math.max(2.0, head.y - floorY);
        c.add(head.add(flat.scale(1.5)).add(0, -drop * 0.35, 0));
        c.add(head.add(flat.scale(2.5)).add(0, -drop * 0.8, 0));
        c.add(new Vec3(head.x + flat.x * 3.0, floorY, head.z + flat.z * 3.0));
        c.add(new Vec3(head.x + flat.x * 3.5, floorY - 0.2, head.z + flat.z * 3.5));
        return new Swim(Polyline.catmullRom(c, 10), Double.NaN);
    }
}
