package com.cosmicbreach.guardian.heliarch;

import java.util.ArrayList;
import java.util.List;

/**
 * Cover from the eclipse (GDD 7.3): the Eclipse Beam and a failed Nova come from the core over the throne, so a player
 * is safe when something that blocks sight stands between the throne's column and them, seen from above: a standing
 * monolith or a Choir Pillar. The beam's wedge sweeps round; each beam that passes a monolith takes one of its
 * integrity pips. Pure: blockers are footprints on the floor plane.
 */
public final class EclipseCover {
    /** Something that blocks sight: a rectangle {x0, z0, x1, z1} or a circle (x, z, radius). */
    public sealed interface Blocker permits Rect, Circle {
        /** True if the segment from (ax, az) to (bx, bz) passes through it. */
        boolean crosses(double ax, double az, double bx, double bz);
    }

    public record Rect(double x0, double z0, double x1, double z1) implements Blocker {
        @Override
        public boolean crosses(double ax, double az, double bx, double bz) {
            // Liang-Barsky clipping of the segment against the box
            double t0 = 0.0;
            double t1 = 1.0;
            double dx = bx - ax;
            double dz = bz - az;
            double[] p = {-dx, dx, -dz, dz};
            double[] q = {ax - x0, x1 - ax, az - z0, z1 - az};
            for (int i = 0; i < 4; i++) {
                if (Math.abs(p[i]) < 1e-12) {
                    if (q[i] < 0) {
                        return false;
                    }
                    continue;
                }
                double r = q[i] / p[i];
                if (p[i] < 0) {
                    t0 = Math.max(t0, r);
                } else {
                    t1 = Math.min(t1, r);
                }
                if (t0 > t1) {
                    return false;
                }
            }
            return true;
        }
    }

    public record Circle(double x, double z, double radius) implements Blocker {
        @Override
        public boolean crosses(double ax, double az, double bx, double bz) {
            double dx = bx - ax;
            double dz = bz - az;
            double len2 = dx * dx + dz * dz;
            double t = len2 < 1e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (z - az) * dz) / len2));
            double nx = ax + dx * t - x;
            double nz = az + dz * t - z;
            return nx * nx + nz * nz <= radius * radius;
        }
    }

    private EclipseCover() {
    }

    /** The blockers standing now: the monoliths whose {@code pips} are above zero, and every pillar. */
    public static List<Blocker> blockers(int[] pips) {
        return blockers(pips, 0);
    }

    /**
     * The blockers standing now: the monoliths whose {@code pips} are above zero, and the pillars still standing (bit
     * {@code i} of {@code pillarsDown} set when pillar {@code i} has fallen with its segment in the Collapse).
     */
    public static List<Blocker> blockers(int[] pips, int pillarsDown) {
        List<Blocker> out = new ArrayList<>();
        for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
            if (pips != null && m.index() < pips.length && pips[m.index()] > 0) {
                double[] r = m.rect();
                out.add(new Rect(r[0], r[1], r[2], r[3]));
            }
        }
        for (int i = 0; i < 8; i++) {
            if ((pillarsDown & (1 << i)) != 0) {
                continue;
            }
            double[] c = HeliarchArena.pillarCircle(i);
            out.add(new Circle(c[0], c[1], c[2]));
        }
        return out;
    }

    /** Only the monoliths (a Nova's cover rule counts the pillars too; see {@link #blockers}). */
    public static List<Blocker> monolithsOnly(int[] pips) {
        List<Blocker> out = new ArrayList<>();
        for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
            if (pips[m.index()] > 0) {
                double[] r = m.rect();
                out.add(new Rect(r[0], r[1], r[2], r[3]));
            }
        }
        return out;
    }

    /** True if a player standing at (x, z) is hidden from the core by one of {@code blockers}. */
    public static boolean covered(double x, double z, List<Blocker> blockers) {
        for (Blocker b : blockers) {
            if (b.crosses(HeliarchArena.CX, HeliarchArena.CZ, x, z)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the beam's sweep

    /** The beam's middle, as a compass angle, {@code t} ticks into its sweep (0 to {@link HeliarchMoves#BEAM_SWEEP}). */
    public static double beamAngle(double start, int dir, double t) {
        double u = Math.max(0.0, Math.min(1.0, t / HeliarchMoves.BEAM_SWEEP));
        double a = (start + dir * HeliarchMoves.BEAM_ARC * u) % 360.0;
        return a < 0 ? a + 360.0 : a;
    }

    /**
     * True if a player at (x, z) is inside the beam's wedge {@code t} ticks into the sweep (its edge counts: the
     * wedge grows by the player's half width at their distance). Cover is not considered here.
     */
    public static boolean inWedge(double x, double z, double start, int dir, double t) {
        double r = HeliarchArena.radiusOf(x, z);
        if (r < 1e-6) {
            return true;
        }
        double half = HeliarchMoves.BEAM_WIDTH / 2.0 + Math.toDegrees(Math.atan2(0.3, r));
        return Math.abs(HeliarchArena.wrap(HeliarchArena.angleOf(x, z) - beamAngle(start, dir, t))) <= half;
    }

    /** True if the beam hits a player at (x, z) now: inside the wedge and not covered. */
    public static boolean beamHits(double x, double z, double start, int dir, double t, List<Blocker> blockers) {
        return inWedge(x, z, start, dir, t) && !covered(x, z, blockers);
    }

    /**
     * The monoliths the beam's wedge reaches between {@code t0} (exclusive) and {@code t1} (inclusive) ticks of the
     * sweep: each loses a pip when the beam first touches it (once per beam; the caller remembers which were taken).
     */
    public static List<Integer> monolithsReached(double start, int dir, double t0, double t1) {
        List<Integer> out = new ArrayList<>();
        for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
            double half = HeliarchMoves.BEAM_WIDTH / 2.0 + Math.toDegrees(Math.atan2(1.5, HeliarchArena.MONOLITH_RADIUS));
            boolean before = t0 >= 0 && touched(m.angle(), start, dir, t0, half);
            boolean now = touched(m.angle(), start, dir, t1, half);
            if (now && !before) {
                out.add(m.index());
            }
        }
        return out;
    }

    /** True if the wedge has touched {@code angle} at any point up to {@code t} ticks into the sweep. */
    private static boolean touched(double angle, double start, int dir, double t, double half) {
        double swept = HeliarchMoves.BEAM_ARC * Math.max(0.0, Math.min(1.0, t / HeliarchMoves.BEAM_SWEEP));
        // how far along the sweep the angle lies, measured in the sweep's direction from its start
        double along = dir * HeliarchArena.wrap(angle - start);
        if (along < -half) {
            along += 360.0;
        }
        return along >= -half && along <= swept + half;
    }

    /**
     * A beam's start angle so that its sweep passes the target's angle in its middle third: the leading side is where
     * the rim flares during the warning.
     */
    public static double startFor(double targetAngle, int dir, double jitter) {
        double a = targetAngle - dir * (HeliarchMoves.BEAM_ARC / 2.0 + jitter);
        a %= 360.0;
        return a < 0 ? a + 360.0 : a;
    }
}
