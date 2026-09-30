package com.cosmicbreach.structure.choir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Choir Floor played through by a perfect player, one who slips once (a Discord and the replay, the ghost silent),
 * one who slips three times (the 10 s rest, then the round again, ghost and all), a co-op floor losing a player
 * (the solo tune takes over: no lockout), and an empty floor falling idle.
 */
class ChoirSessionTest {
    /** Records what the session asked the world to do. */
    static final class Log implements ChoirSession.Listener {
        final List<String> events = new ArrayList<>();
        final List<long[]> notes = new ArrayList<>();
        long now;
        int discords;
        int rests;
        int solved;
        int rotations;

        @Override
        public void call(int round, boolean replay) {
            events.add("call " + round + (replay ? " replay" : ""));
        }

        @Override
        public void note(int pad, boolean dim) {
            notes.add(new long[] {now, pad, dim ? 1 : 0});
        }

        @Override
        public void rotate(int rotation) {
            rotations++;
            events.add("rotate " + rotation);
        }

        @Override
        public void discord(ChoirSession.Miss why, int mistakes) {
            discords++;
            events.add("discord " + why + " " + mistakes);
        }

        @Override
        public void rest(long until) {
            rests++;
            events.add("rest until " + until);
        }

        @Override
        public void roundDone(int round) {
            events.add("round " + round + " done");
        }

        @Override
        public void solved() {
            solved++;
            events.add("solved");
        }

        @Override
        public void idle() {
            events.add("idle");
        }
    }

    private static ChoirSession session(ChoirDifficulty d, long seed) {
        return new ChoirSession(d, coop -> ChoirGenerator.song(seed, d, coop));
    }

    /** Ticks {@code s} until its answer starts (the judge exists). */
    private static long toAnswer(ChoirSession s, Log log, long t, int players) {
        while (s.judge() == null) {
            log.now = t;
            s.tick(t, players, 0, true, log);
            if (s.judge() != null) {
                return t;
            }
            t++;
            if (t > 1_000_000) {
                throw new AssertionError("no answer");
            }
        }
        return t;
    }

    /** Steps every note of the current answer on its beat (plus {@code offset}); returns the tick after. */
    private static long answer(ChoirSession s, Log log, long t, int offset, int players) {
        ChoirJudge j = s.judge();
        ChoirPhrase p = j.phrase();
        long end = j.target(p.size() - 1) + offset;
        for (; t <= end; t++) {
            log.now = t;
            s.tick(t, players, 0, true, log);
            for (int i = 0; i < p.size(); i++) {
                if (s.judge() != null && t == j.target(i) + offset) {
                    for (int pad : p.pads()[i]) {
                        s.step(t, pad, 0, players, log);
                    }
                }
            }
        }
        return t;
    }

    /** Ticks to the first note's beat and steps it; returns the tick after. */
    private static long stepFirst(ChoirSession s, Log log, long t) {
        ChoirPhrase p = s.phrase();
        long target = s.answerStart() + p.onsets()[0];
        for (; t <= target; t++) {
            log.now = t;
            s.tick(t, 1, 0, true, log);
        }
        for (int pad : p.pads()[0]) {
            s.step(target, pad, 0, 1, log);
        }
        assertEquals(1, s.judge().next(), "the first note sounded");
        return t;
    }

    @Test
    void aPhraseNobodyStepsIntoFallsIdleWithoutADiscord() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 17L);
        s.wake(0, 1, ChoirRules.WINDOW);
        long t = toAnswer(s, log, 0, 1);
        ChoirPhrase p = s.phrase();
        long phraseEnd = s.answerStart() + p.onsets()[p.size() - 1] + ChoirRules.WINDOW;
        long idleAt = -1;
        for (; t < phraseEnd + 40; t++) {
            log.now = t;
            s.tick(t, 1, 0, true, log);
            if (s.phase() == ChoirSession.Phase.IDLE) {
                idleAt = t;
                break;
            }
        }
        assertEquals(0, log.discords, "nobody stepped: nobody is playing, no Discord");
        assertTrue(s.silentIdle());
        assertTrue(idleAt > phraseEnd, "it waits out the whole phrase first (idle at " + idleAt + ", phrase ends " + phraseEnd + ")");
        assertTrue(log.events.contains("idle"));
        assertEquals(0, s.round(), "the round is kept");
        s.wake(t + 1, 1, ChoirRules.WINDOW);
        assertFalse(s.silentIdle(), "woken again");
    }

    @Test
    void aLateStepAfterASilentBeatIsADiscord() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 19L);
        s.wake(0, 1, ChoirRules.WINDOW);
        long t = toAnswer(s, log, 0, 1);
        while (!s.silent()) {
            log.now = t;
            s.tick(t, 1, 0, true, log);
            t++;
            assertTrue(t < 100_000);
        }
        assertEquals(0, log.discords, "the beat went by, but nobody was playing yet");
        s.step(t, s.phrase().melody(1), 0, 1, log);
        assertEquals(1, log.discords, "someone steps after all, and late");
        assertEquals(ChoirSession.Phase.CALL, s.phase(), "the phrase replays");
    }

    @Test
    void aMissedBeatHurtsOnlyThoseWhoHaveStepped() {
        ChoirPlayers players = new ChoirPlayers();
        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        players.stepped(a);
        assertEquals(List.of(a), players.hurtByMissedBeat(List.of(a, b), id -> id), "the watcher who never stepped is spared");
        players.stepped(b);
        assertEquals(List.of(a, b), players.hurtByMissedBeat(List.of(a, b), id -> id));
        players.reset();
        assertTrue(players.hurtByMissedBeat(List.of(a, b), id -> id).isEmpty(), "a new session: nobody is playing yet");
    }

    private static int turned(ChoirSession s, int pad) {
        return pad; // the judge takes pads, not slots: the rotation is the world's to apply
    }

    @Test
    void aPerfectPlayerSolvesAllThreeRounds() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 7L);
        s.wake(100, 1, ChoirRules.WINDOW);
        long t = 100;
        for (int round = 0; round < 3; round++) {
            assertEquals(round, s.round());
            t = toAnswer(s, log, t, 1);
            assertEquals(0, s.callStart() % ChoirRules.BEAT, "calls start on a beat");
            assertEquals(0, s.answerStart() % ChoirRules.BEAT, "answers start on a beat");
            t = answer(s, log, t, 0, 1);
        }
        assertEquals(ChoirSession.Phase.SOLVED, s.phase());
        assertEquals(1, log.solved);
        assertEquals(0, log.discords);
        assertEquals(1, log.rotations, "the ring turned once, in round 3");
        assertEquals(1, s.rotation());
        long dim = log.notes.stream().filter(n -> n[2] == 1).count();
        assertEquals(1, dim, "one ghost note, played dim");
    }

    @Test
    void theConductorPlaysEachNoteOnItsTick() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 11L);
        s.wake(0, 1, ChoirRules.WINDOW);
        toAnswer(s, log, 0, 1);
        ChoirPhrase p = s.phrase();
        assertEquals(p.size(), log.notes.size());
        for (int i = 0; i < p.size(); i++) {
            assertEquals(s.callStart() + p.onsets()[i], log.notes.get(i)[0]);
            assertEquals(p.melody(i), log.notes.get(i)[1]);
        }
        assertEquals(s.callStart() + p.length() + 4L * ChoirRules.BEAT, s.answerStart(), "a bar of count-in");
    }

    @Test
    void aMistakeReplaysThePhraseWithTheGhostSilent() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 3L);
        s.wake(0, 1, ChoirRules.WINDOW);
        long t = 0;
        for (int round = 0; round < 2; round++) {
            t = toAnswer(s, log, t, 1);
            t = answer(s, log, t, 0, 1);
        }
        t = toAnswer(s, log, t, 1);
        int ghostsBefore = (int) log.notes.stream().filter(n -> n[2] == 1).count();
        assertEquals(1, ghostsBefore);
        // step onto a pad that is in none of the next notes
        ChoirPhrase p = s.phrase();
        int wrong = -1;
        for (int pad = 0; pad < 8 && wrong < 0; pad++) {
            if (!p.sounds(0, pad) && !p.sounds(1, pad)) {
                wrong = pad;
            }
        }
        long at = s.answerStart();
        for (; t <= at; t++) {
            log.now = t;
            s.tick(t, 1, 0, true, log);
        }
        assertEquals(ChoirJudge.Verdict.WRONG, s.step(at, wrong, 0, 1, log));
        assertEquals(1, log.discords);
        assertEquals(ChoirSession.Phase.CALL, s.phase());
        assertTrue(s.replay());
        int notesBefore = log.notes.size();
        t = toAnswer(s, log, t + 1, 1);
        assertEquals(p.size() - 1, log.notes.size() - notesBefore, "the replay leaves the ghost silent");
        assertEquals(2, log.rotations, "the ring turns again before the second answer");
        t = answer(s, log, t, 0, 1);
        assertEquals(ChoirSession.Phase.SOLVED, s.phase());
    }

    @Test
    void threeMistakesRestTheFloorThenTheRoundComesBack() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 5L);
        s.wake(0, 1, ChoirRules.WINDOW);
        long t = 0;
        for (int miss = 1; miss <= 3; miss++) {
            t = toAnswer(s, log, t, 1);
            // the first note on its beat, then the player stops: the second beat passes
            t = stepFirst(s, log, t);
            while (log.discords < miss) {
                log.now = t;
                s.tick(t, 1, 0, true, log);
                t++;
            }
            assertTrue(log.events.get(log.events.size() - (miss == 3 ? 2 : 1)).startsWith("discord MISSED_BEAT"));
        }
        assertEquals(ChoirSession.Phase.REST, s.phase());
        assertEquals(1, log.rests);
        long restEnd = s.restUntil();
        assertEquals(ChoirRules.REST_TICKS, restEnd - (t - 1));
        for (; t < restEnd; t++) {
            log.now = t;
            s.tick(t, 1, 0, true, log);
            assertEquals(ChoirSession.Phase.REST, s.phase());
        }
        log.now = t;
        s.tick(t, 1, 0, true, log);
        assertEquals(ChoirSession.Phase.CALL, s.phase(), "never a lockout");
        assertEquals(0, s.mistakes());
        assertFalse(s.replay());
        t = toAnswer(s, log, t + 1, 1);
        answer(s, log, t, 2, 1);
        assertEquals(1, s.round(), "round 1 answered 2 ticks late each step");
    }

    @Test
    void aRelaxedFloorTakesFiveTicksLate() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 9L);
        s.wake(0, 1, ChoirRules.RELAXED_WINDOW);
        long t = toAnswer(s, log, 0, 1);
        answer(s, log, t, 5, 1);
        assertEquals(1, s.round());
        assertEquals(0, log.discords);
        ChoirSession strict = session(ChoirDifficulty.CRYPT, 9L);
        Log l2 = new Log();
        strict.wake(0, 1, ChoirRules.WINDOW);
        long u = toAnswer(strict, l2, 0, 1);
        answer(strict, l2, u, 5, 1);
        assertEquals(1, l2.discords, "5 late is a missed beat without Relaxed");
    }

    @Test
    void aCoopFloorFallsBackToTheSoloTuneWhenAPlayerLeaves() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 21L);
        s.wake(0, 2, ChoirRules.WINDOW);
        assertTrue(s.song().coop());
        assertEquals(1, s.phrase().chords());
        long t = toAnswer(s, log, 0, 2);
        t = answer(s, log, t, 0, 2);
        assertEquals(1, s.round());
        assertTrue(s.song().coop(), "still two players: the chords stay");
        // one player leaves; the other steps a wrong pad, and the call starts over
        t = toAnswer(s, log, t, 1);
        assertNotNull(s.judge());
        ChoirPhrase p2 = s.phrase();
        int wrong = -1;
        for (int pad = 0; pad < 8 && wrong < 0; pad++) {
            if (!p2.sounds(0, pad) && !p2.sounds(1, pad)) {
                wrong = pad;
            }
        }
        s.step(t, wrong, 0, 1, log);
        assertFalse(s.song().coop(), "the replay is the solo tune");
        assertEquals(0, s.phrase().chords());
    }

    @Test
    void anEmptyFloorFallsIdleAndKeepsItsRounds() {
        Log log = new Log();
        ChoirSession s = session(ChoirDifficulty.CRYPT, 13L);
        s.wake(0, 1, ChoirRules.WINDOW);
        long t = toAnswer(s, log, 0, 1);
        t = answer(s, log, t, 0, 1);
        assertEquals(1, s.round());
        for (int i = 0; i <= ChoirRules.IDLE_TICKS + 1; i++, t++) {
            s.tick(t, 0, 0, false, log);
        }
        assertEquals(ChoirSession.Phase.IDLE, s.phase());
        assertEquals(1, s.round(), "round 1 stays answered");
        s.wake(t, 1, ChoirRules.WINDOW);
        assertEquals(ChoirSession.Phase.CALL, s.phase());
        assertEquals(1, s.round());
    }

    @Test
    void theRingTurnsASlotAndPadsFollowTheirNotes() {
        for (int rot = 0; rot < 8; rot++) {
            for (int pad = 0; pad < 8; pad++) {
                assertEquals(pad, ChoirRules.padIn(ChoirRules.slotOf(pad, rot), rot));
                assertEquals(turned(null, pad), pad);
            }
        }
        assertEquals(1, ChoirRules.slotOf(0, 1), "turned one slot, the first pad sits where the second was");
    }
}
