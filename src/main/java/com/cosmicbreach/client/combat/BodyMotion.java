package com.cosmicbreach.client.combat;

import com.cosmicbreach.combat.core.CombatRules;
import com.cosmicbreach.combat.server.effect.TetherMath;
import com.cosmicbreach.combat.server.LaunchMath;
import net.minecraft.world.phys.Vec3;

/**
 * The movement half of combat, for the local player. Movement is client-authoritative in Minecraft,
 * so the client moves its own body for its moves and the server only sees where it went.
 *
 * <p>Once per tick, just before the body moves, {@link #velocity} turns the body's velocity into the
 * one to move with this tick:
 * <ul>
 *   <li>Dash: horizontal speed held along the direction picked when it started, for
 *       {@value #DASH_TICKS} ticks: {@value #DASH_GROUND_BLOCKS} blocks from the ground,
 *       {@value #DASH_AIR_BLOCKS} from the air. Then a small residual. An air dash told to {@link #holdHeight}
 *       flies level for its ticks (the Twin Comet Band), so two chain into a double dash without sinking.</li>
 *   <li>Lunge: the move's {@code lunge} blocks along the facing, spread evenly over its startup and
 *       active ticks, starting on its first tick, so the step lands with the hit. Then a small residual.</li>
 *   <li>Rise: one upward velocity that peaks just under the rise height (a fall from exactly the
 *       safe fall distance would still hurt by a rounding error).</li>
 *   <li>Plunge: vertical speed held at {@value #PLUNGE_SPEED} blocks per tick while plunging.</li>
 *   <li>Blink (the Binary Edges' Tether): straight to a point in the world over a few ticks, the same share of
 *       what is left every tick (from wherever the body is, so a bump on the way is made up), up and down
 *       included; then it stops dead.</li>
 * </ul>
 * While a dash or lunge holds the body, the player's own movement input is ignored
 * ({@link #suppressesInput}), so the distances come out exact. Pure: no world access.
 */
public final class BodyMotion {
    public static final int DASH_TICKS = CombatRules.DASH_TICKS;
    public static final double DASH_GROUND_BLOCKS = 5.0;
    public static final double DASH_AIR_BLOCKS = 3.5;
    /** Horizontal speed left after a dash or lunge, in blocks per tick; friction takes it from there. */
    public static final double RESIDUAL_SPEED = 0.12;
    public static final double PLUNGE_SPEED = -1.2;
    /** A rise peaks this far under its height. */
    public static final double RISE_MARGIN = 0.02;

    /** Things that shorten or lengthen the local player's dashes (a Crushing Gravity Plate halves them), multiplied. */
    private static final java.util.List<java.util.function.DoubleSupplier> DASH_SCALES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Adds a dash length factor, read when each dash starts (1 when it has nothing to say). */
    public static void addDashScale(java.util.function.DoubleSupplier scale) {
        DASH_SCALES.add(scale);
    }

    public static double dashScale() {
        double k = 1.0;
        for (java.util.function.DoubleSupplier s : DASH_SCALES) {
            k *= s.getAsDouble();
        }
        return k;
    }

    /** Where a dash goes relative to the facing, which picks its animation. */
    public enum DashKind { FORWARD, BACK, LEFT, RIGHT }

    private enum Drive { NONE, DASH, LUNGE, BLINK }

    private Drive drive = Drive.NONE;
    private double dirX;
    private double dirZ;
    private double speed;
    private int ticksLeft;
    private int holdsApplied;
    private int lungeSerial;
    private boolean residualNext;
    private double residualX;
    private double residualZ;
    private double riseVelocity = Double.NaN;
    private boolean heldThisTick;
    private Vec3 blinkTo = Vec3.ZERO;
    /** The tick after a blink: no vertical speed left over either. */
    private boolean stopFallNext;
    /** The running dash flies level: no vertical speed while it holds the body. */
    private boolean level;

    // ------------------------------------------------------------------ what the combat machine says

    /** A dash started along {@code direction} (horizontal, unit length). */
    public void startDash(Vec3 direction, boolean onGround) {
        startDash(direction, onGround, 1.0);
    }

    /**
     * A dash started along {@code direction} (horizontal, unit length), its distance times {@code distanceScale}
     * (gear: the Driftweave's x1.2). Same ticks, so it is faster, not longer.
     */
    public void startDash(Vec3 direction, boolean onGround, double distanceScale) {
        drive = Drive.DASH;
        dirX = direction.x;
        dirZ = direction.z;
        double scale = Double.isFinite(distanceScale) && distanceScale > 0 ? distanceScale : 1.0;
        // gear (the Driftweave's +20%) times the ground's own pull (a Gravity Plate halves it)
        speed = (onGround ? DASH_GROUND_BLOCKS : DASH_AIR_BLOCKS) * scale / DASH_TICKS * dashScale();
        ticksLeft = DASH_TICKS;
        holdsApplied = 0;
        residualNext = false;
        level = false;
    }

    /** The dash just started flies level for its ticks (the Twin Comet Band's chained air dashes). */
    public void holdHeight() {
        if (drive == Drive.DASH) {
            level = true;
        }
    }

    /** True while a level dash holds the body. */
    public boolean isLevel() {
        return drive == Drive.DASH && level;
    }

    /**
     * The machine ended the dash. It ends a natural dash on its 8th tick, before that tick's hold,
     * so only an earlier end (a dash attack or an ability) stops the drive here.
     */
    public void dashEnded() {
        if (drive == Drive.DASH && holdsApplied < DASH_TICKS - 1) {
            stop();
        }
    }

    /** A move started: it takes the body over from a dash (its own lunge, if any, comes next). */
    public void moveStarted() {
        if (drive == Drive.DASH) {
            stop();
        }
    }

    /** Lunge {@code blocks} over {@code ticks} ticks for the move with this serial. */
    public void startLunge(int serial, double blocks, int ticks) {
        if (blocks <= 0 || ticks <= 0) {
            return;
        }
        drive = Drive.LUNGE;
        lungeSerial = serial;
        speed = blocks / ticks;
        ticksLeft = ticks;
        holdsApplied = 0;
        residualNext = false;
    }

    /** A move ended early (cancelled): its lunge stops, and a blink (nothing else runs during one). */
    public void moveCancelled(int serial) {
        if (drive == Drive.LUNGE && lungeSerial == serial || drive == Drive.BLINK) {
            stop();
        }
    }

    /** Blink to {@code to} (world position of the feet) over {@code ticks} ticks, from the next tick on. */
    public void startBlink(Vec3 to, int ticks) {
        if (ticks <= 0) {
            return;
        }
        drive = Drive.BLINK;
        blinkTo = to;
        ticksLeft = ticks;
        holdsApplied = 0;
        residualNext = false;
        stopFallNext = false;
    }

    public boolean isBlinking() {
        return drive == Drive.BLINK;
    }

    /** Lift the body so it peaks about {@code height} blocks up, under {@code gravity} per tick. */
    public void rise(double height, double gravity) {
        if (height > RISE_MARGIN && gravity > 0) {
            riseVelocity = LaunchMath.velocityForHeight(height - RISE_MARGIN, gravity);
        }
    }

    public void reset() {
        drive = Drive.NONE;
        level = false;
        residualNext = false;
        riseVelocity = Double.NaN;
        heldThisTick = false;
        stopFallNext = false;
    }

    // ------------------------------------------------------------------ once per tick

    /**
     * {@link #velocity(Vec3, float, boolean)} for a body at {@code position}, which a blink steers from.
     * Call exactly once per tick, before the body moves.
     */
    public Vec3 velocity(Vec3 current, float yawDeg, boolean plunging, Vec3 position) {
        if (drive == Drive.BLINK) {
            heldThisTick = true;
            Vec3 step = TetherMath.blinkStep(position, blinkTo, ticksLeft);
            holdsApplied++;
            if (--ticksLeft <= 0) {
                drive = Drive.NONE;
                residualX = 0;
                residualZ = 0;
                residualNext = true;
                stopFallNext = true;
            }
            return step;
        }
        if (stopFallNext) {
            stopFallNext = false;
            Vec3 v = velocity(current, yawDeg, plunging);
            return new Vec3(v.x, 0.0, v.z); // it stops dead in the air too; gravity takes over from here
        }
        return velocity(current, yawDeg, plunging);
    }

    /**
     * The velocity to move with this tick, given the body's current velocity and facing and whether
     * the machine is plunging. Call exactly once per tick, before the body moves.
     */
    public Vec3 velocity(Vec3 current, float yawDeg, boolean plunging) {
        double vx = current.x;
        double vy = current.y;
        double vz = current.z;
        heldThisTick = drive != Drive.NONE;
        boolean flat = drive == Drive.DASH && level;
        if (drive != Drive.NONE) {
            if (drive == Drive.LUNGE) {
                Vec3 facing = forward(yawDeg); // a lunge follows the facing, so the player can steer it
                dirX = facing.x;
                dirZ = facing.z;
            }
            vx = dirX * speed;
            vz = dirZ * speed;
            holdsApplied++;
            if (--ticksLeft <= 0) {
                stop();
            }
        } else if (residualNext) {
            vx = residualX;
            vz = residualZ;
            residualNext = false;
        }
        if (!Double.isNaN(riseVelocity)) {
            vy = riseVelocity;
            riseVelocity = Double.NaN;
        } else if (plunging) {
            vy = PLUNGE_SPEED;
        } else if (flat) {
            vy = 0.0;
        }
        return new Vec3(vx, vy, vz);
    }

    /** True in a tick whose velocity a dash or lunge held: the player's movement input is ignored. */
    public boolean suppressesInput() {
        return heldThisTick;
    }

    public boolean isDashing() {
        return drive == Drive.DASH;
    }

    public boolean isLunging() {
        return drive == Drive.LUNGE;
    }

    /** Ends the drive; if it moved the body at all, the next tick drops to the residual speed. */
    private void stop() {
        if (drive != Drive.NONE && holdsApplied > 0) {
            double residual = Math.min(RESIDUAL_SPEED, speed);
            residualX = dirX * residual;
            residualZ = dirZ * residual;
            residualNext = true;
        }
        drive = Drive.NONE;
    }

    // ------------------------------------------------------------------ helpers

    /** Unit vector along the ground for a Minecraft yaw (0 = south, +Z). */
    public static Vec3 forward(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
    }

    /**
     * The horizontal dash direction for a facing and movement input ({@code forward} and {@code left}
     * impulses, as Minecraft's input has them), rotated the way Minecraft rotates walking input.
     * No input is a backstep.
     */
    public static Vec3 dashDirection(float yawDeg, float forward, float left) {
        double f = forward;
        double l = left;
        if (Math.abs(f) < 1e-3 && Math.abs(l) < 1e-3) {
            f = -1.0;
            l = 0.0;
        }
        double length = Math.sqrt(f * f + l * l);
        f /= length;
        l /= length;
        double r = Math.toRadians(yawDeg);
        double sin = Math.sin(r);
        double cos = Math.cos(r);
        return new Vec3(l * cos - f * sin, 0.0, f * cos + l * sin);
    }

    /** Which way a dash with this input goes: the stronger of the two axes wins, forward on a tie. */
    public static DashKind dashKind(float forward, float left) {
        if (Math.abs(forward) < 1e-3 && Math.abs(left) < 1e-3) {
            return DashKind.BACK;
        }
        if (Math.abs(forward) >= Math.abs(left)) {
            return forward > 0 ? DashKind.FORWARD : DashKind.BACK;
        }
        return left > 0 ? DashKind.LEFT : DashKind.RIGHT;
    }
}
