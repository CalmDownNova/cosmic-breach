package com.cosmicbreach.mount;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Lumen Stag's rules (GDD 8.1): trust, the triple jump, the glide. */
class StagRulesTest {
    // ------------------------------------------------------------------ trust

    @Test
    void aFeedAddsOneOrTwoAndFiveTames() {
        assertEquals(1, StagRules.feed(0, false));
        assertEquals(2, StagRules.feed(0, true));
        assertEquals(4, StagRules.feed(3, false));
        assertEquals(5, StagRules.feed(4, true), "never past five");
        assertFalse(StagRules.tames(4));
        assertTrue(StagRules.tames(5));
        int trust = 0;
        int feeds = 0;
        while (!StagRules.tames(trust)) {
            trust = StagRules.feed(trust, false);
            feeds++;
        }
        assertEquals(5, feeds, "five plain feeds tame it");
        trust = 0;
        feeds = 0;
        while (!StagRules.tames(trust)) {
            trust = StagRules.feed(trust, true);
            feeds++;
        }
        assertEquals(3, feeds, "three lucky ones (2, 4, 5)");
    }

    @Test
    void aScareTakesTwoAndNeverGoesBelowZero() {
        assertEquals(3, StagRules.lose(5));
        assertEquals(0, StagRules.lose(2));
        assertEquals(0, StagRules.lose(1));
        assertEquals(0, StagRules.lose(0));
    }

    @Test
    void sprintersWithinSixteenMakeTheHerdBoltAndWithinEightCostTrust() {
        assertTrue(StagRules.bolts(16.0, true));
        assertTrue(StagRules.bolts(9.0, true));
        assertFalse(StagRules.bolts(16.5, true));
        assertFalse(StagRules.bolts(3.0, false), "walking or sneaking never makes it bolt");
        assertTrue(StagRules.spooks(8.0, true));
        assertFalse(StagRules.spooks(8.5, true), "between 8 and 16 a sprinter only scatters them");
        assertFalse(StagRules.spooks(2.0, false));
    }

    @Test
    void aSprintCostsTrustOncePerCooldown() {
        assertTrue(StagRules.sprintLossReady(100, Long.MIN_VALUE / 2));
        assertFalse(StagRules.sprintLossReady(100 + StagRules.SPRINT_LOSS_COOLDOWN - 1, 100));
        assertTrue(StagRules.sprintLossReady(100 + StagRules.SPRINT_LOSS_COOLDOWN, 100));
    }

    @Test
    void onlySneakingPlayersGetCloseWithoutItSteppingAway() {
        assertTrue(StagRules.wary(3.0, false));
        assertFalse(StagRules.wary(3.0, true));
        assertFalse(StagRules.wary(6.0, false));
    }

    @Test
    void theAntlersBrightenWithEachPointOfTrust() {
        float last = -1f;
        for (int t = 0; t <= 5; t++) {
            float g = StagRules.antlerGlow(t, false);
            assertTrue(g > last, "brighter at " + t);
            last = g;
        }
        assertTrue(StagRules.antlerGlow(0, false) > 0f, "a faint light even at no trust");
        assertEquals(1.0f, StagRules.antlerGlow(5, false), 1e-6);
        assertEquals(1.0f, StagRules.antlerGlow(0, true), 1e-6, "tamed: full");
        assertEquals(StagRules.antlerGlow(0, false), StagRules.antlerGlow(-3, false), 1e-6);
    }

    // ------------------------------------------------------------------ jumps

    @Test
    void eachJumpRisesExactlyItsHeightUnderVanillaMotion() {
        for (int i = 0; i < 4; i++) {
            double v = StagRules.jumpVelocity(i);
            double rise = simulate(v, StagRules.GRAVITY);
            assertEquals(StagRules.jumpHeight(i), rise, 0.01, "jump " + (i + 1));
        }
        assertEquals(2.5, StagRules.jumpHeight(0));
        assertEquals(3.5, StagRules.jumpHeight(1));
        assertEquals(4.0, StagRules.jumpHeight(2));
        assertEquals(4.0, StagRules.jumpHeight(3), "the Comet Bridle's fourth");
    }

    @Test
    void theApexMatchesVanillasMotion() {
        // without the rider's-client scaling it is the player's own jump: 0.42 tops out at about 1.25 blocks
        assertEquals(1.25, StagRules.apex(0.42, 0.08, 1.0), 0.01);
        // a ridden mount on its rider's client, as measured in game: 0.6177 rose 2.332 before the scaling was counted
        assertEquals(2.332, StagRules.apex(0.6177, 0.08, StagRules.CLIENT_DRAG), 0.005);
    }

    @Test
    void jumpsAreUsedInOrderAndTheBridleAddsAFourth() {
        assertEquals(3, StagRules.jumps(false));
        assertEquals(4, StagRules.jumps(true));
        assertEquals(0, StagRules.nextJump(true, 0, false), "from the ground: the first");
        assertEquals(0, StagRules.nextJump(true, 3, false), "landing gives them all back");
        assertEquals(1, StagRules.nextJump(false, 1, false), "then the second in mid-air");
        assertEquals(2, StagRules.nextJump(false, 2, false), "and the third");
        assertEquals(-1, StagRules.nextJump(false, 3, false), "no fourth without the bridle");
        assertEquals(3, StagRules.nextJump(false, 3, true), "the bridle's fourth");
        assertEquals(-1, StagRules.nextJump(false, 4, true));
        assertEquals(1, StagRules.nextJump(false, 0, false), "stepped off a ledge: the mid-air jumps are left");
    }

    // ------------------------------------------------------------------ glide

    @Test
    void theGlideSinksATenthWhileMovingFiveAndAHalfTimesThatForward() {
        double[] v = {0.0, 0.0, 0.0};
        for (int i = 0; i < 40; i++) {
            v = StagRules.glide(v[0], v[2], 0f, false);
        }
        double forward = Math.hypot(v[0], v[2]);
        assertEquals(0.55, forward, 1e-3);
        assertEquals(-0.1, v[1], 1e-9);
        assertEquals(5.5, forward / -v[1], 0.02);
        assertEquals(0.55, v[2], 1e-3, "yaw 0 heads +z");
    }

    @Test
    void theHaloReinsCutTheSinkByThirtyPercent() {
        assertEquals(0.07, StagRules.glideSink(true), 1e-9);
        double[] v = {0.0, 0.0, 0.0};
        for (int i = 0; i < 40; i++) {
            v = StagRules.glide(v[0], v[2], 90f, true);
        }
        assertEquals(-0.07, v[1], 1e-9);
        assertEquals(-0.55, v[0], 1e-3, "yaw 90 heads -x");
        assertEquals(0.55 / 0.07, Math.hypot(v[0], v[2]) / -v[1], 0.05);
    }

    @Test
    void itGlidesOnlyWhenFallingWithJumpHeld() {
        assertTrue(StagRules.glides(true, false, -0.2, false));
        assertTrue(StagRules.glides(true, false, 0.0, false), "at the apex");
        assertFalse(StagRules.glides(true, false, 0.3, false), "still rising");
        assertFalse(StagRules.glides(false, false, -0.2, false));
        assertFalse(StagRules.glides(true, true, -0.2, false));
        assertFalse(StagRules.glides(true, false, -0.2, true), "not in water");
    }

    @Test
    void aGlideNeverAddsToTheFall() {
        assertTrue(StagRules.fallHarmless(-0.1));
        assertTrue(StagRules.fallHarmless(-0.07));
        assertTrue(StagRules.fallHarmless(0.4));
        assertFalse(StagRules.fallHarmless(-0.5));
    }

    @Test
    void theStatsFollowTheTable() {
        assertEquals(30.0, StagRules.HEALTH);
        assertEquals(0.30, StagRules.SPEED);
        assertArrayEquals(new double[] {2.5, 3.5, 4.0}, StagRules.JUMP_HEIGHTS);
    }

    /**
     * The same motion as a ridden mount on its rider's client: LivingEntity.aiStep scales the speed by 0.98 (from the
     * second tick; the jump sets it inside travel, after that), then travel moves by it, subtracts gravity, keeps 98%.
     */
    private static double simulate(double v0, double g) {
        double y = 0;
        double top = 0;
        double v = v0;
        for (int i = 0; i < 400; i++) {
            if (i > 0) {
                v *= 0.98;
                if (Math.abs(v) < 0.003) {
                    v = 0;
                }
            }
            y += v;
            top = Math.max(top, y);
            v = (v - g) * 0.98;
        }
        return top;
    }
}
