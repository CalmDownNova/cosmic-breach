package com.cosmicbreach.onboarding;

import static com.cosmicbreach.onboarding.StarfallSchedule.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.onboarding.StarfallSchedule.Decision;
import org.junit.jupiter.api.Test;

class StarfallScheduleTest {
    private static final int START = 12_000;
    private static final int END = 12_500;

    @Test
    void aNewWorldGetsItsFirstFallAtTheFirstSunset() {
        for (double r : new double[] {0.0, 0.5, 0.999}) {
            long t = StarfallSchedule.firstFall(0, START, END, r);
            assertTrue(t >= 12_000 && t < 12_500, "first fall at " + t);
        }
        // the test world starts at noon
        long t = StarfallSchedule.firstFall(6_000, START, END, 0.3);
        assertTrue(t >= 12_000 && t < 12_500);
    }

    @Test
    void joiningARunningServerWaitsForTheFirstSunsetAfterJoining() {
        // day 3, mid-morning: this evening
        long t = StarfallSchedule.firstFall(3 * DAY + 2_000, START, END, 0.5);
        assertEquals(3 * DAY + 12_250, t);
        // day 3, at night: tomorrow's sunset
        t = StarfallSchedule.firstFall(3 * DAY + 15_000, START, END, 0.0);
        assertEquals(4 * DAY + 12_000, t);
        // inside the window with time left: later in this window, with notice
        t = StarfallSchedule.firstFall(3 * DAY + 12_100, START, END, 0.0);
        assertEquals(3 * DAY + 12_100 + StarfallSchedule.NOTICE, t);
        t = StarfallSchedule.firstFall(3 * DAY + 12_100, START, END, 0.999);
        assertTrue(t < 3 * DAY + 12_500);
        // inside the window with too little left: tomorrow
        t = StarfallSchedule.firstFall(3 * DAY + 12_450, START, END, 0.0);
        assertEquals(4 * DAY + 12_000, t);
    }

    @Test
    void laterFallsComeEachNightBetween13000And18000() {
        long first = 12_300;
        long next = StarfallSchedule.nightAfter(first, 0.0);
        assertEquals(DAY + 13_000, next);
        next = StarfallSchedule.nightAfter(first, 0.9999);
        assertTrue(next >= DAY + 13_000 && next < DAY + 18_000);
        // a nightly fall that came at dawn after sleeping still counts for its own night
        long due = 5 * DAY + 17_000;
        assertTrue(StarfallSchedule.nightAfter(due, 0.5) >= 6 * DAY + 13_000);
        assertTrue(StarfallSchedule.nightAfter(due, 0.5) < 6 * DAY + 18_000);
    }

    @Test
    void aFallSkippedBySleepingComesAtDawn() {
        long due = 2 * DAY + 16_000;
        // bed at 12,600: the night is skipped, the clock jumps to the next dawn
        long dawn = 3 * DAY;
        assertEquals(Decision.FALL, StarfallSchedule.decide(dawn, due));
        // even the earliest nightly fall slept through from the earliest bedtime
        assertEquals(Decision.FALL, StarfallSchedule.decide(3 * DAY, 2 * DAY + 13_000));
        assertEquals(Decision.WAIT, StarfallSchedule.decide(due - 1, due));
        assertEquals(Decision.FALL, StarfallSchedule.decide(due, due));
    }

    @Test
    void aFallMissedWhileAwayIsReplannedNotDropped() {
        long due = 2 * DAY + 16_000;
        assertEquals(Decision.REPLAN, StarfallSchedule.decide(due + StarfallSchedule.MAX_LATE + 1, due));
        // the clock set far back
        assertEquals(Decision.REPLAN, StarfallSchedule.decide(0, 5 * DAY));
        // the fresh plan: tonight if the night has not begun, else within what is left, else tomorrow
        assertEquals(4 * DAY + 13_000, StarfallSchedule.nextNight(4 * DAY + 6_000, 0.0));
        long mid = StarfallSchedule.nextNight(4 * DAY + 14_000, 0.0);
        assertEquals(4 * DAY + 14_000 + StarfallSchedule.NOTICE, mid);
        assertEquals(5 * DAY + 13_000, StarfallSchedule.nextNight(4 * DAY + 20_000, 0.0));
    }

    @Test
    void timeOfDayWrapsNegativeClocks() {
        assertEquals(12_000, StarfallSchedule.timeOfDay(3 * DAY + 12_000));
        assertEquals(23_000, StarfallSchedule.timeOfDay(-1_000));
    }
}
