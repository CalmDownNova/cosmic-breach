package com.cosmicbreach.world.gen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The Deep's Rift Abyss (GDD 2.2): black basalt rising out of nothing. Three kinds of rock, all over the
 * void, none within the Breach's 150-block radius:
 *
 * <ul>
 *   <li><b>Pillars</b> on a jittered grid ({@link #CELL}): a flat platform (8 to 21 blocks in radius, one in
 *       five or six a broad mesa up to 34) at Y 78 to 136, flaring down into a fluted shaft (columnar
 *       basalt) that rises out of the dark: nearly straight, leaning a little, its foot breaking up into
 *       shards a few blocks above the void.</li>
 *   <li><b>Needles</b>: thorns rising out of the void, widest low down and sharp at the top (Y 85 to 141),
 *       standing alone between pillars.</li>
 *   <li><b>Spans</b> between neighbouring pillars (nearest neighbours always, others half the time):
 *       high arches (tubes along a raised curve) or flat walkable bridges with arched undersides.</li>
 * </ul>
 * Rift Scars are long cracks where a ridged noise is thin: they cut clean through platforms and bridges,
 * down to the void.
 *
 * <p>Knobs: {@link #CELL}, the pillar and needle parameters in their constructors, the span odds in
 * {@link #spans}, the scar width in {@link #scar}.
 */
public final class DeepSpans {
    public static final double CELL = 64.0;
    public static final int CEIL_Y = 143;
    private static final int TAG_PILLAR = 31;
    private static final int TAG_NEEDLE = 32;
    private static final int TAG_SPAN = 33;

    private final long salt;
    private final SeededNoise heightNoise;
    private final SeededNoise rock;
    private final SeededNoise topRelief;
    private final SeededNoise scarNoise;
    private final SeededNoise scarRegion;
    private final ThreadLocal<Long2ObjectOpenHashMap<Pillar>> pillars = ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);

    public DeepSpans(long salt) {
        this.salt = salt;
        this.heightNoise = new SeededNoise(salt + 301);
        this.rock = new SeededNoise(salt + 302);
        this.topRelief = new SeededNoise(salt + 303);
        this.scarNoise = new SeededNoise(salt + 304);
        this.scarRegion = new SeededNoise(salt + 305);
    }

    /** A pillar cell: maybe a pillar, its needles and its spans. */
    public final class Pillar {
        public final int ci;
        public final int cj;
        public final boolean exists;
        public final double cx;
        public final double cz;
        public final double top;
        public final double platformR;
        public final double capThick;
        public final double shaftR;
        public final double flare;
        public final double bottom;
        public final double leanX;
        public final double leanZ;
        final int flutes;
        final double fluteAmp;
        public final Needle[] needles;
        private volatile Span[] spans;

        Pillar(int ci, int cj) {
            this.ci = ci;
            this.cj = cj;
            long h = Hashing.hash(salt, TAG_PILLAR, ci, cj, 0);
            this.cx = (ci + 0.5 + (Hashing.unit(h, 0) * 2 - 1) * 0.28) * CELL;
            this.cz = (cj + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * 0.28) * CELL;
            boolean mesa = Hashing.unit(h, 2) < 0.18;
            this.platformR = mesa ? 20.0 + 14.0 * Hashing.unit(h, 3) : 8.0 + 13.0 * Math.pow(Hashing.unit(h, 3), 1.2);
            this.top = TMath.clamp(104.0 + 20.0 * heightNoise.at(cx / 360.0, cz / 360.0) + 12.0 * (Hashing.unit(h, 4) - 0.5), 78, 136);
            this.capThick = 3.0 + 4.0 * Hashing.unit(h, 5) + platformR * 0.12;
            this.shaftR = Math.max(3.0, platformR * (0.32 + 0.2 * Hashing.unit(h, 6)));
            this.flare = capThick + 4.0 + 0.45 * platformR;
            this.bottom = 2.0 + 16.0 * Hashing.unit(h, 7);
            this.leanX = (Hashing.unit(h, 8) - 0.5) * 0.16;
            this.leanZ = (Hashing.unit(h, 9) - 0.5) * 0.16;
            this.flutes = 7 + (int) (Hashing.unit(h, 10) * 5);
            this.fluteAmp = 0.5 + 0.6 * Hashing.unit(h, 11);
            double reach = Math.hypot(cx, cz);
            this.exists = Hashing.unit(h, 12) < 0.8 && reach > BreachShape.DEEP_RADIUS + platformR + 10;
            this.needles = buildNeedles(ci, cj, this);
        }

        /** The spans from this pillar to its neighbours. */
        public Span[] spans() {
            Span[] s = spans;
            if (s == null) {
                s = computeSpans(this);
                spans = s;
            }
            return s;
        }

        /** How far below the platform the shaft's lean has moved the axis at y. */
        double axisX(double y) {
            double below = Math.max(0, top - capThick - y);
            return cx + leanX * below;
        }

        double axisZ(double y) {
            double below = Math.max(0, top - capThick - y);
            return cz + leanZ * below;
        }

        /** Horizontal reach of this pillar's rock around its (leaning) axis. */
        double bound() {
            return Math.max(platformR, shaftR) + 3.0;
        }
    }

    /** A spindle of basalt, pointed at both ends. */
    public static final class Needle {
        public final double x;
        public final double z;
        public final double bottom;
        public final double top;
        public final double rmax;
        final double leanX;
        final double leanZ;

        Needle(double x, double z, double bottom, double top, double rmax, double leanX, double leanZ) {
            this.x = x;
            this.z = z;
            this.bottom = bottom;
            this.top = top;
            this.rmax = rmax;
            this.leanX = leanX;
            this.leanZ = leanZ;
        }

        double axisX(double y) {
            return x + leanX * (y - bottom);
        }

        double axisZ(double y) {
            return z + leanZ * (y - bottom);
        }
    }

    /** An arch or a bridge between two pillars: a quadratic curve from rim to rim, sampled as a polyline. */
    public static final class Span {
        static final int SEGMENTS = 12;
        public final boolean bridge;
        final double[] px = new double[SEGMENTS + 1];
        final double[] py = new double[SEGMENTS + 1];
        final double[] pz = new double[SEGMENTS + 1];
        final double radius;
        final double minX, maxX, minZ, maxZ, minY, maxY;

        Span(double ax, double ay, double az, double bx, double by, double bz, double rise, double bow, boolean bridge, double radius) {
            this.bridge = bridge;
            this.radius = radius;
            // the control point: raised by twice the rise, and pushed sideways so the span bows in plan
            double len = Math.hypot(bx - ax, bz - az);
            double mx = (ax + bx) / 2 - (bz - az) / len * bow * len;
            double mz = (az + bz) / 2 + (bx - ax) / len * bow * len;
            double my = (ay + by) / 2 + 2 * rise;
            double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            double y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (int i = 0; i <= SEGMENTS; i++) {
                double t = i / (double) SEGMENTS;
                double u = 1 - t;
                px[i] = u * u * ax + 2 * u * t * mx + t * t * bx;
                py[i] = u * u * ay + 2 * u * t * my + t * t * by;
                pz[i] = u * u * az + 2 * u * t * mz + t * t * bz;
                x0 = Math.min(x0, px[i]);
                x1 = Math.max(x1, px[i]);
                z0 = Math.min(z0, pz[i]);
                z1 = Math.max(z1, pz[i]);
                y0 = Math.min(y0, py[i]);
                y1 = Math.max(y1, py[i]);
            }
            double pad = radius * 1.8 + 3;
            this.minX = x0 - pad;
            this.maxX = x1 + pad;
            this.minZ = z0 - pad;
            this.maxZ = z1 + pad;
            this.minY = y0 - pad - 4;
            this.maxY = y1 + pad;
        }
    }

    private Needle[] buildNeedles(int ci, int cj, Pillar pillar) {
        long h = Hashing.hash(salt, TAG_NEEDLE, ci, cj, 0);
        double u = Hashing.unit(h, 0);
        int count = u < 0.45 ? 0 : (u < 0.85 ? 1 : 2);
        List<Needle> out = new ArrayList<>(2);
        for (int n = 0; n < count; n++) {
            double x = (ci + 0.1 + 0.8 * Hashing.unit(h, 1 + n * 8)) * CELL;
            double z = (cj + 0.1 + 0.8 * Hashing.unit(h, 2 + n * 8)) * CELL;
            if (Math.hypot(x - pillar.cx, z - pillar.cz) < pillar.platformR + 8 || Math.hypot(x, z) < BreachShape.DEEP_RADIUS + 10) {
                continue;
            }
            double bottom = 2.0 + 20.0 * Hashing.unit(h, 3 + n * 8);
            double top = Math.min(141.0, 85.0 + 56.0 * Hashing.unit(h, 4 + n * 8));
            double rmax = 2.5 + 3.5 * Hashing.unit(h, 5 + n * 8);
            out.add(new Needle(x, z, bottom, top, rmax, (Hashing.unit(h, 6 + n * 8) - 0.5) * 0.18,
                    (Hashing.unit(h, 7 + n * 8) - 0.5) * 0.18));
        }
        return out.toArray(new Needle[0]);
    }

    public Pillar pillar(int ci, int cj) {
        Long2ObjectOpenHashMap<Pillar> map = pillars.get();
        long key = ReachIslands.pack(ci, cj);
        Pillar p = map.get(key);
        if (p == null) {
            if (map.size() > 4096) {
                map.clear();
            }
            p = new Pillar(ci, cj);
            map.put(key, p);
        }
        return p;
    }

    private Span[] computeSpans(Pillar a) {
        if (!a.exists) {
            return new Span[0];
        }
        // nearest existing neighbour of a pillar, among the 5x5 around it
        Pillar nearestA = nearestNeighbour(a);
        List<Span> out = new ArrayList<>(4);
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                if (di == 0 && dj == 0) {
                    continue;
                }
                Pillar b = pillar(a.ci + di, a.cj + dj);
                if (!b.exists) {
                    continue;
                }
                double len = Math.hypot(b.cx - a.cx, b.cz - a.cz);
                double gap = len - a.platformR - b.platformR;
                if (gap > 70 || gap < 6 || !gabriel(a, b)) {
                    continue;
                }
                boolean swap = a.ci > b.ci || (a.ci == b.ci && a.cj > b.cj);
                Pillar p = swap ? b : a;
                Pillar q = swap ? a : b;
                long h = Hashing.hash(salt, TAG_SPAN, p.ci, p.cj, (q.ci - p.ci) * 7 + (q.cj - p.cj));
                boolean forced = nearestA == b || nearestNeighbour(b) == a;
                if (!forced && Hashing.unit(h, 0) >= 0.22) {
                    continue;
                }
                out.add(span(p, q, h));
            }
        }
        return out.toArray(new Span[0]);
    }

    /** One span between p and q, the same whichever pillar builds it. */
    private Span span(Pillar p, Pillar q, long h) {
        double dx = q.cx - p.cx;
        double dz = q.cz - p.cz;
        double len = Math.hypot(dx, dz);
        dx /= len;
        dz /= len;
        boolean bridge = Hashing.unit(h, 1) < 0.45;
        double ay = p.top - p.capThick * (bridge ? 0.3 : 0.6);
        double by = q.top - q.capThick * (bridge ? 0.3 : 0.6);
        double ax = p.cx + dx * (p.platformR - 2.5);
        double az = p.cz + dz * (p.platformR - 2.5);
        double bx = q.cx - dx * (q.platformR - 2.5);
        double bz = q.cz - dz * (q.platformR - 2.5);
        double rise = bridge ? 1.0 * Hashing.unit(h, 2) : 8.0 + 16.0 * Hashing.unit(h, 2);
        double radius = bridge ? 3.2 + 1.4 * Hashing.unit(h, 3) : 2.2 + 1.6 * Hashing.unit(h, 3);
        double bow = (Hashing.unit(h, 4) - 0.5) * 0.4;
        return new Span(ax, ay, az, bx, by, bz, rise, bow, bridge, radius);
    }

    private @Nullable Pillar nearestNeighbour(Pillar a) {
        Pillar best = null;
        double bestD = Double.MAX_VALUE;
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                if (di == 0 && dj == 0) {
                    continue;
                }
                Pillar b = pillar(a.ci + di, a.cj + dj);
                if (b.exists) {
                    double d = Math.hypot(b.cx - a.cx, b.cz - a.cz);
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    private boolean gabriel(Pillar a, Pillar b) {
        double mx = (a.cx + b.cx) / 2;
        double mz = (a.cz + b.cz) / 2;
        double r2 = (TMath.sq(a.cx - b.cx) + TMath.sq(a.cz - b.cz)) / 4;
        int mi = (int) Math.floor(mx / CELL);
        int mj = (int) Math.floor(mz / CELL);
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                Pillar c = pillar(mi + di, mj + dj);
                if (c == a || c == b || !c.exists) {
                    continue;
                }
                if (TMath.sq(c.cx - mx) + TMath.sq(c.cz - mz) < r2) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ columns

    /** One column's Deep data: the rock that may reach it. Mutable, reused. */
    public static final class Column {
        final List<Pillar> pillars = new ArrayList<>(4);
        final List<Needle> needles = new ArrayList<>(4);
        final List<Span> spans = new ArrayList<>(6);
        public boolean scar;
        public int minY;
        public int maxY;

        void clear() {
            pillars.clear();
            needles.clear();
            spans.clear();
            scar = false;
            minY = Integer.MAX_VALUE;
            maxY = Integer.MIN_VALUE;
        }

        public boolean empty() {
            return pillars.isEmpty() && needles.isEmpty() && spans.isEmpty();
        }
    }

    /** True where a Rift Scar runs: a long crack through platforms and bridges. */
    public boolean scar(double x, double z) {
        if (scarRegion.at(x / 170.0, z / 170.0) < -0.15) {
            return false;
        }
        double n = scarNoise.at(x / 38.0, z / 38.0);
        double width = 0.045 + 0.025 * scarRegion.at(x / 23.0 + 70.0, z / 23.0);
        return Math.abs(n) < width;
    }

    public void sample(int x, int z, Column out) {
        out.clear();
        double px = x + 0.5;
        double pz = z + 0.5;
        int ci = (int) Math.floor(px / CELL);
        int cj = (int) Math.floor(pz / CELL);
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                Pillar p = pillar(ci + di, cj + dj);
                if (p.exists) {
                    double lean = Math.hypot(p.leanX, p.leanZ) * (p.top - p.bottom);
                    double b = p.bound() + lean;
                    if (TMath.sq(px - p.cx) + TMath.sq(pz - p.cz) <= b * b) {
                        out.pillars.add(p);
                        out.minY = Math.min(out.minY, (int) Math.floor(p.bottom));
                        out.maxY = Math.max(out.maxY, (int) Math.ceil(p.top + 2));
                    }
                    for (Span s : p.spans()) {
                        if (px >= s.minX && px <= s.maxX && pz >= s.minZ && pz <= s.maxZ && !out.spans.contains(s)) {
                            out.spans.add(s);
                            out.minY = Math.min(out.minY, (int) Math.floor(s.minY));
                            out.maxY = Math.max(out.maxY, (int) Math.ceil(s.maxY));
                        }
                    }
                }
                for (Needle n : p.needles) {
                    double lean = Math.hypot(n.leanX, n.leanZ) * (n.top - n.bottom);
                    double b = n.rmax + 2.0 + lean;
                    if (TMath.sq(px - n.x) + TMath.sq(pz - n.z) <= b * b) {
                        out.needles.add(n);
                        out.minY = Math.min(out.minY, (int) Math.floor(n.bottom));
                        out.maxY = Math.max(out.maxY, (int) Math.ceil(n.top));
                    }
                }
            }
        }
        // spans reach up to two cells: also look at the ring around the 3x3
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                if (Math.abs(di) < 2 && Math.abs(dj) < 2) {
                    continue;
                }
                Pillar p = pillar(ci + di, cj + dj);
                if (!p.exists) {
                    continue;
                }
                for (Span s : p.spans()) {
                    if (px >= s.minX && px <= s.maxX && pz >= s.minZ && pz <= s.maxZ && !out.spans.contains(s)) {
                        out.spans.add(s);
                        out.minY = Math.min(out.minY, (int) Math.floor(s.minY));
                        out.maxY = Math.max(out.maxY, (int) Math.ceil(s.maxY));
                    }
                }
            }
        }
        if (!out.empty()) {
            out.scar = scar(px, pz);
            out.minY = Math.max(out.minY, 1);
            out.maxY = Math.min(out.maxY, CEIL_Y);
        }
    }

    /** Density of the Deep at y in a sampled column, in blocks (positive is rock). */
    public double density(Column c, int x, int y, int z) {
        if (c.empty() || y < c.minY || y > c.maxY) {
            return -8.0;
        }
        double best = -8.0;
        double px = x + 0.5;
        double py = y + 0.5;
        double pz = z + 0.5;
        for (int i = 0, n = c.pillars.size(); i < n; i++) {
            best = Math.max(best, pillarDensity(c.pillars.get(i), c.scar, x, y, z, px, py, pz));
        }
        for (int i = 0, n = c.needles.size(); i < n; i++) {
            Needle nd = c.needles.get(i);
            if (py < nd.bottom || py > nd.top) {
                continue;
            }
            // a thorn rising out of the void: widest low down, a sharp point at the top, a ragged foot
            double t = (py - nd.bottom) / (nd.top - nd.bottom);
            double r = nd.rmax * Math.pow(1.0 - t, 0.9) * TMath.smoothstep(0.0, 0.12, t) + 0.35;
            double rho = Math.hypot(px - nd.axisX(py), pz - nd.axisZ(py));
            if (rho > r + 1.5) {
                continue;
            }
            best = Math.max(best, r - rho + 0.6 * rock.at(x / 5.0, y / 8.0, z / 5.0));
        }
        for (int i = 0, n = c.spans.size(); i < n; i++) {
            Span s = c.spans.get(i);
            if (py < s.minY || py > s.maxY) {
                continue;
            }
            best = Math.max(best, spanDensity(s, c.scar, x, y, z, px, py, pz));
        }
        return best;
    }

    private double pillarDensity(Pillar p, boolean scar, int x, int y, int z, double px, double py, double pz) {
        if (py < p.bottom || py > p.top + 2) {
            return -8.0;
        }
        double capBottom = p.top - p.capThick;
        double ax = p.axisX(py);
        double az = p.axisZ(py);
        double dx = px - ax;
        double dz = pz - az;
        double rho = Math.sqrt(dx * dx + dz * dz);
        if (rho > p.bound() + 2) {
            return -8.0;
        }
        double localTop = p.top + 0.45 * topRelief.at(px / 14.0, pz / 14.0);
        double dTop = localTop - py;
        if (dTop <= 0) {
            return dTop;
        }
        double r;
        boolean cap = py >= capBottom;
        if (cap) {
            r = p.platformR - Math.max(0, py - (localTop - 1.5)) * 0.8;
        } else if (py >= capBottom - p.flare) {
            double t = (py - (capBottom - p.flare)) / p.flare;
            double shaftTop = p.shaftR;
            r = TMath.lerp(shaftTop, p.platformR, Math.pow(t, 2.2));
        } else {
            // a fluted column rising out of the dark: nearly straight, dissolving into shards near its foot
            double t = (py - p.bottom) / Math.max(1.0, capBottom - p.flare - p.bottom);
            r = p.shaftR * (0.82 + 0.18 * TMath.clamp(t, 0, 1));
            double foot = TMath.smoothstep(0.0, 22.0, py - p.bottom);
            if (foot < 1.0) {
                double shards = rock.at(x / 4.0 + 500.0, y / 3.0, z / 4.0);
                r = r * foot + (1.0 - foot) * (shards * p.shaftR - 1.0);
            }
        }
        if (rho > r + 3.5) {
            return Math.max(-8.0, r - rho);
        }
        if (scar && py > capBottom - p.flare * 0.5) {
            return -1.0;
        }
        double flute = 0;
        if (!cap && rho > 0.5) {
            double angle = Math.atan2(dz, dx);
            flute = p.fluteAmp * Math.cos(angle * p.flutes);
        }
        double n = 1.6 * rock.at(x / 9.0, y / 11.0, z / 9.0);
        return Math.min(dTop, r + flute + n - rho);
    }

    private double spanDensity(Span s, boolean scar, int x, int y, int z, double px, double py, double pz) {
        if (px < s.minX || px > s.maxX || pz < s.minZ || pz > s.maxZ) {
            return -8.0;
        }
        // closest point on the polyline
        double bestD2 = Double.MAX_VALUE;
        double bestT = 0;
        double cyAt = 0;
        double horiz = 0;
        for (int i = 0; i < Span.SEGMENTS; i++) {
            double ax = s.px[i], ay = s.py[i], az = s.pz[i];
            double bx = s.px[i + 1], by = s.py[i + 1], bz = s.pz[i + 1];
            double ex = bx - ax, ey = by - ay, ez = bz - az;
            double len2 = ex * ex + ey * ey + ez * ez;
            double t = TMath.clamp(((px - ax) * ex + (py - ay) * ey + (pz - az) * ez) / len2, 0, 1);
            double qx = ax + ex * t, qy = ay + ey * t, qz = az + ez * t;
            double d2 = TMath.sq(px - qx) + TMath.sq(py - qy) + TMath.sq(pz - qz);
            if (s.bridge) {
                // bridges: distance in the horizontal plane only, height handled below
                double tt = TMath.clamp(((px - ax) * ex + (pz - az) * ez) / (ex * ex + ez * ez), 0, 1);
                qx = ax + ex * tt;
                qy = ay + ey * tt;
                qz = az + ez * tt;
                d2 = TMath.sq(px - qx) + TMath.sq(pz - qz);
                t = tt;
            }
            if (d2 < bestD2) {
                bestD2 = d2;
                bestT = (i + t) / Span.SEGMENTS;
                cyAt = qy;
                horiz = Math.sqrt(d2);
            }
        }
        double ends = 1.0 - Math.sin(Math.PI * bestT);
        double n = rock.at(x / 7.0 + 300.0, y / 6.0, z / 7.0);
        if (s.bridge) {
            double halfWidth = s.radius * (1.0 + 0.35 * ends);
            double topY = cyAt + 1.2;
            double thick = 2.5 + 4.0 * ends;
            double dTop = topY - py;
            if (dTop <= 0) {
                return dTop;
            }
            if (scar) {
                return -1.0;
            }
            return Math.min(dTop, Math.min(halfWidth - horiz + 0.8 * n, py - (topY - thick) + 1.8 * n));
        }
        double r = s.radius * (1.0 + 0.6 * ends);
        return r - Math.sqrt(bestD2) + 0.9 * n;
    }
}
