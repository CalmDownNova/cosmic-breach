package com.cosmicbreach.mount;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Drift Manta's flight and Phase Blink (GDD 8.1). */
class MantaRulesTest {
    private static double[] cruise(double forward, double strafe, boolean jump, float yaw, float pitch, boolean drift,
                                   boolean ground, boolean fins, int ticks) {
        double[] v = {0, 0, 0};
        for (int i = 0; i < ticks; i++) {
            v = MantaRules.step(v, MantaRules.target(forward, strafe, jump, yaw, pitch, drift, ground, fins), drift);
        }
        return v;
    }

    @Test
    void inTheDriftItFliesThreeAndAHalfTenthsForwardAndAQuarterUp() {
        double[] fwd = cruise(1, 0, false, 0f, 0f, true, false, false, 60);
        assertEquals(0.35, Math.hypot(fwd[0], fwd[2]), 1e-3);
        assertEquals(0.0, fwd[1], 1e-6, "level while looking level");
        double[] up = cruise(0, 0, true, 0f, 0f, true, false, false, 60);
        assertEquals(0.25, up[1], 1e-3, "jump climbs at 0.25");
        assertEquals(0.0, Math.hypot(up[0], up[2]), 1e-6);
        double[] dive = cruise(1, 0, false, 0f, 60f, true, false, false, 60);
        assertEquals(-0.25, dive[1], 1e-3, "looking down while moving: the full dive");
        double[] climb = cruise(1, 0, false, 0f, -60f, true, false, false, 60);
        assertEquals(0.25, climb[1], 1e-3, "looking up while moving: the full climb");
        double[] hover = cruise(0, 0, false, 0f, 0f, true, false, false, 60);
        assertEquals(0.0, Math.abs(hover[0]) + Math.abs(hover[1]) + Math.abs(hover[2]), 1e-9, "no input: it hovers");
    }

    @Test
    void itSwimsIntoItsSpeedRatherThanSnapping() {
        double[] one = cruise(1, 0, false, 0f, 0f, true, false, false, 1);
        assertEquals(0.35 * MantaRules.ACCEL, one[2], 1e-9);
        double[] ten = cruise(1, 0, false, 0f, 0f, true, false, false, 10);
        assertTrue(ten[2] > 0.3 && ten[2] < 0.35);
    }

    @Test
    void theGaleFinsAddFifteenPercent() {
        double[] fwd = cruise(1, 0, false, 0f, 0f, true, false, true, 80);
        assertEquals(0.35 * 1.15, Math.hypot(fwd[0], fwd[2]), 1e-3);
        double[] up = cruise(0, 0, true, 0f, 0f, true, false, true, 80);
        assertEquals(0.25 * 1.15, up[1], 1e-3);
    }

    @Test
    void outsideTheDriftItGlidesAndCannotGainHeight() {
        double[] glide = cruise(1, 0, false, 0f, 0f, false, false, false, 60);
        assertEquals(0.35, Math.hypot(glide[0], glide[2]), 1e-3, "forward speed as in the Drift");
        assertEquals(-MantaRules.GLIDE_SINK, glide[1], 1e-3, "it sinks");
        double[] jump = cruise(1, 0, true, 0f, -80f, false, false, false, 60);
        assertTrue(jump[1] < 0.0, "jump and looking up still sink: " + jump[1]);
        double[] rising = MantaRules.step(new double[] {0, 0.3, 0}, new double[] {0, 0.3, 0}, false);
        assertEquals(0.0, rising[1], 1e-9, "even carried momentum can't lift it");
        double[] dive = cruise(1, 0, false, 0f, 70f, false, false, false, 60);
        assertEquals(-0.25, dive[1], 1e-3, "it can dive");
        double[] ground = cruise(1, 0, true, 0f, 0f, false, true, false, 60);
        assertEquals(0.35 * MantaRules.GROUND_SHARE, Math.hypot(ground[0], ground[2]), 1e-3, "on the ground it shuffles");
        assertEquals(0.0, ground[1], 1e-9);
    }

    @Test
    void movementFollowsVanillasHeadings() {
        double[] south = MantaRules.target(1, 0, false, 0f, 0f, true, false, false);
        assertEquals(0.35, south[2], 1e-9, "yaw 0 is +z");
        double[] west = MantaRules.target(1, 0, false, 90f, 0f, true, false, false);
        assertEquals(-0.35, west[0], 1e-9, "yaw 90 is -x");
        double[] left = MantaRules.target(0, 1, false, 0f, 0f, true, false, false);
        assertEquals(0.35 * 0.5, left[0], 1e-9, "strafe left at yaw 0 is +x");
        double[] back = MantaRules.target(-1, 0, false, 0f, 0f, true, false, false);
        assertEquals(-0.35 * 0.25, back[2], 1e-9);
    }

    @Test
    void phaseBlinkShieldsSixTicksAndWaitsSixSeconds() {
        assertEquals(120, MantaRules.blinkCooldown(false));
        assertEquals(84, MantaRules.blinkCooldown(true), "the Nebula Reins: -30%");
        assertTrue(MantaRules.blinkReady(1000, 880, false));
        assertFalse(MantaRules.blinkReady(1000, 881, false));
        assertTrue(MantaRules.blinkReady(1000, 916, true));
        assertFalse(MantaRules.blinkReady(1000, 917, true));
        for (int t = 0; t < 6; t++) {
            assertTrue(MantaRules.shielded(500 + t, 500), "tick " + t);
        }
        assertFalse(MantaRules.shielded(506, 500));
        assertFalse(MantaRules.shielded(499, 500));
        assertEquals(8.0, MantaRules.BLINK_DISTANCE);
    }

    @Test
    void theBlinkFollowsTheLookButNeverRisesOutsideTheDrift() {
        double[] level = MantaRules.blinkDirection(0f, 0f, true);
        assertEquals(1.0, level[2], 1e-9);
        double[] up = MantaRules.blinkDirection(0f, -45f, true);
        assertEquals(Math.sqrt(0.5), up[1], 1e-9, "in the Drift it blinks where the rider looks");
        double[] upOut = MantaRules.blinkDirection(0f, -45f, false);
        assertEquals(0.0, upOut[1], 1e-9, "outside it the blink stays level");
        assertEquals(1.0, Math.sqrt(upOut[0] * upOut[0] + upOut[2] * upOut[2]), 1e-9);
        double[] downOut = MantaRules.blinkDirection(0f, 30f, false);
        assertEquals(-0.5, downOut[1], 1e-9, "down is allowed");
        double[] straightUp = MantaRules.blinkDirection(90f, -90f, false);
        assertEquals(-1.0, straightUp[0], 1e-9, "straight up outside: level along the heading");
    }
}
