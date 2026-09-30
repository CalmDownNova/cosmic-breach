package com.cosmicbreach.gear.vanguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The Starfall Vanguard's numbers (GDD 5.1). */
class VanguardRulesTest {
    @Test
    void shockwaveNeedsFourBlocksAndGrowsHalfADamageABlockToTen() {
        assertEquals(0.0, VanguardRules.shockwaveDamage(3.9), 1e-9);
        assertEquals(5.0, VanguardRules.shockwaveDamage(4.0), 1e-9);
        assertEquals(6.0, VanguardRules.shockwaveDamage(6.0), 1e-9);
        assertEquals(10.0, VanguardRules.shockwaveDamage(14.0), 1e-9);
        assertEquals(10.0, VanguardRules.shockwaveDamage(40.0), 1e-9, "capped at 10");
        assertEquals(0.0, VanguardRules.shockwaveDamage(Double.NaN), 1e-9);
        assertEquals(3.0, VanguardRules.SHOCKWAVE_RADIUS, 1e-9);
        assertEquals(12.0, VanguardRules.SHOCKWAVE_IMPACT, 1e-9);
    }

    @Test
    void heavyLandingHalvesFallDamage() {
        assertEquals(1.5, VanguardRules.heavyLanding(3.0), 1e-9);
        assertEquals(0.0, VanguardRules.heavyLanding(-2.0), 1e-9);
    }

    @Test
    void vanillaFallDamageIsTheBlocksPastTheSafeDistanceRoundedUp() {
        assertEquals(3.0, VanguardRules.vanillaFallDamage(6.0, 1.0, 3.0, 1.0), 1e-9);
        assertEquals(3.0, VanguardRules.vanillaFallDamage(5.2, 1.0, 3.0, 1.0), 1e-9);
        assertEquals(0.0, VanguardRules.vanillaFallDamage(2.0, 1.0, 3.0, 1.0), 1e-9);
        assertEquals(9.0, VanguardRules.vanillaFallDamage(12.0, 1.0, 3.0, 1.0), 1e-9);
    }

    @Test
    void heatStacksToTwenty() {
        assertEquals(1.5, VanguardRules.storeHeat(0.0, 1.5), 1e-9);
        assertEquals(12.0, VanguardRules.storeHeat(4.0, 8.0), 1e-9);
        assertEquals(20.0, VanguardRules.storeHeat(15.0, 9.0), 1e-9, "at most 20");
        assertEquals(20.0, VanguardRules.storeHeat(0.0, 70.0), 1e-9);
        assertEquals(60, VanguardRules.HEAT_TICKS);
        assertEquals(10.0, VanguardRules.CHARGING_POISE, 1e-9);
    }

    @Test
    void meteorScalesWithPowerAtGradeBAndFlaresOnScorch() {
        assertEquals(12.0, VanguardRules.meteorDamage(0, false), 1e-9);
        assertEquals(12.0 * (1 + 0.55 * 0.25), VanguardRules.meteorDamage(10, false), 1e-9, "S(10) = 0.25");
        assertEquals(12.0 * (1 + 0.55 * 0.5), VanguardRules.meteorDamage(30, false), 1e-9, "S(30) = 0.5");
        assertEquals(18.0, VanguardRules.meteorDamage(0, true), 1e-9, "Scorched: +50%");
        assertEquals(600, VanguardRules.METEOR_COOLDOWN);
        assertEquals(24.0, VanguardRules.METEOR_RANGE, 1e-9);
        assertEquals(4.0, VanguardRules.METEOR_RADIUS, 1e-9);
        assertEquals(30, VanguardRules.METEOR_WARNING_TICKS);
        assertEquals(60.0, VanguardRules.METEOR_IMPACT, 1e-9);
        assertEquals(60, VanguardRules.CRATER_TICKS);
    }
}
