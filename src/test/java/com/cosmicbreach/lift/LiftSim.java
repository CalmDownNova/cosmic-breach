package com.cosmicbreach.lift;

import com.cosmicbreach.guardian.leviathan.RiftLayout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A stand-in for the client's own motion in a Rift in the Drift (gravity 0.4 x 0.08, 0.98 drag down, 0.91 across in the air,
 * a player's box 0.6 wide and 1.8 tall that stops at the Rift's real blocks), used to run {@link RiftLift} tick by tick the way
 * the client does: the lift sets the velocity first, then the player moves and gravity and drag follow. Blocks come from the
 * layout's own {@code kind}, so the sim meets the same rock, planks, posts and boulders worldgen builds. The Drift's weather can
 * be added: another gravity (a Gravity Tide's) and a flow that moves the player by exactly that much each tick, before anything
 * else, as {@code WeatherPush} does (nothing excludes the arenas).
 */
final class LiftSim {
    static final double GRAVITY = 0.08 * 0.4;
    /** A Gravity Tide's gravity: 0.15 of vanilla's. */
    static final double TIDE_GRAVITY = 0.08 * 0.15;
    static final double HALF = 0.3;
    static final double HEIGHT = 1.8;
    private static final double EPS = 1e-7;

    final RiftLayout layout;
    final double gravity;
    /** The weather's flow, blocks a tick (a Tide's current is 0.06, a standing current's tube 0.05, both 0.11); zero in still air. */
    final Vec3 push;
    private final Map<Long, Boolean> solid = new HashMap<>();

    LiftSim(RiftLayout layout) {
        this(layout, GRAVITY, Vec3.ZERO);
    }

    LiftSim(RiftLayout layout, double gravity, Vec3 push) {
        this.layout = layout;
        this.gravity = gravity;
        this.push = push;
    }

    /** True for a block a player cannot stand in: everything the Rift builds except air and its (non solid) updrafts. */
    boolean solid(int x, int y, int z) {
        return solid.computeIfAbsent(BlockPos.asLong(x, y, z), k -> {
            RiftLayout.Kind kind = layout.kind(layout.slice(x, z, x, z), x, y, z);
            return kind != RiftLayout.Kind.AIR && kind != RiftLayout.Kind.KEEP && kind != RiftLayout.Kind.UPDRAFT
                    && kind != RiftLayout.Kind.UPDRAFT_TOP;
        });
    }

    /** The kind of block at a point (for messages). */
    RiftLayout.Kind kindAt(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        return layout.kind(layout.slice(bx, bz, bx, bz), bx, by, bz);
    }

    /** What one tick's move did: where the feet ended up and which way they were stopped. */
    record Move(Vec3 feet, boolean hitX, boolean hitY, boolean hitZ, boolean onGround) {
        boolean blockedSideways() {
            return hitX || hitZ;
        }
    }

    /** Moves a player's box with its feet at {@code feet} by {@code d}, vertical first and then the longer horizontal axis (vanilla's order). */
    Move move(Vec3 feet, Vec3 d) {
        double x0 = feet.x - HALF;
        double x1 = feet.x + HALF;
        double y0 = feet.y;
        double y1 = feet.y + HEIGHT;
        double z0 = feet.z - HALF;
        double z1 = feet.z + HALF;
        double dy = clipY(x0, x1, y0, y1, z0, z1, d.y);
        y0 += dy;
        y1 += dy;
        double dx;
        double dz;
        if (Math.abs(d.x) >= Math.abs(d.z)) {
            dx = clipX(x0, x1, y0, y1, z0, z1, d.x);
            x0 += dx;
            x1 += dx;
            dz = clipZ(x0, x1, y0, y1, z0, z1, d.z);
        } else {
            dz = clipZ(x0, x1, y0, y1, z0, z1, d.z);
            z0 += dz;
            z1 += dz;
            dx = clipX(x0, x1, y0, y1, z0, z1, d.x);
        }
        return new Move(new Vec3(feet.x + dx, feet.y + dy, feet.z + dz), dx != d.x, dy != d.y, dz != d.z, d.y < 0 && dy != d.y);
    }

    private double clipY(double x0, double x1, double y0, double y1, double z0, double z1, double dy) {
        if (dy == 0) {
            return 0;
        }
        int bx0 = (int) Math.floor(x0 + EPS);
        int bx1 = (int) Math.floor(x1 - EPS);
        int bz0 = (int) Math.floor(z0 + EPS);
        int bz1 = (int) Math.floor(z1 - EPS);
        if (dy > 0) {
            for (int by = (int) Math.floor(y1 - EPS); by <= (int) Math.floor(y1 + dy); by++) {
                if (by < y1 - EPS) {
                    continue;
                }
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int bz = bz0; bz <= bz1; bz++) {
                        if (solid(bx, by, bz)) {
                            dy = Math.min(dy, by - y1);
                        }
                    }
                }
            }
        } else {
            for (int by = (int) Math.floor(y0 + EPS) - 1; by >= (int) Math.floor(y0 + dy) - 1; by--) {
                if (by + 1 > y0 + EPS) {
                    continue;
                }
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int bz = bz0; bz <= bz1; bz++) {
                        if (solid(bx, by, bz)) {
                            dy = Math.max(dy, by + 1 - y0);
                        }
                    }
                }
            }
        }
        return dy;
    }

    private double clipX(double x0, double x1, double y0, double y1, double z0, double z1, double dx) {
        if (dx == 0) {
            return 0;
        }
        int by0 = (int) Math.floor(y0 + EPS);
        int by1 = (int) Math.floor(y1 - EPS);
        int bz0 = (int) Math.floor(z0 + EPS);
        int bz1 = (int) Math.floor(z1 - EPS);
        if (dx > 0) {
            for (int bx = (int) Math.floor(x1 - EPS); bx <= (int) Math.floor(x1 + dx); bx++) {
                if (bx < x1 - EPS) {
                    continue;
                }
                for (int by = by0; by <= by1; by++) {
                    for (int bz = bz0; bz <= bz1; bz++) {
                        if (solid(bx, by, bz)) {
                            dx = Math.min(dx, bx - x1);
                        }
                    }
                }
            }
        } else {
            for (int bx = (int) Math.floor(x0 + EPS) - 1; bx >= (int) Math.floor(x0 + dx) - 1; bx--) {
                if (bx + 1 > x0 + EPS) {
                    continue;
                }
                for (int by = by0; by <= by1; by++) {
                    for (int bz = bz0; bz <= bz1; bz++) {
                        if (solid(bx, by, bz)) {
                            dx = Math.max(dx, bx + 1 - x0);
                        }
                    }
                }
            }
        }
        return dx;
    }

    private double clipZ(double x0, double x1, double y0, double y1, double z0, double z1, double dz) {
        if (dz == 0) {
            return 0;
        }
        int bx0 = (int) Math.floor(x0 + EPS);
        int bx1 = (int) Math.floor(x1 - EPS);
        int by0 = (int) Math.floor(y0 + EPS);
        int by1 = (int) Math.floor(y1 - EPS);
        if (dz > 0) {
            for (int bz = (int) Math.floor(z1 - EPS); bz <= (int) Math.floor(z1 + dz); bz++) {
                if (bz < z1 - EPS) {
                    continue;
                }
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int by = by0; by <= by1; by++) {
                        if (solid(bx, by, bz)) {
                            dz = Math.min(dz, bz - z1);
                        }
                    }
                }
            }
        } else {
            for (int bz = (int) Math.floor(z0 + EPS) - 1; bz >= (int) Math.floor(z0 + dz) - 1; bz--) {
                if (bz + 1 > z0 + EPS) {
                    continue;
                }
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int by = by0; by <= by1; by++) {
                        if (solid(bx, by, bz)) {
                            dz = Math.max(dz, bz + 1 - z0);
                        }
                    }
                }
            }
        }
        return dz;
    }

    /**
     * The result of {@link #ride}: ticks until the player stood on a platform (or -1), ticks the body was pressed against rock on the way,
     * the longest such run, where they ended, which platform that was, how many times the lift caught them (1 for a clean rescue;
     * more means they were let go in the air, fell and were caught again), and the tick of the first catch (-1 if none): in the
     * weather's flow a player at a platform's edge can be carried along it, or down a walkway, for a long while before they fall,
     * so a rescue's own time is counted from its catch.
     */
    record Result(int ticks, int blocked, int worstRun, Vec3 feet, int platform, boolean ground, @Nullable RiftLift.Ride lastRide, int catches, int caughtAt) {
        boolean landed() {
            return ticks > 0;
        }
    }

    /** True if {@code feet}, standing, are on platform {@code p}: its top, or a boulder, a crystal or a post standing on it (up to 4 blocks over). */
    boolean standsOn(Vec3 feet, RiftLayout.Platform p) {
        return feet.y >= RiftLift.standY(p) - 0.01 && feet.y <= RiftLift.standY(p) + 4.0 && Math.hypot(feet.x - p.x(), feet.z - p.z()) <= p.radius() + 0.7;
    }

    /** The index of the platform {@code feet} stand on, or -1. */
    int platformUnder(Vec3 feet) {
        for (RiftLayout.Platform p : layout.platforms()) {
            if (standsOn(feet, p)) {
                return p.index();
            }
        }
        return -1;
    }

    /** True if a player's box with its feet at {@code feet} overlaps no block. */
    boolean free(Vec3 feet) {
        for (int x = (int) Math.floor(feet.x - HALF + EPS); x <= (int) Math.floor(feet.x + HALF - EPS); x++) {
            for (int z = (int) Math.floor(feet.z - HALF + EPS); z <= (int) Math.floor(feet.z + HALF - EPS); z++) {
                for (int y = (int) Math.floor(feet.y + EPS); y <= (int) Math.floor(feet.y + HEIGHT - EPS); y++) {
                    if (solid(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** True if there is a block right under a player's box with its feet at {@code feet} (they stand there rather than fall). */
    boolean supported(Vec3 feet) {
        int y = (int) Math.floor(feet.y - 1e-3);
        for (int x = (int) Math.floor(feet.x - HALF + EPS); x <= (int) Math.floor(feet.x + HALF - EPS); x++) {
            for (int z = (int) Math.floor(feet.z - HALF + EPS); z <= (int) Math.floor(feet.z + HALF - EPS); z++) {
                if (solid(x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Where a player walking from platform {@code p}'s middle in direction {@code angle} first has nothing left under them
     * (their box clear of every block as well), or null if the way runs on as a bridge or the like past the platform's reach.
     */
    @Nullable Vec3 firstStepOff(RiftLayout.Platform p, double angle) {
        Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        boolean stood = false;
        for (double d = 0; d < p.radius() + 4.5; d += 0.05) {
            Vec3 at = new Vec3(p.x() + dir.x * d, RiftLift.standY(p), p.z() + dir.z * d);
            if (!free(at)) {
                stood = false;
                continue;
            }
            if (supported(at)) {
                stood = true;
            } else if (stood) {
                return d > p.radius() + 2.0 ? null : at;
            }
        }
        return null;
    }

    /**
     * Runs the lift for a player at {@code feet} moving {@code v} (on the ground or not) for up to {@code maxTicks}, not
     * sneaking: ticks until they stand on a platform again (or -1), how many ticks the body was stopped by rock sideways
     * while the lift held them, the longest such run, where they ended, which platform they stand on and whether they stand at all.
     */
    Result ride(Vec3 feet, Vec3 v, boolean onGround, int maxTicks) {
        RiftLift.Ride ride = null;
        RiftLift.Ride last = null;
        int blocked = 0;
        int run = 0;
        int worst = 0;
        int catches = 0;
        int caughtAt = -1;
        for (int t = 0; t < maxTicks; t++) {
            if (push.lengthSqr() > 0.0) {
                feet = move(feet, push).feet(); // the weather's flow, before the player's own tick, sliding along a block it meets
            }
            RiftLift.Step s = RiftLift.step(layout, ride, feet, v, onGround, false);
            boolean was = ride != null;
            ride = s.ride();
            if (ride != null) {
                last = ride;
                if (!was) {
                    catches++;
                    if (caughtAt < 0) {
                        caughtAt = t;
                    }
                }
            }
            if (s.velocity() != null) {
                v = s.velocity();
            }
            Move m = move(feet, v);
            feet = m.feet();
            if ((was || ride != null) && m.blockedSideways()) {
                blocked++;
                run++;
                worst = Math.max(worst, run);
            } else {
                run = 0;
            }
            onGround = m.onGround();
            double friction = onGround ? 0.6 * 0.91 : 0.91;
            v = new Vec3((m.hitX() ? 0 : v.x) * friction, ((m.hitY() ? 0 : v.y) - gravity) * 0.98, (m.hitZ() ? 0 : v.z) * friction);
            if (onGround && last != null) {
                int on = platformUnder(feet);
                if (on >= 0) {
                    return new Result(t + 1, blocked, worst, feet, on, true, last, catches, caughtAt);
                }
            }
        }
        return new Result(-1, blocked, worst, feet, -1, onGround, last, catches, caughtAt);
    }
}
