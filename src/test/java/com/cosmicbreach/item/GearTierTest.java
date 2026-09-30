package com.cosmicbreach.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Gear tiers: steps above the unlock tier, x1.15 each, and a colour and numeral per tier. */
class GearTierTest {
    @Test
    void stepsCountFromTheUnlockTier() {
        assertEquals(0, GearTier.steps(1, 1));
        assertEquals(1, GearTier.steps(2, 1));
        assertEquals(3, GearTier.steps(4, 1));
        assertEquals(1, GearTier.steps(3, 2), "the Maul unlocks at II");
        assertEquals(0, GearTier.steps(1, 2), "never negative");
        assertEquals(3, GearTier.steps(9, 1), "clamped to IV");
    }

    @Test
    void eachStepMultipliesByOnePointOneFive() {
        assertEquals(1.0, GearTier.multiplier(0), 1e-9);
        assertEquals(1.15, GearTier.multiplier(1), 1e-9);
        assertEquals(1.15 * 1.15 * 1.15, GearTier.multiplier(3), 1e-9);
    }

    @Test
    void numeralsAndColours() {
        assertEquals("I", GearTier.roman(1));
        assertEquals("IV", GearTier.roman(4));
        assertEquals("IV", GearTier.roman(7));
        for (int a = 1; a <= 4; a++) {
            for (int b = a + 1; b <= 4; b++) {
                assertNotEquals(GearTier.color(a), GearTier.color(b), "tiers " + a + " and " + b + " read apart");
            }
        }
    }
}
