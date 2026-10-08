package com.cosmicbreach.lift;

import com.cosmicbreach.guardian.leviathan.RiftLayout;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The boss 2 arena's rescue lift (1.1 design section 6), pure. A player not on a mount who drops more than
 * {@value #CATCH_BELOW} blocks below the nearest platform's standing height inside a Rift is caught: the fall is held,
 * they are steered to an open column of air beside that platform (inside the ring toward the core, or outside it,
 * whichever side they fell on, three blocks to one side of the platform's middle line where the Moorage's bridges grow),
 * lifted up it, carried over the platform {@value #CLEARANCE} blocks above its top and lowered onto it (never let go in the
 * air: the Drift's weather moves a free faller sideways, and a landing 1.1 in from the inner rim is not far from the edge).
 * Sneaking sinks instead, so the bowl stays reachable. Only the player's own velocity is ever set: the client applies it
 * to its own player, the server runs the same step to cancel fall damage and show the effects; the Leviathan, its
 * dives, its death sink and every other entity are never read or touched.
 */
public final class RiftLift {
    public static final double CATCH_BELOW = 6.0;
    public static final double RISE = 0.7;
    public static final double LIFT = 0.12;
    public static final double BRAKE = 0.15;
    public static final double STEER = 0.32;
    /**
     * A catch far from its column (a fall off the way in, 40 or more blocks out) is steered faster the further it has to
     * go: {@value #STEER} a tick up to {@value #STEER_FROM} blocks away, then {@value #STEER_RAMP} more for every block past
     * that, to at most {@value #STEER_MAX} (from about 15 blocks out). The rim falls and the jumps from the bowl keep nearly
     * their old pace (their last stretch is within {@value #STEER_FROM} blocks of the column).
     */
    public static final double STEER_FROM = 8.0;
    public static final double STEER_RAMP = 0.04;
    public static final double STEER_MAX = 0.6;
    public static final double SINK = -0.12;
    /** The inner column stands this far past the platform's inner rim toward the core. */
    public static final double INNER_OUT = 3.5;
    /** The outer column stands this far past the platform's outer rim. */
    public static final double OUTER_OUT = 2.5;
    /** Both columns stand this far to one side of the platform's middle line. */
    public static final double SIDE = 3.0;
    /**
     * Where it sets players down on the platform, measured in from the rim they climbed beside. Inside the ring that is the
     * platform's inner rim (cut at {@code CLEAR_RADIUS}) and the spot lies in the bay between its two boulders and the rim,
     * on its middle line; outside it the platform is bare but for crystals on its rim, and the spot lies well in from them.
     * Chosen over 1,500 layouts so that the spot, and the point they are let go at, is solid ground with room to stand.
     */
    public static final double LAND_IN_INNER = 1.1;
    public static final double LAND_IN_OUTER = 3.0;
    /** The landing lies this far to the ride's side of the platform's middle line (the bay is widest on it). */
    public static final double LAND_SIDE = 0.0;
    /** It carries them over the platform this high above where they will stand. */
    public static final double CLEARANCE = 1.5;
    public static final double AT_COLUMN = 1.0;
    /** Carried over the platform, they are lowered once they are this close to the landing. */
    public static final double LANDED = 0.35;
    /**
     * They are lowered onto the landing this fast a tick, steered to it all the way down, and the ride ends when they stand. A rider
     * let go in the air over it fell about 11 ticks at the Drift's gravity and 17 in a Gravity Tide, and the weather's flow
     * (up to 0.11 a tick) carried them off an inner rim, so they were caught again and looped until it stopped.
     */
    public static final double DESCENT = 0.25;
    /** Below the carry height they stay on the set-down while they are within this far of the landing (a nudge from the weather does not end it). */
    public static final double SET_DOWN_REACH = 1.2;
    /** A rider this far under the standing height or less, and within reach of the landing, is on the set-down; at the standing height or under they are down. */
    private static final double SET_DOWN_BELOW = 0.3;
    private static final double STANDING = 0.02;

    /** A ride: the platform it lands on, which side of the ring it climbs, which side of the middle line. */
    public record Ride(int platform, boolean outer, int side) {
    }

    /** One tick: the ride now (null once it ends) and the velocity to give the player (null: leave it alone). */
    public record Step(@Nullable Ride ride, @Nullable Vec3 velocity) {
    }

    private static final Step NONE = new Step(null, null);

    private RiftLift() {
    }

    /** Where a player's feet are when standing on platform {@code p}. */
    public static double standY(RiftLayout.Platform p) {
        return p.top() + 1.0;
    }

    static double angle(RiftLayout l, Vec3 p) {
        Vec3 c = l.centre();
        return Math.atan2(p.z - c.z, p.x - c.x);
    }

    static double radius(RiftLayout l, Vec3 p) {
        Vec3 c = l.centre();
        return Math.hypot(p.x - c.x, p.z - c.z);
    }

    private static double wrap(double a) {
        double r = a % (2.0 * Math.PI);
        if (r > Math.PI) {
            r -= 2.0 * Math.PI;
        } else if (r < -Math.PI) {
            r += 2.0 * Math.PI;
        }
        return r;
    }

    /** True if a player whose feet are at {@code feet} has dropped far enough under the nearest platform to be caught. */
    public static boolean caught(RiftLayout l, Vec3 feet) {
        if (!l.inside(feet)) {
            return false;
        }
        return feet.y < standY(l.nearestPlatform(angle(l, feet))) - CATCH_BELOW;
    }

    /**
     * The ride for a player caught at {@code feet}: the nearest platform, the side of the ring and of its middle line they
     * are on. A catch near the bowl's floor ({@value #DEEP_BELOW} blocks or more under the platform), or under the central
     * asteroid, is checked first: the bowl has rubble and rings of rock standing in it, so the first way up that is clear of
     * them is taken (that platform's other columns, then its two neighbours'), and null means there is none (a pocket under
     * an overhang: the lift leaves them alone rather than hold them against the rock). A catch higher up, out beside the
     * platforms, is in open air all round and is not checked.
     */
    public static @Nullable Ride start(RiftLayout l, Vec3 feet) {
        double a = angle(l, feet);
        List<RiftLayout.Platform> near = l.nearestPlatforms(a, 3);
        RiftLayout.Platform nearest = near.get(0);
        boolean outer = radius(l, feet) > RiftLayout.PLATFORM_RING;
        if (feet.y > standY(nearest) - DEEP_BELOW && radius(l, feet) > RiftLayout.CORE_RADIUS + 3.0) {
            return new Ride(nearest.index(), outer, wrap(a - nearest.angle()) >= 0 ? 1 : -1);
        }
        for (RiftLayout.Platform p : near) {
            int side = wrap(a - p.angle()) >= 0 ? 1 : -1;
            for (int flip = 0; flip < 4; flip++) {
                Ride r = new Ride(p.index(), outer ^ (flip >= 2), (flip & 1) == 0 ? side : -side);
                if (clearWay(l, feet, r)) {
                    return r;
                }
            }
        }
        return null;
    }

    /** How fast a catch {@code h} blocks (across) from its column is steered toward it: see {@link #STEER_FROM}. */
    static double approachSpeed(double h) {
        return Math.min(STEER_MAX, STEER + STEER_RAMP * Math.max(0.0, h - STEER_FROM));
    }

    /** A catch this far under the platform's standing height or lower is in the bowl's own rubble (the line itself is {@value #CATCH_BELOW}). */
    public static final double DEEP_BELOW = 20.0;

    /**
     * True if the way {@code r} takes from {@code feet} meets no rock it cannot rise past: across toward its column while
     * rising (sliding up along any rock in the way, as the player's own body would), then up the column to the platform's
     * height. A dry run in steps of two ticks (about 0.5 up a tick, held below the platform's top until beside the column,
     * and across at the pace {@link #approachSpeed} gives that distance, looked at every body's width of the way).
     */
    static boolean clearWay(RiftLayout l, Vec3 feet, Ride r) {
        Vec3 column = column(l, r);
        Vec3 land = landing(l, r);
        double stand = standY(l.platform(r.platform()));
        RiftLayout.Slice region = l.slice((int) Math.floor(Math.min(Math.min(feet.x, column.x), land.x)) - 2,
                (int) Math.floor(Math.min(Math.min(feet.z, column.z), land.z)) - 2,
                (int) Math.ceil(Math.max(Math.max(feet.x, column.x), land.x)) + 2,
                (int) Math.ceil(Math.max(Math.max(feet.z, column.z), land.z)) + 2);
        double x = feet.x;
        double y = feet.y + 0.05;
        double z = feet.z;
        for (int look = 0; look < 160 && y < stand + CLEARANCE; look++) {
            double h = Math.hypot(column.x - x, column.z - z);
            boolean atColumn = h <= AT_COLUMN;
            // two ticks across (as fast as the ride goes at this distance) and up, looked at in parts no longer than a body
            double across = atColumn ? 0.0 : Math.min(2.0 * approachSpeed(h), h);
            double up = atColumn ? 1.0 : Math.min(1.0, Math.max(0.0, stand - 1.0 - y));
            int parts = Math.max(1, (int) Math.ceil(across / (2.0 * STEER) - 1e-9));
            double ux = atColumn ? 0.0 : (column.x - x) / h;
            double uz = atColumn ? 0.0 : (column.z - z) / h;
            for (int part = 0; part < parts; part++) {
                double nx = x + ux * across / parts;
                double nz = z + uz * across / parts;
                double ny = y + up / parts;
                if (!bodyMeetsRock(l, region, nx, ny, nz)) {
                    x = nx;
                    y = ny;
                    z = nz;
                } else if (!bodyMeetsRock(l, region, x, ny, z)) {
                    y = ny;
                } else {
                    return false;
                }
            }
        }
        return y >= stand + CLEARANCE;
    }

    /** True if a player's box (0.6 wide, 1.8 tall) with its feet at the point overlaps any block of the Rift that is not air. */
    private static boolean bodyMeetsRock(RiftLayout l, RiftLayout.Slice region, double x, double y, double z) {
        for (int bx = (int) Math.floor(x - 0.3 + 1e-7); bx <= (int) Math.floor(x + 0.3 - 1e-7); bx++) {
            for (int bz = (int) Math.floor(z - 0.3 + 1e-7); bz <= (int) Math.floor(z + 0.3 - 1e-7); bz++) {
                for (int by = (int) Math.floor(y + 1e-7); by <= (int) Math.floor(y + 1.8 - 1e-7); by++) {
                    RiftLayout.Kind k = l.kind(region, bx, by, bz);
                    if (k != RiftLayout.Kind.AIR && k != RiftLayout.Kind.KEEP && k != RiftLayout.Kind.UPDRAFT && k != RiftLayout.Kind.UPDRAFT_TOP) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** A point at the platform's standing height, {@code along} from its middle toward the core (or away, for an outer ride) and {@code across} to the ride's side. */
    private static Vec3 onPlatform(RiftLayout l, Ride r, double along, double across) {
        RiftLayout.Platform p = l.platform(r.platform());
        double ux = -Math.cos(p.angle());
        double uz = -Math.sin(p.angle());
        double tx = -Math.sin(p.angle());
        double tz = Math.cos(p.angle());
        double s = r.outer() ? -along : along;
        return new Vec3(p.x() + ux * s + tx * r.side() * across, standY(p), p.z() + uz * s + tz * r.side() * across);
    }

    /** The open column the climb runs up (at the platform's standing height). */
    public static Vec3 column(RiftLayout l, Ride r) {
        RiftLayout.Platform p = l.platform(r.platform());
        return onPlatform(l, r, p.radius() + (r.outer() ? OUTER_OUT : INNER_OUT), SIDE);
    }

    /** The radius of the platform's rim on the side a ride climbs: the inner rim is cut where the Leviathan's orbit passes. */
    static double rim(RiftLayout.Platform p, boolean outer) {
        return outer ? p.radius() : Math.min(p.radius(), RiftLayout.PLATFORM_RING - RiftLayout.CLEAR_RADIUS);
    }

    /** Where it sets the player down, on the platform. */
    public static Vec3 landing(RiftLayout l, Ride r) {
        RiftLayout.Platform p = l.platform(r.platform());
        return onPlatform(l, r, rim(p, r.outer()) - (r.outer() ? LAND_IN_OUTER : LAND_IN_INNER), LAND_SIDE);
    }

    /** Below this far under a platform's top there is none of its rock (its body goes down about 13, its crystals about 10). */
    static final double BODY_DEPTH = 15.0;
    /** How far a player's head is from their feet. */
    private static final double HEAD = 1.8;
    /** How far outside a platform's rock the lift keeps a player's body (half a body's width and some air). */
    private static final double FLANK_CLEAR = 0.6;

    /**
     * True if rising would put the head of a player at {@code feet} into platform {@code p}'s lower flank (or its hanging
     * crystals): its rock narrows from its rim by a block for every 1.5 it goes down, so the lift holds the rise until
     * they are out beside it. Worst case over the rim's noise, with the next tick's rise added.
     */
    static boolean underBody(RiftLayout.Platform p, Vec3 feet) {
        double below = p.top() + 1.0 - (feet.y + HEAD + RISE);
        if (below < 0.0 || below > BODY_DEPTH) {
            return false;
        }
        double d = Math.hypot(feet.x - p.x(), feet.z - p.z());
        double reach = p.radius() + 0.6 - Math.max(0.0, below - 4.5) / 1.5;
        if (below >= 5.0 && below <= 11.5) {
            reach = Math.max(reach, 0.55 * p.radius() + 1.5); // the singing crystals hanging under it
        }
        return d < reach + FLANK_CLEAR;
    }

    /** Farther than a player ever moves in one tick (a terminal fall is under 4 blocks): they were moved from outside. */
    public static final double TELEPORT = 8.0;

    /** True if the move from {@code last} to {@code now} in one tick can only be a teleport (an ender pearl, a command, a respawn). */
    public static boolean teleported(@Nullable Vec3 last, Vec3 now) {
        return last != null && last.distanceToSqr(now) > TELEPORT * TELEPORT;
    }

    /**
     * {@link #step} for a tick the player moved from {@code last} to {@code feet}: a teleport ends the ride and steers nothing, on the
     * client and the server alike, so neither goes on carrying a player to the column they were once headed for.
     */
    public static Step stepAfterMove(RiftLayout l, @Nullable Ride ride, @Nullable Vec3 last, Vec3 feet, Vec3 v, boolean onGround, boolean sneaking) {
        if (teleported(last, feet)) {
            return NONE;
        }
        return step(l, ride, feet, v, onGround, sneaking);
    }

    /**
     * One tick of the lift for a player at {@code feet} moving {@code v}, riding {@code ride} (null if not yet caught).
     * Ends (null ride, null velocity) on the ground or outside the sphere.
     */
    public static Step step(RiftLayout l, @Nullable Ride ride, Vec3 feet, Vec3 v, boolean onGround, boolean sneaking) {
        if (ride == null) {
            if (onGround || !caught(l, feet)) {
                return NONE;
            }
            ride = start(l, feet);
            if (ride == null) {
                return NONE;
            }
        } else if (onGround || !l.inside(feet)) {
            return NONE;
        }
        if (sneaking) {
            return new Step(ride, new Vec3(v.x * 0.9, Math.max(v.y, SINK), v.z * 0.9));
        }
        RiftLayout.Platform platform = l.platform(ride.platform());
        double stand = standY(platform);
        Vec3 land = landing(l, ride);
        double lx = land.x - feet.x;
        double lz = land.z - feet.z;
        double toLanding = Math.hypot(lx, lz);
        if (feet.y < stand + CLEARANCE && feet.y > stand - SET_DOWN_BELOW && toLanding <= SET_DOWN_REACH) {
            if (feet.y <= stand + STANDING) {
                return new Step(null, new Vec3(0, Math.min(v.y, 0.0), 0)); // down (the ground check normally ends the ride first)
            }
            // being lowered onto the landing, steered to it all the way down; never let go in the air
            double k = toLanding < 1e-6 ? 0.0 : Math.min(STEER, toLanding) / toLanding;
            return new Step(ride, new Vec3(lx * k, -DESCENT, lz * k));
        }
        if (feet.y < stand + CLEARANCE) {
            Vec3 column = column(l, ride);
            double hx = column.x - feet.x;
            double hz = column.z - feet.z;
            double h = Math.hypot(hx, hz);
            if (h > AT_COLUMN) {
                // toward the open column: the fall is braked into a rise, held while the player's head is in the platform's own
                // flank, and kept below the platform's top until they are beside the column (so they are never carried
                // across the ring at platform height before they have climbed it)
                double vy = Math.min(Math.max(v.y + BRAKE, -2.0 * BRAKE), RISE);
                double dx = hx / h;
                double dz = hz / h;
                if (underBody(platform, feet)) {
                    vy = Math.min(vy, Math.min(0.04, Math.max(v.y + BRAKE, -BRAKE)));
                    // slide out from under its flank (away from its middle, only half toward the column) rather than along it
                    double ax = feet.x - platform.x();
                    double az = feet.z - platform.z();
                    double a = Math.max(Math.hypot(ax, az), 1e-6);
                    double mx = ax / a + 0.5 * dx;
                    double mz = az / a + 0.5 * dz;
                    double m = Math.max(Math.hypot(mx, mz), 1e-6);
                    dx = mx / m;
                    dz = mz / m;
                }
                vy = Math.min(vy, Math.max(0.0, (stand - 1.0 - feet.y) * 0.25));
                double speed = Math.min(approachSpeed(h), h);
                return new Step(ride, new Vec3(dx * speed, vy, dz * speed));
            }
            // up the column
            return new Step(ride, new Vec3(hx * 0.3, Math.min(Math.max(v.y, 0.0) + LIFT, RISE), hz * 0.3));
        }
        // over the platform: carried to the landing at the carry height, then lowered onto it (above); the ride ends when they stand
        double k = toLanding < 1e-6 ? 0.0 : Math.min(STEER, toLanding) / toLanding;
        return new Step(ride, new Vec3(lx * k, toLanding <= LANDED ? -DESCENT : 0.0, lz * k));
    }
}
