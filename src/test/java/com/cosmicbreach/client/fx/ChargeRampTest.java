package com.cosmicbreach.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeRampTest {

    @Test
    void theChoirSwellsAndRisesToItsPeakAtFullCharge() {
        assertEquals(ChargeRamp.VOLUME_START, ChargeRamp.volume(0), 1e-6);
        assertEquals(ChargeRamp.VOLUME_FULL, ChargeRamp.volume(1), 1e-6);
        assertEquals(ChargeRamp.PITCH_START, ChargeRamp.pitch(0), 1e-6);
        assertEquals(ChargeRamp.PITCH_FULL, ChargeRamp.pitch(1), 1e-6);
        float volume = -1f;
        float pitch = -1f;
        for (int i = 0; i <= 20; i++) {
            double progress = i / 20.0;
            assertTrue(ChargeRamp.volume(progress) > volume);
            assertTrue(ChargeRamp.pitch(progress) > pitch);
            volume = ChargeRamp.volume(progress);
            pitch = ChargeRamp.pitch(progress);
        }
    }

    @Test
    void itHoldsOutsideTheCharge() {
        assertEquals(ChargeRamp.volume(1), ChargeRamp.volume(3), 1e-6);
        assertEquals(ChargeRamp.pitch(0), ChargeRamp.pitch(-1), 1e-6);
    }
}
