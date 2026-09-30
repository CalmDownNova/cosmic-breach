package com.cosmicbreach.relic.lastlight;

import com.cosmicbreach.relic.lastlight.LastLightRules.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Last Light's pure rules against GDD 7.3: Dawnguard's automatic parries and their cost, Daybreak, Sunlight. */
class LastLightRulesTest {
    @Test
    void theStanceParriesEveryAttackItCanPayFor() {
        assertEquals(Outcome.PARRY, LastLightRules.answer(true, false, 100.0));
        assertEquals(Outcome.PARRY, LastLightRules.answer(true, false, 10.0), "exactly the cost is enough");
        assertEquals(Outcome.UNPAID, LastLightRules.answer(true, false, 9.99), "short of 10 Resonance the hit lands");
        assertEquals(Outcome.UNPAID, LastLightRules.answer(true, false, 0.0));
    }

    @Test
    void theWorldsHarmAndUnstoppableDamageAreNotParried() {
        assertEquals(Outcome.IGNORED, LastLightRules.answer(false, false, 100.0), "a fall, lava: no attacker");
        assertEquals(Outcome.IGNORED, LastLightRules.answer(true, true, 100.0), "/kill and the void go through");
    }

    @Test
    void eachParryCostsTenSoAFullBarPaysForSixAfterTheCast() {
        double resonance = 100.0 - 35.0; // the cast
        int parries = 0;
        while (LastLightRules.answer(true, false, resonance) == Outcome.PARRY) {
            resonance -= LastLightRules.PARRY_COST;
            parries++;
        }
        assertEquals(6, parries);
        assertEquals(5.0, resonance, 1e-9);
    }

    @Test
    void parriesInOneTickShareADaybreak() {
        assertTrue(LastLightRules.daybreakDue(Long.MIN_VALUE, 100L), "the first ever");
        assertFalse(LastLightRules.daybreakDue(100L, 100L), "a second parry in the same tick");
        assertTrue(LastLightRules.daybreakDue(100L, 101L), "the next tick's parry has its own");
    }

    @Test
    void daybreakIsTheGddsArc() {
        assertEquals(5.0, LastLightRules.DAYBREAK_RADIUS);
        assertEquals(270.0, LastLightRules.DAYBREAK_ANGLE);
        assertEquals(2.4, LastLightRules.DAYBREAK_MV);
    }

    @Test
    void sunlightStoresAChargePerParryUpToThree() {
        int charges = 0;
        charges = LastLightRules.store(charges);
        assertEquals(1, charges);
        charges = LastLightRules.store(charges);
        charges = LastLightRules.store(charges);
        assertEquals(3, charges);
        assertEquals(3, LastLightRules.store(charges), "capped at three");
        assertEquals(1, LastLightRules.store(-4), "never below none");
    }

    @Test
    void aChargedAttackSpendsThemForFortyPercentEach() {
        assertEquals(1.0, LastLightRules.chargedMultiplier(0), 1e-12);
        assertEquals(1.4, LastLightRules.chargedMultiplier(1), 1e-12);
        assertEquals(1.8, LastLightRules.chargedMultiplier(2), 1e-12);
        assertEquals(2.2, LastLightRules.chargedMultiplier(3), 1e-12);
        assertEquals(2.2, LastLightRules.chargedMultiplier(7), 1e-12, "no more than three are ever spent");
    }

    @Test
    void theStanceIsThirtyTicksAfterTheStartup() {
        assertFalse(LastLightRules.stanceHolds(3, 2), "still raising the guard");
        assertTrue(LastLightRules.stanceHolds(3, 3));
        assertTrue(LastLightRules.stanceHolds(3, 32));
        assertFalse(LastLightRules.stanceHolds(3, 33), "30 ticks, then the guard lowers");
    }
}
