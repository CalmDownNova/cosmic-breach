package com.cosmicbreach.structure.choir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.structure.choir.ChoirJudge.Verdict;
import org.junit.jupiter.api.Test;

/** GDD 6.3's judging: a step counts within 3 ticks of its beat (5 when Relaxed), plus latency grace on the late side. */
class ChoirJudgeTest {
    /** D4 E4 F#4 then home: one per beat. */
    private static final ChoirPhrase FOUR = new ChoirPhrase(new int[][] {{2}, {1}, {3}, {0}}, new int[] {0, 12, 24, 36}, -1);

    @Test
    void theWindowIsThreeTicksEitherSide() {
        for (int off = -6; off <= 6; off++) {
            assertEquals(Math.abs(off) <= 3, ChoirJudge.within(off, ChoirRules.WINDOW, 0), "offset " + off);
        }
    }

    @Test
    void relaxedIsFive() {
        for (int off = -8; off <= 8; off++) {
            assertEquals(Math.abs(off) <= 5, ChoirJudge.within(off, ChoirRules.RELAXED_WINDOW, 0), "offset " + off);
        }
    }

    @Test
    void latencyGraceWidensOnlyTheLateSide() {
        assertTrue(ChoirJudge.within(5, 3, 2));
        assertFalse(ChoirJudge.within(6, 3, 2));
        assertFalse(ChoirJudge.within(-4, 3, 2), "grace never lets a step come earlier");
        assertEquals(0, ChoirRules.graceTicks(0));
        assertEquals(2, ChoirRules.graceTicks(100), "a 100 ms round trip is 2 ticks");
        assertEquals(ChoirRules.MAX_GRACE, ChoirRules.graceTicks(2000), "capped");
    }

    @Test
    void stepsOnTheBeatSolveThePhrase() {
        ChoirJudge j = new ChoirJudge(FOUR, 1000, 3);
        assertEquals(Verdict.HIT, j.enter(1000, 2, 0));
        assertEquals(Verdict.HIT, j.enter(1012 + 3, 1, 0));
        assertEquals(Verdict.HIT, j.enter(1024 - 3, 3, 0));
        assertEquals(Verdict.DONE, j.enter(1036, 0, 0));
        assertEquals(4, j.hits().size());
        assertEquals(3, j.hits().get(1)[2], "offsets are recorded");
        assertEquals(-3, j.hits().get(2)[2]);
    }

    @Test
    void aStepOutsideTheWindowDoesNotCountAndTheBeatIsMissed() {
        ChoirJudge j = new ChoirJudge(FOUR, 1000, 3);
        assertEquals(Verdict.IGNORED, j.enter(996, 2, 0), "4 early: nothing yet");
        assertFalse(j.missed(1003, 0));
        assertTrue(j.missed(1004, 0), "the window closed unsounded");
        assertFalse(j.missed(1004, 1), "a player with grace keeps it open a tick longer");
        ChoirJudge late = new ChoirJudge(FOUR, 1000, 3);
        assertEquals(Verdict.IGNORED, late.enter(1004, 2, 0));
        assertEquals(Verdict.HIT, new ChoirJudge(FOUR, 1000, 3).enter(1004, 2, 1), "one tick of grace takes it");
    }

    @Test
    void aWrongPadIsAMistakeButNeighbouringNotesAreNot() {
        ChoirJudge j = new ChoirJudge(FOUR, 1000, 3);
        assertEquals(Verdict.WRONG, j.enter(1000, 7, 0));
        ChoirJudge k = new ChoirJudge(FOUR, 1000, 3);
        assertEquals(Verdict.IGNORED, k.enter(1000, 1, 0), "the note after next is an early step, not a wrong one");
        assertEquals(Verdict.HIT, k.enter(1001, 2, 0));
        assertEquals(Verdict.IGNORED, k.enter(1005, 2, 0), "back onto the pad just played");
        assertEquals(Verdict.WRONG, k.enter(1006, 0, 0), "home, two notes early, is wrong");
    }

    @Test
    void aChordNeedsBothPadsInEitherOrder() {
        ChoirPhrase chord = new ChoirPhrase(new int[][] {{1, 5}, {0}}, new int[] {0, 12}, -1);
        ChoirJudge j = new ChoirJudge(chord, 0, 3);
        assertEquals(Verdict.HIT, j.enter(1, 5, 0));
        assertEquals(0, j.next(), "one pad of the chord is not the chord");
        assertEquals(Verdict.IGNORED, j.enter(2, 5, 0), "the same pad again");
        assertTrue(j.missed(4, 0), "the other pad never came");
        ChoirJudge k = new ChoirJudge(chord, 0, 3);
        assertEquals(Verdict.HIT, k.enter(-2, 1, 0));
        assertEquals(Verdict.HIT, k.enter(2, 5, 0));
        assertEquals(1, k.next());
        assertEquals(Verdict.DONE, k.enter(12, 0, 0));
    }

    @Test
    void anEighthPairIsSixTicksApart() {
        ChoirPhrase pair = new ChoirPhrase(new int[][] {{2}, {1}, {0}}, new int[] {0, 6, 18}, -1);
        ChoirJudge j = new ChoirJudge(pair, 120, 3);
        assertEquals(Verdict.HIT, j.enter(121, 2, 0));
        assertEquals(Verdict.HIT, j.enter(126, 1, 0));
        assertEquals(Verdict.DONE, j.enter(138, 0, 0));
    }
}
