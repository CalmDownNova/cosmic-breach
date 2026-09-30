package com.cosmicbreach.sandbox;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveScheduleTest {
    @Test
    void aWarmUpPairComesAfterFiveSecondsThenNormalPacks() {
        WaveSchedule schedule = new WaveSchedule(() -> 5);
        for (int tick = 1; tick < WaveSchedule.FIRST_DELAY; tick++) {
            assertEquals(0, schedule.tick(0), "tick " + tick);
        }
        assertEquals(2, schedule.tick(0), "tick 100: a pair");
        assertEquals(1, schedule.sent());
        assertEquals(1.5f, WaveSchedule.paceOf(1), "the pair rests 50% longer");
        for (int i = 0; i < 50; i++) {
            schedule.tick(2);
        }
        int size;
        while ((size = schedule.tick(0)) == 0) {
            // the pair is gone; waiting 6 s
        }
        assertEquals(5, size, "then a full pack");
        assertEquals(1.0f, WaveSchedule.paceOf(2), "at the normal pace");
        assertEquals(1.0f, WaveSchedule.paceOf(7));
    }

    @Test
    void theNextPackComesSixSecondsAfterEachPackIsGone() {
        int[] roll = {2};
        WaveSchedule schedule = new WaveSchedule(() -> roll[0]++);
        int tick = 0;
        while (schedule.tick(0) == 0) {
            tick++;
        }
        // The pack fights for a while.
        for (int i = 0; i < 300; i++) {
            assertEquals(0, schedule.tick(3 - i / 100));
        }
        // It is gone: 120 ticks later the next one comes, not before.
        int waited = 0;
        int size;
        while ((size = schedule.tick(0)) == 0) {
            waited++;
            assertTrue(waited < 1000);
        }
        assertEquals(WaveSchedule.NEXT_DELAY - 1, waited, "the pack comes on the 120th tick after the last one fell");
        assertEquals(3, size, "the roll of 2 is clamped to 3");
        List<Integer> sizes = new ArrayList<>();
        for (int pack = 0; pack < 5; pack++) {
            schedule.tick(4); // fighting
            while ((size = schedule.tick(0)) == 0) {
                // waiting
            }
            sizes.add(size);
        }
        assertEquals(List.of(3, 4, 5, 5, 5), sizes, "rolls 3 to 7 clamped to 3 to 5");
    }

    @Test
    void aPackSentByHandIsWaitedFor() {
        WaveSchedule schedule = new WaveSchedule(() -> 3);
        for (int i = 0; i < 50; i++) {
            schedule.tick(0);
        }
        // /cosmicbreach wave sent one at tick 50
        for (int i = 0; i < 200; i++) {
            assertEquals(0, schedule.tick(2));
        }
        int waited = 0;
        while (schedule.tick(0) == 0) {
            waited++;
        }
        assertEquals(WaveSchedule.NEXT_DELAY - 1, waited);
    }
}
