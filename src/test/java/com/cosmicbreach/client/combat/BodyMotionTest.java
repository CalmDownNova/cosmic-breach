package com.cosmicbreach.client.combat;

import com.cosmicbreach.combat.server.LaunchMath;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dash, lunge, rise and plunge numbers, run through the same per-tick physics Minecraft applies to
 * a player: move by the velocity, then friction (0.546 on grass, 0.91 in the air) and gravity.
 */
class BodyMotionTest {
    private static final double GROUND_FRICTION = 0.6 * 0.91;
    private static final double AIR_FRICTION = 0.91;
    private static final double GRAVITY = 0.08;

    /** Horizontal distance covered in {@code ticks} ticks on the ground, from standing still. */
    private static double groundDistance(BodyMotion motion, float yaw, int ticks) {
        Vec3 v = Vec3.ZERO;
        double x = 0;
        double z = 0;
        for (int i = 0; i < ticks; i++) {
            v = motion.velocity(v, yaw, false);
            x += v.x;
            z += v.z;
            v = new Vec3(v.x * GROUND_FRICTION, 0, v.z * GROUND_FRICTION);
        }
        return Math.sqrt(x * x + z * z);
    }

    @Test
    void aGroundDashCoversFiveBlocksInEightTicksThenSlidesALittle() {
        BodyMotion motion = new BodyMotion();
        motion.startDash(BodyMotion.dashDirection(0f, 1f, 0f), true);
        assertEquals(5.0, groundDistance(motion, 0f, 8), 1e-9);
        double total = 5.0 + groundDistance(motion, 0f, 40);
        assertEquals(5.0 + BodyMotion.RESIDUAL_SPEED / (1 - GROUND_FRICTION), total, 1e-6);
        assertTrue(Math.abs(total - 5.0) < 0.15 * 5.0, "within the 15% the movement test allows: " + total);
    }

    @Test
    void anAirDashCoversThreeAndAHalf() {
        BodyMotion motion = new BodyMotion();
        motion.startDash(BodyMotion.dashDirection(0f, 1f, 0f), false);
        Vec3 v = Vec3.ZERO;
        double z = 0;
        for (int i = 0; i < 8; i++) {
            v = motion.velocity(v, 0f, false);
            z += v.z;
            v = new Vec3(v.x * AIR_FRICTION, 0, v.z * AIR_FRICTION);
        }
        assertEquals(3.5, z, 1e-9);
    }

    /** Height lost over {@code ticks} ticks of falling from rest in the air, with the dash (or not) driving. */
    private static double airDrop(BodyMotion motion, int ticks) {
        Vec3 v = Vec3.ZERO;
        double y = 0;
        for (int i = 0; i < ticks; i++) {
            v = motion.velocity(v, 0f, false);
            y += v.y;
            v = new Vec3(v.x * AIR_FRICTION, (v.y - GRAVITY) * 0.98, v.z * AIR_FRICTION);
        }
        return -y;
    }

    @Test
    void anAirDashSinksUnlessItHoldsItsHeight() {
        BodyMotion plain = new BodyMotion();
        plain.startDash(BodyMotion.dashDirection(0f, 1f, 0f), false);
        assertTrue(airDrop(plain, 8) > 1.5, "gravity pulls a plain air dash down");
        BodyMotion comet = new BodyMotion();
        comet.startDash(BodyMotion.dashDirection(0f, 1f, 0f), false);
        comet.holdHeight();
        assertTrue(comet.isLevel());
        assertEquals(0.0, airDrop(comet, 8), 1e-9, "the Twin Comet Band: level for all eight ticks");
        assertFalse(comet.isLevel(), "and then gravity again");
        comet.startDash(BodyMotion.dashDirection(0f, 1f, 0f), false);
        assertFalse(comet.isLevel(), "each dash is told again");
    }

    @Test
    void onlyADashCanHoldItsHeight() {
        BodyMotion motion = new BodyMotion();
        motion.holdHeight();
        assertFalse(motion.isLevel());
        motion.startLunge(1, 1.0, 4);
        motion.holdHeight();
        assertFalse(motion.isLevel(), "a lunge is not a dash");
    }

    @Test
    void theDashFollowsTheInputAndBackstepsWithoutIt() {
        assertVec(new Vec3(0, 0, 1), BodyMotion.dashDirection(0f, 1f, 0f));   // facing south, W: south
        assertVec(new Vec3(0, 0, -1), BodyMotion.dashDirection(0f, 0f, 0f));  // no input: back north
        assertVec(new Vec3(1, 0, 0), BodyMotion.dashDirection(0f, 0f, 1f));   // A: the player's left, east
        assertVec(new Vec3(-1, 0, 0), BodyMotion.dashDirection(90f, 1f, 0f)); // facing west, W: west
        Vec3 diagonal = BodyMotion.dashDirection(0f, 1f, -1f);
        assertEquals(1.0, diagonal.length(), 1e-9, "a diagonal dash is as long as a straight one");
        assertEquals(BodyMotion.DashKind.FORWARD, BodyMotion.dashKind(1f, 0f));
        assertEquals(BodyMotion.DashKind.BACK, BodyMotion.dashKind(0f, 0f));
        assertEquals(BodyMotion.DashKind.BACK, BodyMotion.dashKind(-1f, 0f));
        assertEquals(BodyMotion.DashKind.LEFT, BodyMotion.dashKind(0f, 1f));
        assertEquals(BodyMotion.DashKind.RIGHT, BodyMotion.dashKind(0f, -1f));
        assertEquals(BodyMotion.DashKind.FORWARD, BodyMotion.dashKind(1f, 1f), "forward wins a tie");
    }

    @Test
    void theNaturalDashEndStillGetsItsEighthTick() {
        BodyMotion motion = new BodyMotion();
        motion.startDash(new Vec3(0, 0, 1), true);
        for (int i = 0; i < 7; i++) {
            motion.velocity(Vec3.ZERO, 0f, false);
        }
        motion.dashEnded(); // the machine reports the end on the 8th tick, before its hold
        assertEquals(0.625, motion.velocity(Vec3.ZERO, 0f, false).z, 1e-9);
        assertEquals(BodyMotion.RESIDUAL_SPEED, motion.velocity(Vec3.ZERO, 0f, false).z, 1e-9);
        assertFalse(motion.isDashing());
    }

    @Test
    void aDashAttackTakesTheBodyOverWithItsLunge() {
        BodyMotion motion = new BodyMotion();
        motion.startDash(new Vec3(0, 0, -1), true); // a backstep
        for (int i = 0; i < 5; i++) {
            motion.velocity(Vec3.ZERO, 0f, false);
        }
        motion.dashEnded();
        motion.moveStarted();
        motion.startLunge(7, 3.0, 5); // Pass: 3 blocks over 2 startup and 3 active ticks, forward
        double z = 0;
        for (int i = 0; i < 5; i++) {
            Vec3 v = motion.velocity(Vec3.ZERO, 0f, false);
            assertTrue(motion.suppressesInput());
            z += v.z;
        }
        assertEquals(3.0, z, 1e-9);
        assertFalse(motion.isLunging());
    }

    @Test
    void anAbilityStopsADashEarly() {
        BodyMotion motion = new BodyMotion();
        motion.startDash(new Vec3(1, 0, 0), true);
        motion.velocity(Vec3.ZERO, 0f, false);
        motion.velocity(Vec3.ZERO, 0f, false);
        motion.dashEnded();
        motion.moveStarted();
        motion.startLunge(3, 0.0, 9); // Zenith has no lunge
        Vec3 next = motion.velocity(new Vec3(0.625, 0, 0), 0f, false);
        assertEquals(BodyMotion.RESIDUAL_SPEED, next.x, 1e-9, "down to the residual");
        assertFalse(motion.suppressesInput());
    }

    @Test
    void aLungeFollowsTheFacingAndOnlyItsOwnCancelStopsIt() {
        BodyMotion motion = new BodyMotion();
        motion.startLunge(4, 1.0, 7); // L3: 1 block over 5 startup and 2 active ticks
        Vec3 first = motion.velocity(Vec3.ZERO, 0f, false);
        assertEquals(1.0 / 7, first.z, 1e-9);
        Vec3 turned = motion.velocity(Vec3.ZERO, 90f, false);
        assertEquals(-1.0 / 7, turned.x, 1e-9, "turned west, the lunge goes west");
        motion.moveCancelled(99);
        assertTrue(motion.isLunging(), "another move's cancel leaves it alone");
        motion.moveCancelled(4);
        assertFalse(motion.isLunging());
        assertEquals(-BodyMotion.RESIDUAL_SPEED, motion.velocity(Vec3.ZERO, 90f, false).x, 1e-9, "the residual keeps the last direction");
    }

    @Test
    void theRisePeaksJustUnderItsHeight() {
        BodyMotion motion = new BodyMotion();
        motion.rise(3.0, GRAVITY);
        Vec3 v = motion.velocity(Vec3.ZERO, 0f, false);
        assertEquals(LaunchMath.velocityForHeight(3.0 - BodyMotion.RISE_MARGIN, GRAVITY), v.y, 1e-12);
        double apex = LaunchMath.apex(v.y, GRAVITY);
        assertTrue(apex < 3.0 && apex > 2.9, "apex " + apex);
        assertEquals(0.0, motion.velocity(new Vec3(0, 0.3, 0), 0f, false).y - 0.3, 1e-12, "a rise is one kick");
    }

    /** Heights over {@code ticks} ticks from standing, with the ability let go after {@code heldTicks}. */
    private static double[] heldRise(BodyMotion motion, int ticks, int heldTicks) {
        double[] ys = new double[ticks];
        Vec3 v = Vec3.ZERO;
        double y = 0;
        for (int i = 0; i < ticks; i++) {
            motion.ground(y <= 0);
            if (i >= heldTicks) {
                motion.releaseLift();
            }
            v = motion.velocity(v, 0f, false);
            y = Math.max(0, y + v.y);
            ys[i] = y;
            v = new Vec3(0, y <= 0 ? 0 : (v.y - GRAVITY) * 0.98, 0);
        }
        return ys;
    }

    @Test
    void aHeldRiseHangsAtItsApexForItsHoverTicksThenFalls() {
        BodyMotion motion = new BodyMotion();
        motion.rise(3.0, GRAVITY, 30);
        double[] ys = heldRise(motion, 80, 80);
        double peak = java.util.Arrays.stream(ys).max().orElseThrow();
        assertTrue(peak > 2.9 && peak < 3.0, "peak " + peak);
        long high = java.util.Arrays.stream(ys).filter(y -> y >= 2.0).count();
        assertTrue(high >= 34,"hangs near the top for the hover's 30 ticks and the end of the rise: " + high);
        assertEquals(0.0, ys[ys.length - 1], 1e-9, "then comes down");
        assertFalse(motion.isLifting(), "and the hold is over");
        for (int i = 1; i < ys.length; i++) {
            assertTrue(ys[i - 1] - ys[i] < 1.0, "no sudden drop at tick " + i);
        }
    }

    @Test
    void lettingGoEndsTheHoldAndARiseWithoutHoverIsOneKick() {
        BodyMotion held = new BodyMotion();
        held.rise(3.0, GRAVITY, 30);
        BodyMotion letGo = new BodyMotion();
        letGo.rise(3.0, GRAVITY, 30);
        BodyMotion kick = new BodyMotion();
        kick.rise(3.0, GRAVITY);
        double[] a = heldRise(held, 40, 40);
        double[] b = heldRise(letGo, 40, 12);
        double[] c = heldRise(kick, 40, 40);
        assertTrue(a[25] > 2.0, "held: still up at tick 25");
        assertEquals(0.0, b[30], 1e-9, "let go at tick 12: down by tick 30");
        assertEquals(0.0, c[30], 1e-9, "no hover ticks: down by tick 30");
    }

    @Test
    void aPlungeOrADashBreaksTheHold() {
        BodyMotion motion = new BodyMotion();
        motion.rise(3.0, GRAVITY, 30);
        heldRise(motion, 15, 15);
        assertTrue(motion.isHovering());
        assertEquals(BodyMotion.PLUNGE_SPEED, motion.velocity(Vec3.ZERO, 0f, true).y, 1e-12);
        assertFalse(motion.isLifting());

        BodyMotion dashing = new BodyMotion();
        dashing.rise(3.0, GRAVITY, 30);
        heldRise(dashing, 15, 15);
        dashing.startDash(new Vec3(0, 0, 1), false);
        assertFalse(dashing.isLifting());
    }

    @Test
    void aPlungeHoldsItsFallSpeed() {
        BodyMotion motion = new BodyMotion();
        assertEquals(-1.2, motion.velocity(new Vec3(0.1, 0.2, 0), 0f, true).y, 1e-12);
        assertEquals(0.1, motion.velocity(new Vec3(0.1, 0.2, 0), 0f, true).x, 1e-12, "horizontal untouched");
        assertEquals(0.2, motion.velocity(new Vec3(0.1, 0.2, 0), 0f, false).y, 1e-12);
    }

    @Test
    void inputIsOnlySuppressedWhileADriveHoldsTheBody() {
        BodyMotion motion = new BodyMotion();
        motion.velocity(Vec3.ZERO, 0f, false);
        assertFalse(motion.suppressesInput());
        motion.startDash(new Vec3(0, 0, 1), true);
        for (int i = 0; i < BodyMotion.DASH_TICKS; i++) {
            motion.velocity(Vec3.ZERO, 0f, false);
            assertTrue(motion.suppressesInput(), "hold " + i);
        }
        motion.velocity(Vec3.ZERO, 0f, false);
        assertFalse(motion.suppressesInput(), "the residual tick is the player's again");
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1e-9, "x");
        assertEquals(expected.y, actual.y, 1e-9, "y");
        assertEquals(expected.z, actual.z, 1e-9, "z");
    }

    // ------------------------------------------------------------------ the blink (Binary Edges)

    @Test
    void aBlinkFliesTheBodyToItsPointInItsTicksAndStopsDead() {
        BodyMotion motion = new BodyMotion();
        Vec3 pos = new Vec3(0, 64, 0);
        Vec3 to = new Vec3(0, 65, 7.0);
        motion.startBlink(to, 6);
        Vec3 v = Vec3.ZERO;
        for (int i = 0; i < 6; i++) {
            assertTrue(motion.isBlinking());
            v = motion.velocity(v, 0f, false, pos);
            assertTrue(motion.suppressesInput(), "the player's own input is ignored");
            if (i == 2) {
                v = v.scale(0.5); // bumped: made up on the next ticks
            }
            pos = pos.add(v);
            v = new Vec3(v.x * AIR_FRICTION, (v.y - GRAVITY) * 0.98, v.z * AIR_FRICTION);
        }
        assertEquals(0.0, pos.distanceTo(to), 1e-9, "arrived exactly");
        assertFalse(motion.isBlinking());
        Vec3 after = motion.velocity(new Vec3(0.3, -0.5, 0.4), 0f, false, pos);
        assertEquals(0.0, after.x, 1e-9, "no speed left over");
        assertEquals(0.0, after.y, 1e-9);
        assertEquals(0.0, after.z, 1e-9);
    }

    @Test
    void aCancelledMoveStopsABlink() {
        BodyMotion motion = new BodyMotion();
        motion.startBlink(new Vec3(0, 64, 5), 6);
        motion.velocity(Vec3.ZERO, 0f, false, new Vec3(0, 64, 0));
        motion.moveCancelled(99);
        assertFalse(motion.isBlinking());
    }

    @Test
    void gearMakesTheDashLongerInTheSameTicks() {
        BodyMotion m = new BodyMotion();
        m.startDash(new Vec3(0, 0, 1), true, 1.2);
        double z = 0;
        int held = 0;
        for (int t = 0; t < BodyMotion.DASH_TICKS; t++) {
            z += m.velocity(Vec3.ZERO, 0f, false).z;
            if (m.suppressesInput()) {
                held++;
            }
        }
        assertEquals(6.0, z, 1e-9, "5 blocks x 1.2 over the same 8 ticks");
        assertEquals(BodyMotion.DASH_TICKS, held);
        BodyMotion air = new BodyMotion();
        air.startDash(new Vec3(1, 0, 0), false, 1.2);
        double x = 0;
        for (int t = 0; t < BodyMotion.DASH_TICKS; t++) {
            x += air.velocity(Vec3.ZERO, 0f, false).x;
        }
        assertEquals(4.2, x, 1e-9, "3.5 in the air x 1.2");
        BodyMotion plain = new BodyMotion();
        plain.startDash(new Vec3(0, 0, 1), true, Double.NaN);
        double p = 0;
        for (int t = 0; t < BodyMotion.DASH_TICKS; t++) {
            p += plain.velocity(Vec3.ZERO, 0f, false).z;
        }
        assertEquals(5.0, p, 1e-9, "a nonsense scale leaves the dash alone");
    }
}
