package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.world.gen.Hashing;
import com.cosmicbreach.world.gen.SeededNoise;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The Leviathan Rift (Thalassine Leviathan design v1, "The lair"), pure: what goes where, from its centre and a seed.
 *
 * <ul>
 *   <li>A hollow sphere {@value #RX} blocks in radius across (it fits the Drift's band by being a little flattened:
 *       {@value #RY} blocks tall, Y 167 to 291 round a centre at {@value #CENTRE_Y}), its shell a lattice of ringed
 *       asteroids (Driftstone with Rimeglass rings) with gaps a manta flies through, closed below latitude
 *       {@value #BOWL_LAT} degrees into a bowl of rubble that catches anyone who drops (about 50 blocks under the
 *       platforms; falls inside the Rift land softly, see {@code LeviathanRift}).</li>
 *   <li>Inside, open air: the central asteroid (radius {@value #CORE_RADIUS}, Driftstone round a Rimeglass geode core
 *       whose crystals break through near its poles), and a ring of {@value #PLATFORMS} platform asteroids 10 to 12
 *       across at radius {@value #PLATFORM_RING}, their tops staggered on the orbit's wave (within 6 blocks of the centre's equator), joined
 *       by Driftwood bridges so a player who can't make a jump always has a way round. Nothing but the core stands
 *       within {@value #CLEAR_RADIUS} of the axis near the equator: that is the Leviathan's orbit.</li>
 *   <li>The entrance: a gap in the shell with a ledge outside and a walkway to platform 0, the platform nearest the
 *       entrance, which carries the Leviathan's altar, a Rimeglass bell.</li>
 *   <li>Two updrafts (the Observatory's gravity lifts) rise from the bowl to platforms 2 and 6: the climb back.</li>
 * </ul>
 */
public final class RiftLayout {
    public static final int RX = 80;
    public static final int RY = 62;
    public static final int CENTRE_Y = 229;
    public static final int CORE_RADIUS = 12;
    public static final double PLATFORM_RING = 30.0;
    public static final int PLATFORMS = 8;
    /** How far over the block it stands on a player's swing is centred (its chest, a block up plus 0.55 of 1.8). */
    public static final double SWING_BAND = 2.0;
    /** Just outside the Leviathan's widest flank in its orbit (radius 22 plus its head's 2.5): platform rock stops here. */
    public static final double CLEAR_RADIUS = 24.7;
    public static final double BOWL_LAT = -25.0;
    /** The bowl's inner surface, as a fraction of the shell's radii. */
    public static final double BOWL_INNER = 0.965;
    /** How far the shell's rocks and rings reach outside the sphere. */
    private static final double OUTER = 1.13;
    private static final int SHELL_ROCKS = 150;
    private static final double GOLDEN = Math.PI * (3.0 - Math.sqrt(5.0));

    public enum Kind { KEEP, AIR, DRIFTSTONE, BRICKS, RIMEGLASS, SINGING_RIMEGLASS, NEBULITE, PLANKS, SLAB, BELL, UPDRAFT, UPDRAFT_TOP }

    /** A platform: its index, angle round the axis, centre column, top block's Y and top radius. */
    public record Platform(int index, double angle, double x, double z, int top, int radius) {
        public Vec3 topCentre() {
            return new Vec3(x, top + 1.0, z);
        }
    }

    /** One of the shell's asteroids and its Rimeglass ring (ring radius 0 for none). */
    record Rock(double x, double y, double z, double r, double nx, double ny, double nz, double ring, long seed) {
    }

    /** A bridge: a walkway 3 wide from (ax, az) at walking height {@code sa} to (bx, bz) at {@code sb}. */
    record Bridge(double ax, double az, double sa, double bx, double bz, double sb, double halfWidth) {
    }

    /** An updraft column: its column, bottom and top Y, and the step it pushes riders off toward at the top. */
    public record Updraft(int x, int z, int bottom, int top, int stepX, int stepZ) {
    }

    private final int cx;
    private final int cy;
    private final int cz;
    private final long seed;
    private final double entrance;
    private final double wave;
    private final Platform[] platforms = new Platform[PLATFORMS];
    private final List<Rock> rocks = new ArrayList<>();
    private final List<Bridge> bridges = new ArrayList<>();
    private final List<Updraft> updrafts = new ArrayList<>();
    private final Rock ledge;
    private final SeededNoise lumps;

    public RiftLayout(int cx, int cy, int cz, long seed) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.seed = seed;
        this.lumps = new SeededNoise(seed ^ 0x5EA5EAL);
        long h = Hashing.hash(seed, 0x1E7A, cx, cy, cz);
        this.entrance = Hashing.unit(h, 0) * 2.0 * Math.PI;
        this.wave = Hashing.unit(h, 1) * 2.0 * Math.PI;
        for (int k = 0; k < PLATFORMS; k++) {
            double a = entrance + k * 2.0 * Math.PI / PLATFORMS;
            int r = 5 + (int) (Hashing.unit(h, 10 + k) * 2.0); // 10 to 12 across: every inner rim within a swing of the orbit
            double px = cx + 0.5 + PLATFORM_RING * Math.cos(a);
            double pz = cz + 0.5 + PLATFORM_RING * Math.sin(a);
            // the top rides the orbit's wave: a swing's band (centred two blocks over the top) meets the body going by
            int top = (int) Math.round(cy + 0.5 + LeviathanMoves.WAVE * Math.sin(2.0 * a + wave) - SWING_BAND);
            platforms[k] = new Platform(k, a, px, pz, top, r);
        }
        for (int k = 0; k < PLATFORMS; k++) {
            Platform p = platforms[k];
            Platform q = platforms[(k + 1) % PLATFORMS];
            double dx = q.x() - p.x();
            double dz = q.z() - p.z();
            double len = Math.hypot(dx, dz);
            double ux = dx / len;
            double uz = dz / len;
            bridges.add(new Bridge(p.x() + ux * (p.radius() - 1.0), p.z() + uz * (p.radius() - 1.0), p.top() + 1.0,
                    q.x() - ux * (q.radius() - 1.0), q.z() - uz * (q.radius() - 1.0), q.top() + 1.0, 1.5));
        }
        // the entrance: a walkway from platform 0 out to a ledge beyond the shell's gap
        Platform p0 = platforms[0];
        double ex = Math.cos(entrance);
        double ez = Math.sin(entrance);
        double ledgeR = RX + 7.0;
        this.ledge = new Rock(cx + 0.5 + ex * ledgeR, p0.top(), cz + 0.5 + ez * ledgeR, 5.5, 0, 1, 0, 0, h);
        bridges.add(new Bridge(p0.x() + ex * (p0.radius() - 1.0), p0.z() + ez * (p0.radius() - 1.0), p0.top() + 1.0,
                cx + 0.5 + ex * (ledgeR - 4.0), cz + 0.5 + ez * (ledgeR - 4.0), p0.top() + 1.0, 1.5));
        // the shell: Fibonacci points on the flattened sphere, clear of the entrance
        double phase = Hashing.unit(h, 3) * 2.0 * Math.PI;
        for (int i = 0; i < SHELL_ROCKS; i++) {
            double sinLat = 1.0 - 2.0 * (i + 0.5) / SHELL_ROCKS;
            double cosLat = Math.sqrt(Math.max(0.0, 1.0 - sinLat * sinLat));
            double lon = i * GOLDEN + phase;
            double x = cx + 0.5 + RX * cosLat * Math.cos(lon);
            double y = cy + 0.5 + RY * sinLat;
            double z = cz + 0.5 + RX * cosLat * Math.sin(lon);
            double gx = cx + 0.5 + ex * RX;
            double gz = cz + 0.5 + ez * RX;
            if (Math.hypot(x - gx, z - gz) < 20.0 && Math.abs(y - p0.top()) < 18.0) {
                continue;
            }
            long rh = Hashing.hash(seed, 0x5E11, i, 0, 0);
            double r = 5.0 + 3.0 * Hashing.unit(rh, 0);
            double ring = Hashing.unit(rh, 1) < 0.72 ? r + 2.2 + Hashing.unit(rh, 2) * 1.5 : 0.0;
            double tilt = 0.35 + Hashing.unit(rh, 3) * 0.9;
            double spin = Hashing.unit(rh, 4) * 2.0 * Math.PI;
            rocks.add(new Rock(x, y, z, r, Math.sin(tilt) * Math.cos(spin), Math.cos(tilt), Math.sin(tilt) * Math.sin(spin), ring, rh));
        }
        // updrafts from the bowl up the outer edge of platforms 2 and 6, pushing riders in onto them
        for (int k : new int[] {2, 6}) {
            Platform p = platforms[k];
            double r = PLATFORM_RING + p.radius() + 1.0;
            int ux = (int) Math.floor(cx + 0.5 + r * Math.cos(p.angle()));
            int uz = (int) Math.floor(cz + 0.5 + r * Math.sin(p.angle()));
            double inX = -Math.cos(p.angle());
            double inZ = -Math.sin(p.angle());
            int sx = Math.abs(inX) >= Math.abs(inZ) ? (int) Math.signum(inX) : 0;
            int sz = sx == 0 ? (int) Math.signum(inZ) : 0;
            int bottom = (int) Math.floor(bowlSurface(Math.hypot(ux + 0.5 - cx - 0.5, uz + 0.5 - cz - 0.5))) + 1;
            updrafts.add(new Updraft(ux, uz, bottom, p.top() + 2, sx, sz));
        }
    }

    // ------------------------------------------------------------------ what the rest reads

    /**
     * The phase of the Leviathan's orbit wave in this lair ({@link LeviathanOrbit#point}): its platforms are set on the
     * same wave, each top {@value #SWING_BAND} blocks under the orbit where it passes.
     */
    public double wavePhase() {
        return wave;
    }

    public int x() {
        return cx;
    }

    public int y() {
        return cy;
    }

    public int z() {
        return cz;
    }

    public long seed() {
        return seed;
    }

    public BlockPos centreBlock() {
        return new BlockPos(cx, cy, cz);
    }

    /** The centre of the central asteroid, the orbit's centre. */
    public Vec3 centre() {
        return new Vec3(cx + 0.5, cy + 0.5, cz + 0.5);
    }

    public double entranceAngle() {
        return entrance;
    }

    public Platform platform(int k) {
        return platforms[Math.floorMod(k, PLATFORMS)];
    }

    public List<Platform> platforms() {
        return List.of(platforms);
    }

    public List<Updraft> updrafts() {
        return List.copyOf(updrafts);
    }

    /**
     * The boulders on platform {@code k}: two waist-high rocks either side of its middle toward the orbit, the cover that
     * hides a player from the Song of Pulling ({x, y, z} of each one's middle; its radius {@value #BOULDER}).
     */
    public List<Vec3> boulders(int k) {
        Platform p = platform(k);
        List<Vec3> out = new ArrayList<>();
        double in = p.angle() + Math.PI;
        for (int side : new int[] {-1, 1}) {
            double a = in + side * Math.toRadians(55.0);
            double r = p.radius() * 0.5;
            out.add(new Vec3(p.x() + r * Math.cos(a), p.top() + 2.0, p.z() + r * Math.sin(a)));
        }
        return out;
    }

    public static final double BOULDER = 1.8;

    /** The altar: a Rimeglass bell in the middle of platform 0. */
    public BlockPos bell() {
        Platform p = platforms[0];
        return new BlockPos((int) Math.floor(p.x()), p.top() + 1, (int) Math.floor(p.z()));
    }

    /** Where a player arrives: the ledge outside the shell's gap, on its walkway. */
    public Vec3 ledgeTop() {
        return new Vec3(ledge.x(), ledge.y() + 1.0, ledge.z());
    }

    /** {min x, min y, min z, max x, max y, max z}. */
    public int[] bounds() {
        int r = (int) Math.ceil(RX * OUTER) + 2;
        int ry = (int) Math.ceil(RY * OUTER) + 2;
        int reach = Math.max(r, (int) Math.ceil(RX + 13.0));
        return new int[] {cx - reach, cy - ry, cz - reach, cx + reach, cy + ry, cz + reach};
    }

    /** The shell's radius at a point as a fraction (1 on the shell's middle surface). */
    public double shellFraction(double x, double y, double z) {
        double dx = (x - cx - 0.5) / RX;
        double dy = (y - cy - 0.5) / RY;
        double dz = (z - cz - 0.5) / RX;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** True inside the sphere (the arena: targeting, participants, anti-cheese, the Song). */
    public boolean inside(Vec3 p) {
        return shellFraction(p.x - 0.5, p.y - 0.5, p.z - 0.5) < 1.0;
    }

    /** The Y of the bowl's inner surface {@code r} blocks from the axis (as a real number). */
    public double bowlSurface(double r) {
        double f = BOWL_INNER * BOWL_INNER - (r / RX) * (r / RX);
        return cy + 0.5 - RY * Math.sqrt(Math.max(0.0, f));
    }

    /** The nearest platform to an angle round the axis. */
    public Platform nearestPlatform(double angle) {
        Platform best = platforms[0];
        double bestD = Double.MAX_VALUE;
        for (Platform p : platforms) {
            double d = Math.abs(wrap(p.angle() - angle));
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** The {@code n} platforms nearest to an angle, nearest first. */
    public List<Platform> nearestPlatforms(double angle, int n) {
        List<Platform> all = new ArrayList<>(List.of(platforms));
        all.sort((a, b) -> Double.compare(Math.abs(wrap(a.angle() - angle)), Math.abs(wrap(b.angle() - angle))));
        return all.subList(0, Math.min(n, all.size()));
    }

    static double wrap(double a) {
        double r = a % (2.0 * Math.PI);
        if (r > Math.PI) {
            r -= 2.0 * Math.PI;
        } else if (r < -Math.PI) {
            r += 2.0 * Math.PI;
        }
        return r;
    }

    // ------------------------------------------------------------------ what goes where

    /** The rocks and bridges that reach a box of columns, so each chunk only tests what can touch it. */
    public final class Slice {
        final List<Rock> rocks = new ArrayList<>();
        final List<Bridge> bridges = new ArrayList<>();
        final List<Platform> platforms = new ArrayList<>();
        final boolean ledgeNear;
        final boolean coreNear;

        Slice(int x0, int z0, int x1, int z1) {
            for (Rock r : RiftLayout.this.rocks) {
                double reach = Math.max(r.r() * 1.25, r.ring() + 1.0) + 1.0;
                if (r.x() + reach >= x0 && r.x() - reach <= x1 + 1 && r.z() + reach >= z0 && r.z() - reach <= z1 + 1) {
                    rocks.add(r);
                }
            }
            for (Bridge b : RiftLayout.this.bridges) {
                double m = b.halfWidth() + 1.0;
                if (Math.max(b.ax(), b.bx()) + m >= x0 && Math.min(b.ax(), b.bx()) - m <= x1 + 1
                        && Math.max(b.az(), b.bz()) + m >= z0 && Math.min(b.az(), b.bz()) - m <= z1 + 1) {
                    bridges.add(b);
                }
            }
            for (Platform p : RiftLayout.this.platforms) {
                double m = p.radius() + 2.0;
                if (p.x() + m >= x0 && p.x() - m <= x1 + 1 && p.z() + m >= z0 && p.z() - m <= z1 + 1) {
                    platforms.add(p);
                }
            }
            double lr = ledge.r() + 2.0;
            ledgeNear = ledge.x() + lr >= x0 && ledge.x() - lr <= x1 + 1 && ledge.z() + lr >= z0 && ledge.z() - lr <= z1 + 1;
            double cr = CORE_RADIUS + 6.0;
            coreNear = cx + cr >= x0 && cx - cr <= x1 + 1 && cz + cr >= z0 && cz - cr <= z1 + 1;
        }
    }

    public Slice slice(int x0, int z0, int x1, int z1) {
        return new Slice(x0, z0, x1, z1);
    }

    /** What belongs at a block. {@link Kind#KEEP}: leave it (outside the Rift); {@link Kind#AIR}: clear it. */
    public Kind kind(Slice s, int x, int y, int z) {
        double e = shellFraction(x, y, z);
        if (e > OUTER + 0.02 && !s.ledgeNear) {
            return Kind.KEEP;
        }
        double px = x + 0.5;
        double py = y + 0.5;
        double pz = z + 0.5;
        // the walkways first (they cross the shell's gap)
        for (Bridge b : s.bridges) {
            Kind k = bridge(b, px, py, pz);
            if (k != null) {
                return k;
            }
        }
        for (Updraft u : updrafts) {
            if (x == u.x() && z == u.z() && y >= u.bottom() && y <= u.top()) {
                return y >= u.top() - 1 ? Kind.UPDRAFT_TOP : Kind.UPDRAFT;
            }
        }
        BlockPos bell = bell();
        if (x == bell.getX() && y == bell.getY() && z == bell.getZ()) {
            return Kind.BELL;
        }
        if (s.coreNear) {
            Kind k = core(px, py, pz);
            if (k != null) {
                return k;
            }
        }
        for (Platform p : s.platforms) {
            Kind k = platform(p, x, y, z, px, pz);
            if (k != null) {
                return k;
            }
        }
        if (s.ledgeNear) {
            Kind k = ledge(x, y, z, px, pz);
            if (k != null) {
                return k;
            }
        }
        for (Rock r : s.rocks) {
            Kind k = rock(r, px, py, pz);
            if (k != null) {
                return k;
            }
        }
        double dy = (py - cy - 0.5) / RY;
        if (dy < Math.sin(Math.toRadians(BOWL_LAT)) + 0.04 * lumps.at(px * 0.07, pz * 0.07)) {
            double inner = BOWL_INNER - 0.006 * (1.0 + lumps.at(px * 0.11, py * 0.11, pz * 0.11));
            if (e >= inner && e <= 1.0) {
                return Hashing.unit(Hashing.hash(seed, 0x80, x, y, z)) < 0.03 ? Kind.NEBULITE : Kind.DRIFTSTONE;
            }
        }
        return e < 1.0 ? Kind.AIR : Kind.KEEP;
    }

    private Kind core(double px, double py, double pz) {
        double dx = px - cx - 0.5;
        double dy = py - cy - 0.5;
        double dz = pz - cz - 0.5;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > CORE_RADIUS + 6.0) {
            return null;
        }
        double inv = d < 1e-6 ? 0 : 1.0 / d;
        double lump = 1.1 * lumps.at(dx * inv * 1.8 + 3.1, dy * inv * 1.8, dz * inv * 1.8 - 2.2);
        double surface = CORE_RADIUS + lump;
        double lat = Math.abs(dy) * inv;
        if (d <= surface) {
            if (d < 7.5) {
                return Kind.RIMEGLASS;
            }
            // the geode's singing crystal breaks through in patches: flush with the rock near the equator (where the coil
            // lies against it), and wider further from it
            double patch = lumps.at(dx * 0.35 + 40, dy * 0.35, dz * 0.35);
            if (d > surface - 1.6 && (lat > 0.45 && patch > 0.35 || lat > 0.2 && patch > 0.5)) {
                return Kind.SINGING_RIMEGLASS;
            }
            return Kind.DRIFTSTONE;
        }
        // singing crystal spikes bristling from its middle latitudes, clear of the equator's coil and orbit
        if (lat > 0.42) {
            for (int i = 0; i < 12; i++) {
                long h = Hashing.hash(seed, 0xC4, i, 0, 0);
                double sLat = 0.5 + 0.2 * Hashing.unit(h, 0);
                double sLon = (i + Hashing.unit(h, 1) * 0.8) / 12.0 * 2.0 * Math.PI;
                double sy = (i % 2 == 0 ? 1 : -1) * sLat;
                double sr = Math.sqrt(1 - sLat * sLat);
                double ux = sr * Math.cos(sLon);
                double uz = sr * Math.sin(sLon);
                double along = dx * ux + dy * sy + dz * uz;
                double len = CORE_RADIUS + 2.5 + 2.5 * Hashing.unit(h, 2);
                if (along < CORE_RADIUS - 1 || along > len) {
                    continue;
                }
                double ox = dx - ux * along;
                double oy = dy - sy * along;
                double oz = dz - uz * along;
                double off = Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (off <= 1.3 * (len - along) / (len - CORE_RADIUS + 1.0)) {
                    return Kind.SINGING_RIMEGLASS;
                }
            }
        }
        // crystal spikes near the poles
        if (lat > 0.72) {
            for (int i = 0; i < 6; i++) {
                long h = Hashing.hash(seed, 0xC0, i, 0, 0);
                double sLat = 0.78 + 0.2 * Hashing.unit(h, 0);
                double sLon = Hashing.unit(h, 1) * 2.0 * Math.PI;
                double sy = (i % 2 == 0 ? 1 : -1) * sLat;
                double sr = Math.sqrt(1 - sLat * sLat);
                double ux = sr * Math.cos(sLon);
                double uz = sr * Math.sin(sLon);
                double along = dx * ux + dy * sy + dz * uz;
                double len = CORE_RADIUS + 3.0 + 3.0 * Hashing.unit(h, 2);
                if (along < CORE_RADIUS - 1 || along > len) {
                    continue;
                }
                double ox = dx - ux * along;
                double oy = dy - sy * along;
                double oz = dz - uz * along;
                double off = Math.sqrt(ox * ox + oy * oy + oz * oz);
                double width = 1.4 * (len - along) / (len - CORE_RADIUS + 1.0);
                if (off <= width) {
                    return Kind.SINGING_RIMEGLASS;
                }
            }
        }
        return null;
    }

    private Kind platform(Platform p, int x, int y, int z, double px, double pz) {
        double d = Math.hypot(px - p.x(), pz - p.z());
        if (d > p.radius() + 1.5 || y > p.top() + 4 || y < p.top() - 13) {
            return null;
        }
        if (Math.hypot(px - cx - 0.5, pz - cz - 0.5) < CLEAR_RADIUS) {
            return null;
        }
        double edge = p.radius() + 0.6 * lumps.at(px * 0.3, pz * 0.3);
        if (d > edge) {
            return null;
        }
        if (y > p.top()) {
            if (p.index() == 0 && y <= p.top() + 2 && bellPost(x, z)) {
                return Kind.SINGING_RIMEGLASS;
            }
            for (Vec3 b : boulders(p.index())) {
                double bx = px - b.x;
                double by = y + 0.5 - b.y;
                double bz = pz - b.z;
                double lump = BOULDER + 0.35 * lumps.at(bx * 0.6 + b.x, by * 0.6, bz * 0.6 + b.z);
                if (bx * bx + by * by * 0.7 + bz * bz <= lump * lump && y <= p.top() + 4) {
                    return Kind.DRIFTSTONE;
                }
            }
            if (y > p.top() + 1) {
                return null;
            }
        }
        if (y == p.top() + 1) {
            // singing crystals at a few points of the rim, none on the inner rim facing the orbit
            double a = Math.atan2(pz - p.z(), px - p.x());
            boolean rim = d > edge - 1.1;
            boolean inner = Math.cos(a - (p.angle() + Math.PI)) > 0.55;
            long h = Hashing.hash(seed, 0x9A, x, 0, z);
            return rim && !inner && Math.sin(a * 3.0 + p.index()) > 0.9 && Hashing.unit(h) < 0.6 ? Kind.SINGING_RIMEGLASS : null;
        }
        double depth = 2.0 + (edge - d) * 1.5 + 1.5 * (0.5 + 0.5 * lumps.at(px * 0.21, y * 0.2, pz * 0.21));
        if (y < p.top() - depth) {
            return hanging(p, y, px, pz) ? Kind.SINGING_RIMEGLASS : null;
        }
        if (y == p.top()) {
            return d < edge - 1.2 && p.index() == 0 ? Kind.BRICKS : Kind.DRIFTSTONE;
        }
        return Hashing.unit(Hashing.hash(seed, 0x9B, x, y, z)) < 0.04 ? Kind.NEBULITE : Kind.DRIFTSTONE;
    }

    /**
     * The lit posts by the bell on platform 0, two blocks tall: a pair flanking the way in from the entrance two blocks out
     * from the bell, and a pair either side of it (none on the side toward the orbit, where the fight is).
     */
    private boolean bellPost(int x, int z) {
        for (BlockPos post : postBlocks()) {
            if (post.getX() == x && post.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> postBlocks() {
        BlockPos bell = bell();
        double ox = Math.cos(entrance);
        double oz = Math.sin(entrance);
        double[][] at = {{2.2, 2.0}, {2.2, -2.0}, {0.0, 2.8}, {0.0, -2.8}};   // (out, across)
        List<BlockPos> out = new ArrayList<>();
        for (double[] a : at) {
            double x = bell.getX() + 0.5 + ox * a[0] - oz * a[1];
            double z = bell.getZ() + 0.5 + oz * a[0] + ox * a[1];
            out.add(BlockPos.containing(x, bell.getY(), z));
        }
        return out;
    }

    /** Where the bell's lit posts stand (the middles of their lower blocks). */
    public List<Vec3> bellPosts() {
        List<Vec3> out = new ArrayList<>();
        for (BlockPos post : postBlocks()) {
            out.add(Vec3.atCenterOf(post));
        }
        return out;
    }

    /** Two singing crystals hanging under each platform, to either side of it, 3 to 5 blocks long. */
    private boolean hanging(Platform p, int y, double px, double pz) {
        for (int side : new int[] {-1, 1}) {
            long h = Hashing.hash(seed, 0xA7, p.index(), side, 0);
            double a = p.angle() + side * Math.toRadians(95.0);
            double hx = p.x() + 0.55 * p.radius() * Math.cos(a);
            double hz = p.z() + 0.55 * p.radius() * Math.sin(a);
            double from = p.top() - 5.0;
            double len = 3.0 + 2.0 * Hashing.unit(h, 0);
            double below = from - (y + 0.5);
            if (below < 0 || below > len) {
                continue;
            }
            double width = 1.25 * (1.0 - below / len) + 0.15;
            if (Math.hypot(px - hx, pz - hz) <= width) {
                return true;
            }
        }
        return false;
    }

    private Kind ledge(int x, int y, int z, double px, double pz) {
        double d = Math.hypot(px - ledge.x(), pz - ledge.z());
        int top = (int) ledge.y();
        if (d > ledge.r() || y > top || y < top - 8) {
            return null;
        }
        double depth = 2.0 + (ledge.r() - d) * 1.2;
        return y >= top - depth ? Kind.DRIFTSTONE : null;
    }

    private static Kind bridge(Bridge b, double px, double py, double pz) {
        double dx = b.bx() - b.ax();
        double dz = b.bz() - b.az();
        double len2 = dx * dx + dz * dz;
        double t = Math.max(0.0, Math.min(1.0, ((px - b.ax()) * dx + (pz - b.az()) * dz) / len2));
        double qx = b.ax() + dx * t;
        double qz = b.az() + dz * t;
        if (Math.hypot(px - qx, pz - qz) > b.halfWidth()) {
            return null;
        }
        double surface = b.sa() + (b.sb() - b.sa()) * t;
        int floor = (int) Math.floor(surface);
        int y = (int) Math.floor(py);
        boolean half = surface - floor >= 0.5;
        if (y == floor - 1) {
            return Kind.PLANKS;
        }
        if (half && y == floor) {
            return Kind.SLAB;
        }
        if (y >= floor && y <= floor + 3) {
            return Kind.AIR; // headroom over the walkway (a shell rock never blocks it)
        }
        return null;
    }

    private Kind rock(Rock r, double px, double py, double pz) {
        double dx = px - r.x();
        double dy = py - r.y();
        double dz = pz - r.z();
        double d2 = dx * dx + dy * dy + dz * dz;
        double reach = Math.max(r.r() * 1.25, r.ring() + 1.0);
        if (d2 > reach * reach) {
            return null;
        }
        double d = Math.sqrt(d2);
        double inv = d < 1e-6 ? 0 : 1.0 / d;
        double lump = 1.0 + 0.2 * lumps.at(dx * inv * 1.6 + r.x() * 0.01, dy * inv * 1.6, dz * inv * 1.6 + r.z() * 0.01);
        if (d <= r.r() * lump) {
            return Hashing.unit(Hashing.hash(seed, 0x51, (int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz))) < 0.05
                    ? Kind.NEBULITE : Kind.DRIFTSTONE;
        }
        if (r.ring() > 0) {
            double along = dx * r.nx() + dy * r.ny() + dz * r.nz();
            double flat = Math.sqrt(Math.max(0.0, d2 - along * along));
            if (Math.abs(along) <= 0.7 && Math.abs(flat - r.ring()) <= 0.85) {
                return Kind.RIMEGLASS;
            }
        }
        return null;
    }
}
