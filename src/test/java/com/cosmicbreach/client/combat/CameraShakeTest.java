package com.cosmicbreach.client.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraShakeTest {

    @Test
    void traumaAddsUpToOneAndDrainsOneAndAHalfASecond() {
        CameraShake shake = new CameraShake();
        shake.addTrauma(0.7);
        shake.addTrauma(0.7);
        assertEquals(1.0, shake.trauma(), 1e-12);
        for (int i = 0; i < 10; i++) {
            shake.tick();
        }
        assertEquals(1.0 - 1.5 / 2, shake.trauma(), 1e-9, "half a second later");
        for (int i = 0; i < 10; i++) {
            shake.tick();
        }
        assertEquals(0.0, shake.trauma(), 1e-12);
        shake.addTrauma(-1);
        assertEquals(0.0, shake.trauma(), 1e-12);
    }

    @Test
    void theShakeIsTraumaSquaredTimesTheSetting() {
        CameraShake shake = new CameraShake();
        shake.addTrauma(0.5);
        assertEquals(0.25, shake.shake(0f, 1.0), 1e-12);
        assertEquals(0.15, shake.shake(0f, 0.6), 1e-12);
        assertEquals(0.0, shake.shake(0f, 0.0), 1e-12);
        assertEquals((0.5 - 0.075 / 2) * (0.5 - 0.075 / 2), shake.shake(0.5f, 1.0), 1e-9, "fades between ticks");
    }

    @Test
    void offsetsStayWithinAFewDegrees() {
        CameraShake shake = new CameraShake();
        shake.addTrauma(1.0);
        float[] offsets;
        boolean moved = false;
        for (int frame = 0; frame < 200; frame++) {
            offsets = shake.offsets((frame % 4) / 4f, 1.0);
            assertTrue(Math.abs(offsets[0]) <= CameraShake.MAX_YAW);
            assertTrue(Math.abs(offsets[1]) <= CameraShake.MAX_PITCH);
            assertTrue(Math.abs(offsets[2]) <= CameraShake.MAX_ROLL);
            moved |= Math.abs(offsets[0]) > 0.1;
            if (frame % 4 == 3) {
                shake.addTrauma(0.075); // hold trauma up for the test
                shake.tick();
            }
        }
        assertTrue(moved, "the camera actually moves at full trauma");
        CameraShake calm = new CameraShake();
        float[] none = calm.offsets(0.3f, 1.0);
        assertEquals(0f, none[0]);
        assertEquals(0f, none[1]);
        assertEquals(0f, none[2]);
    }

    @Test
    void theNoiseIsSmoothAndBounded() {
        double previous = CameraShake.noise(7, 0.0);
        for (int i = 1; i <= 1000; i++) {
            double t = i / 100.0;
            double value = CameraShake.noise(7, t);
            assertTrue(value >= -1.0 && value <= 1.0);
            assertTrue(Math.abs(value - previous) < 0.1, "no jumps between close samples at " + t);
            previous = value;
        }
    }

    @Test
    void heavierHitsShakeMore() {
        assertTrue(CameraShake.forLandedHit(25, false) > CameraShake.forLandedHit(6, false));
        assertTrue(CameraShake.forLandedHit(6, true) > CameraShake.forLandedHit(6, false));
        assertEquals(CameraShake.MAX_HIT_TRAUMA, CameraShake.forLandedHit(500, true), 1e-12);
        assertTrue(CameraShake.forTakenHit(6) < 0.5);
    }

    @Test
    void aKickDipsFastAndComesBackSlower() {
        CameraShake shake = new CameraShake();
        assertFalse(shake.active());
        shake.kick(4.0);
        assertTrue(shake.active());
        assertEquals(0.0, shake.kickAt(0f), 1e-9);
        double[] dip = new double[CameraShake.KICK_TICKS + 1];
        for (int t = 0; t <= CameraShake.KICK_TICKS; t++) {
            dip[t] = shake.kickAt(0f);
            shake.tick();
        }
        int deepest = 0;
        for (int t = 1; t < dip.length; t++) {
            if (dip[t] > dip[deepest]) {
                deepest = t;
            }
        }
        assertTrue(deepest <= 2, "the dip peaks within 2 ticks, at " + deepest);
        assertEquals(4.0, dip[deepest], 0.8);
        for (int t = deepest + 1; t < dip.length; t++) {
            assertTrue(dip[t] <= dip[t - 1] + 1e-9, "recovers without bouncing");
        }
        assertEquals(0.0, dip[CameraShake.KICK_TICKS], 1e-9);
        assertFalse(shake.active());
    }

    @Test
    void theKickPitchesTheViewDownAndFollowsTheSetting() {
        CameraShake shake = new CameraShake();
        shake.kick(5.0);
        shake.tick();
        shake.tick();
        float[] full = shake.offsets(0f, 1.0);
        float[] half = shake.offsets(0f, 0.5);
        float[] off = shake.offsets(0f, 0.0);
        assertTrue(full[1] > 3.0f);
        assertEquals(full[1] * 0.5f, half[1], 1e-4);
        assertEquals(0f, off[1]);
        assertEquals(0f, full[0]);
    }

    @Test
    void aSmallerKickDoesNotCutABiggerOneShort() {
        CameraShake shake = new CameraShake();
        shake.kick(5.0);
        shake.tick();
        double before = shake.kickAt(0f);
        shake.kick(1.0);
        assertEquals(before, shake.kickAt(0f), 1e-9);
    }
}
