package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.PlayerCombat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputRateLimiterTest {

    @Test
    void eightInputsPerTickThenDrop() {
        InputRateLimiter limiter = new InputRateLimiter(PlayerCombat.MAX_INPUTS_PER_TICK);
        for (int i = 0; i < 8; i++) {
            assertTrue(limiter.tryAcquire(), "input " + (i + 1));
        }
        assertFalse(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        assertEquals(2, limiter.dropped());
    }

    @Test
    void theBudgetRefillsEachTick() {
        InputRateLimiter limiter = new InputRateLimiter(8);
        for (int i = 0; i < 20; i++) {
            limiter.tryAcquire();
        }
        limiter.nextTick();
        assertEquals(0, limiter.dropped());
        for (int i = 0; i < 8; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void aNormalClientNeverHitsTheCap() {
        InputRateLimiter limiter = new InputRateLimiter(8);
        for (int tick = 0; tick < 100; tick++) {
            // attack press + release, ability press + release, dash, parry
            for (int i = 0; i < 6; i++) {
                assertTrue(limiter.tryAcquire());
            }
            limiter.nextTick();
        }
    }

    @Test
    void needsARealBudget() {
        assertThrows(IllegalArgumentException.class, () -> new InputRateLimiter(0));
    }
}
