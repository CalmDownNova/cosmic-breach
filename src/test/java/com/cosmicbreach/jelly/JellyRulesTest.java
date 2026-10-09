package com.cosmicbreach.jelly;

import com.cosmicbreach.jelly.JellyRules.BloomPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drift jelly's rules (1.2 design section 7): the bounce, the sting, the skim boost, where it hovers and spawns, the bloom. */
class JellyRulesTest {
    // ------------------------------------------------------------------ the bounce

    @Test
    void theBounceBeatsASlimeBlockAtEveryHeight() {
        for (double fall = 0.5; fall <= 38.0; fall += 0.5) { // beyond that a launch is capped (see below)
            double slime = JellyRules.slimeLaunch(fall);
            double jelly = JellyRules.launchSpeed(fall);
            assertTrue(jelly > slime, "fall " + fall + ": jelly " + jelly + " vs slime " + slime);
        }
    }

    @Test
    void aGentleLandingStillBouncesHigh() {
        assertEquals(JellyRules.BOUNCE_MIN, JellyRules.launchSpeed(0.2), 1e-9);
        assertTrue(JellyRules.apexHeight(JellyRules.BOUNCE_MIN) > 4.0, "at least four blocks: more than three jumps");
    }

    @Test
    void aLongFallBouncesHigherButNotWithoutLimit() {
        assertTrue(JellyRules.launchSpeed(12.0) > JellyRules.launchSpeed(3.0));
        assertEquals(JellyRules.BOUNCE_MAX, JellyRules.launchSpeed(500.0), 1e-9);
        assertTrue(JellyRules.apexHeight(JellyRules.BOUNCE_MAX) < 40.0, "a launch never throws a player out of the layer");
    }

    @Test
    void landingAgainAndAgainSettlesInsteadOfClimbing() {
        double fall = 8.0;
        double last = 0.0;
        for (int i = 0; i < 12; i++) {
            double apex = JellyRules.apexHeight(JellyRules.launchSpeed(fall));
            assertTrue(apex <= 16.0, "bounce " + i + " reached " + apex);
            last = fall;
            fall = apex;
        }
        assertEquals(last, fall, 0.5, "the bounces have settled");
        assertTrue(fall >= 12.0, "at a good height: " + fall);
        // a fall from high up does not get a launch higher than what it fell, beyond a slime's own return
        assertTrue(JellyRules.launchSpeed(30.0) <= JellyRules.slimeLaunch(30.0) * 1.06 + 1e-9);
    }

    @Test
    void landingOnTheBellNeedsTheFeetAtItsTopAndInsideItsWidth() {
        double top = 100 + JellyRules.HEIGHT;
        assertTrue(JellyRules.landsOnBell(0.0, 0.0, top, 100));
        assertTrue(JellyRules.landsOnBell(1.4, -1.4, top + 0.2, 100), "near a corner of the bell still counts");
        assertFalse(JellyRules.landsOnBell(2.4, 0.0, top, 100), "beside it");
        assertFalse(JellyRules.landsOnBell(0.0, 0.0, top - 1.0, 100), "inside the bell: that is the sting's side");
        assertFalse(JellyRules.landsOnBell(0.0, 0.0, top + 1.5, 100), "still well above it");
    }

    // ------------------------------------------------------------------ skim

    @Test
    void skimAddsThirtyPercentToAirAcceleration() {
        assertEquals(0.0f, JellyRules.extraAirAccel(false));
        assertEquals(JellyRules.AIR_ACCEL * 0.30f, JellyRules.extraAirAccel(true), 1e-9);
        assertEquals(80, JellyRules.SKIM_TICKS, "about four seconds");
    }

    // ------------------------------------------------------------------ the sting

    @Test
    void theStingIsSmallAndHangsBelowTheBellOnly() {
        assertTrue(JellyRules.STING_DAMAGE <= 3.0f && JellyRules.STING_DAMAGE >= 1.0f, "small damage");
        assertTrue(JellyRules.stingsAt(0.0, -1.0, 0.0), "a block under the bell");
        assertTrue(JellyRules.stingsAt(1.0, -3.9, -1.0), "down at the tendrils' ends");
        assertFalse(JellyRules.stingsAt(0.0, 0.5, 0.0), "inside the bell");
        assertFalse(JellyRules.stingsAt(0.0, -JellyRules.TENDRIL_HANG - 0.5, 0.0), "below the tips");
        assertFalse(JellyRules.stingsAt(2.0, -1.0, 0.0), "beside the tendrils");
    }

    @Test
    void aTargetIsStungOnceASecondAtMost() {
        assertFalse(JellyRules.stingReady(100, 90), "ten ticks after the last sting");
        assertTrue(JellyRules.stingReady(100, 79), "twenty-one after");
        assertTrue(JellyRules.stingReady(100, Integer.MIN_VALUE / 2), "never stung");
    }

    // ------------------------------------------------------------------ hovering

    @Test
    void itClimbsWhenLowSinksWhenHighAndHoldsInTheBand() {
        assertTrue(JellyRules.hoverVelocity(0.5) > 0, "too close to the ground");
        assertTrue(JellyRules.hoverVelocity(7.5) < 0, "too high above it");
        assertEquals(0.0, JellyRules.hoverVelocity(3.5), 1e-9);
        assertEquals(0.0, JellyRules.hoverVelocity(JellyRules.UNKNOWN_GROUND), 1e-9, "over a void it holds its height");
        assertTrue(JellyRules.hoverVelocity(2.5) >= 0 && JellyRules.hoverVelocity(4.5) <= 0);
    }

    // ------------------------------------------------------------------ spawning

    @Test
    void mostSpawnsHoverTwoToFiveBlocksOverGround() {
        for (int d = 2; d <= 5; d++) {
            assertEquals(1.0, JellyRules.spawnChance(d, false), "over ground at " + d);
        }
        assertTrue(JellyRules.spawnChance(1, false) < 0.5, "tendrils would be in the ground");
        assertTrue(JellyRules.spawnChance(7, false) < 1.0);
        assertTrue(JellyRules.spawnChance(JellyRules.UNKNOWN_GROUND, false) < 0.05, "fewer over voids");
    }

    @Test
    void voidSpawnsAreDenserInTheDenseZonesButStillRarerThanLand() {
        double plain = JellyRules.spawnChance(JellyRules.UNKNOWN_GROUND, false);
        double dense = JellyRules.spawnChance(JellyRules.UNKNOWN_GROUND, true);
        assertTrue(dense > plain * 2.5);
        assertTrue(dense < JellyRules.spawnChance(3, true));
        assertEquals(JellyRules.spawnChance(3, false), JellyRules.spawnChance(3, true), "land is land");
    }

    @Test
    void anAreaHoldsOnlyAFewNaturalJellies() {
        assertFalse(JellyRules.crowded(1));
        assertTrue(JellyRules.crowded(JellyRules.AREA_CAP));
    }

    // ------------------------------------------------------------------ the bloom

    @Test
    void aBloomIsFifteenToTwentyFiveJellies() {
        assertEquals(15, JellyRules.bloomCount(0.0));
        assertEquals(25, JellyRules.bloomCount(0.999999));
        for (double r = 0; r < 1.0; r += 0.01) {
            int n = JellyRules.bloomCount(r);
            assertTrue(n >= 15 && n <= 25, "" + n);
        }
    }

    @Test
    void aBloomRisesDriftsThenSinksAway() {
        int life = 6000;
        assertEquals(BloomPhase.RISE, JellyRules.bloomPhase(0, life));
        assertEquals(BloomPhase.RISE, JellyRules.bloomPhase(life * 2 / 5 - 1, life));
        assertEquals(BloomPhase.DRIFT, JellyRules.bloomPhase(life * 2 / 5, life));
        assertEquals(BloomPhase.DRIFT, JellyRules.bloomPhase(life * 7 / 10 - 1, life));
        assertEquals(BloomPhase.SINK, JellyRules.bloomPhase(life * 7 / 10, life));
        assertEquals(BloomPhase.GONE, JellyRules.bloomPhase(life, life));
        assertTrue(JellyRules.bloomVertical(BloomPhase.RISE) > 0);
        assertEquals(0.0, JellyRules.bloomVertical(BloomPhase.DRIFT));
        assertTrue(JellyRules.bloomVertical(BloomPhase.SINK) < 0);
    }

    @Test
    void aBloomLastsAFewMinutesAndIsRare() {
        assertTrue(JellyRules.bloomLife(0.0) >= 4 * 60 * 20 && JellyRules.bloomLife(0.999) <= 7 * 60 * 20, "four to seven minutes");
        assertTrue(JellyRules.bloomDelay(0.0) >= 20 * 60 * 20, "no sooner than twenty minutes");
        assertTrue(JellyRules.bloomDelay(0.999) <= 70 * 60 * 20);
    }

    @Test
    void aBloomRisesFarEnoughToGetThroughTheLayer() {
        int life = JellyRules.bloomLife(0.5);
        double rise = JellyRules.bloomVertical(BloomPhase.RISE) * (life * 2 / 5);
        assertTrue(rise > 60.0, "rises over sixty blocks, from the layer's floor into its middle: " + rise);
        double sink = -JellyRules.bloomVertical(BloomPhase.SINK) * (life * 3 / 10);
        assertTrue(sink > 60.0, "and sinks as far again: " + sink);
    }
}
