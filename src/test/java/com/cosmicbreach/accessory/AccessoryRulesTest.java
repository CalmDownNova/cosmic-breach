package com.cosmicbreach.accessory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every accessory's numbers and cooldowns (GDD 5.2). */
class AccessoryRulesTest {
    @Test
    void theSlotsAreTwoRingsANecklaceAndACharm() {
        int rings = 0;
        int necklaces = 0;
        int charms = 0;
        for (Accessory a : Accessory.values()) {
            switch (a.slot()) {
                case RING -> rings++;
                case NECKLACE -> necklaces++;
                case CHARM -> charms++;
            }
        }
        assertEquals(4, rings, "Twin Comet Band, Leechstar Signet, Perihelion Loop, Gravity Loop");
        assertEquals(2, necklaces, "Heart of a Dying Star, Choir Pendant");
        assertEquals(4, charms, "Halo of Nine, Event Horizon Lens, Hourglass of Vesper, Sunshard Compass");
        assertEquals(2, Accessory.Slot.RING.count());
        assertEquals(1, Accessory.Slot.NECKLACE.count());
        assertEquals(1, Accessory.Slot.CHARM.count());
        assertEquals("ring", Accessory.Slot.RING.id());
        assertEquals("cosmicbreach:heart_of_a_dying_star", Accessory.HEART_OF_A_DYING_STAR.id().toString(), "ids unchanged");
    }

    @Test
    void theCometBandGivesOneAirDash() {
        assertEquals(1, AccessoryRules.COMET_AIR_DASHES);
    }

    @Test
    void theLeechstarHealsTwoPlusAQuarter() {
        assertEquals(4.0, AccessoryRules.leechHeal(8.0), 1e-12);
        assertEquals(2.0, AccessoryRules.leechHeal(0.0), 1e-12);
        assertEquals(2.75, AccessoryRules.leechHeal(3.0), 1e-12, "a zombie's 3");
        assertEquals(2.0, AccessoryRules.leechHeal(-5.0), 1e-12, "never less than the 2");
    }

    @Test
    void thePerihelionLoopsWindowIsFortyTicksAfterADash() {
        assertEquals(0.08, AccessoryRules.PERIHELION_CRIT_CHANCE, 1e-12);
        assertEquals(0.25, AccessoryRules.perihelionCritBonus(true, Long.MIN_VALUE / 2, 1000), 1e-12, "during the dash");
        assertEquals(0.25, AccessoryRules.perihelionCritBonus(false, 1000, 1000), 1e-12, "as it ends");
        assertEquals(0.25, AccessoryRules.perihelionCritBonus(false, 1000, 1040), 1e-12, "40 ticks after");
        assertEquals(0.0, AccessoryRules.perihelionCritBonus(false, 1000, 1041), 1e-12, "41 ticks after");
        assertEquals(0.0, AccessoryRules.perihelionCritBonus(false, Long.MIN_VALUE / 2, 1000), 1e-12, "never dashed");
        assertFalse(AccessoryRules.afterDash(false, 2000, 1000), "a dash from another clock doesn't count");
    }

    @Test
    void theGravityLoopsWellOpensFromTenBlocks() {
        assertFalse(AccessoryRules.opensWell(9.99));
        assertTrue(AccessoryRules.opensWell(10.0));
        assertTrue(AccessoryRules.opensWell(12.0));
        assertEquals(4.0, AccessoryRules.LOOP_WELL_RADIUS, 1e-12);
        assertEquals(20, AccessoryRules.LOOP_WELL_TICKS);
    }

    @Test
    void theHeartFiresUnderThirtyPercentThenRestsNinetySeconds() {
        assertEquals(4.0, AccessoryRules.HEART_HEALTH, 1e-12);
        double max = 24.0; // 20 + the Heart's 4
        assertFalse(AccessoryRules.novaDue(7.5, max, 0, 100), "31% is not under it");
        assertTrue(AccessoryRules.novaDue(7.0, max, 0, 100), "29% is");
        assertFalse(AccessoryRules.novaDue(0.0, max, 0, 100), "dead: nothing");
        long ready = AccessoryRules.heartReadyAfter(100);
        assertEquals(1900, ready, "90 s = 1800 ticks");
        assertFalse(AccessoryRules.novaDue(5.0, max, ready, 1899));
        assertTrue(AccessoryRules.novaDue(5.0, max, ready, 1900));
        assertEquals(5.0, AccessoryRules.NOVA_RADIUS, 1e-12);
        assertEquals(8.0f, AccessoryRules.NOVA_DAMAGE, 1e-6);
        assertEquals(60, AccessoryRules.NOVA_RESISTANCE_TICKS);
        assertEquals(1, AccessoryRules.NOVA_RESISTANCE_AMPLIFIER, "Resistance II");
    }

    @Test
    void thePendantGivesThreeAnEnemyUpToFifteenACast() {
        assertEquals(20, AccessoryRules.PENDANT_RESONANCE);
        int given = 0;
        int[] each = new int[7];
        for (int i = 0; i < 7; i++) {
            each[i] = AccessoryRules.pendantGain(given);
            given += each[i];
        }
        assertArrayEquals(new int[] {3, 3, 3, 3, 3, 0, 0}, each);
        assertEquals(15, given);
        assertEquals(2, AccessoryRules.pendantGain(13), "the last one tops it up to 15");
    }

    @Test
    void theHalosShardsTurnOnceASecondAThirdApart() {
        double[] a = AccessoryRules.shardOffset(0, 0);
        assertEquals(1.6, Math.hypot(a[0], a[1]), 1e-9, "radius 1.6");
        double[] later = AccessoryRules.shardOffset(0, 20);
        assertEquals(a[0], later[0], 1e-9, "one turn a second");
        assertEquals(a[1], later[1], 1e-9);
        double[] quarter = AccessoryRules.shardOffset(0, 5);
        assertEquals(0.0, quarter[0], 1e-9);
        assertEquals(1.6, quarter[1], 1e-9, "a quarter turn in 5 ticks");
        assertEquals(2 * Math.PI / 3, AccessoryRules.shardAngle(1, 0) - AccessoryRules.shardAngle(0, 0), 1e-9);
    }

    @Test
    void aShardRegrowsAfter120TicksAndCutsAtArcaneTwentyEveryTenTicks() {
        assertEquals(1120, AccessoryRules.regrowAt(1000));
        assertFalse(AccessoryRules.shardAlive(1120, 1119));
        assertTrue(AccessoryRules.shardAlive(1120, 1120));
        assertTrue(AccessoryRules.shardAlive(0, 5), "never broke");
        assertFalse(AccessoryRules.shardsCut(19));
        assertTrue(AccessoryRules.shardsCut(20));
        assertTrue(AccessoryRules.cutReady(100, 110));
        assertFalse(AccessoryRules.cutReady(100, 109));
        assertEquals(3.0f, AccessoryRules.HALO_CUT_DAMAGE, 1e-6);
    }

    @Test
    void haloStateCountsItsShards() {
        HaloState halo = new HaloState(true, 0, 0, 0);
        assertEquals(3, halo.count(500));
        halo = halo.broken(1, 500);
        assertEquals(2, halo.count(500));
        assertEquals(620, halo.regrowAt(1));
        halo = halo.broken(0, 510).broken(2, 520);
        assertEquals(0, halo.count(600), "three arrows, three shards");
        assertEquals(1, halo.count(620));
        assertEquals(3, halo.count(640), "all back 120 ticks after each broke");
        assertFalse(halo.withWorn(false).worn());
    }

    @Test
    void theLensAddsTwoTicksAndItsHoleHasRadiusFourForTwentyTicks() {
        assertEquals(2, AccessoryRules.LENS_PARRY_TICKS);
        assertEquals(4.0, AccessoryRules.HOLE_RADIUS, 1e-12);
        assertEquals(20, AccessoryRules.HOLE_TICKS);
    }

    @Test
    void aPullStepsTowardTheMiddleAndStopsShortOfIt() {
        double[] step = AccessoryRules.pullStep(3.0, 0.0, AccessoryRules.PULL);
        assertEquals(0.12, step[0], 1e-12);
        assertEquals(0.0, step[1], 1e-12);
        step = AccessoryRules.pullStep(0.0, -0.65, AccessoryRules.PULL);
        assertEquals(-0.05, step[1], 1e-9, "only up to 0.6 from the middle");
        assertArrayEquals(new double[] {0.0, 0.0}, AccessoryRules.pullStep(0.3, 0.2, AccessoryRules.PULL), 1e-12);
        double x = 3.0;
        for (int t = 0; t < AccessoryRules.LOOP_WELL_TICKS; t++) {
            x -= AccessoryRules.pullStep(x, 0.0, AccessoryRules.PULL)[0];
        }
        assertEquals(0.6, x, 1e-9, "20 ticks pull a body 3 blocks out all the way in");
    }

    @Test
    void theHourglassSlowsBySeventyPercentForTwentyTicksWithinSixBlocks() {
        assertEquals(0.3, AccessoryRules.slowedSpeed(1.0), 1e-12);
        assertEquals(0.069, AccessoryRules.slowedSpeed(0.23), 1e-12, "a zombie's 0.23");
        assertEquals(20, AccessoryRules.HOURGLASS_TICKS);
        assertEquals(6.0, AccessoryRules.HOURGLASS_RADIUS, 1e-12);
    }

    @Test
    void theCompassWidensTellsToEightBlocks() {
        assertEquals(8.0, AccessoryRules.tellRadius(true, 4.0), 1e-12);
        assertEquals(4.0, AccessoryRules.tellRadius(false, 4.0), 1e-12);
        assertEquals(9.0, AccessoryRules.tellRadius(true, 9.0), 1e-12, "never narrower");
    }

    @Test
    void theNeedlePointsClockwiseFromStraightAhead() {
        // facing south (yaw 0): a vault to the south is ahead, one to the west is on the right
        assertEquals(0.0, AccessoryRules.needle(0, 0, 0f, 0, 10), 1e-9);
        assertEquals(0.25, AccessoryRules.needle(0, 0, 0f, -10, 0), 1e-9, "west, a quarter turn right");
        assertEquals(0.5, AccessoryRules.needle(0, 0, 0f, 0, -10), 1e-9, "north, behind");
        assertEquals(0.75, AccessoryRules.needle(0, 0, 0f, 10, 0), 1e-9, "east, on the left");
        assertEquals(0.0, AccessoryRules.needle(0, 0, 90f, -10, 0), 1e-9, "facing west, west is ahead");
        assertEquals(0.0, AccessoryRules.needle(0, 0, 450f, -10, 0), 1e-9, "yaw wraps");
        assertEquals(4, AccessoryRules.needleFrame(0.25));
        assertEquals(0, AccessoryRules.needleFrame(0.99), "just under a full turn is straight up again");
        assertEquals(15, AccessoryRules.needleFrame(0.94));
    }
}
