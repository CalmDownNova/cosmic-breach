package com.cosmicbreach.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VesperClockTest {

    @Test
    void oneHundredBeatsAMinuteIsTwelveTicksABeat() {
        assertEquals(12, VesperClock.TICKS_PER_BEAT);
        assertEquals(20 * 60, VesperClock.BPM * VesperClock.TICKS_PER_BEAT, "100 beats fill a minute of 20 ticks a second");
        assertEquals(20, VesperClock.BEATS_PER_SWEEP, "the beam sweeps once every 12 s: 20 beats");
    }

    @Test
    void beatsStartOnMultiplesOfTwelve() {
        assertEquals(0, VesperClock.beat(0));
        assertEquals(0, VesperClock.beat(11));
        assertEquals(1, VesperClock.beat(12));
        assertEquals(-1, VesperClock.beat(-1), "floored, so a beat is always 12 ticks long");
        assertTrue(VesperClock.isBeat(24));
        assertFalse(VesperClock.isBeat(25));
        assertEquals(1, VesperClock.tickInBeat(25));
        assertEquals(12, VesperClock.ticksToNextBeat(12));
        assertEquals(11, VesperClock.ticksToNextBeat(13));
    }

    @Test
    void barsOfFour() {
        assertEquals(0, VesperClock.beatInBar(0));
        assertEquals(1, VesperClock.beatInBar(12 * 5));
        assertEquals(3, VesperClock.beatInBar(12 * 7 + 11));
        assertEquals(1, VesperClock.bar(48));
        assertEquals(0, VesperClock.bar(47));
    }

    @Test
    void phaseAndPulseAreSmoothWithinABeat() {
        assertEquals(0.0, VesperClock.phase(12, 0f), 1e-12);
        assertEquals(0.5, VesperClock.phase(18, 0f), 1e-12);
        assertEquals(0.5 + 0.5 / 12, VesperClock.phase(18, 0.5f), 1e-9);
        assertTrue(VesperClock.phase(23, 1f) < 1.0, "a partial tick of 1 never reaches the next beat");
        assertEquals(1.0f, VesperClock.pulse(1200, 0f), 1e-6);
        assertEquals(Math.exp(-5.0 * 0.5), VesperClock.pulse(6, 0f), 1e-6);
        assertTrue(VesperClock.pulse(11, 0.99f) < 0.01, "faded out before the next beat");
        assertEquals(100.5, VesperClock.beats(1206, 0f), 1e-9);
    }

    @Test
    void theBeamTurnsOnceInTwelveSeconds() {
        assertEquals(0.0, VesperClock.sweepRadians(0, 0f), 1e-12);
        assertEquals(Math.PI, VesperClock.sweepRadians(120, 0f), 1e-12);
        assertEquals(0.0, VesperClock.sweepRadians(240, 0f), 1e-12);
        assertEquals(Math.PI / 2, VesperClock.sweepRadians(240 * 7 + 60, 0f), 1e-12);
    }
}
