package com.cosmicbreach.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FxRatesTest {

    @Test
    void fullRateNearHalfOutTo48NoneBeyondExceptImportantEffects() {
        assertEquals(1.0, FxRates.distanceFactor(0, false));
        assertEquals(1.0, FxRates.distanceFactor(16, false));
        assertEquals(0.5, FxRates.distanceFactor(16.01, false));
        assertEquals(0.5, FxRates.distanceFactor(48, false));
        assertEquals(0.0, FxRates.distanceFactor(48.01, false));
        assertEquals(0.0, FxRates.distanceFactor(200, false));
        assertEquals(0.5, FxRates.distanceFactor(200, true), "a parry or big impact still shows far away");
        assertEquals(1.0, FxRates.distanceFactor(10, true));
    }

    @Test
    void theVanillaSettingScalesTheCount() {
        assertEquals(10, FxRates.count(10, FxRates.ALL, 1500, 0.5, false));
        assertEquals(4, FxRates.count(10, FxRates.DECREASED, 1500, 0.99, false));
        assertEquals(1, FxRates.count(10, FxRates.MINIMAL, 1500, 0.99, false));
    }

    @Test
    void fractionsRoundUpAsOftenAsTheyShould() {
        // 3 at 10% is 0.3 of a particle: one 30% of the time.
        assertEquals(1, FxRates.count(3, 0.1, 1500, 0.2, false));
        assertEquals(0, FxRates.count(3, 0.1, 1500, 0.5, false));
        int total = 0;
        for (int i = 0; i < 1000; i++) {
            total += FxRates.count(3, 0.1, 1500, (i + 0.5) / 1000.0, false);
        }
        assertEquals(300, total, "on average 0.3 a call");
    }

    @Test
    void importantEffectsKeepAtLeastOneUnlessTheRateIsZero() {
        assertEquals(1, FxRates.count(3, 0.1, 1500, 0.9, true));
        assertEquals(0, FxRates.count(3, 0.0, 1500, 0.0, true));
    }

    @Test
    void neverMoreThanTheRoomLeft() {
        assertEquals(7, FxRates.count(40, 1.0, 7, 0.0, false));
        assertEquals(0, FxRates.count(40, 1.0, 0, 0.0, true));
        assertEquals(0, FxRates.count(0, 1.0, 1500, 0.0, true));
    }
}
