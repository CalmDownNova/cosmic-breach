package com.cosmicbreach.combat.server.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Cratered ground's slow, as the speed things really move at. */
class CraterTest {
    @Test
    void aPlayerLosesThirtyPercentOfItsSpeedAttribute() {
        assertEquals(0.7, Crater.attributeFactor(0.3, false), 1e-12);
    }

    @Test
    void aMobLosesTheSquareRootSoItsSquaredWalkIsThirtyPercentSlower() {
        double factor = Crater.attributeFactor(0.3, true);
        assertEquals(Math.sqrt(0.7), factor, 1e-12);
        assertEquals(0.7, factor * factor, 1e-12, "a mob's ground speed goes with the attribute squared");
    }

    @Test
    void theSlowStaysBetweenNothingAndAStop() {
        assertEquals(1.0, Crater.attributeFactor(0.0, true), 1e-12);
        assertEquals(0.0, Crater.attributeFactor(1.5, false), 1e-12);
        assertEquals(1.0, Crater.attributeFactor(-0.2, false), 1e-12);
    }
}
