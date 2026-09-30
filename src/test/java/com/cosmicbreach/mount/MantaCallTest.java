package com.cosmicbreach.mount;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Taming a Drift Manta by call and response (GDD 8.1), judged like the Choir Floor (GDD 6.3). */
class MantaCallTest {
    private static final int[] PHRASE = {0, 2, 3};

    /** Ticks the call from {@code from} to {@code to} inclusive, collecting what happens. */
    private static List<MantaCall.Event> run(MantaCall c, long from, long to, int grace) {
        List<MantaCall.Event> out = new ArrayList<>();
        for (long t = from; t <= to; t++) {
            out.addAll(c.tick(t, grace, () -> PHRASE));
        }
        return out;
    }

    private static long count(List<MantaCall.Event> events, MantaCall.Event.Kind kind) {
        return events.stream().filter(e -> e.kind() == kind).count();
    }

    /** Answers the phrase in progress with each use {@code offsets[i]} ticks from its beat; returns the verdicts. */
    private static List<MantaCall.Verdict> answer(MantaCall c, long[] clock, int[] offsets, int grace) {
        List<MantaCall.Verdict> out = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            long at = c.answerBeat(i) + offsets[i];
            run(c, clock[0] + 1, at, grace);
            clock[0] = at;
            out.add(c.chime(at, grace));
            if (c.phase() == MantaCall.Phase.RETREAT) {
                break;
            }
        }
        return out;
    }

    private static MantaCall started(long now) {
        MantaCall c = new MantaCall();
        assertTrue(c.start(now, 3, PHRASE));
        return c;
    }

    @Test
    void theCallStartsOnTheNextBarLineAtLeastABeatAway() {
        assertEquals(48, MantaCall.nextCallStart(0));
        assertEquals(48, MantaCall.nextCallStart(36));
        assertEquals(96, MantaCall.nextCallStart(37), "less than a beat before a bar: the one after");
        assertEquals(96, MantaCall.nextCallStart(48));
        MantaCall c = started(10);
        assertEquals(48, c.callStart());
        assertEquals(96, c.answerStart());
        assertEquals(MantaCall.Phase.LEAD, c.phase());
    }

    @Test
    void itSingsThreeNotesOnTheBeat() {
        MantaCall c = started(10);
        List<Long> when = new ArrayList<>();
        List<Integer> pads = new ArrayList<>();
        for (long t = 11; t <= 90; t++) {
            for (MantaCall.Event e : c.tick(t, 0, () -> PHRASE)) {
                if (e.kind() == MantaCall.Event.Kind.SING) {
                    when.add(t);
                    pads.add(e.pad());
                }
            }
        }
        assertEquals(List.of(48L, 60L, 72L), when, "beats 0, 1, 2 of the bar");
        assertEquals(List.of(0, 2, 3), pads);
        assertEquals(MantaCall.Phase.CALL, c.phase());
    }

    @Test
    void threeCleanPhrasesInARowTameIt() {
        MantaCall c = started(10);
        long[] clock = {10};
        List<MantaCall.Event> all = new ArrayList<>();
        for (int phrase = 0; phrase < 3; phrase++) {
            assertEquals(List.of(MantaCall.Verdict.HIT, MantaCall.Verdict.HIT, MantaCall.Verdict.DONE),
                    answer(c, clock, new int[] {-3, 0, 3}, 0), "phrase " + phrase + ": the window's edges count");
            long end = c.answerBeat(2) + 3;
            all.addAll(run(c, clock[0] + 1, end + 1, 0));
            clock[0] = end + 1;
        }
        assertEquals(2, count(all, MantaCall.Event.Kind.PHRASE_CLEAN));
        assertEquals(1, count(all, MantaCall.Event.Kind.TAMED));
        assertTrue(c.tamed());
        assertFalse(c.start(clock[0] + 5, 3, PHRASE), "a tamed manta calls no more");
    }

    @Test
    void phrasesFollowEachOtherEveryTwoBars() {
        MantaCall c = started(10);
        long[] clock = {10};
        long firstCall = c.callStart();
        answer(c, clock, new int[] {0, 0, 0}, 0);
        run(c, clock[0] + 1, c.answerBeat(2) + 4, 0);
        assertEquals(firstCall + 2 * MantaCall.BAR, c.callStart(), "the next call on the bar after the answer");
        assertEquals(1, c.clean());
    }

    @Test
    void anEarlyOrLateUseIsAMissAndSendsItOffForThirtySeconds() {
        for (int off : new int[] {-4, 4}) {
            MantaCall c = started(10);
            long[] clock = {10};
            List<MantaCall.Verdict> v = answer(c, clock, new int[] {0, off}, 0);
            // early: the use itself is the miss; late: the beat's window closed on the tick before the use was heard
            assertEquals(List.of(MantaCall.Verdict.HIT, off < 0 ? MantaCall.Verdict.MISS : MantaCall.Verdict.IGNORED), v,
                    "off by " + off);
            assertEquals(MantaCall.Phase.RETREAT, c.phase());
            assertEquals(clock[0] + 600, c.retreatUntil());
            assertEquals(MantaCall.Verdict.IGNORED, c.chime(clock[0] + 10, 0), "while away it hears nothing");
            List<MantaCall.Event> events = run(c, clock[0] + 1, clock[0] + 600, 0);
            assertEquals(1, count(events, MantaCall.Event.Kind.RETREAT_OVER));
            assertEquals(MantaCall.Phase.IDLE, c.phase());
            assertEquals(0, c.clean(), "the count starts over");
            assertTrue(c.start(clock[0] + 601, 3, PHRASE), "after 30 s it listens again");
        }
    }

    @Test
    void latencyGraceWidensOnlyTheLateSide() {
        MantaCall c = started(10);
        long[] clock = {10};
        assertEquals(List.of(MantaCall.Verdict.HIT, MantaCall.Verdict.HIT), answer(c, clock, new int[] {5, 5}, 2));
        MantaCall early = started(10);
        long[] clock2 = {10};
        assertEquals(List.of(MantaCall.Verdict.MISS), answer(early, clock2, new int[] {-5}, 2));
    }

    @Test
    void aBeatLeftUnansweredIsAMiss() {
        MantaCall c = started(10);
        long[] clock = {10};
        answer(c, clock, new int[] {0}, 0);
        List<MantaCall.Event> events = run(c, clock[0] + 1, c.answerBeat(1) + 4, 0);
        assertEquals(1, count(events, MantaCall.Event.Kind.MISSED_BEAT));
        assertEquals(MantaCall.Phase.RETREAT, c.phase());
    }

    @Test
    void chimingOverTheMantasSongOrOnceTooOftenIsAMiss() {
        MantaCall during = started(10);
        run(during, 11, 60, 0);
        assertEquals(MantaCall.Verdict.MISS, during.chime(60, 0), "over its own second note");

        MantaCall extra = started(10);
        long[] clock = {10};
        answer(extra, clock, new int[] {0, 0, 0}, 0);
        assertEquals(MantaCall.Verdict.MISS, extra.chime(clock[0] + 1, 0), "a fourth answer");

        MantaCall lead = started(10);
        assertEquals(MantaCall.Verdict.IGNORED, lead.chime(12, 0), "a second click before the call starts is forgiven");
        assertEquals(MantaCall.Phase.LEAD, lead.phase());
    }

    @Test
    void theWindowIsTheChoirFloorsAndRelaxedWidensIt() {
        MantaCall relaxed = new MantaCall();
        assertTrue(relaxed.start(10, 5, PHRASE));
        long[] clock = {10};
        assertEquals(List.of(MantaCall.Verdict.HIT), answer(relaxed, clock, new int[] {-5}, 0));
    }

    @Test
    void theJudgeLogsEachAnswersOffset() {
        MantaCall c = started(10);
        long[] clock = {10};
        answer(c, clock, new int[] {-2, 1, 3}, 0);
        assertEquals(3, c.log().size());
        assertEquals(-2, c.log().get(0)[2]);
        assertEquals(1, c.log().get(1)[2]);
        assertEquals(3, c.log().get(2)[2]);
    }

    @Test
    void phrasesAreStepwiseTunesInsideTheOctave() {
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 2000; i++) {
            int[] p = MantaCall.phrase(r::nextInt);
            assertEquals(3, p.length);
            for (int k = 0; k < 3; k++) {
                assertTrue(p[k] >= 0 && p[k] < MantaCall.NOTE_PADS);
                if (k > 0) {
                    assertTrue(Math.abs(p[k] - p[k - 1]) <= 2, "steps of one or two: " + java.util.Arrays.toString(p));
                }
            }
        }
        assertArrayEquals(PHRASE, started(0).notes());
    }
}
