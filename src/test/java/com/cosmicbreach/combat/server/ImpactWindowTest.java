package com.cosmicbreach.combat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Poise: Impact adds up over a rolling 60 ticks and a stagger resets it (GDD section 3.4). */
class ImpactWindowTest {
    private static final double POISE = 20.0;

    @Test
    void impactAddsUpInsideTheWindow() {
        ImpactWindow w = new ImpactWindow(60);
        assertFalse(w.add(100, 6, POISE));
        assertFalse(w.add(110, 6, POISE));
        assertEquals(12.0, w.total(120), 1e-9);
    }

    @Test
    void reachingPoiseStaggersAndResets() {
        ImpactWindow w = new ImpactWindow(60);
        assertFalse(w.add(0, 14, POISE));
        assertTrue(w.add(10, 6, POISE), "14 + 6 reaches 20 exactly");
        assertEquals(0.0, w.total(10), 1e-9, "a stagger empties the window");
        assertFalse(w.add(11, 14, POISE), "the count starts over");
    }

    @Test
    void impactOlderThanSixtyTicksNoLongerCounts() {
        ImpactWindow w = new ImpactWindow(60);
        w.add(0, 10, POISE);
        w.add(30, 5, POISE);
        assertEquals(15.0, w.total(59), 1e-9);
        assertEquals(5.0, w.total(60), 1e-9, "tick 0 drops out 60 ticks later");
        assertFalse(w.add(61, 10, POISE), "10 at tick 0 is gone, so 5 + 10 does not reach 20");
        assertEquals(15.0, w.total(61), 1e-9);
        assertEquals(10.0, w.total(90), 1e-9, "tick 30 drops out at 90");
        assertEquals(0.0, w.total(121), 1e-9);
    }

    @Test
    void slowChipDamageNeverStaggers() {
        ImpactWindow w = new ImpactWindow(60);
        for (long t = 0; t < 1000; t += 20) {
            assertFalse(w.add(t, 6, POISE), "6 Impact a second peaks at 18 inside 3 s, at tick " + t);
        }
    }

    @Test
    void noImpactNeverStaggers() {
        ImpactWindow w = new ImpactWindow(60);
        assertFalse(w.add(0, 0, 0));
        assertFalse(w.add(0, -5, POISE));
        assertEquals(0.0, w.total(0), 1e-9);
    }

    @Test
    void aBigHitStaggersAtOnce() {
        assertTrue(new ImpactWindow(60).add(0, 25, POISE));
    }

    @Test
    void windowMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ImpactWindow(0));
    }
}
