package com.cosmicbreach.structure.choir;

import java.util.function.Function;

/**
 * A Choir Floor's play, round by round (GDD 6.3), as a pure state machine the Conductor's block entity drives
 * with the game time. Everything happens on whole ticks of Vesper's clock:
 *
 * <ol>
 *   <li><b>Call.</b> On a beat at least {@link ChoirRules#LEAD_BEATS} beats ahead, the Conductor plays the round's
 *       phrase pad by pad ({@link Listener#note}). A ghost note sounds dim, and only on the first call of an
 *       attempt: a replay leaves it silent. After the last note come {@link ChoirRules#COUNT_IN_BEATS} beats of
 *       count-in; in round 3 the ring turns a slot on the first of them ({@link Listener#rotate}), so the answer
 *       has to follow notes, not places.</li>
 *   <li><b>Answer.</b> The player steps the phrase back in the same order and rhythm, starting on the beat after
 *       the count-in; {@link ChoirJudge} judges each step on the tick.</li>
 *   <li><b>Discord.</b> A wrong pad or a missed beat is a mistake ({@link Listener#discord}: the block entity hurts
 *       and pushes whoever it was, and for a missed beat only the players who have stepped on a pad) and the phrase
 *       replays. The third mistake in a round rests the floor for {@link ChoirRules#REST_TICKS} ticks; then the round
 *       starts again, ghost and all. There is never a lockout.</li>
 *   <li><b>Silence.</b> If a beat goes by before anyone has stepped on a pad during this answer, nobody is playing: no
 *       Discord. The floor waits out the phrase; if still nobody steps, it falls idle ({@link #silentIdle}), as it does
 *       when the ring is empty. A step in the meantime means someone is playing after all, and late: a Discord.</li>
 *   <li>A round answered, the next one begins; the third solves the floor.</li>
 * </ol>
 * The song is chosen when the floor wakes: co-op (with chords) if two or more players are in the room. A co-op song
 * falls back to the solo one at the next call if fewer than two remain. With nobody on the floor for
 * {@link ChoirRules#IDLE_TICKS} ticks the floor falls idle, keeping its finished rounds.
 */
public final class ChoirSession {
    public enum Phase { IDLE, CALL, ANSWER, REST, SOLVED }

    public enum Miss { WRONG_PAD, MISSED_BEAT }

    /** What the world should do, as it happens. */
    public interface Listener {
        default void call(int round, boolean replay) {
        }

        /** The Conductor sounds {@code pad} now; {@code dim} for the ghost note. */
        default void note(int pad, boolean dim) {
        }

        /** Count-in beat: {@code beatsLeft} (4 to 1) beats before the answer. */
        default void countIn(int beatsLeft) {
        }

        default void rotate(int rotation) {
        }

        default void hit(int note, int pad, int offset) {
        }

        default void discord(Miss why, int mistakes) {
        }

        default void rest(long until) {
        }

        default void roundDone(int round) {
        }

        default void solved() {
        }

        default void idle() {
        }
    }

    private final ChoirDifficulty difficulty;
    private final Function<Boolean, ChoirSong> songs;
    private ChoirSong song;
    private Phase phase = Phase.IDLE;
    private int round;
    private int rotation;
    private long rotatedAt = Long.MIN_VALUE;
    private int mistakes;
    private long callStart = -1;
    private long answerStart = -1;
    private long restUntil = -1;
    private boolean replay;
    private int window = ChoirRules.WINDOW;
    private ChoirJudge judge;
    private long lastOnFloor;
    private long[] lastMiss = {-1, -1, -1};
    /** Pad steps during the current answer. */
    private int stepsThisAnswer;
    /** A beat went by with nobody stepping: the tick the phrase ends, or -1. */
    private long silentUntil = -1;
    private boolean silentIdle;

    public ChoirSession(ChoirDifficulty difficulty, Function<Boolean, ChoirSong> songs) {
        this.difficulty = difficulty;
        this.songs = songs;
    }

    // ------------------------------------------------------------------ reading

    public Phase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public int rotation() {
        return rotation;
    }

    public long rotatedAt() {
        return rotatedAt;
    }

    public int mistakes() {
        return mistakes;
    }

    public long callStart() {
        return callStart;
    }

    public long answerStart() {
        return answerStart;
    }

    public long restUntil() {
        return restUntil;
    }

    /** True if the current call is a replay after a Discord (its ghost note stays silent). */
    public boolean replay() {
        return replay;
    }

    public int window() {
        return window;
    }

    public ChoirDifficulty difficulty() {
        return difficulty;
    }

    /** The song, once woken (else null). */
    public ChoirSong song() {
        return song;
    }

    /** The current round's phrase, or null before the floor first wakes. */
    public ChoirPhrase phrase() {
        return song == null ? null : song.phrase(round);
    }

    /** True if the floor last fell idle because a whole phrase passed with nobody stepping on a pad. */
    public boolean silentIdle() {
        return silentIdle;
    }

    /** True while the floor waits out a phrase nobody has stepped into (after a beat went by unanswered). */
    public boolean silent() {
        return silentUntil >= 0;
    }

    /** The last mistake: {note, the tick it was due, the tick it happened}. */
    public long[] lastMiss() {
        return lastMiss.clone();
    }

    /** The judge of the current answer, or null outside one. */
    public ChoirJudge judge() {
        return judge;
    }

    // ------------------------------------------------------------------ driving

    /**
     * A player came onto the floor of an idle floor: wake it. {@code players} in the room choose solo or co-op;
     * {@code window} is 3, or 5 when the server is Relaxed.
     */
    public void wake(long now, int players, int window) {
        if (phase != Phase.IDLE) {
            return;
        }
        silentIdle = false;
        this.window = window;
        lastOnFloor = now;
        if (song == null) {
            song = songs.apply(players >= 2);
        }
        mistakes = 0;
        startCall(now, false, players);
    }

    /** Restores a saved floor: its song mode, rounds done, the ring's turn, solved or not. It wakes idle. */
    public void restore(boolean coop, int round, int rotation, boolean solved) {
        this.song = songs.apply(coop);
        this.round = Math.max(0, Math.min(ChoirRules.ROUNDS - 1, round));
        this.rotation = Math.floorMod(rotation, ChoirRules.PADS);
        this.phase = solved ? Phase.SOLVED : Phase.IDLE;
    }

    /**
     * Once per game tick. {@code players}: in the room; {@code maxGrace}: the most latency grace of the players on
     * the floor; {@code anyoneOnFloor}: whether any is.
     */
    public void tick(long now, int players, int maxGrace, boolean anyoneOnFloor, Listener out) {
        if (anyoneOnFloor) {
            lastOnFloor = now;
        }
        switch (phase) {
            case IDLE, SOLVED -> {
            }
            case REST -> {
                if (now >= restUntil) {
                    mistakes = 0;
                    startCall(now, false, players);
                }
            }
            case CALL -> {
                if (now - lastOnFloor > ChoirRules.IDLE_TICKS) {
                    goIdle(out);
                    return;
                }
                ChoirPhrase p = phrase();
                if (now == callStart) {
                    out.call(round, replay);
                }
                for (int i = 0; i < p.size(); i++) {
                    if (now == callStart + p.onsets()[i]) {
                        boolean ghost = i == p.ghost();
                        if (ghost && replay) {
                            continue;
                        }
                        for (int pad : p.pads()[i]) {
                            out.note(pad, ghost);
                        }
                    }
                }
                if (difficulty.rotates(round) && now == callStart + p.length()) {
                    rotation = Math.floorMod(rotation + 1, ChoirRules.PADS);
                    rotatedAt = now;
                    out.rotate(rotation);
                }
                for (int k = ChoirRules.COUNT_IN_BEATS; k >= 1; k--) {
                    if (now == answerStart - (long) k * ChoirRules.BEAT) {
                        out.countIn(k);
                    }
                }
                if (now >= answerStart - window) {
                    phase = Phase.ANSWER;
                    judge = new ChoirJudge(p, answerStart, window);
                    stepsThisAnswer = 0;
                    silentUntil = -1;
                }
            }
            case ANSWER -> {
                if (now - lastOnFloor > ChoirRules.IDLE_TICKS) {
                    goIdle(out);
                    return;
                }
                if (silentUntil >= 0) {
                    if (now > silentUntil) {
                        silentIdle = true;
                        goIdle(out); // a whole phrase and nobody stepped: nobody is playing, no Discord
                    }
                    return;
                }
                if (judge.missed(now, maxGrace)) {
                    if (stepsThisAnswer == 0) {
                        ChoirPhrase p = judge.phrase();
                        silentUntil = judge.target(p.size() - 1) + window + maxGrace;
                    } else {
                        discord(now, Miss.MISSED_BEAT, players, out);
                    }
                }
            }
        }
    }

    /**
     * A player's feet entered pad {@code pad} (already turned by the ring's rotation) on tick {@code now}. Returns the
     * judge's verdict ({@link ChoirJudge.Verdict#IGNORED} outside an answer).
     */
    public ChoirJudge.Verdict step(long now, int pad, int grace, int players, Listener out) {
        if (phase != Phase.ANSWER || judge == null) {
            return ChoirJudge.Verdict.IGNORED;
        }
        stepsThisAnswer++;
        if (silentUntil >= 0) {
            // a beat already went by; someone is playing after all, and late
            silentUntil = -1;
            discord(now, Miss.MISSED_BEAT, players, out);
            return ChoirJudge.Verdict.IGNORED;
        }
        int before = judge.hits().size();
        ChoirJudge.Verdict v = judge.enter(now, pad, grace);
        if (judge.hits().size() > before) {
            int[] h = judge.hits().get(judge.hits().size() - 1);
            out.hit(h[0], h[1], h[2]);
        }
        if (v == ChoirJudge.Verdict.DONE) {
            roundDone(now, players, out);
        } else if (v == ChoirJudge.Verdict.WRONG) {
            discord(now, Miss.WRONG_PAD, players, out);
        }
        return v;
    }

    // ------------------------------------------------------------------ debug

    /** Answers the current round at once (debug). */
    public void skipRound(long now, int players, Listener out) {
        if (phase == Phase.SOLVED) {
            return;
        }
        if (song == null) {
            song = songs.apply(players >= 2);
        }
        lastOnFloor = now;
        roundDone(now, players, out);
    }

    /** Solves the floor at once (debug). */
    public void solveNow(Listener out) {
        if (phase == Phase.SOLVED) {
            return;
        }
        if (song == null) {
            song = songs.apply(false);
        }
        round = ChoirRules.ROUNDS - 1;
        judge = null;
        phase = Phase.SOLVED;
        out.solved();
    }

    public void setWindow(int window) {
        this.window = window;
    }

    // ------------------------------------------------------------------ inside

    private void startCall(long now, boolean replay, int players) {
        if (song.coop() && players < 2) {
            song = songs.apply(false); // a chord needs two: with one player left, the solo tune (no lockout)
        }
        this.replay = replay;
        judge = null;
        phase = Phase.CALL;
        callStart = ChoirRules.nextBeat(now + (long) ChoirRules.LEAD_BEATS * ChoirRules.BEAT);
        answerStart = callStart + phrase().length() + (long) ChoirRules.COUNT_IN_BEATS * ChoirRules.BEAT;
    }

    private void discord(long now, Miss why, int players, Listener out) {
        mistakes++;
        lastMiss = judge == null || judge.done() ? new long[] {-1, -1, now} : new long[] {judge.next(), judge.target(judge.next()), now};
        judge = null;
        out.discord(why, mistakes);
        if (mistakes >= ChoirRules.MISTAKES_TO_REST) {
            phase = Phase.REST;
            restUntil = now + ChoirRules.REST_TICKS;
            out.rest(restUntil);
        } else {
            startCall(now, true, players);
        }
    }

    private void roundDone(long now, int players, Listener out) {
        out.roundDone(round);
        judge = null;
        mistakes = 0;
        if (round + 1 >= ChoirRules.ROUNDS) {
            phase = Phase.SOLVED;
            out.solved();
            return;
        }
        round++;
        startCall(now, false, players);
    }

    private void goIdle(Listener out) {
        phase = Phase.IDLE;
        judge = null;
        mistakes = 0;
        silentUntil = -1;
        out.idle();
    }
}
