package com.cosmicbreach.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class LandingSiteTest {
    private static final double PX = 100.5;
    private static final double PZ = -40.5;

    @Test
    void landsBetween48And96BlocksAwayOnOpenGround() {
        for (long seed = 0; seed < 200; seed++) {
            Optional<LandingSite.Spot> spot = LandingSite.choose(PX, PZ, 48, 96, (x, z) -> 64, RandomSource.create(seed));
            assertTrue(spot.isPresent());
            double d = spot.get().distanceTo(PX, PZ);
            assertTrue(d >= 47.5 && d <= 96.5, "landed " + d + " away");
        }
    }

    @Test
    void takesTheHighestGroundInItsDirection() {
        // the ground rises with the distance from the player: the farthest sample along the ray is highest
        LandingSite.Surface rising = (x, z) -> (int) Math.hypot(x + 0.5 - PX, z + 0.5 - PZ);
        for (long seed = 0; seed < 50; seed++) {
            LandingSite.Spot s = LandingSite.choose(PX, PZ, 48, 96, rising, RandomSource.create(seed)).orElseThrow();
            assertTrue(s.distanceTo(PX, PZ) > 90, "should take the hilltop, took " + s.distanceTo(PX, PZ));
        }
    }

    @Test
    void turnsToAnotherDirectionWhenOneIsWater() {
        // only land to the west of the player
        LandingSite.Surface west = (x, z) -> x < PX - 40 ? 70 : LandingSite.Surface.NONE;
        for (long seed = 0; seed < 50; seed++) {
            LandingSite.Spot s = LandingSite.choose(PX, PZ, 48, 96, west, RandomSource.create(seed)).orElseThrow();
            assertTrue(s.x() < PX - 40);
            assertTrue(s.distanceTo(PX, PZ) >= 47.5);
        }
    }

    @Test
    void fallsBackToWithin16BlocksOfThePlayer() {
        // an island: nothing valid farther than 20 blocks out
        LandingSite.Surface island = (x, z) -> Math.hypot(x + 0.5 - PX, z + 0.5 - PZ) < 20 ? 63 : LandingSite.Surface.NONE;
        for (long seed = 0; seed < 50; seed++) {
            LandingSite.Spot s = LandingSite.choose(PX, PZ, 48, 96, island, RandomSource.create(seed)).orElseThrow();
            double d = s.distanceTo(PX, PZ);
            assertTrue(d <= 16.5 && d >= LandingSite.NEAR_MIN - 1, "fallback landed " + d + " away");
        }
    }

    @Test
    void nothingValidMeansNoLanding() {
        assertFalse(LandingSite.choose(PX, PZ, 48, 96, (x, z) -> LandingSite.Surface.NONE, RandomSource.create(1)).isPresent());
    }

    @Test
    void cratersAreNotCutIntoCliffs() {
        assertTrue(LandingSite.levelEnough(70, new int[] {70, 71, 72, 69, 68, 70, 70, 71}));
        assertFalse(LandingSite.levelEnough(70, new int[] {70, 71, 73, 70, 70, 70, 70, 70}));
        assertFalse(LandingSite.levelEnough(70, new int[] {70, 70, 70, LandingSite.Surface.NONE, 70, 70, 70, 70}));
    }

    @Test
    void theDistanceRangeIsConfigurable() {
        LandingSite.Spot s = LandingSite.choose(0.5, 0.5, 20, 20, (x, z) -> 64, RandomSource.create(7)).orElseThrow();
        assertEquals(20.0, s.distanceTo(0.5, 0.5), 1.0);
    }
}
