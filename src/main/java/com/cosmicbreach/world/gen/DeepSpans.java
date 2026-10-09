package com.cosmicbreach.world.gen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The Deep (GDD 2.2, zones from Aetheria 1.2): black basalt rising out of nothing, none of it within the Breach's
 * 150-block radius. One {@link Pillar} per cell of a jittered {@link #CELL}-block grid, shaped by the zone
 * ({@link DeepZones}) its centre falls in:
 *
 * <ul>
 *   <li><b>The Spans</b> (1.0's Rift Abyss): a flat platform (8 to 21 blocks in radius, one in five or six a broad
 *       mesa up to 34) at Y 78 to 136 on a fluted shaft; <b>needles</b> (thorns out of the void) between pillars; and
 *       <b>spans</b> between neighbouring pillars, mostly flat walkable bridges, some high arches. Rift Scars, long
 *       cracks where a ridged noise is thin, cut through platforms and bridges.</li>
 *   <li><b>The Lichen Gardens</b>: mesas 40 to 90 in radius that merge into broad landmasses under one rolling height
 *       field (soft hills, shallow basins), a thick slab with rounded rims over a broad fluted shaft at each mesa's
 *       middle.</li>
 *   <li><b>The Hanging Wood</b>: ceiling masses (lens shaped, tops at Y 118 to 134) trailing long roots and
 *       stalactites down toward Y 40, a few small ledges on the roots. Mostly flown through.</li>
 *   <li><b>The Shattered Field</b>: no bridges; floating basalt chunks (inverted cones) of radius 3 to 25 at Y 50 to
 *       135, each placed 4 to 12 blocks from one already there, so a stag can hop and a manta can thread them.</li>
 * </ul>
 * A pillar's zone is drawn from the zone weights at its centre, so every piece of rock belongs whole to one zone (a
 * border never cuts anything) and near a border the two zones' shapes mingle; rock also shrinks toward a border by its
 * zone's weight. In the Shattered Field and the Hanging Wood a few cells are <b>clearings</b>: a lane out from the
 * pillar ({@link #CLEAR_LENGTH} long) kept free of every other piece of rock, so the boss arena, which needs void
 * beside its platform, can stand there as in the Spans.
 *
 * <p>Knobs: {@link #CELL}, the zone constructors in {@link Pillar}, the span odds in {@link #computeSpans}, the scar
 * width in {@link #scar}.
 */
public final class DeepSpans {
    public static final double CELL = 64.0;
    public static final int CEIL_Y = 143;
    /** A clearing's lane: from the pillar's middle this far along one axis, this wide on each side of it. */
    public static final double CLEAR_LENGTH = 76.0;
    public static final double CLEAR_HALF = 27.0;
    private static final int TAG_PILLAR = 31;
    private static final int TAG_NEEDLE = 32;
    private static final int TAG_SPAN = 33;
    private static final int TAG_SATELLITE = 34;
    private static final int TAG_ROOT = 35;
    private static final int[][] AXES = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    private final long salt;
    public final DeepZones zones;
    private final SeededNoise heightNoise;
    private final SeededNoise rock;
    private final SeededNoise topRelief;
    private final SeededNoise scarNoise;
    private final SeededNoise scarRegion;
    private final SeededNoise hills;
    private final SeededNoise basins;
    private final SeededNoise edge;
    private final ThreadLocal<Long2ObjectOpenHashMap<Pillar>> pillars = ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);

    public DeepSpans(long salt) {
        this(salt, new DeepZones(salt));
    }

    public DeepSpans(long salt, DeepZones zones) {
        this.salt = salt;
        this.zones = zones;
        this.heightNoise = new SeededNoise(salt + 301);
        this.rock = new SeededNoise(salt + 302);
        this.topRelief = new SeededNoise(salt + 303);
        this.scarNoise = new SeededNoise(salt + 304);
        this.scarRegion = new SeededNoise(salt + 305);
        this.hills = new SeededNoise(salt + 306);
        this.basins = new SeededNoise(salt + 307);
        this.edge = new SeededNoise(salt + 308);
    }

    /** What a cell is before its pillar is built: its nominal centre, zone and clearing (no neighbours read). */
    private record CellPlan(double cx, double cz, int zone, double weight, boolean clearing, int clearAxis) {}

    private CellPlan plan(int ci, int cj) {
        long h = Hashing.hash(salt, TAG_PILLAR, ci, cj, 0);
        double cx = (ci + 0.5 + (Hashing.unit(h, 0) * 2 - 1) * 0.28) * CELL;
        double cz = (cj + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * 0.28) * CELL;
        double[] w = zones.weights(cx, cz, new double[DeepZones.COUNT]);
        int zone = DeepZones.pick(w, Hashing.unit(h, 13));
        boolean clearing = (zone == DeepZones.SHATTERED || zone == DeepZones.HANGING) && w[zone] > 0.9
                && Hashing.unit(h, 20) < 0.22 && Math.hypot(cx, cz) > BreachShape.DEEP_RADIUS + 150;
        return new CellPlan(cx, cz, zone, w[zone], clearing, (int) (Hashing.unit(h, 21) * 4));
    }

    /** True if (x, z) lies in the lane of a clearing among the 5 by 5 cells around cell (ci, cj), other than (si, sj). */
    private boolean inClearing(double x, double z, double pad, int ci, int cj, int si, int sj) {
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                if (ci + di == si && cj + dj == sj) {
                    continue;
                }
                CellPlan c = plan(ci + di, cj + dj);
                if (c.clearing && inLane(c, x, z, pad)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inLane(CellPlan c, double x, double z, double pad) {
        int[] a = AXES[c.clearAxis];
        double u = (x - c.cx) * a[0] + (z - c.cz) * a[1];
        double v = (x - c.cx) * -a[1] + (z - c.cz) * a[0];
        return u > -pad - 6 && u < CLEAR_LENGTH + pad && Math.abs(v) < CLEAR_HALF + pad;
    }

    /** A cell: maybe a pillar (whatever its zone makes of it), its needles, roots, satellites and spans. */
    public final class Pillar {
        public final int ci;
        public final int cj;
        /** The zone this pillar was built for ({@link DeepZones}). */
        public final int zone;
        public final boolean exists;
        public final double cx;
        public final double cz;
        /** The walkable top at the middle. */
        public final double top;
        /** The radius of the walkable top (Gardens: of this mesa's part of the landmass). */
        public final double platformR;
        public final double capThick;
        public final double shaftR;
        public final double flare;
        public final double bottom;
        public final double leanX;
        public final double leanZ;
        final int flutes;
        final double fluteAmp;
        /** Gardens: the top of the shaft hidden in the slab, and its radius there. */
        final double bodyTop;
        final double bodyR;
        /** A clearing (Shattered Field, Hanging Wood): which axis its lane runs along ({@link #AXES}), else -1. */
        public final int clearAxis;
        public final Needle[] needles;
        /** Hanging Wood: the roots and stalactites under the mass. */
        public final Root[] roots;
        /** Hanging Wood: small shelves on the roots. */
        public final Chunk[] ledges;
        private volatile Span[] spans;
        private volatile Chunk[] satellites;

        Pillar(int ci, int cj) {
            this.ci = ci;
            this.cj = cj;
            long h = Hashing.hash(salt, TAG_PILLAR, ci, cj, 0);
            CellPlan plan = plan(ci, cj);
            this.cx = plan.cx;
            this.cz = plan.cz;
            this.zone = plan.zone;
            this.clearAxis = plan.clearing ? plan.clearAxis : -1;
            double shrink = 0.55 + 0.45 * plan.weight;
            double reach = Math.hypot(cx, cz);
            double rTop;
            double rPlat;
            double rCap;
            double rShaft;
            double rFlare;
            double rBottom;
            double rBodyTop = 0;
            double rBodyR = 0;
            boolean rExists;
            Needle[] rNeedles = new Needle[0];
            Root[] rRoots = new Root[0];
            Chunk[] rLedges = new Chunk[0];
            switch (zone) {
                case DeepZones.GARDENS -> {
                    rPlat = (40.0 + 50.0 * Hashing.unit(h, 3)) * shrink;
                    rTop = gardenTop(cx, cz);
                    rCap = 14.0 + 0.12 * rPlat;
                    rShaft = TMath.clamp(rPlat * 0.24, 7.0, 18.0);
                    rFlare = 10.0 + 0.3 * rPlat;
                    rBottom = 2.0 + 16.0 * Hashing.unit(h, 7);
                    rBodyTop = rTop - 9.0;
                    rBodyR = Math.max(rShaft + 2.0, rPlat * 0.42);
                    rExists = Hashing.unit(h, 12) < 0.31 && reach > BreachShape.DEEP_RADIUS + rPlat + 20;
                }
                case DeepZones.HANGING -> {
                    rPlat = (plan.clearing ? 16.0 + 10.0 * Hashing.unit(h, 3) : 20.0 + 16.0 * Hashing.unit(h, 3)) * shrink;
                    rTop = plan.clearing ? 100.0 + 18.0 * Hashing.unit(h, 4) : 118.0 + 16.0 * Hashing.unit(h, 4);
                    rCap = 24.0 + 18.0 * Hashing.unit(h, 5);
                    rShaft = 0;
                    rFlare = 0;
                    rBottom = rTop - rCap;
                    rExists = (plan.clearing || Hashing.unit(h, 12) < 0.8) && reach > BreachShape.DEEP_RADIUS + rPlat + 20
                            && !inClearing(cx, cz, rPlat + 4, ci, cj, ci, cj);
                    if (rExists) {
                        rRoots = buildRoots(cx, cz, rTop, rCap, rPlat);
                        rLedges = buildLedges(h, rRoots);
                    }
                }
                case DeepZones.SHATTERED -> {
                    rPlat = plan.clearing ? 12.0 + 6.0 * Hashing.unit(h, 3)
                            : (6.0 + 10.0 * Math.pow(Hashing.unit(h, 3), 0.8)) * (0.6 + 0.4 * plan.weight);
                    rTop = plan.clearing ? 88.0 + 28.0 * Hashing.unit(h, 4) : 84.0 + 40.0 * Hashing.unit(h, 4);
                    rCap = 0.8 * rPlat + 4.0 + 6.0 * Hashing.unit(h, 5);
                    rShaft = 0;
                    rFlare = 0;
                    rBottom = rTop - rCap;
                    rExists = (plan.clearing || Hashing.unit(h, 12) < 0.9) && reach > BreachShape.DEEP_RADIUS + rPlat + 20
                            && !inClearing(cx, cz, rPlat + 4, ci, cj, ci, cj);
                }
                default -> {
                    boolean mesa = Hashing.unit(h, 2) < 0.18;
                    rPlat = mesa ? 20.0 + 14.0 * Hashing.unit(h, 3) : 8.0 + 13.0 * Math.pow(Hashing.unit(h, 3), 1.2);
                    rTop = TMath.clamp(104.0 + 20.0 * heightNoise.at(cx / 360.0, cz / 360.0) + 12.0 * (Hashing.unit(h, 4) - 0.5), 78, 136);
                    rCap = 3.0 + 4.0 * Hashing.unit(h, 5) + rPlat * 0.12;
                    rShaft = Math.max(3.0, rPlat * (0.32 + 0.2 * Hashing.unit(h, 6)));
                    rFlare = rCap + 4.0 + 0.45 * rPlat;
                    rBottom = 2.0 + 16.0 * Hashing.unit(h, 7);
                    rExists = Hashing.unit(h, 12) < 0.55 && reach > BreachShape.DEEP_RADIUS + rPlat + 10;
                }
            }
            this.top = rTop;
            this.platformR = rPlat;
            this.capThick = rCap;
            this.shaftR = rShaft;
            this.flare = rFlare;
            this.bottom = rBottom;
            this.bodyTop = rBodyTop;
            this.bodyR = rBodyR;
            this.exists = rExists;
            boolean leaning = zone == DeepZones.SPANS || zone == DeepZones.GARDENS;
            this.leanX = leaning ? (Hashing.unit(h, 8) - 0.5) * 0.16 : 0;
            this.leanZ = leaning ? (Hashing.unit(h, 9) - 0.5) * 0.16 : 0;
            this.flutes = 7 + (int) (Hashing.unit(h, 10) * 5);
            this.fluteAmp = 0.5 + 0.6 * Hashing.unit(h, 11);
            this.needles = zone == DeepZones.SPANS ? buildNeedles(ci, cj, this) : rNeedles;
            this.roots = rRoots;
            this.ledges = rLedges;
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

        /** Shattered Field: the floating chunks around this cell's main chunk (not the main chunk itself). */
        public Chunk[] satellites() {
            Chunk[] s = satellites;
            if (s == null) {
                s = computeSatellites(this);
                satellites = s;
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

        /** Horizontal reach of this pillar's own rock around its (leaning) axis. */
        double bound() {
            return Math.max(platformR, shaftR) + 3.0;
        }

        /**
         * The horizontal gap from (x, z) to the nearest rock of this cell (its body unless {@code skipBody}, needles,
         * roots, ledges, satellites; a span is left to its pillars), less {@code pad}. Negative where rock is nearer.
         */
        public double gapTo(double x, double z, boolean skipBody) {
            double gap = Double.MAX_VALUE;
            if (exists && !skipBody) {
                gap = Math.hypot(cx - x, cz - z) - bound();
            }
            for (Needle n : needles) {
                gap = Math.min(gap, Math.hypot(n.x - x, n.z - z) - n.rmax - 2.0 - Math.hypot(n.leanX, n.leanZ) * (n.top - n.bottom));
            }
            for (Root r : roots) {
                gap = Math.min(gap, r.gapTo(x, z));
            }
            for (Chunk c : ledges) {
                gap = Math.min(gap, Math.hypot(c.x - x, c.z - z) - c.r - 2.0);
            }
            for (Chunk c : satellites()) {
                gap = Math.min(gap, Math.hypot(c.x - x, c.z - z) - c.r - 2.0);
            }
            return gap;
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

    /**
     * A root or stalactite hanging from a Hanging Wood mass: a tapering tube along a gently swaying axis, sampled
     * every {@link #STEP} blocks from {@link #y0} (its top, inside the mass) down to {@link #y1} (its tip).
     */
    public static final class Root {
        static final double STEP = 8.0;
        public final double y0;
        public final double y1;
        final double r0;
        final double taper;
        final double[] ax;
        final double[] az;
        final double minX, maxX, minZ, maxZ;

        Root(double x, double z, double y0, double y1, double r0, double taper, double leanX, double leanZ, double sway, double period,
                double phase) {
            this.y0 = y0;
            this.y1 = y1;
            this.r0 = r0;
            this.taper = taper;
            int n = (int) Math.ceil((y0 - y1) / STEP) + 1;
            this.ax = new double[n + 1];
            this.az = new double[n + 1];
            double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (int i = 0; i <= n; i++) {
                double d = i * STEP;
                double grow = Math.min(1.0, d / 24.0);
                double a = d / period * Math.PI * 2 + phase;
                ax[i] = x + leanX * d + sway * grow * Math.sin(a);
                az[i] = z + leanZ * d + sway * grow * Math.cos(a * 0.8);
                x0 = Math.min(x0, ax[i]);
                x1 = Math.max(x1, ax[i]);
                z0 = Math.min(z0, az[i]);
                z1 = Math.max(z1, az[i]);
            }
            double pad = r0 + 2.0;
            this.minX = x0 - pad;
            this.maxX = x1 + pad;
            this.minZ = z0 - pad;
            this.maxZ = z1 + pad;
        }

        boolean touches(double x, double z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** The radius at y (0 outside the root). */
        double radius(double y) {
            if (y > y0 || y < y1) {
                return 0;
            }
            double t = (y - y1) / (y0 - y1);
            return r0 * (0.25 + 0.75 * Math.pow(t, taper)) * TMath.smoothstep(0.0, 5.0, y - y1);
        }

        double axisX(double y) {
            double f = (y0 - y) / STEP;
            int i = (int) TMath.clamp(Math.floor(f), 0, ax.length - 2);
            double t = TMath.clamp(f - i, 0, 1);
            return ax[i] + (ax[i + 1] - ax[i]) * t;
        }

        double axisZ(double y) {
            double f = (y0 - y) / STEP;
            int i = (int) TMath.clamp(Math.floor(f), 0, az.length - 2);
            double t = TMath.clamp(f - i, 0, 1);
            return az[i] + (az[i + 1] - az[i]) * t;
        }

        double gapTo(double x, double z) {
            double gap = Double.MAX_VALUE;
            for (int i = 0; i < ax.length; i++) {
                gap = Math.min(gap, Math.hypot(ax[i] - x, az[i] - z));
            }
            return gap - r0 - 2.0;
        }
    }

    /**
     * A floating chunk of basalt (Shattered Field), a ledge (Hanging Wood) or a fallen piece of a span (the Spans): a top,
     * maybe tilted ({@link #tiltX}, {@link #tiltZ}: rise per block) and broken ({@link #jag}: blocks of relief), over an
     * inverted cone.
     */
    public static final class Chunk {
        public final double x;
        public final double z;
        public final double top;
        public final double r;
        public final double depth;
        public final double tiltX;
        public final double tiltZ;
        public final double jag;

        Chunk(double x, double z, double top, double r, double depth) {
            this(x, z, top, r, depth, 0, 0, 0.5);
        }

        Chunk(double x, double z, double top, double r, double depth, double tiltX, double tiltZ, double jag) {
            this.x = x;
            this.z = z;
            this.top = top;
            this.r = r;
            this.depth = depth;
            this.tiltX = tiltX;
            this.tiltZ = tiltZ;
            this.jag = jag;
        }

        /** How far the top can rise or fall from {@link #top} anywhere on the chunk. */
        double lift() {
            return Math.hypot(tiltX, tiltZ) * r + jag + 1;
        }

        boolean touches(double px, double pz) {
            return Math.abs(px - x) <= r + 3 && Math.abs(pz - z) <= r + 3;
        }
    }

    /** An arch or a bridge between two pillars: a quadratic curve from rim to rim, sampled as a polyline. */
    public static final class Span {
        static final int SEGMENTS = 12;
        public final boolean bridge;
        /** Where along the span (0 to 1) it is broken, and half the break's length in the same units (0: whole). */
        public final double breakT;
        public final double breakHalf;
        /** The piece that fell out of a broken bridge, tilted, a little below the break; null if none. */
        public final @Nullable Chunk rubble;
        final double[] px = new double[SEGMENTS + 1];
        final double[] py = new double[SEGMENTS + 1];
        final double[] pz = new double[SEGMENTS + 1];
        final double radius;
        final double minX, maxX, minZ, maxZ, minY, maxY;

        /** The point at fraction {@code t} (0 to 1) along the span's curve, as x, y, z. */
        public double[] at(double t) {
            int i = (int) Math.round(TMath.clamp(t, 0, 1) * SEGMENTS);
            return new double[] {px[i], py[i], pz[i]};
        }

        Span(double ax, double ay, double az, double bx, double by, double bz, double rise, double bow, boolean bridge, double radius,
                double breakT, double breakHalf) {
            this.bridge = bridge;
            this.breakT = breakT;
            this.breakHalf = breakHalf;
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
            if (bridge && breakHalf > 0) {
                int i = (int) Math.round(breakT * SEGMENTS);
                double tilt = bow > 0 ? 0.35 : -0.35;
                this.rubble = new Chunk(px[i] + 3 * Math.signum(bow + 1e-9), pz[i] - 2 * Math.signum(bow + 1e-9),
                        py[i] - 8 - 30 * Math.abs(bow), radius * 0.9 + 1, 3.5, tilt, tilt * 0.5, 1.2);
            } else {
                this.rubble = null;
            }
        }
    }

    /** The Lichen Gardens' ground height at (x, z): one field for the whole landmass (hills, shallow basins). */
    public double gardenTop(double x, double z) {
        double base = 106.0 + 16.0 * heightNoise.at(x / 360.0, z / 360.0);
        double hill = 7.0 * hills.fbm(x / 150.0, z / 150.0, 2);
        double basin = -5.0 * TMath.smoothstep(0.3, 0.75, basins.at(x / 150.0, z / 150.0));
        return TMath.clamp(base + hill + basin, 88.0, 126.0);
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

    /** Hanging Wood: 4 to 9 long roots and 3 to 6 stalactites under a mass; none in a clearing's lane. */
    private Root[] buildRoots(double cx, double cz, double top, double cap, double r) {
        long h = Hashing.hash(salt, TAG_ROOT, (int) Math.floor(cx), (int) Math.floor(cz), 0);
        int ci = (int) Math.floor(cx / CELL);
        int cj = (int) Math.floor(cz / CELL);
        int roots = 4 + (int) (Hashing.unit(h, 0) * 6);
        int stalactites = 4 + (int) (Hashing.unit(h, 1) * 5);
        List<Root> out = new ArrayList<>(roots + stalactites);
        int k = 2;
        for (int n = 0; n < roots + stalactites; n++, k += 12) {
            boolean root = n < roots;
            double a = Hashing.unit(h, k) * Math.PI * 2;
            double d = Math.sqrt(Hashing.unit(h, k + 1)) * r * (root ? 0.7 : 0.45);
            double x = cx + Math.cos(a) * d;
            double z = cz + Math.sin(a) * d;
            double under = top - hangDepth(cap, d / r);
            double y0 = under + (root ? 2.0 : 4.0);
            double y1 = root ? 40.0 + 55.0 * Hashing.unit(h, k + 2) : Math.max(40.0, y0 - 20.0 - 35.0 * Hashing.unit(h, k + 2));
            if (y0 - y1 < 8) {
                continue;
            }
            double r0 = root ? 2.2 + 2.8 * Hashing.unit(h, k + 3) : 4.0 + 5.0 * Hashing.unit(h, k + 3);
            double lean = root ? 0.22 : 0.06;
            double leanX = (Hashing.unit(h, k + 4) - 0.5) * lean;
            double leanZ = (Hashing.unit(h, k + 5) - 0.5) * lean;
            double sway = root ? 1.5 + 3.5 * Hashing.unit(h, k + 6) : 0.4;
            double period = 22.0 + 22.0 * Hashing.unit(h, k + 7);
            Root rt = new Root(x, z, y0, y1, r0, root ? 0.8 : 1.3, leanX, leanZ, sway, period, Hashing.unit(h, k + 8) * 6.28);
            // keep the Breach and every clearing's lane (this cell's own included) free
            boolean blocked = nearBreach(rt.minX, rt.maxX, rt.minZ, rt.maxZ)
                    || laneHit(rt.minX, rt.maxX, rt.minZ, rt.maxZ, ci, cj);
            if (!blocked) {
                out.add(rt);
            }
        }
        return out.toArray(new Root[0]);
    }

    /** Hanging Wood: a small shelf on a few of the long roots, somewhere between Y 60 and 110. */
    private Chunk[] buildLedges(long cellHash, Root[] roots) {
        List<Chunk> out = new ArrayList<>(2);
        for (int i = 0; i < roots.length; i++) {
            Root rt = roots[i];
            double u = Hashing.unit(cellHash, 40 + i);
            if (u > 0.35 || rt.y0 - rt.y1 < 40) {
                continue;
            }
            double y = TMath.clamp(rt.y1 + 12 + (rt.y0 - rt.y1 - 24) * Hashing.unit(cellHash, 60 + i), 60, 110);
            double rl = 3.0 + 3.0 * Hashing.unit(cellHash, 80 + i);
            double a = Hashing.unit(cellHash, 100 + i) * Math.PI * 2;
            double off = rt.radius(y) + rl * 0.5;
            out.add(new Chunk(rt.axisX(y) + Math.cos(a) * off, rt.axisZ(y) + Math.sin(a) * off, y, rl, 2.0 + rl * 0.5));
        }
        return out.toArray(new Chunk[0]);
    }

    private static boolean nearBreach(double x0, double x1, double z0, double z1) {
        double nx = TMath.clamp(0, x0, x1);
        double nz = TMath.clamp(0, z0, z1);
        return Math.hypot(nx, nz) < BreachShape.DEEP_RADIUS + 20;
    }

    /** True if a box meets the lane of any clearing among the 5 by 5 cells around (ci, cj). */
    private boolean laneHit(double x0, double x1, double z0, double z1, int ci, int cj) {
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                CellPlan c = plan(ci + di, cj + dj);
                if (!c.clearing) {
                    continue;
                }
                int[] a = AXES[c.clearAxis];
                // the lane as a box
                double ex = c.cx + a[0] * CLEAR_LENGTH;
                double ez = c.cz + a[1] * CLEAR_LENGTH;
                double lx0 = Math.min(c.cx, ex) - (a[0] == 0 ? CLEAR_HALF : 6);
                double lx1 = Math.max(c.cx, ex) + (a[0] == 0 ? CLEAR_HALF : 6);
                double lz0 = Math.min(c.cz, ez) - (a[1] == 0 ? CLEAR_HALF : 6);
                double lz1 = Math.max(c.cz, ez) + (a[1] == 0 ? CLEAR_HALF : 6);
                if (x1 >= lx0 && x0 <= lx1 && z1 >= lz0 && z0 <= lz1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Shattered Field: a chain of chunks, each 4 to 12 blocks from one placed before it, kept near its cell. */
    private Chunk[] computeSatellites(Pillar p) {
        if (p.zone != DeepZones.SHATTERED) {
            return new Chunk[0];
        }
        long h = Hashing.hash(salt, TAG_SATELLITE, p.ci, p.cj, 0);
        double shrink = 0.6 + 0.4 * plan(p.ci, p.cj).weight;
        List<Chunk> placed = new ArrayList<>(14);
        if (p.exists) {
            placed.add(new Chunk(p.cx, p.cz, p.top, p.platformR, p.capThick));
        } else {
            double r = 5.0 + 5.0 * Hashing.unit(h, 0);
            Chunk seed = new Chunk((p.ci + 0.3 + 0.4 * Hashing.unit(h, 1)) * CELL, (p.cj + 0.3 + 0.4 * Hashing.unit(h, 2)) * CELL,
                    60.0 + 70.0 * Hashing.unit(h, 3), r, 0.8 * r + 3.0);
            placed.add(seed);
        }
        int own = p.exists ? 1 : 0;
        int want = 7 + (int) (Hashing.unit(h, 4) * 5);
        double lo = p.ci * CELL - 6;
        double hi = (p.ci + 1) * CELL + 6;
        double loz = p.cj * CELL - 6;
        double hiz = (p.cj + 1) * CELL + 6;
        int n = 5;
        for (int attempt = 0; attempt < 48 && placed.size() - 1 + own < want + own; attempt++, n += 6) {
            Chunk a = placed.get((int) (Hashing.unit(h, n) * placed.size()));
            double r = (3.0 + 7.0 * Math.pow(Hashing.unit(h, n + 1), 1.5)) * shrink;
            double gap = 5.0 + 7.0 * Hashing.unit(h, n + 2);
            double ang = Hashing.unit(h, n + 3) * Math.PI * 2;
            double dist = a.r + r + gap;
            double x = a.x + Math.cos(ang) * dist;
            double z = a.z + Math.sin(ang) * dist;
            double top = TMath.clamp(a.top - 12.0 + 18.0 * Hashing.unit(h, n + 4), 50.0, 135.0);
            double depth = 0.8 * r + 2.0 + 3.0 * Hashing.unit(h, n + 5);
            if (x < lo || x > hi || z < loz || z > hiz || Math.hypot(x, z) < BreachShape.DEEP_RADIUS + r + 20) {
                continue;
            }
            if (inClearing(x, z, r + 3, p.ci, p.cj, Integer.MIN_VALUE, Integer.MIN_VALUE)) {
                continue;
            }
            boolean clash = false;
            for (Chunk c : placed) {
                boolean overlapY = top + 3 > c.top - c.depth && c.top + 3 > top - depth;
                if (overlapY && Math.hypot(c.x - x, c.z - z) < c.r + r + 4.5) {
                    clash = true;
                    break;
                }
            }
            if (!clash) {
                // shards, not slabs: tilted up to about 25 degrees and broken a block or two across the top
                double tilt = 0.45 * Hashing.unit(h, n + 6);
                double ta = Hashing.unit(h, n + 7) * Math.PI * 2;
                placed.add(new Chunk(x, z, top, r, depth, Math.cos(ta) * tilt, Math.sin(ta) * tilt, 1.0 + 1.8 * Hashing.unit(h, n + 8)));
            }
        }
        if (p.exists) {
            placed.remove(0); // the main chunk is the pillar itself
        }
        return placed.toArray(new Chunk[0]);
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
        if (!a.exists || a.zone != DeepZones.SPANS) {
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
                if (!b.exists || b.zone != DeepZones.SPANS) {
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

    /** One span between p and q, the same whichever pillar builds it: mostly flat bridges, a few high arches. */
    private Span span(Pillar p, Pillar q, long h) {
        double dx = q.cx - p.cx;
        double dz = q.cz - p.cz;
        double len = Math.hypot(dx, dz);
        dx /= len;
        dz /= len;
        boolean bridge = Hashing.unit(h, 1) < 0.8;
        double ay = p.top - p.capThick * (bridge ? 0.3 : 0.6);
        double by = q.top - q.capThick * (bridge ? 0.3 : 0.6);
        double ax = p.cx + dx * (p.platformR - 2.5);
        double az = p.cz + dz * (p.platformR - 2.5);
        double bx = q.cx - dx * (q.platformR - 2.5);
        double bz = q.cz - dz * (q.platformR - 2.5);
        double rise = bridge ? 1.0 * Hashing.unit(h, 2) : 8.0 + 16.0 * Hashing.unit(h, 2);
        double radius = bridge ? 3.2 + 1.4 * Hashing.unit(h, 3) : 2.2 + 1.6 * Hashing.unit(h, 3);
        double bow = (Hashing.unit(h, 4) - 0.5) * 0.4;
        // ruins, not a causeway: most arches snapped into two stumps, some bridges fallen through for 2 to 4 blocks
        double reachLen = Math.max(8.0, Math.hypot(bx - ax, bz - az));
        double breakT = 0.3 + 0.4 * Hashing.unit(h, 5);
        double breakHalf = 0;
        if (!bridge && Hashing.unit(h, 6) < 0.92) {
            breakHalf = 0.08 + 0.12 * Hashing.unit(h, 7);
        } else if (bridge && Hashing.unit(h, 6) < 0.75) {
            breakHalf = (1.0 + 1.0 * Hashing.unit(h, 7)) / reachLen;
        }
        return new Span(ax, ay, az, bx, by, bz, rise, bow, bridge, radius, breakT, breakHalf);
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
                if (b.exists && b.zone == DeepZones.SPANS) {
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
        final List<Root> roots = new ArrayList<>(6);
        final List<Chunk> chunks = new ArrayList<>(6);
        /** Lichen Gardens: how far inside the landmass's rim (negative outside), and its slab's top and bottom. */
        double gardenCover;
        double gardenTop;
        double gardenBottom;
        public boolean scar;
        public int minY;
        public int maxY;

        void clear() {
            pillars.clear();
            needles.clear();
            spans.clear();
            roots.clear();
            chunks.clear();
            gardenCover = -1e9;
            scar = false;
            minY = Integer.MAX_VALUE;
            maxY = Integer.MIN_VALUE;
        }

        public boolean empty() {
            return pillars.isEmpty() && needles.isEmpty() && spans.isEmpty() && roots.isEmpty() && chunks.isEmpty()
                    && gardenCover < -3;
        }

        void span(double lo, double hi) {
            minY = Math.min(minY, (int) Math.floor(lo));
            maxY = Math.max(maxY, (int) Math.ceil(hi));
        }
    }

    /** True where a Rift Scar runs: a long crack through the Spans' platforms and bridges. */
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
        boolean spansHere = false;
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                boolean inner = Math.abs(di) < 2 && Math.abs(dj) < 2;
                Pillar p = pillar(ci + di, cj + dj);
                double d2 = TMath.sq(px - p.cx) + TMath.sq(pz - p.cz);
                switch (p.zone) {
                    case DeepZones.GARDENS -> {
                        if (!p.exists) {
                            break;
                        }
                        double reach = p.platformR * 1.16 + 2;
                        if (d2 <= reach * reach) {
                            double d = Math.sqrt(d2);
                            double c = p.platformR * (1.0 + 0.13 * edge.at(px / 26.0, pz / 26.0)) - d;
                            out.gardenCover = Math.max(out.gardenCover, c);
                        }
                        double lean = Math.hypot(p.leanX, p.leanZ) * (p.bodyTop - p.bottom);
                        double b = p.bodyR + 3 + lean;
                        if (d2 <= b * b) {
                            out.pillars.add(p);
                            out.span(p.bottom, p.bodyTop + 1);
                        }
                    }
                    case DeepZones.HANGING -> {
                        if (!p.exists) {
                            break;
                        }
                        double b = p.platformR + 4;
                        if (d2 <= b * b) {
                            out.pillars.add(p);
                            out.span(p.top - p.capThick - 4, p.top + 3);
                        }
                        for (Root r : p.roots) {
                            if (r.touches(px, pz)) {
                                out.roots.add(r);
                                out.span(r.y1, r.y0);
                            }
                        }
                        for (Chunk c : p.ledges) {
                            if (c.touches(px, pz)) {
                                out.chunks.add(c);
                                out.span(c.top - c.depth - 2 - c.lift(), c.top + 2 + c.lift());
                            }
                        }
                    }
                    case DeepZones.SHATTERED -> {
                        if (!inner) {
                            break;
                        }
                        if (p.exists) {
                            double b = p.platformR + 3;
                            if (d2 <= b * b) {
                                out.pillars.add(p);
                                out.span(p.top - p.capThick - 4, p.top + 4);
                            }
                        }
                        for (Chunk c : p.satellites()) {
                            if (c.touches(px, pz)) {
                                out.chunks.add(c);
                                out.span(c.top - c.depth - 2 - c.lift(), c.top + 2 + c.lift());
                            }
                        }
                    }
                    default -> {
                        if (inner && p.exists) {
                            spansHere = true;
                            double lean = Math.hypot(p.leanX, p.leanZ) * (p.top - p.bottom);
                            double b = p.bound() + lean;
                            if (d2 <= b * b) {
                                out.pillars.add(p);
                                out.span(p.bottom, p.top + 2);
                            }
                        }
                        if (inner) {
                            for (Needle n : p.needles) {
                                double lean = Math.hypot(n.leanX, n.leanZ) * (n.top - n.bottom);
                                double b = n.rmax + 2.0 + lean;
                                if (TMath.sq(px - n.x) + TMath.sq(pz - n.z) <= b * b) {
                                    out.needles.add(n);
                                    out.span(n.bottom, n.top);
                                }
                            }
                        }
                        // spans reach up to two cells
                        if (p.exists) {
                            for (Span s : p.spans()) {
                                if (px >= s.minX && px <= s.maxX && pz >= s.minZ && pz <= s.maxZ && !out.spans.contains(s)) {
                                    out.spans.add(s);
                                    out.span(s.minY, s.maxY);
                                    spansHere = true;
                                }
                                if (s.rubble != null && s.rubble.touches(px, pz) && !out.chunks.contains(s.rubble)) {
                                    out.chunks.add(s.rubble);
                                    out.span(s.rubble.top - s.rubble.depth - 2 - s.rubble.lift(), s.rubble.top + 2 + s.rubble.lift());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (out.gardenCover > -3) {
            double cover = out.gardenCover;
            double rim = TMath.smoothstep(0.0, 10.0, cover);
            double topY = gardenTop(px, pz) - 2.5 * (1.0 - rim);
            double thick = 4.0 + 0.6 * Math.min(cover, 36.0) + 2.5 * edge.at(px / 11.0 + 40.0, pz / 11.0);
            out.gardenTop = topY;
            out.gardenBottom = topY - Math.max(2.0, thick);
            out.span(out.gardenBottom - 3, topY + 2);
        }
        if (!out.empty()) {
            out.scar = spansHere && scar(px, pz);
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
        if (c.gardenCover > -3 && py > c.gardenBottom - 3 && py < c.gardenTop + 2) {
            double d = Math.min(Math.min(c.gardenTop - py, py - c.gardenBottom), c.gardenCover);
            if (d > -2.5 && d < 2.5) {
                d += 1.1 * rock.at(x / 8.0, y / 6.0, z / 8.0);
            }
            best = Math.max(best, d);
        }
        for (int i = 0, n = c.pillars.size(); i < n; i++) {
            Pillar p = c.pillars.get(i);
            double d = switch (p.zone) {
                case DeepZones.GARDENS -> gardenShaft(p, x, y, z, px, py, pz);
                case DeepZones.HANGING -> massDensity(p, x, y, z, px, py, pz);
                case DeepZones.SHATTERED -> chunkDensity(p.cx, p.cz, p.top, p.platformR, p.capThick, 0, 0, 2.0, x, y, z, px, py, pz);
                default -> pillarDensity(p, c.scar, x, y, z, px, py, pz);
            };
            best = Math.max(best, d);
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
        for (int i = 0, n = c.roots.size(); i < n; i++) {
            Root r = c.roots.get(i);
            if (py > r.y0 || py < r.y1) {
                continue;
            }
            double rad = r.radius(py);
            double rho = Math.hypot(px - r.axisX(py), pz - r.axisZ(py));
            if (rho > rad + 1.5) {
                continue;
            }
            best = Math.max(best, rad - rho + 0.5 * rock.at(x / 4.0, y / 7.0, z / 4.0));
        }
        for (int i = 0, n = c.chunks.size(); i < n; i++) {
            Chunk k = c.chunks.get(i);
            best = Math.max(best, chunkDensity(k.x, k.z, k.top, k.r, k.depth, k.tiltX, k.tiltZ, k.jag, x, y, z, px, py, pz));
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
        if (snapped(p) && py > capBottom - p.flare) {
            // a snapped column: no platform, the shaft broken off in a jagged edge a few blocks down
            double brokenTop = p.top - 3.0 - 6.0 * Math.abs(edge.at(px / 3.5 + 20.0, pz / 3.5));
            return Math.min(brokenTop - py, p.shaftR * 1.15 + 1.6 * rock.at(x / 9.0, y / 11.0, z / 9.0) - rho);
        }
        if (cap) {
            r = p.platformR - Math.max(0, py - (localTop - 1.5)) * 0.8 - 2.2 * Math.max(0, edge.at(px / 6.0 + 7.0, pz / 6.0));
        } else if (py >= capBottom - p.flare) {
            double t = (py - (capBottom - p.flare)) / p.flare;
            double shaftTop = p.shaftR;
            r = TMath.lerp(shaftTop, p.platformR, Math.pow(t, 2.2));
        } else {
            r = shaftRadius(p, x, y, z, py, capBottom - p.flare);
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

    /** True for the Spans' small pillars that stand snapped: ruin teeth, too small for any structure to want. */
    static boolean snapped(Pillar p) {
        return p.zone == DeepZones.SPANS && p.platformR < 10.5 && Hashing.unit(Hashing.mix(p.ci * 31L + p.cj * 1_000_003L), 0) < 0.5;
    }

    /** A fluted column rising out of the dark: nearly straight, dissolving into shards near its foot. */
    private double shaftRadius(Pillar p, int x, int y, int z, double py, double shaftTop) {
        double t = (py - p.bottom) / Math.max(1.0, shaftTop - p.bottom);
        double r = p.shaftR * (0.82 + 0.18 * TMath.clamp(t, 0, 1));
        double foot = TMath.smoothstep(0.0, 22.0, py - p.bottom);
        if (foot < 1.0) {
            double shards = rock.at(x / 4.0 + 500.0, y / 3.0, z / 4.0);
            r = r * foot + (1.0 - foot) * (shards * p.shaftR - 1.0);
        }
        return r;
    }

    /** Lichen Gardens: the broad shaft under a mesa, flaring into the slab (whose own density covers the top). */
    private double gardenShaft(Pillar p, int x, int y, int z, double px, double py, double pz) {
        if (py < p.bottom || py > p.bodyTop) {
            return -8.0;
        }
        double dx = px - p.axisX(py);
        double dz = pz - p.axisZ(py);
        double rho = Math.sqrt(dx * dx + dz * dz);
        double flareFrom = p.bodyTop - p.flare;
        double r = py >= flareFrom
                ? TMath.lerp(p.shaftR, p.bodyR, Math.pow((py - flareFrom) / p.flare, 2.2))
                : shaftRadius(p, x, y, z, py, flareFrom);
        if (rho > r + 3.5) {
            return Math.max(-8.0, r - rho);
        }
        double flute = rho > 0.5 ? p.fluteAmp * Math.cos(Math.atan2(dz, dx) * p.flutes) : 0;
        return Math.min(p.bodyTop - py, r + flute + 1.6 * rock.at(x / 9.0, y / 11.0, z / 9.0) - rho);
    }

    /** Hanging Wood: how far below a mass's top its underside hangs at {@code q} (distance over radius). */
    static double hangDepth(double cap, double q) {
        return cap * Math.pow(Math.max(0, 1 - q), 2.0);
    }

    /** Hanging Wood: an inverted mountain, flat and bumpy on top, narrowing to a point far below. */
    private double massDensity(Pillar p, int x, int y, int z, double px, double py, double pz) {
        double rho = Math.hypot(px - p.cx, pz - p.cz);
        double q = rho / p.platformR;
        if (q > 1.15 || py > p.top + 3) {
            return -8.0;
        }
        double belly = hangDepth(p.capThick, q);
        double under = p.top - belly;
        if (py < under - 5) {
            return -8.0;
        }
        double dTop = p.top + 0.9 * topRelief.at(px / 9.0, pz / 9.0) - py;
        double dUnder = py - under + 2.5 * edge.at(px / 13.0 + 90.0, pz / 13.0);
        double d = Math.min(Math.min(dTop, dUnder), p.platformR - rho);
        if (d > -2.5 && d < 2.5) {
            d += 1.2 * rock.at(x / 7.0, y / 5.0, z / 7.0);
        }
        return d;
    }

    /** A floating chunk: a top (tilted and broken by {@code tiltX}, {@code tiltZ}, {@code jag}) over a cone narrowing to a point. */
    private double chunkDensity(double cx, double cz, double top0, double r, double depth, double tiltX, double tiltZ, double jag, int x,
            int y, int z, double px, double py, double pz) {
        double lift = Math.hypot(tiltX, tiltZ) * r + jag + 1;
        if (py > top0 + 2 + lift || py < top0 - depth - 2 - lift) {
            return -8.0;
        }
        double rho = Math.hypot(px - cx, pz - cz);
        if (rho > r + 3) {
            return -8.0;
        }
        double top = top0 + tiltX * (px - cx) + tiltZ * (pz - cz) + jag * topRelief.at(px / 5.0 + 11.0, pz / 5.0);
        double dTop = top - py;
        double t = TMath.clamp((top - py) / depth, 0, 1);
        double rr = r * Math.pow(1.0 - t, 0.75) - 0.6 * TMath.smoothstep(1.5, 0.0, top - py);
        double d = Math.min(dTop, rr - rho);
        if (d > -2.5 && d < 2.5) {
            d += 1.3 * rock.at(x / 6.0, y / 5.0, z / 6.0);
        }
        return d;
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
        if (s.breakHalf > 0 && Math.abs(bestT - s.breakT) < s.breakHalf * (1.0 + 0.5 * n)) {
            return -1.0;
        }
        if (s.bridge) {
            // ragged edges (whole blocks missing along the sides) and a deck that rises and dips a block or two
            double edgeBite = 1.6 * edge.at(px / 5.0, pz / 5.0);
            double halfWidth = s.radius * (1.0 + 0.35 * ends) + edgeBite;
            double topY = cyAt + 1.2 + 1.6 * topRelief.at(px / 11.0 + 50.0, pz / 11.0);
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
