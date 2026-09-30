package com.cosmicbreach.gear.regalia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Choir Regalia's numbers (GDD 5.1): Harmonics' count and the Hymn's ring. */
class RegaliaRulesTest {
    @Test
    void theTableMatchesTheDesign() {
        assertEquals(0.5, RegaliaRules.DRIFT_TARGET, 1e-12);
        assertEquals(0.9, RegaliaRules.ABILITY_COST, 1e-12);
        assertEquals(3, RegaliaRules.ECHO_EVERY);
        assertEquals(200, RegaliaRules.ECHO_WINDOW, "10 s");
        assertEquals(10, RegaliaRules.ECHO_DELAY);
        assertEquals(0.6, RegaliaRules.ECHO_POWER, 1e-12);
        assertEquals(800, RegaliaRules.HYMN_COOLDOWN);
        assertEquals(120, RegaliaRules.HYMN_TICKS);
        assertEquals(6.0, RegaliaRules.HYMN_RADIUS, 1e-12);
        assertEquals(30.0, RegaliaRules.HYMN_HASTE, 1e-12);
        assertEquals(2.0, RegaliaRules.HYMN_RESONANCE_GAIN, 1e-12);
        assertEquals(1.1, RegaliaRules.ALIGNED_ABILITY_DAMAGE, 1e-12);
    }

    @Test
    void theThirdCastWithinTenSecondsEchoes() {
        RegaliaRules.Harmonics h = new RegaliaRules.Harmonics();
        assertFalse(h.cast(0));
        assertFalse(h.primed(10));
        assertFalse(h.cast(90));
        assertTrue(h.primed(100), "two counted: the next one echoes");
        assertTrue(h.cast(180), "the third within 200 ticks");
        assertEquals(0, h.count(180), "the count starts over");
        assertFalse(h.cast(190));
        assertFalse(h.cast(200));
        assertTrue(h.cast(210), "and every third after");
    }

    @Test
    void castsOlderThanTheWindowDropOut() {
        RegaliaRules.Harmonics h = new RegaliaRules.Harmonics();
        h.cast(0);
        h.cast(150);
        assertEquals(200, h.nextDrop());
        assertEquals(1, h.count(200), "the first cast left the window at 200");
        assertFalse(h.cast(260), "only two within the window: 150 and 260");
        assertTrue(h.cast(340), "150, 260, 340: three within 200 ticks");
        assertEquals(Long.MAX_VALUE, h.nextDrop());
    }

    @Test
    void castsSpreadOutNeverEcho() {
        RegaliaRules.Harmonics h = new RegaliaRules.Harmonics();
        for (long t = 0; t < 2000; t += 110) {
            assertFalse(h.cast(t), "one every 5.5 s leaves at most two in any 10 s");
        }
    }

    @Test
    void theRingHoldsWhatStandsOnIt() {
        assertTrue(RegaliaRules.insideRing(0, 0, 0, 6));
        assertTrue(RegaliaRules.insideRing(6, 0, 0, 6), "its edge counts");
        assertTrue(RegaliaRules.insideRing(3, 3.9, 4, 6), "a jump over it still counts");
        assertFalse(RegaliaRules.insideRing(4.3, 0, 4.3, 6), "past the radius on the diagonal");
        assertFalse(RegaliaRules.insideRing(0, -2.5, 0, 6), "too far below");
        assertFalse(RegaliaRules.insideRing(0, 4.5, 0, 6), "too far above");
    }
}
