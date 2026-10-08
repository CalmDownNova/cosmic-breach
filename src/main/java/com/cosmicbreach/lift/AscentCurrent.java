package com.cosmicbreach.lift;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A rising current (1.1 design section 5), pure: a column of air beside a shrine that stands over a drop, from the
 * level below up past that shrine's floor. A player (not riding, not flying) who walks or jumps into it, anywhere from
 * its foot up, is drawn to its axis and carried up at up to {@value #RISE} blocks a tick, then across to the landing beside
 * the shrine, {@value #CLEARANCE} above the ground, and lowered onto it. Sneaking lets go (and a sneaking
 * player is never caught), so anyone can stand in its foot or drop through it.
 *
 * <p>A current never catches a falling player. Whoever enters its column having dropped more than {@value #DROP_LIMIT}
 * blocks since they last stood on anything (a jump in the Drift is under 5), or faster than {@value #FALL_FAST} blocks a
 * tick, or who is coming down through its top {@value #TOP_GUARD} blocks, is "falling in": they are left alone however long they stay, so a drop from the level above lands normally on
 * whatever is at the foot. It catches them again once they step out of the column and back in, or jump in it (an upward
 * motion of {@value #HOP} or more, which a fall never has). The landing in front of the shrine is
 * {@value #LANDING_ROOM} blocks past the catch at least ({@link Current#landingClear}), so a rider is never set down in
 * a current.
 *
 * <p>The client moves its own player with this step; the server runs the same step to cancel fall damage and greet the
 * arrival. Only players are moved.
 */
public final class AscentCurrent {
    public static final double CATCH = 1.6;
    public static final double RISE = 1.0;
    public static final double LIFT = 0.12;
    public static final double DRAW = 0.25;
    public static final double STEER = 0.3;
    public static final double CLEARANCE = 1.5;
    public static final double LANDED = 0.6;
    /** A rider pushed this far past the way across (from both the axis and the landing, beyond the distance between them) is let go; one on the way is never. */
    public static final double LOST = 6.0;
    /** The most a player may have dropped since they last stood on anything and still be "walking in" (a Drift jump is 4.6 up and 4.6 down). */
    public static final double DROP_LIMIT = 6.0;
    /** Falling faster than this (blocks a tick) is falling in, however short the drop. */
    public static final double FALL_FAST = 0.9;
    /**
     * The top this many blocks of a current are for the way up only: someone not rising there (stepping off the island's rim
     * into the column, or coming down through it) is falling in, however short the drop so far.
     */
    public static final double TOP_GUARD = 8.0;
    /** An upward speed that only a jump (or a rising current) gives. */
    public static final double HOP = 0.1;
    /** How far past the catch's edge a player must go for "falling in" to be forgotten. */
    public static final double KEEP_OUT = 0.5;
    /** Moving this far in one tick is a teleport (a fall tops out near 4, a rider moves 1): what they carried is forgotten. */
    public static final double TELEPORT = 8.0;
    /** The landing lies at least this far past the catch's edge. */
    public static final double LANDING_ROOM = 1.0;

    /** A current: its axis (x, z), the feet height it starts at, the shrine's floor (feet height), the landing (x, z). */
    public record Current(double x, double z, double bottom, double top, double landX, double landZ) {
        public static final StreamCodec<ByteBuf, Current> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Current::x, ByteBufCodecs.DOUBLE, Current::z,
                ByteBufCodecs.DOUBLE, Current::bottom, ByteBufCodecs.DOUBLE, Current::top,
                ByteBufCodecs.DOUBLE, Current::landX, ByteBufCodecs.DOUBLE, Current::landZ,
                Current::new);

        /** True if feet at {@code p} are in its catch. */
        public boolean catches(Vec3 p) {
            return across(p) <= CATCH && p.y >= bottom - 0.5 && p.y < top + CLEARANCE;
        }

        /** How far {@code p} is from the axis, level. */
        double across(Vec3 p) {
            return Math.hypot(p.x - x, p.z - z);
        }

        /** True if the landing is clear of the catch with room to spare, so nobody is set down in the current. */
        public boolean landingClear() {
            return Math.hypot(landX - x, landZ - z) > CATCH + LANDING_ROOM;
        }
    }

    /**
     * What a player carries from tick to tick: the current that carries them (null: none), the highest their feet have
     * been since they last stood on anything (their drop is measured from it), the current they fell into (null: none), and where
     * their feet were last tick (null at the start; a move of {@value #TELEPORT} blocks or more in a tick is a teleport).
     */
    public record State(@Nullable Current ride, double peak, @Nullable Current fellIn, @Nullable Vec3 last) {
        public static final State START = new State(null, Double.NEGATIVE_INFINITY, null, null);
    }

    /** One tick: the state now and the velocity to give the player (null: leave it alone). */
    public record Step(State state, @Nullable Vec3 velocity) {
    }

    private AscentCurrent() {
    }

    /**
     * One tick for a player (not riding anything, not flying: callers check) whose feet are at {@code feet}, moving at
     * {@code v} a tick (the client's own motion, the server's measured one), among {@code currents}.
     */
    public static Step step(List<Current> currents, State s, Vec3 feet, Vec3 v, boolean onGround, boolean sneaking) {
        if (s.last() != null && feet.distanceToSqr(s.last()) >= TELEPORT * TELEPORT) {
            s = State.START; // moved by a command, a respawn, a portal: no fall, no ride, nothing to remember
        }
        Current ride = s.ride();
        double peak = ride != null || onGround ? feet.y : Math.max(s.peak(), feet.y);
        Current fellIn = s.fellIn();
        if (fellIn != null && (fellIn.across(feet) > CATCH + KEEP_OUT || v.y > HOP)) {
            fellIn = null; // stepped out of it, or jumped in it
        }
        if (ride == null) {
            Current in = null;
            for (Current c : currents) {
                if (c.catches(feet)) {
                    in = c;
                    break;
                }
            }
            if (in == null) {
                return new Step(new State(null, peak, fellIn, feet), null);
            }
            if (fellIn == null && (peak - feet.y > DROP_LIMIT || v.y < -FALL_FAST
                    || (!onGround && v.y < 0.0 && feet.y > in.top() - TOP_GUARD))) {
                fellIn = in;
            }
            if (fellIn != null || sneaking || (onGround && feet.y >= in.top() - 0.5)) {
                return new Step(new State(null, peak, fellIn, feet), null);
            }
            ride = in;
        }
        return carry(ride, feet, v, sneaking);
    }

    /** One tick of a ride on {@code c}: carried on, or let go (with the velocity of a last lowering, or none). */
    private static Step carry(Current c, Vec3 feet, Vec3 v, boolean sneaking) {
        double ax = c.x() - feet.x;
        double az = c.z() - feet.z;
        double toAxis = Math.hypot(ax, az);
        double lx = c.landX() - feet.x;
        double lz = c.landZ() - feet.z;
        double toLand = Math.hypot(lx, lz);
        State letGo = new State(null, feet.y, null, feet);
        double span = Math.hypot(c.landX() - c.x(), c.landZ() - c.z());
        if (sneaking || (toAxis > span + LOST && toLand > span + LOST)) {
            return new Step(letGo, null); // a sneak lets go: a long drop through it stays a normal fall
        }
        State riding = new State(c, feet.y, null, feet);
        if (feet.y < c.top() + CLEARANCE) {
            // up the column, drawn to its axis
            double k = toAxis < 1e-6 ? 0.0 : Math.min(DRAW, toAxis) / toAxis;
            return new Step(riding, new Vec3(ax * k, Math.min(Math.max(v.y, 0.0) + LIFT, RISE), az * k));
        }
        // across to the landing at this height, then let go
        if (toLand <= LANDED) {
            return new Step(letGo, new Vec3(0.0, Math.min(v.y, 0.0), 0.0));
        }
        double k = Math.min(STEER, toLand) / toLand;
        return new Step(riding, new Vec3(lx * k, 0.0, lz * k));
    }
}
