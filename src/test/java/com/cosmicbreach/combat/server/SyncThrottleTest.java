package com.cosmicbreach.combat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncThrottleTest {

    @Test
    void sendsFirstThenEveryTenTicksWhenNothingChanges() {
        SyncThrottle sync = new SyncThrottle();
        assertTrue(sync.update(30, 2, 0), "the first tick always sends");
        for (int i = 1; i < SyncThrottle.INTERVAL_TICKS; i++) {
            assertFalse(sync.update(30, 2, 0), "tick " + i);
        }
        assertTrue(sync.update(30, 2, 0), "tick 10");
    }

    @Test
    void aDashChargeChangeSendsAtOnce() {
        SyncThrottle sync = new SyncThrottle();
        sync.update(30, 2, 0);
        assertTrue(sync.update(30, 1, 0));
    }

    @Test
    void aHitsWorthOfResonanceSendsButDriftWaits() {
        SyncThrottle sync = new SyncThrottle();
        sync.update(30, 2, 0);
        assertFalse(sync.update(30.25, 2, 0), "drift of 0.25 a tick is predicted");
        assertFalse(sync.update(30.5, 2, 0));
        assertTrue(sync.update(36.5, 2, 0), "+6 from a hit");
    }

    @Test
    void aCooldownCountingDownIsPredictedButAStartIsSent() {
        SyncThrottle sync = new SyncThrottle();
        sync.update(0, 2, 0);
        assertTrue(sync.update(0, 2, 100), "Zenith's cooldown starts");
        for (int left = 99; left > 91; left--) {
            assertFalse(sync.update(0, 2, left), "counting down to " + left);
        }
    }
}
