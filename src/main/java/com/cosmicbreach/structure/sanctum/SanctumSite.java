package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.DeepSpans;
import com.cosmicbreach.world.gen.DeepZones;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a world's Breach Sanctum reaches the Deep: which side of the arena its halls lie on and which Deep platform
 * its causeway runs to. Read from the terrain model alone ({@link AetheriaTerrain}), so worldgen, the placement, the
 * debug commands and the tests all agree, and cached per terrain.
 *
 * <p>The choice: among the Deep's pillar platforms (8.5 blocks across or more) within 70 degrees of either halls'
 * axis, the causeway's end sits 4 blocks inside the platform's rim on the line toward the forecourt; it must stay
 * clear of every other pillar and needle, climb or fall no more than {@link #MAX_SLOPE} a block (so a slab ramp
 * stays walkable) and keep the whole structure within 8 chunks of its start chunk. The shortest such causeway wins
 * (steepness counts against it).
 */
public final class SanctumSite {
    public static final double MIN_PLATFORM = 8.5;
    public static final double MAX_SLOPE = 0.42;
    public static final double MAX_TURN_COS = Math.cos(Math.toRadians(70.0));
    /** The causeway ends this far inside the platform's rim. */
    public static final double INSET = 4.0;
    private static final ConcurrentHashMap<Long, SanctumSite> CACHE = new ConcurrentHashMap<>();

    public final int side;
    public final int endX;
    public final int endY;
    public final int endZ;
    /** True if no platform met every rule and the fallback was taken (the tests require this never happens). */
    public final boolean fallback;

    SanctumSite(int side, int endX, int endY, int endZ, boolean fallback) {
        this.side = side;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.fallback = fallback;
    }

    /** The site for a terrain (cached). */
    public static SanctumSite of(AetheriaTerrain t) {
        return CACHE.computeIfAbsent(t.salt, s -> choose(t));
    }

    /** The layout this site builds, its puzzles drawn from {@code seed}. */
    public SanctumLayout layout(long seed) {
        return new SanctumLayout(side, endX, endY, endZ, seed);
    }

    static SanctumSite choose(AetheriaTerrain t) {
        SanctumSite best = null;
        double bestScore = Double.MAX_VALUE;
        for (int ci = -6; ci <= 5; ci++) {
            for (int cj = -6; cj <= 5; cj++) {
                DeepSpans.Pillar p = t.deep.pillar(ci, cj);
                // the Spans' platforms only (the zone around the Breach): their shapes are what the causeway is built for
                if (!p.exists || p.platformR < MIN_PLATFORM || p.zone != DeepZones.SPANS) {
                    continue;
                }
                for (int side : new int[] {-1, 1}) {
                    SanctumSite s = candidate(t, p, side);
                    if (s == null) {
                        continue;
                    }
                    SanctumLayout l = s.layout(0L);
                    double score = l.causewayLength() + 60.0 * l.causewaySlope();
                    if (score < bestScore) {
                        bestScore = score;
                        best = s;
                    }
                }
            }
        }
        return best != null ? best : new SanctumSite(-1, 0, SanctumLayout.HALL_Y, -170, true);
    }

    /** The causeway from the forecourt on {@code side} to platform {@code p}, if it keeps every rule. */
    static SanctumSite candidate(AetheriaTerrain t, DeepSpans.Pillar p, int side) {
        double fx = 0.5;
        double fz = side * SanctumLayout.COURT_D1 + 0.5;
        double dx = p.cx - fx;
        double dz = p.cz - fz;
        double len = Math.hypot(dx, dz);
        if (len < 1.0 || dz * side / len < MAX_TURN_COS) {
            return null;
        }
        double inset = p.platformR - INSET;
        int ex = (int) Math.floor(p.cx - dx / len * inset);
        int ez = (int) Math.floor(p.cz - dz / len * inset);
        int ey = surface(t, ex, ez, (int) Math.ceil(p.top) + 3);
        if (ey < 0) {
            return null;
        }
        SanctumSite s = new SanctumSite(side, ex, ey, ez, false);
        SanctumLayout l = s.layout(0L);
        if (l.causewaySlope() > MAX_SLOPE || !l.fitsReferenceRange()) {
            return null;
        }
        return clear(t, p, fx, fz, ex + 0.5, ez + 0.5) ? s : null;
    }

    /** True if no other pillar and no needle stands within reach of the causeway's line. */
    static boolean clear(AetheriaTerrain t, DeepSpans.Pillar home, double ax, double az, double bx, double bz) {
        int c0 = (int) Math.floor(Math.min(ax, bx) / DeepSpans.CELL) - 2;
        int c1 = (int) Math.floor(Math.max(ax, bx) / DeepSpans.CELL) + 2;
        int d0 = (int) Math.floor(Math.min(az, bz) / DeepSpans.CELL) - 2;
        int d1 = (int) Math.floor(Math.max(az, bz) / DeepSpans.CELL) + 2;
        for (int ci = c0; ci <= c1; ci++) {
            for (int cj = d0; cj <= d1; cj++) {
                DeepSpans.Pillar q = t.deep.pillar(ci, cj);
                if (q.exists && !(q.ci == home.ci && q.cj == home.cj)) {
                    double reach = Math.max(q.platformR, q.shaftR) + 3.0 + SanctumLayout.CAUSEWAY_HALF + 2.0;
                    if (distanceToSegment(q.cx, q.cz, ax, az, bx, bz) < reach) {
                        return false;
                    }
                }
                for (DeepSpans.Needle n : q.needles) {
                    if (distanceToSegment(n.x, n.z, ax, az, bx, bz) < n.rmax + SanctumLayout.CAUSEWAY_HALF + 4.0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double l2 = vx * vx + vz * vz;
        double t = l2 < 1e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - ax) * vx + (pz - az) * vz) / l2));
        return Math.hypot(px - (ax + t * vx), pz - (az + t * vz));
    }

    /** The first air block over rock at column (x, z), searching down from {@code fromY} over 16 blocks, or -1. */
    static int surface(AetheriaTerrain t, int x, int z, int fromY) {
        AetheriaTerrain.Column col = new AetheriaTerrain.Column();
        t.sampleColumn(x, z, col);
        for (int y = fromY; y > fromY - 16; y--) {
            if (t.blocks(col, x, y, z) > 0) {
                return y + 1;
            }
        }
        return -1;
    }
}
