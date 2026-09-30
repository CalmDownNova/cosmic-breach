package com.cosmicbreach.world.gen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The Upper Reach's floating plateaus (GDD 2.2): islands 60 to 200 blocks across with flat-ish tops at
 * Y 346 to 381 (bodies from Y 323), 6 to 24 thick with lobed keels below, rough undersides, and natural
 * necks and bridges joining neighbours.
 *
 * <p>How it is built. Island centres sit on a jittered grid ({@link #CELL}) in a domain-warped plane, so
 * outlines wander. A point belongs to its nearest centre's island when it is inside that island's radius
 * and more than half a gap (8 to 38 blocks, per pair) from the bisector with every other centre: big
 * islands fill their Voronoi cell up to the gap, small ones are rounder with wider sky around them.
 * Each island's radius is at least its nearest neighbour's distance minus 82, so its nearest
 * neighbour is never more than about 38 blocks away, and that pair is always joined by a neck. Other
 * Voronoi-adjacent pairs (Gabriel neighbours) get a neck 38% of the time when their gap is 42 or less.
 *
 * <p>Tops are an exact heightfield (walkable, no 3D noise): gentle relief and a low dome on Shattered
 * Spires islands, 3 to 6 block terraces with ramps on Sunfield islands, a rim that rolls off (except where
 * the Breach cut the island: that is a sheer cliff). Undersides are a bowl deepening away from the rim,
 * with a lobed keel under big islands, roughness and drips; 3D noise only carves the cliffs and undersides
 * (it may build out by less than a block), so no crumbs float off them. A neck's top ramps between the
 * two rims and the islands' tops blend into it, so walking across needs at most a one-block step; necks
 * meander a little, are thick at the ends and thin in the middle, like arches. A test walks 99% of them.
 *
 * <p>Knobs: {@link #CELL} (island spacing), {@link #MIN_RADIUS}/{@link #MAX_RADIUS}, gap range in
 * {@link #pairGap}, link odds {@link #EXTRA_LINK_CHANCE}, {@link #SUNFIELD_THRESHOLD} (Sunfield share),
 * heights in {@link Isle}'s constructor, shapes in {@link #sample} and {@link #density}.
 */
public final class ReachIslands {
    public static final double CELL = 136.0;
    static final double JITTER = 0.30;
    public static final double MIN_RADIUS = 44.0;
    public static final double MAX_RADIUS = 100.0;
    /** Lowest Y any island rock may reach (Shear band A ends at 319). */
    public static final int FLOOR_Y = 323;
    /** Share of the Reach's islands that are Sunfield Terraces: the style noise above this value. */
    public static final double SUNFIELD_THRESHOLD = 0.30;
    static final double EXTRA_LINK_CHANCE = 0.38;
    static final double MAX_LINK_GAP = 42.0;
    /** Islands whose centre lies this close to the Breach's axis are left out. */
    static final double BREACH_CLEARANCE = BreachShape.REACH_RADIUS + 12;
    private static final int TAG_ISLE = 11;
    private static final int TAG_PAIR = 12;

    private final long salt;
    private final BreachShape breach;
    private final SeededNoise warpX;
    private final SeededNoise warpZ;
    private final SeededNoise edgeNoise;
    private final SeededNoise edgeFine;
    private final SeededNoise heightNoise;
    private final SeededNoise styleNoise;
    private final SeededNoise styleFine;
    private final SeededNoise relief;
    private final SeededNoise reliefFine;
    private final SeededNoise terraceNoise;
    private final SeededNoise rampNoise;
    private final SeededNoise rough;
    private final SeededNoise drip;
    private final SeededNoise neckNoise;
    private final SeededNoise erosion;
    private final ThreadLocal<Long2ObjectOpenHashMap<Isle>> isleCache = ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);

    public ReachIslands(long salt, BreachShape breach) {
        this.salt = salt;
        this.breach = breach;
        this.warpX = new SeededNoise(salt + 101);
        this.warpZ = new SeededNoise(salt + 102);
        this.edgeNoise = new SeededNoise(salt + 103);
        this.edgeFine = new SeededNoise(salt + 104);
        this.heightNoise = new SeededNoise(salt + 105);
        this.styleNoise = new SeededNoise(salt + 106);
        this.styleFine = new SeededNoise(salt + 107);
        this.relief = new SeededNoise(salt + 108);
        this.reliefFine = new SeededNoise(salt + 109);
        this.terraceNoise = new SeededNoise(salt + 110);
        this.rampNoise = new SeededNoise(salt + 111);
        this.rough = new SeededNoise(salt + 112);
        this.drip = new SeededNoise(salt + 113);
        this.neckNoise = new SeededNoise(salt + 114);
        this.erosion = new SeededNoise(salt + 115);
    }

    // ------------------------------------------------------------------ islands

    /** One island: its cell, centre (in warped space) and shape parameters. Immutable once built. */
    public final class Isle {
        public final int ci;
        public final int cj;
        public final double cx;
        public final double cz;
        public final boolean exists;
        public final double radius;
        /** Base height of the top surface. */
        public final double top;
        public final double thickness;
        /** How far the underside's lobes hang below the bowl at the island's heart. */
        public final double keel;
        public final double dome;
        public final boolean sunfield;
        public final int terraceStep;
        public final double terracePhase;
        /** Cell of the nearest existing neighbour (packed), or {@link Long#MIN_VALUE}. */
        final long nearest;
        private volatile Link[] links;

        Isle(int ci, int cj) {
            this.ci = ci;
            this.cj = cj;
            long h = Hashing.hash(salt, TAG_ISLE, ci, cj, 0);
            double[] c = centre(ci, cj);
            this.cx = c[0];
            this.cz = c[1];
            this.exists = existsAt(ci, cj);
            long nn = Long.MIN_VALUE;
            double nnDist = Double.MAX_VALUE;
            for (int di = -2; di <= 2; di++) {
                for (int dj = -2; dj <= 2; dj++) {
                    if ((di != 0 || dj != 0) && existsAt(ci + di, cj + dj)) {
                        double[] o = centre(ci + di, cj + dj);
                        double d = Math.hypot(o[0] - cx, o[1] - cz);
                        if (d < nnDist) {
                            nnDist = d;
                            nn = pack(ci + di, cj + dj);
                        }
                    }
                }
            }
            this.nearest = nn;
            double rRand = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * Math.pow(Hashing.unit(h, 3), 1.15);
            this.radius = TMath.clamp(Math.max(rRand, nnDist - 82.0), MIN_RADIUS, MAX_RADIUS + 12.0);
            this.top = 361.0 + 10.0 * heightNoise.at(cx / 520.0, cz / 520.0) + 5.0 * (Hashing.unit(h, 4) - 0.5);
            double size = TMath.smoothstep(MIN_RADIUS, MAX_RADIUS, radius);
            this.thickness = (8.0 + 16.0 * Hashing.unit(h, 5)) * (0.65 + 0.35 * size);
            this.keel = (4.0 + 14.0 * Hashing.unit(h, 9)) * (0.4 + 0.6 * size);
            this.dome = 1.0 + 3.5 * Hashing.unit(h, 6);
            this.sunfield = styleValue(cx, cz) > SUNFIELD_THRESHOLD;
            this.terraceStep = 3 + (int) (Hashing.unit(h, 7) * 4.0);
            this.terracePhase = Hashing.unit(h, 8) * terraceStep;
        }

        /** The necks joining this island to its neighbours (computed once, on first use). */
        public Link[] links() {
            Link[] l = links;
            if (l == null) {
                l = computeLinks(this);
                links = l;
            }
            return l;
        }
    }

    /** A natural neck between two islands, the same whichever end it is read from. */
    public static final class Link {
        public final double ax, az, bx, bz;
        /** Heights of the rims the neck leaves from and arrives at. */
        public final double ha, hb;
        /** Distances along the axis from a's centre where a's rim and b's rim are (about). */
        public final double rimA, rimB;
        public final double length;
        public final double halfWidth;
        public final double arch;

        Link(Isle a, Isle b, double gap, long h) {
            // Canonical order so both ends build the identical link.
            boolean swap = a.ci > b.ci || (a.ci == b.ci && a.cj > b.cj);
            Isle p = swap ? b : a;
            Isle q = swap ? a : b;
            this.ax = p.cx;
            this.az = p.cz;
            this.bx = q.cx;
            this.bz = q.cz;
            this.length = Math.hypot(bx - ax, bz - az);
            double half = length / 2 - gap / 2;
            this.rimA = Math.min(p.radius, half);
            this.rimB = length - Math.min(q.radius, half);
            this.ha = p.top - 1.0;
            this.hb = q.top - 1.0;
            this.halfWidth = 3.5 + 3.5 * Hashing.unit(h, 1);
            this.arch = -1.0 + 4.0 * Hashing.unit(h, 2);
        }
    }

    static long pack(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    double[] centre(int ci, int cj) {
        long h = Hashing.hash(salt, TAG_ISLE, ci, cj, 0);
        return new double[] {
                (ci + 0.5 + (Hashing.unit(h, 0) * 2 - 1) * JITTER) * CELL,
                (cj + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * JITTER) * CELL};
    }

    boolean existsAt(int ci, int cj) {
        long h = Hashing.hash(salt, TAG_ISLE, ci, cj, 0);
        double x = (ci + 0.5 + (Hashing.unit(h, 0) * 2 - 1) * JITTER) * CELL;
        double z = (cj + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * JITTER) * CELL;
        if (x * x + z * z < BREACH_CLEARANCE * BREACH_CLEARANCE) {
            return false;
        }
        return Hashing.unit(h, 2) >= 0.06;
    }

    /** The Sunfield style value at an island centre: above {@link #SUNFIELD_THRESHOLD} is Sunfield. */
    double styleValue(double cx, double cz) {
        return styleNoise.at(cx / 700.0, cz / 700.0) + 0.35 * styleFine.at(cx / 260.0 + 11.0, cz / 260.0 - 5.0);
    }

    public Isle isle(int ci, int cj) {
        Long2ObjectOpenHashMap<Isle> cache = isleCache.get();
        long key = pack(ci, cj);
        Isle isle = cache.get(key);
        if (isle == null) {
            if (cache.size() > 4096) {
                cache.clear();
            }
            isle = new Isle(ci, cj);
            cache.put(key, isle);
        }
        return isle;
    }

    /** The gap between two islands' bisector-limited rims: 8 to 38 blocks, biased small. */
    double pairGap(Isle a, Isle b) {
        return 8.0 + 30.0 * Math.pow(Hashing.unit(pairHash(a, b), 0), 1.3);
    }

    private long pairHash(Isle a, Isle b) {
        boolean swap = a.ci > b.ci || (a.ci == b.ci && a.cj > b.cj);
        Isle p = swap ? b : a;
        Isle q = swap ? a : b;
        return Hashing.hash(salt, TAG_PAIR, p.ci, p.cj, (q.ci - p.ci) * 7 + (q.cj - p.cj));
    }

    private Link[] computeLinks(Isle a) {
        if (!a.exists) {
            return new Link[0];
        }
        List<Link> out = new ArrayList<>(4);
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                if (di == 0 && dj == 0) {
                    continue;
                }
                Isle b = isle(a.ci + di, a.cj + dj);
                if (!b.exists || !gabriel(a, b)) {
                    continue;
                }
                double len = Math.hypot(b.cx - a.cx, b.cz - a.cz);
                double gap = Math.max(pairGap(a, b), len - a.radius - b.radius);
                boolean forced = b.nearest == pack(a.ci, a.cj) || a.nearest == pack(b.ci, b.cj);
                long h = pairHash(a, b);
                if (forced || (Hashing.unit(h, 3) < EXTRA_LINK_CHANCE && gap <= MAX_LINK_GAP)) {
                    out.add(new Link(a, b, pairGap(a, b), h));
                }
            }
        }
        return out.toArray(new Link[0]);
    }

    /** True when no other island centre lies inside the circle whose diameter is a to b. */
    private boolean gabriel(Isle a, Isle b) {
        double mx = (a.cx + b.cx) / 2;
        double mz = (a.cz + b.cz) / 2;
        double r2 = TMath.sq(a.cx - b.cx) / 4 + TMath.sq(a.cz - b.cz) / 4;
        int mi = (int) Math.floor(mx / CELL);
        int mj = (int) Math.floor(mz / CELL);
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                int i = mi + di;
                int j = mj + dj;
                if ((i == a.ci && j == a.cj) || (i == b.ci && j == b.cj) || !existsAt(i, j)) {
                    continue;
                }
                double[] c = centre(i, j);
                if (TMath.sq(c[0] - mx) + TMath.sq(c[1] - mz) < r2) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ columns

    /** One column's Reach data. Mutable and reused; filled by {@link #sample}. */
    public static final class Column {
        public boolean island;
        public double top;
        public double bottom;
        /** Distance to the island's rim (positive inside), before 3D erosion. */
        public double edge;
        public boolean neck;
        public double neckTop;
        public double neckBottom;
        public double neckSide;
        /** 0 far from any neck, 1 on a neck's axis. */
        public double neckness;
        public @Nullable Isle isle;
        /** No rock outside [minY, maxY]. */
        public int minY;
        public int maxY;

        void clear() {
            island = false;
            neck = false;
            neckness = 0;
            isle = null;
            minY = Integer.MAX_VALUE;
            maxY = Integer.MIN_VALUE;
        }
    }

    /** The island whose territory holds (x, z) (the nearest centre in warped space), or null. */
    public @Nullable Isle isleAt(double x, double z) {
        double wx = warpedX(x, z);
        double wz = warpedZ(x, z);
        return nearestIsle(wx, wz);
    }

    private double warpedX(double x, double z) {
        return x + 26.0 * warpX.fbm(x / 150.0, z / 150.0, 2);
    }

    private double warpedZ(double x, double z) {
        return z + 26.0 * warpZ.fbm(x / 150.0 + 31.7, z / 150.0 - 12.9, 2);
    }

    private @Nullable Isle nearestIsle(double wx, double wz) {
        int ci = (int) Math.floor(wx / CELL);
        int cj = (int) Math.floor(wz / CELL);
        Isle best = null;
        double bestD2 = Double.MAX_VALUE;
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                Isle isle = isle(ci + di, cj + dj);
                if (!isle.exists) {
                    continue;
                }
                double d2 = TMath.sq(wx - isle.cx) + TMath.sq(wz - isle.cz);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = isle;
                }
            }
        }
        return best;
    }

    /** Fills {@code out} with column (x, z). */
    public void sample(double x, double z, Column out) {
        out.clear();
        double wx = warpedX(x, z);
        double wz = warpedZ(x, z);
        int ci = (int) Math.floor(wx / CELL);
        int cj = (int) Math.floor(wz / CELL);

        Isle best = null;
        double bestD2 = Double.MAX_VALUE;
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                Isle isle = isle(ci + di, cj + dj);
                if (!isle.exists) {
                    continue;
                }
                double d2 = TMath.sq(wx - isle.cx) + TMath.sq(wz - isle.cz);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = isle;
                }
            }
        }
        if (best == null) {
            return;
        }
        out.isle = best;
        double breachOut = breach.outside(x, z, BreachShape.REACH_RADIUS);

        // ---- the island's rim
        double edgeB = Double.MAX_VALUE;
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                Isle o = isle(ci + di, cj + dj);
                if (o == best || !o.exists) {
                    continue;
                }
                double ax = o.cx - best.cx;
                double az = o.cz - best.cz;
                double len = Math.sqrt(ax * ax + az * az);
                double along = ((wx - best.cx) * ax + (wz - best.cz) * az) / len;
                double bisector = len / 2 - along;
                edgeB = Math.min(edgeB, bisector - pairGap(best, o) / 2);
            }
        }
        double d = Math.sqrt(bestD2);
        double edgeR = best.radius + 7.0 * edgeNoise.at(wx / 31.0, wz / 31.0) - d;
        double ownEdge = Math.min(edgeB + 4.0 * edgeFine.at(wx / 23.0, wz / 23.0), edgeR);
        // the Breach cuts islands off sheer: its rim is a cliff, not a rolled edge
        double edge = Math.min(ownEdge, breachOut);

        // ---- the top surface
        double domeT = Math.max(0, 1 - TMath.sq(d / best.radius));
        double top;
        if (best.sunfield) {
            double raw = best.top + 6.5 * terraceNoise.fbm(wx / 120.0, wz / 120.0, 2) + best.dome * 1.2 * domeT;
            double step = best.terraceStep;
            double q = step * Math.floor((raw + best.terracePhase) / step) - best.terracePhase;
            double ramp = TMath.smoothstep(0.18, 0.42, rampNoise.at(wx / 44.0, wz / 44.0));
            top = q + (raw - q) * ramp;
        } else {
            top = best.top + best.dome * domeT + 2.8 * relief.fbm(wx / 80.0, wz / 80.0, 2)
                    + 0.45 * reliefFine.at(wx / 24.0, wz / 24.0);
        }
        top -= 2.2 * (1.0 - TMath.smoothstep(0.0, 9.0, ownEdge));

        // ---- necks: the strongest one through this column, and the islands' tops blending into it
        Link neck = null;
        double neckSide = -Double.MAX_VALUE;
        double neckTop = 0;
        double neckV = 0;
        double blendTop = top;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                Isle a = isle(ci + di, cj + dj);
                if (!a.exists) {
                    continue;
                }
                for (Link link : a.links()) {
                    double lx = link.bx - link.ax;
                    double lz = link.bz - link.az;
                    double along = ((wx - link.ax) * lx + (wz - link.az) * lz) / link.length;
                    double from = link.rimA - 14.0;
                    double to = link.rimB + 14.0;
                    if (along < from || along > to) {
                        continue;
                    }
                    // signed distance across the axis, wobbled so necks meander instead of running ruler-straight
                    double lateral = ((wx - link.ax) * lz - (wz - link.az) * lx) / link.length;
                    double wobble = 4.0 * neckNoise.at(along / 26.0 + link.ax * 0.013, link.az * 0.017);
                    double s = Math.abs(lateral - wobble);
                    double hw = link.halfWidth * (1.0 + 0.25 * neckNoise.at(wx / 13.0, wz / 13.0));
                    if (s > hw + 10.0) {
                        continue;
                    }
                    double v = (along - from) / (to - from);
                    double nTop = TMath.lerp(link.ha, link.hb, TMath.smoothstep(0.15, 0.85, v)) + link.arch * Math.sin(Math.PI * v);
                    double side = hw - s;
                    // islands' tops ease into the neck within a few blocks of where it lands
                    if (best == a || (best.cx == link.ax && best.cz == link.az) || (best.cx == link.bx && best.cz == link.bz)) {
                        double w = (1.0 - TMath.smoothstep(hw, hw + 9.0, s)) * (1.0 - TMath.smoothstep(4.0, 20.0, edge));
                        blendTop = TMath.lerp(blendTop, nTop, w);
                    }
                    if (side > neckSide) {
                        neckSide = side;
                        neck = link;
                        neckTop = nTop;
                        neckV = v;
                    }
                }
            }
        }
        top = blendTop;
        if (neck != null && neckSide > -8.0) {
            double thin = 3.0 + 4.5 * (1.0 - Math.sin(Math.PI * neckV));
            out.neck = true;
            out.neckTop = neckTop;
            out.neckBottom = neckTop - thin - 1.5 * neckNoise.at(wx / 9.0 + 50.0, wz / 9.0);
            out.neckSide = Math.min(neckSide, breachOut);
            out.neckness = TMath.clamp(1.0 + neckSide / 6.0, 0, 1);
            out.minY = (int) Math.floor(out.neckBottom - 3);
            out.maxY = (int) Math.ceil(out.neckTop);
        }

        // ---- the underside
        double core = best.thickness * (0.55 + 0.45 * Math.max(0, 1 - Math.pow(d / best.radius, 1.5)));
        double depth = 2.5 + (core - 2.5) * Math.pow(TMath.smoothstep(0.0, 26.0, edge), 0.75);
        // lobed keel: the heart of the island hangs lower, in a few rounded lobes (an inverted landscape)
        double lobes = 0.55 + 0.6 * drip.at(wx / 29.0 + 70.0, wz / 29.0 - 30.0);
        double keel = best.keel * TMath.smoothstep(10.0, 55.0, edge) * Math.max(0.15, lobes);
        double r = 2.2 * rough.at(wx / 13.0, wz / 13.0) + 1.3 * rough.at(wx / 6.0 + 40.0, wz / 6.0 - 40.0);
        double dn = Math.max(0, drip.at(wx / 10.0, wz / 10.0) - 0.3) / 0.7;
        double drips = 11.0 * Math.pow(dn, 1.6) * TMath.smoothstep(3.0, 14.0, edge);
        double total = Math.max(2.5, depth + keel + r + drips);
        // stay above the floor without flattening: squeeze the whole profile, not just its tips
        double room = top - FLOOR_Y - 1.0;
        if (total > room * 0.7) {
            total = room * 0.7 + (total - room * 0.7) * (room * 0.3) / (room * 0.3 + (total - room * 0.7));
        }
        out.island = edge > -4.0;
        out.top = top;
        out.bottom = top - total;
        out.edge = edge;
        if (out.island) {
            out.minY = Math.min(out.minY, (int) Math.floor(out.bottom - 4));
            out.maxY = Math.max(out.maxY, (int) Math.ceil(top));
        }
        out.minY = Math.max(out.minY, FLOOR_Y);
    }

    /** Density of the Reach at y in a sampled column, in blocks (positive is rock). */
    public double density(Column c, int x, int y, int z) {
        if (y < c.minY || y > c.maxY) {
            return -8.0;
        }
        double yc = y + 0.5;
        double best = -8.0;
        if (c.island) {
            double dTop = c.top - yc;
            double dBot = yc - c.bottom;
            double dEdge = c.edge;
            if (dTop <= 0) {
                best = dTop;
            } else if (dBot > 3.2 && dEdge > 4.4) {
                best = Math.min(dTop, 3.0);
            } else if (dBot < -0.8 || dEdge < -0.8) {
                best = Math.min(dBot, dEdge);
            } else {
                double n = erosion.at(x / 10.0, y / 7.0, z / 10.0);
                double m = erosion.at(x / 9.0 + 100.0, y / 8.0, z / 9.0);
                // the grass line stays clean; cliffs erode more the further down they go (undercut)
                double down = TMath.smoothstep(0.5, 6.0, dTop);
                // erosion carves in (noise below zero) far more than it builds out, so no crumbs float off the cliffs
                double nb = n < 0 ? n : n * 0.25;
                double me = m < 0 ? m : m * 0.25;
                best = Math.min(dTop, Math.min(dBot + 3.2 * nb, dEdge + (0.5 + 2.6 * down) * me - 1.2 * down));
            }
        }
        if (c.neck) {
            double dTop = c.neckTop - yc;
            double dBot = yc - c.neckBottom;
            double dSide = c.neckSide;
            double v;
            if (dTop <= 0 || dSide < -1.6 || dBot < -2.0) {
                v = Math.min(dTop, Math.min(dSide, dBot));
            } else {
                double n = erosion.at(x / 6.0 + 200.0, y / 5.0, z / 6.0);
                double nc = n < 0 ? n : n * 0.25;
                v = Math.min(dTop, Math.min(dBot + 2.0 * nc, dSide + 1.6 * nc));
            }
            best = Math.max(best, v);
        }
        return best;
    }
}
