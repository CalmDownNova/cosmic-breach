package com.cosmicbreach.combat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchMathTest {
    private static final double GRAVITY = 0.08; // a living entity's default

    @Test
    void zenithsLaunchPeaksAtThreeAndAHalfBlocks() {
        double v0 = LaunchMath.velocityForHeight(3.5, GRAVITY);
        assertEquals(3.5, LaunchMath.apex(v0, GRAVITY), 0.02);
        assertTrue(v0 > 0.7 && v0 < 0.9, "about 0.8 blocks a tick, got " + v0);
    }

    @Test
    void higherNeedsFaster() {
        double previous = 0;
        for (double h = 0.5; h <= 10; h += 0.5) {
            double v0 = LaunchMath.velocityForHeight(h, GRAVITY);
            assertTrue(v0 > previous, "height " + h);
            assertEquals(h, LaunchMath.apex(v0, GRAVITY), 0.05, "height " + h);
            previous = v0;
        }
    }

    @Test
    void apexFollowsMinecraftAirPhysics() {
        // Move by v, then v = (v - g) x 0.98: 0.42 (a jump) peaks near 1.25 blocks.
        assertEquals(1.25, LaunchMath.apex(0.42, GRAVITY), 0.02);
        assertEquals(0.0, LaunchMath.apex(0.0, GRAVITY), 1e-9);
    }

    @Test
    void noHeightNoSpeedAndNoGravityNoLaunch() {
        assertEquals(0.0, LaunchMath.velocityForHeight(0, GRAVITY), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> LaunchMath.velocityForHeight(3.5, 0));
    }
}
