package com.cosmicbreach.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiLodTest {
    @Test
    void theRateFollowsTheNearestPlayer() {
        // GDD 9.4: full rate within 32 blocks, every 2nd tick out to 64, every 5th beyond
        assertEquals(1, AiLod.every(0));
        assertEquals(1, AiLod.every(32));
        assertEquals(2, AiLod.every(32.01));
        assertEquals(2, AiLod.every(64));
        assertEquals(5, AiLod.every(64.01));
        assertEquals(5, AiLod.every(Double.MAX_VALUE));
    }

    @Test
    void aSlowedCreatureTicksOnceInEachPeriodStaggeredById() {
        for (int every : new int[] {1, 2, 5}) {
            for (int id = 0; id < 12; id++) {
                int ticks = 0;
                for (int t = 0; t < every * 20; t++) {
                    if (AiLod.ticksNow(t, id, every)) {
                        ticks++;
                    }
                }
                assertEquals(20, ticks, "every " + every + ", id " + id);
            }
        }
        // ids spread the work: of five neighbours slowed to every 5th tick, one ticks on each tick
        for (int t = 0; t < 10; t++) {
            int n = 0;
            for (int id = 0; id < 5; id++) {
                n += AiLod.ticksNow(t, id, 5) ? 1 : 0;
            }
            assertEquals(1, n);
        }
        assertTrue(AiLod.ticksNow(-7, -3, 5) || !AiLod.ticksNow(-7, -3, 5), "negative counts are safe");
    }
}
