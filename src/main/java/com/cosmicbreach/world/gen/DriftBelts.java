package com.cosmicbreach.world.gen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The Drift's asteroid field (GDD 2.2): lumpy rocks and angular shards 4 to 30 blocks across, clustered in
 * belts a few hundred blocks long with open lanes between, about one asteroid per 40-block cube inside a
 * belt, at Y 162 to 298.
 *
 * <p>How it is built. Belts are gently curved bands 200 to 500 blocks long and 70 to 130 wide, one per
 * 360-block cell at most (so lanes open between them), each with its own height and thickness
 * ({@link #beltAt}). Space is cut into 40-block cubes ({@link #CELL}); a cube
 * holds one asteroid with a probability that follows the belt's strength at its centre. An asteroid is an
 * ellipsoid with random axes and rotation, roughened by 3D noise (lumps at its own scale plus fine grain);
 * a quarter are faceted shards (a cuboctahedral norm), and big ones carry a crater. Lone asteroids dot the
 * lanes. None within the Breach's radius.
 *
 * <p>Knobs: {@link #CELL}, {@link #BELT_CELL}, the belt size ranges in {@link #beltAt}, the radius curve and shard share in
 * {@link Asteroid}'s constructor.
 */
public final class DriftBelts {
    public static final int CELL = 40;
    static final int K_MIN = 4;
    static final int K_MAX = 7;
    public static final int FLOOR_Y = 162;
    public static final int CEIL_Y = 298;
    private static final int TAG = 21;

    private final long salt;
    private final SeededNoise beltWarpX;
    private final SeededNoise beltWarpZ;
    private final SeededNoise lumps;
    private final ThreadLocal<Long2ObjectOpenHashMap<Asteroid>> cache = ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);
    private static final Asteroid NONE = new Asteroid();

    public DriftBelts(long salt) {
        this.salt = salt;
        this.beltWarpX = new SeededNoise(salt + 202);
        this.beltWarpZ = new SeededNoise(salt + 203);
        this.lumps = new SeededNoise(salt + 207);
    }

    /** One asteroid, immutable. */
    public static final class Asteroid {
        public final double cx;
        public final double cy;
        public final double cz;
        /** Radius in blocks (half its size across). */
        public final double r;
        /** Bounding radius: no rock of this asteroid farther than this from its centre. */
        public final double bound;
        public final boolean shard;
        public final long seed;
        final double sx, sy, sz;
        final double m00, m01, m02, m10, m11, m12, m20, m21, m22;
        final double lump;
        final double lumpScale;
        final double ox, oy, oz;
        final double craterX, craterY, craterZ, craterR;
        /** Height of a flat top cut (a broken face to stand on), or +infinity for none. */
        public final double flatTop;
        /** Small companion rocks: x, y, z, radius per moonlet. */
        final double[] moons;

        private Asteroid() {
            cx = cy = cz = r = bound = 0;
            shard = false;
            seed = 0;
            sx = sy = sz = 1;
            m00 = m11 = m22 = 1;
            m01 = m02 = m10 = m12 = m20 = m21 = 0;
            lump = lumpScale = ox = oy = oz = 0;
            craterX = craterY = craterZ = craterR = 0;
            flatTop = Double.POSITIVE_INFINITY;
            moons = new double[0];
        }

        Asteroid(long h, double cx, double cy, double cz, double belt) {
            this.seed = h;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            double size = 2.0 + 13.0 * Math.pow(Hashing.unit(h, 10), 1.9);
            this.r = Math.max(2.0, Math.min(15.0, size * (0.8 + 0.4 * belt)));
            this.shard = Hashing.unit(h, 11) < 0.25;
            double a = 1.0;
            double b = shard ? 0.42 + 0.2 * Hashing.unit(h, 12) : 0.7 + 0.3 * Hashing.unit(h, 12);
            double c = shard ? 0.42 + 0.2 * Hashing.unit(h, 13) : 0.65 + 0.35 * Hashing.unit(h, 13);
            this.sx = 1.0 / (a * r);
            this.sy = 1.0 / (b * r);
            this.sz = 1.0 / (c * r);
            double yaw = Hashing.unit(h, 14) * Math.PI * 2;
            double tilt = (Hashing.unit(h, 15) - 0.5) * Math.PI;
            double roll = Hashing.unit(h, 16) * Math.PI * 2;
            double cy1 = Math.cos(yaw), sy1 = Math.sin(yaw);
            double ct = Math.cos(tilt), st = Math.sin(tilt);
            double cr = Math.cos(roll), sr = Math.sin(roll);
            // R = Rx(roll) * Rz(tilt) * Ry(yaw): world to local
            double[][] ry = {{cy1, 0, -sy1}, {0, 1, 0}, {sy1, 0, cy1}};
            double[][] rz = {{ct, st, 0}, {-st, ct, 0}, {0, 0, 1}};
            double[][] rx = {{1, 0, 0}, {0, cr, sr}, {0, -sr, cr}};
            double[][] m = mul(rx, mul(rz, ry));
            m00 = m[0][0]; m01 = m[0][1]; m02 = m[0][2];
            m10 = m[1][0]; m11 = m[1][1]; m12 = m[1][2];
            m20 = m[2][0]; m21 = m[2][1]; m22 = m[2][2];
            this.lump = shard ? 0.08 : 0.16 + 0.14 * Hashing.unit(h, 17);
            this.lumpScale = 1.0 / (r * 0.55 + 2.5);
            this.ox = Hashing.unit(h, 18) * 1000;
            this.oy = Hashing.unit(h, 19) * 1000;
            this.oz = Hashing.unit(h, 20) * 1000;
            if (r >= 8 && !shard) {
                double u = Hashing.unit(h, 21) * 2 - 1;
                double t = Hashing.unit(h, 22) * Math.PI * 2;
                double k = Math.sqrt(1 - u * u);
                craterX = cx + k * Math.cos(t) * r * 0.95;
                craterY = cy + u * r * 0.95;
                craterZ = cz + k * Math.sin(t) * r * 0.95;
                craterR = r * (0.3 + 0.15 * Hashing.unit(h, 23));
            } else {
                craterX = craterY = craterZ = craterR = 0;
            }
            this.flatTop = !shard && r >= 6 && Hashing.unit(h, 24) < 0.5
                    ? cy + r * (0.35 + 0.25 * Hashing.unit(h, 25)) : Double.POSITIVE_INFINITY;
            int moonCount = !shard && r >= 7 ? (int) (Hashing.unit(h, 26) * 4) : 0;
            this.moons = new double[moonCount * 4];
            double reach = r * (1.0 + lump) + 2.0;
            for (int i = 0; i < moonCount; i++) {
                double u = Hashing.unit(h, 30 + i * 4) * 1.6 - 0.8;
                double t = Hashing.unit(h, 31 + i * 4) * Math.PI * 2;
                double k = Math.sqrt(1 - u * u);
                double dist = r * (1.3 + 0.45 * Hashing.unit(h, 32 + i * 4));
                double mr = 1.5 + 1.7 * Hashing.unit(h, 33 + i * 4);
                moons[i * 4] = cx + k * Math.cos(t) * dist;
                moons[i * 4 + 1] = cy + u * dist;
                moons[i * 4 + 2] = cz + k * Math.sin(t) * dist;
                moons[i * 4 + 3] = mr;
                reach = Math.max(reach, dist + mr + 2.0);
            }
            this.bound = reach;
        }

        private static double[][] mul(double[][] a, double[][] b) {
            double[][] o = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    o[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
                }
            }
            return o;
        }

        /** True for rocks big enough to carry a Driftwood tree and a Nebulite core. */
        public boolean big() {
            return r >= 8.0;
        }
    }

    /** Belts sit on a 360-block jittered grid, at most one per cell. */
    static final double BELT_CELL = 360.0;
    private static final int TAG_BELT = 22;

    /** The belt data at a point: its strength (0 in the lanes, 1 on a belt's spine), centre height and half thickness. */
    public record BeltPoint(double strength, double centreY, double halfThickness) {
        static final BeltPoint LANE = new BeltPoint(0, 230, 40);
    }

    /**
     * The belt at (x, z). Each belt is a gently curved band 200 to 500 blocks long and 70 to 130 wide at a
     * random heading, with its own height (Y 200 to 260) and thickness; strength falls off toward its sides
     * and rounded ends. Where belts overlap the strongest wins.
     */
    public BeltPoint beltAt(double x, double z) {
        double wx = x + 30.0 * beltWarpX.at(x / 180.0, z / 180.0);
        double wz = z + 30.0 * beltWarpZ.at(x / 180.0 + 17.0, z / 180.0 - 9.0);
        int bi = (int) Math.floor(wx / BELT_CELL);
        int bj = (int) Math.floor(wz / BELT_CELL);
        BeltPoint best = BeltPoint.LANE;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                long h = Hashing.hash(salt, TAG_BELT, bi + di, bj + dj, 0);
                if (Hashing.unit(h, 0) > 0.82) {
                    continue;
                }
                double cx = (bi + di + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * 0.3) * BELT_CELL;
                double cz = (bj + dj + 0.5 + (Hashing.unit(h, 2) * 2 - 1) * 0.3) * BELT_CELL;
                double length = 200.0 + 300.0 * Hashing.unit(h, 3);
                double width = 70.0 + 60.0 * Hashing.unit(h, 4);
                double angle = Hashing.unit(h, 5) * Math.PI;
                double bend = (Hashing.unit(h, 6) - 0.5) * 0.5;
                double ca = Math.cos(angle);
                double sa = Math.sin(angle);
                double u = (wx - cx) * ca + (wz - cz) * sa;
                double v = -(wx - cx) * sa + (wz - cz) * ca;
                double un = 2.0 * u / length;
                if (Math.abs(un) >= 1.0) {
                    continue;
                }
                v -= bend * length * (un * un - 0.3);
                double vn = 2.0 * v / width;
                if (Math.abs(vn) >= 1.0) {
                    continue;
                }
                double strength = (1.0 - Math.pow(Math.abs(un), 4)) * (1.0 - vn * vn);
                if (strength > best.strength()) {
                    double centreY = 230.0 + 30.0 * (Hashing.unit(h, 7) * 2 - 1);
                    double half = 30.0 + 20.0 * Hashing.unit(h, 8);
                    best = new BeltPoint(strength, centreY, half);
                }
            }
        }
        return best;
    }

    /** Belt strength at (x, z): 0 in the lanes, up to 1 along a belt's spine. */
    public double belt(double x, double z) {
        return beltAt(x, z).strength();
    }

    /** The asteroid in 40-block cube (i, j, k), or null. */
    public @Nullable Asteroid asteroid(int i, int j, int k) {
        if (k < K_MIN || k > K_MAX) {
            return null;
        }
        Long2ObjectOpenHashMap<Asteroid> map = cache.get();
        long key = ((long) i << 40) ^ ((long) (j & 0xFFFFF) << 20) ^ (k & 0xFFFFF);
        Asteroid a = map.get(key);
        if (a == null) {
            if (map.size() > 8192) {
                map.clear();
            }
            a = build(i, j, k);
            map.put(key, a == null ? NONE : a);
            return a;
        }
        return a == NONE ? null : a;
    }

    private @Nullable Asteroid build(int i, int j, int k) {
        long h = Hashing.hash(salt, TAG, i, j, k);
        double cx = (i + 0.5 + (Hashing.unit(h, 0) * 2 - 1) * 0.3) * CELL;
        double cz = (j + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * 0.3) * CELL;
        double cy = (k + 0.5 + (Hashing.unit(h, 2) * 2 - 1) * 0.3) * CELL;
        BeltPoint bp = beltAt(cx, cz);
        double belt = bp.strength();
        double vert = TMath.clamp(1.0 - TMath.sq((cy - bp.centreY()) / bp.halfThickness()), 0, 1);
        double p = Math.pow(belt, 0.5) * Math.pow(vert, 0.7) * 1.1;
        boolean lone = Hashing.unit(h, 3) < 0.012;
        if (Hashing.unit(h, 4) >= p && !lone) {
            return null;
        }
        Asteroid a = new Asteroid(h, cx, cy, cz, lone ? 0.2 : belt);
        double lo = FLOOR_Y + a.bound;
        double hi = CEIL_Y - a.bound;
        if (a.cy < lo || a.cy > hi) {
            a = new Asteroid(h, cx, TMath.clamp(cy, lo, hi), cz, lone ? 0.2 : belt);
        }
        double d = Math.hypot(cx, cz);
        if (d < BreachShape.DRIFT_RADIUS + a.bound + 6) {
            return null;
        }
        return a;
    }

    /** Asteroids whose bounding sphere reaches column (x, z), into {@code out} (cleared first). */
    public void candidates(int x, int z, List<Asteroid> out) {
        out.clear();
        int i0 = Math.floorDiv(x, CELL);
        int j0 = Math.floorDiv(z, CELL);
        double px = x + 0.5;
        double pz = z + 0.5;
        for (int i = i0 - 1; i <= i0 + 1; i++) {
            for (int j = j0 - 1; j <= j0 + 1; j++) {
                for (int k = K_MIN; k <= K_MAX; k++) {
                    Asteroid a = asteroid(i, j, k);
                    if (a != null && TMath.sq(px - a.cx) + TMath.sq(pz - a.cz) <= a.bound * a.bound) {
                        out.add(a);
                    }
                }
            }
        }
    }

    /** Density of one asteroid at a block, in blocks (positive is rock). */
    public double density(Asteroid a, int x, int y, int z) {
        double dx = x + 0.5 - a.cx;
        double dy = y + 0.5 - a.cy;
        double dz = z + 0.5 - a.cz;
        if (dx * dx + dy * dy + dz * dz > a.bound * a.bound) {
            return -8.0;
        }
        double lx = (a.m00 * dx + a.m01 * dy + a.m02 * dz) * a.sx;
        double ly = (a.m10 * dx + a.m11 * dy + a.m12 * dz) * a.sy;
        double lz = (a.m20 * dx + a.m21 * dy + a.m22 * dz) * a.sz;
        double dist;
        if (a.shard) {
            double ax = Math.abs(lx);
            double ay = Math.abs(ly);
            double az = Math.abs(lz);
            dist = Math.max(Math.max(ax, Math.max(ay, az)) * 0.95, (ax + ay + az) * 0.62);
        } else {
            dist = Math.sqrt(lx * lx + ly * ly + lz * lz);
        }
        double base = (1.0 - dist) * a.r;
        double n = lumps.at(dx * a.lumpScale + a.ox, dy * a.lumpScale + a.oy, dz * a.lumpScale + a.oz);
        double fine = lumps.at(x / 3.3 + a.oy, y / 3.3, z / 3.3 + a.ox);
        double v = base + a.lump * a.r * n + 0.55 * fine;
        if (a.flatTop < Double.POSITIVE_INFINITY) {
            v = Math.min(v, a.flatTop + 0.6 * fine - (y + 0.5));
        }
        for (int i = 0; i < a.moons.length; i += 4) {
            double md = Math.sqrt(TMath.sq(x + 0.5 - a.moons[i]) + TMath.sq(y + 0.5 - a.moons[i + 1]) + TMath.sq(z + 0.5 - a.moons[i + 2]));
            v = Math.max(v, a.moons[i + 3] - md + 0.5 * fine);
        }
        if (a.craterR > 0) {
            double cd = Math.sqrt(TMath.sq(x + 0.5 - a.craterX) + TMath.sq(y + 0.5 - a.craterY) + TMath.sq(z + 0.5 - a.craterZ));
            v = Math.min(v, cd - a.craterR);
        }
        return v;
    }
}
