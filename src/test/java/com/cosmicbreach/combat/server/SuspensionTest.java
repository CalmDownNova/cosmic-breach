package com.cosmicbreach.combat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Zenith's Suspended state, run against the same air physics the server applies. */
class SuspensionTest {
    private static final double GRAVITY = 0.08;

    /** One simulated body: moves by its velocity, then gravity and drag, then the Suspension's override. */
    private static final class Body {
        double y;
        double vy;
        double vx;
        final double floor;

        Body(double vy, double vx) {
            this.vy = vy;
            this.vx = vx;
            this.floor = 0;
        }

        boolean onGround() {
            return y <= floor + 1e-9;
        }

        void tick(Suspension s) {
            y = Math.max(floor, y + vy);
            vy = onGround() ? 0 : (vy - GRAVITY) * LaunchMath.AIR_DRAG;
            vx *= 0.91;
            double forced = s.afterTick(vy, onGround());
            if (!Double.isNaN(forced)) {
                vy = forced;
                vx *= Suspension.HORIZONTAL_KEEP;
            }
        }
    }

    @Test
    void risesToTheApexThenHoversSinkingSlowlyThenFalls() {
        double v0 = LaunchMath.velocityForHeight(3.5, GRAVITY);
        Suspension s = new Suspension(30);
        Body body = new Body(v0, 0.3);
        int ticks = 0;
        while (s.phase() == Suspension.Phase.RISING && ticks < 40) {
            body.tick(s);
            ticks++;
        }
        assertEquals(Suspension.Phase.HOVERING, s.phase());
        double apex = body.y;
        assertTrue(apex > 3.2 && apex < 3.6, "rose to about 3.5, got " + apex);

        int hover = 0;
        while (!s.finished() && hover < 100) {
            body.tick(s);
            hover++;
        }
        assertEquals(30, hover, "Suspended for suspend_ticks");
        double sunk = apex - body.y;
        assertTrue(sunk > 0.5 && sunk < 1.2, "sinks slowly, about 0.9 blocks, got " + sunk);
        assertTrue(Math.abs(body.vx) < 1e-3, "horizontal speed damped away");
    }

    @Test
    void landingEndsItEarly() {
        Suspension s = new Suspension(30);
        s.afterTick(0.5, false);
        s.afterTick(-0.1, false);
        assertEquals(Suspension.Phase.HOVERING, s.phase());
        assertTrue(Double.isNaN(s.afterTick(0, true)));
        assertTrue(s.finished());
    }

    @Test
    void aBlockedLaunchEndsAtOnce() {
        Suspension s = new Suspension(30);
        assertTrue(Double.isNaN(s.afterTick(-0.0784, true)), "a ceiling stopped it: leave it alone");
        assertTrue(s.finished());
    }

    @Test
    void theRiseIsCapped() {
        Suspension s = new Suspension(10);
        for (int i = 1; i < Suspension.MAX_RISE_TICKS; i++) {
            assertTrue(Double.isNaN(s.afterTick(1.0, false)), "rise tick " + i);
        }
        assertEquals(-Suspension.SINK_PER_TICK, s.afterTick(1.0, false), 1e-12);
        assertFalse(s.finished());
    }
}
