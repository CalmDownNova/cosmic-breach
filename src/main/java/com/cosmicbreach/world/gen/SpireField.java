package com.cosmicbreach.world.gen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The Shattered Spires' spires (GDD 2.2): faceted stone needles 20 to 90 tall, 2 to 7 in radius at the base,
 * tapering to a point; 15% lean 5 to 20 degrees; 10% have snapped, their stump showing a Starsteel core and
 * the fallen top lying beside them, across a gap when the stump stands near an edge.
 *
 * <p>Spires are placed on a jittered grid ({@link #CELL}), 45% of cells on Shattered Spires islands and 3.5%
 * on Sunfield Terraces, on land away from the rim and off the necks. Everything about a spire follows from
 * its cell's hash and the island model, so the feature that builds them ({@code SpireFieldFeature}) can
 * draw, chunk by chunk, only the part of each spire inside the chunk being decorated: a 90-block spire or
 * a 50-block fallen top never writes outside the one chunk a feature owns.
 */
public final class SpireField {
    public static final int CELL = 22;
    /** The farthest any part of a spire reaches horizontally from its cell's corner, with margin. */
    public static final int REACH = 72;
    static final double SPIRES_CHANCE = 0.45;
    static final double SUNFIELD_CHANCE = 0.035;
    private static final int TAG = 41;

    private final long salt;
    private final ReachIslands reach;
    private final ThreadLocal<Long2ObjectOpenHashMap<Object>> cache = ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);
    private static final Object NONE = new Object();

    public SpireField(long salt, ReachIslands reach) {
        this.salt = salt;
        this.reach = reach;
    }

    /** One spire. Coordinates are block space; the base sits on the island top at {@link #groundY}. */
    public static final class Spire {
        public final long seed;
        public final double bx;
        public final double bz;
        public final int groundY;
        public final double height;
        public final double radius;
        public final double leanX;
        public final double leanZ;
        public final boolean crystalTip;
        public final int facets;
        public final double rotation;
        public final double twist;
        /** Height above the ground where it broke, or the full height when intact. */
        public final double standing;
        public final boolean snapped;
        /** The fallen top: a cone from (f0) with radius fr0 to (f1) with radius fr1. */
        public final double f0x, f0y, f0z, f1x, f1y, f1z, fr0, fr1;
        public final int minX, maxX, minZ, maxZ, minY, maxY;

        Spire(long seed, double bx, double bz, int groundY, double height, double radius, double leanX, double leanZ,
                boolean crystalTip, int facets, double rotation, double twist, double standing, boolean snapped,
                double[] fallen) {
            this.seed = seed;
            this.bx = bx;
            this.bz = bz;
            this.groundY = groundY;
            this.height = height;
            this.radius = radius;
            this.leanX = leanX;
            this.leanZ = leanZ;
            this.crystalTip = crystalTip;
            this.facets = facets;
            this.rotation = rotation;
            this.twist = twist;
            this.standing = standing;
            this.snapped = snapped;
            if (fallen != null) {
                f0x = fallen[0];
                f0y = fallen[1];
                f0z = fallen[2];
                f1x = fallen[3];
                f1y = fallen[4];
                f1z = fallen[5];
                fr0 = fallen[6];
                fr1 = fallen[7];
            } else {
                f0x = f0y = f0z = f1x = f1y = f1z = fr0 = fr1 = 0;
            }
            double flare = radius * 1.6 + 2;
            double topX = bx + leanX * standing;
            double topZ = bz + leanZ * standing;
            double x0 = Math.min(bx, topX) - flare;
            double x1 = Math.max(bx, topX) + flare;
            double z0 = Math.min(bz, topZ) - flare;
            double z1 = Math.max(bz, topZ) + flare;
            int y1 = groundY + (int) Math.ceil(standing) + 2;
            if (fallen != null) {
                double pad = fr0 + 2;
                x0 = Math.min(x0, Math.min(f0x, f1x) - pad);
                x1 = Math.max(x1, Math.max(f0x, f1x) + pad);
                z0 = Math.min(z0, Math.min(f0z, f1z) - pad);
                z1 = Math.max(z1, Math.max(f0z, f1z) + pad);
                y1 = Math.max(y1, (int) Math.ceil(Math.max(f0y, f1y) + pad));
            }
            this.minX = (int) Math.floor(x0);
            this.maxX = (int) Math.ceil(x1);
            this.minZ = (int) Math.floor(z0);
            this.maxZ = (int) Math.ceil(z1);
            this.minY = groundY - 5;
            this.maxY = Math.min(475, y1);
        }

        /** Radius of the standing part at {@code h} blocks above the ground. */
        public double radiusAt(double h) {
            double t = TMath.clamp(h / height, 0, 1);
            double r = radius * Math.pow(1.0 - t, 0.9) + 0.45;
            if (h < 0) {
                return radius * 1.55;
            }
            return r * (1.0 + 0.55 * Math.exp(-h / 2.5));
        }

        /** Horizontal distance of (dx, dz) from the axis measured in this spire's polygon, at height h. */
        public double polygonDistance(double dx, double dz, double h) {
            double a0 = rotation + twist * h;
            double best = 0;
            double inv = 1.0 / Math.cos(Math.PI / facets);
            for (int k = 0; k < facets; k++) {
                double a = a0 + k * (Math.PI * 2 / facets);
                double d = dx * Math.cos(a) + dz * Math.sin(a);
                if (d > best) {
                    best = d;
                }
            }
            return best * inv;
        }

        public double axisX(double h) {
            return bx + leanX * h;
        }

        public double axisZ(double h) {
            return bz + leanZ * h;
        }

        public boolean touches(int x0, int z0, int x1, int z1) {
            return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
        }
    }

    /** The spire of cell (i, j), or null. */
    public @Nullable Spire spire(int i, int j) {
        Long2ObjectOpenHashMap<Object> map = cache.get();
        long key = ReachIslands.pack(i, j);
        Object o = map.get(key);
        if (o == null) {
            if (map.size() > 4096) {
                map.clear();
            }
            Spire s = build(i, j);
            map.put(key, s == null ? NONE : s);
            return s;
        }
        return o == NONE ? null : (Spire) o;
    }

    /** Every spire whose bounds touch the box [x0, x1] x [z0, z1], into {@code out}. */
    public void spiresTouching(int x0, int z0, int x1, int z1, List<Spire> out) {
        out.clear();
        int i0 = Math.floorDiv(x0 - REACH, CELL);
        int i1 = Math.floorDiv(x1 + REACH, CELL);
        int j0 = Math.floorDiv(z0 - REACH, CELL);
        int j1 = Math.floorDiv(z1 + REACH, CELL);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                Spire s = spire(i, j);
                if (s != null && s.touches(x0, z0, x1, z1)) {
                    out.add(s);
                }
            }
        }
    }

    private @Nullable Spire build(int i, int j) {
        long h = Hashing.hash(salt, TAG, i, j, 0);
        double bx = (i + 0.5 + (Hashing.unit(h, 0) * 2 - 1) * 0.4) * CELL;
        double bz = (j + 0.5 + (Hashing.unit(h, 1) * 2 - 1) * 0.4) * CELL;
        ReachIslands.Column col = new ReachIslands.Column();
        reach.sample(bx, bz, col);
        if (!col.island || col.isle == null || col.edge < 2) {
            return null;
        }
        double chance = col.isle.sunfield ? SUNFIELD_CHANCE : SPIRES_CHANCE;
        if (Hashing.unit(h, 2) >= chance) {
            return null;
        }
        double height = 20.0 + 70.0 * Math.pow(Hashing.unit(h, 3), 1.25);
        double radius = 2.0 + 5.0 * Math.pow((height - 20.0) / 70.0, 0.7) * (0.8 + 0.2 * Hashing.unit(h, 4));
        boolean snapped = Hashing.unit(h, 5) < 0.10 && height > 28;
        if (col.neckness > 0.25 || (col.edge < radius * 1.6 + 3 && !snapped)) {
            return null;
        }
        int groundY = (int) Math.floor(col.top - 0.5);
        double leanX = 0;
        double leanZ = 0;
        if (Hashing.unit(h, 6) < 0.15) {
            double angle = Math.toRadians(5.0 + 15.0 * Hashing.unit(h, 7));
            double dir = Hashing.unit(h, 8) * Math.PI * 2;
            leanX = Math.tan(angle) * Math.cos(dir);
            leanZ = Math.tan(angle) * Math.sin(dir);
        }
        boolean crystalTip = Hashing.unit(h, 9) < 0.35;
        int facets = Hashing.unit(h, 10) < 0.5 ? 6 : 8;
        double rotation = Hashing.unit(h, 11) * Math.PI;
        double twist = (Hashing.unit(h, 12) - 0.5) * 0.05;
        double standing = height;
        double[] fallen = null;
        if (snapped) {
            standing = height * (0.42 + 0.25 * Hashing.unit(h, 13));
            fallen = fallenTop(h, bx, bz, groundY, height, radius, standing, leanX, leanZ, col);
        }
        return new Spire(h, bx, bz, groundY, height, radius, leanX, leanZ, crystalTip, facets, rotation, twist, standing, snapped, fallen);
    }

    /**
     * Where the snapped top lies: pointing at the nearest gap when the stump is within its length of an edge
     * (so it bridges or overhangs), otherwise any way. It rests on the ground where there is ground, and keeps
     * its height over the void (lodged).
     */
    private double[] fallenTop(long h, double bx, double bz, int groundY, double height, double radius, double standing,
            double leanX, double leanZ, ReachIslands.Column baseCol) {
        double length = height - standing;
        double r0 = radius * Math.pow(1.0 - standing / height, 0.9) + 0.45;
        double dir = Hashing.unit(h, 14) * Math.PI * 2;
        if (baseCol.edge < length * 0.8) {
            // look around for the direction in which the rim is nearest
            double bestEdge = Double.MAX_VALUE;
            ReachIslands.Column probe = new ReachIslands.Column();
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                reach.sample(bx + Math.cos(a) * baseCol.edge, bz + Math.sin(a) * baseCol.edge, probe);
                double e = probe.island ? probe.edge : -10;
                if (e < bestEdge) {
                    bestEdge = e;
                    dir = a;
                }
            }
        }
        double cx = Math.cos(dir);
        double cz = Math.sin(dir);
        double startOff = radius * 1.2 + 1.0;
        double sx = bx + leanX * standing * 0.3 + cx * startOff;
        double sz = bz + leanZ * standing * 0.3 + cz * startOff;
        double ex = sx + cx * length;
        double ez = sz + cz * length;
        double sy = groundY + 0.5 + r0 * 0.8;
        ReachIslands.Column endCol = new ReachIslands.Column();
        reach.sample(ex, ez, endCol);
        double ey = endCol.island && endCol.edge > 1 ? Math.floor(endCol.top - 0.5) + 1.2 : sy - length * 0.08;
        return new double[] {sx, sy, sz, ex, ey, ez, r0, 0.5};
    }
}
