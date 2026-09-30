package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.colossus.ShatterState.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shatter: the 20 s countdown from the first shard's death, the kill, and the re-merge (Prism Colossus design v1). */
class ShatterStateTest {
    @Test
    void theFirstDeathStartsTheCountdown() {
        ShatterState s = new ShatterState(3);
        assertFalse(s.counting());
        assertEquals(Result.CONTINUE, s.tick(1_000));
        assertEquals(400, s.ticksLeft(1_000), "the whole 20 s before any shard dies");
        assertEquals(Result.CONTINUE, s.shardDied(1_000));
        assertTrue(s.counting());
        assertEquals(300, s.ticksLeft(1_100));
    }

    @Test
    void allThreeWithinTwentySecondsKillsIt() {
        ShatterState s = new ShatterState(3);
        s.shardDied(0);
        assertEquals(Result.CONTINUE, s.shardDied(150));
        assertEquals(Result.CONTINUE, s.tick(398));
        assertEquals(Result.KILLED, s.shardDied(399), "the last one a tick before the end");
        assertTrue(s.over());
        assertEquals(Result.CONTINUE, s.tick(500), "no re-merge after the kill");
    }

    @Test
    void theCountdownRunningOutReMergesTheSurvivors() {
        ShatterState s = new ShatterState(3);
        s.shardDied(0);
        assertEquals(Result.CONTINUE, s.tick(399));
        assertEquals(Result.REMERGE, s.tick(400), "exactly 20 s after the first death");
        assertEquals(2, s.alive());
        assertEquals(Result.CONTINUE, s.shardDied(401), "a shard dying after the re-merge changes nothing");
        assertEquals(0, s.ticksLeft(420));
    }

    @Test
    void aLateDeathDoesNotCount() {
        ShatterState s = new ShatterState(3);
        s.shardDied(0);
        s.shardDied(10);
        assertEquals(Result.CONTINUE, s.shardDied(400), "the countdown is over on tick 400");
        assertEquals(Result.REMERGE, s.tick(400));
    }

    @Test
    void theColossusReFormsAtAQuarterOfItsHealth() {
        assertEquals(0.25, ShatterState.REMERGE_HEALTH, 1e-9);
        assertEquals(400, ShatterState.COUNTDOWN_TICKS, "20 seconds");
    }

    @Test
    void aSavedStateComesBack() {
        ShatterState s = ShatterState.restore(3, 1, 50);
        assertEquals(1, s.alive());
        assertEquals(350, s.ticksLeft(100));
        assertEquals(Result.KILLED, s.shardDied(200));
    }
}
