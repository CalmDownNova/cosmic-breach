package com.cosmicbreach.mount;

import com.cosmicbreach.structure.choir.ChoirJudge;
import com.cosmicbreach.structure.choir.ChoirPhrase;
import com.cosmicbreach.world.VesperClock;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Taming a Drift Manta by call and response (GDD 8.1), on Vesper's clock, judged by the Choir Floor's judge
 * ({@link ChoirJudge}). Pure: the entity feeds it game ticks and Chime uses and acts on what it reports.
 *
 * <p>A player starts it with a Chime near a wild manta. On the next bar line at least a beat away the manta sings its
 * phrase: three notes on the bar's first three beats ({@link Phase#CALL}). On the next bar the player answers, one
 * Chime use a note, each within the judge's window of its beat, late by at most their latency grace as well
 * ({@link Phase#ANSWER}); each use plays the next of the manta's notes. Then the next phrase is called on the bar after.
 * Three clean phrases in a row tame it. Anything else is a miss: a use outside every window (early, late, during the
 * call, or one too many), or a beat's window closing unanswered. A miss sends the manta off for {@value #RETREAT_TICKS}
 * ticks ({@link Phase#RETREAT}) and the count starts over.
 */
public final class MantaCall {
    public static final int NOTES = 3;
    public static final int PHRASES_TO_TAME = 3;
    public static final int RETREAT_TICKS = 600;
    public static final int BEAT = VesperClock.TICKS_PER_BEAT;
    public static final int BAR = VesperClock.TICKS_PER_BEAT * VesperClock.BEATS_PER_BAR;
    /** The note pads the manta sings from: the Choir Floor's first six, D4 to D5 (the game's pitch reaches an octave). */
    public static final int NOTE_PADS = 6;

    public enum Phase { IDLE, LEAD, CALL, ANSWER, RETREAT }

    /** What a Chime use was. */
    public enum Verdict {
        /** Nothing to answer (no call, or the manta is away): no effect. */
        IGNORED,
        /** The note was answered on time. */
        HIT,
        /** The phrase's last note was answered on time. */
        DONE,
        /** Off the beat, or when no note was due: the manta leaves. */
        MISS
    }

    /** Something the manta does this tick. */
    public record Event(Kind kind, int note, int pad) {
        public enum Kind { SING, PHRASE_CLEAN, TAMED, MISSED_BEAT, RETREAT_OVER }
    }

    private Phase phase = Phase.IDLE;
    private int window = 3;
    private long callStart;
    private int[] notes = new int[0];
    private @Nullable ChoirJudge judge;
    private int clean;
    private int sung;
    private long retreatUntil;
    private boolean tamed;
    /** Every Chime use judged in an answer: {tick, note, offset from its beat, verdict ordinal}. */
    private final List<long[]> log = new ArrayList<>();

    /** The first bar line at least a beat after {@code now}. */
    public static long nextCallStart(long now) {
        return Math.floorDiv(now + BEAT + BAR - 1, BAR) * BAR;
    }

    /**
     * A phrase of {@value #NOTES} pads (0 to {@value #NOTE_PADS} - 1) drawn from {@code random} (each call gives 0 up
     * to the bound it is asked for): it starts low and moves by steps of one or two, as a tune does.
     */
    public static int[] phrase(java.util.function.IntUnaryOperator random) {
        int[] out = new int[NOTES];
        out[0] = random.applyAsInt(4);
        for (int i = 1; i < NOTES; i++) {
            int step = 1 + random.applyAsInt(2);
            int dir = random.applyAsInt(2) == 0 ? -1 : 1;
            int p = out[i - 1] + dir * step;
            if (p < 0 || p >= NOTE_PADS) {
                p = out[i - 1] - dir * step;
            }
            out[i] = Math.max(0, Math.min(NOTE_PADS - 1, p));
        }
        return out;
    }

    public Phase phase() {
        return phase;
    }

    public boolean tamed() {
        return tamed;
    }

    /** Clean phrases in a row so far. */
    public int clean() {
        return clean;
    }

    public long callStart() {
        return callStart;
    }

    /** The tick the answer's first beat falls on. */
    public long answerStart() {
        return callStart + BAR;
    }

    /** The tick answer note {@code i} is due. */
    public long answerBeat(int i) {
        return answerStart() + (long) i * BEAT;
    }

    public int[] notes() {
        return notes.clone();
    }

    public int window() {
        return window;
    }

    public long retreatUntil() {
        return retreatUntil;
    }

    /** The answer notes judged so far this phrase (0 to 3). */
    public int answered() {
        return judge == null ? 0 : judge.next();
    }

    public List<long[]> log() {
        return log;
    }

    /**
     * A player starts the call at {@code now}: from calm, the manta will sing on the next bar line at least a beat
     * away. {@code window} is the judge's (the Choir Floor's: 3, or 5 when Relaxed). Returns false if it is away or
     * already calling.
     */
    public boolean start(long now, int window, int[] firstPhrase) {
        if (phase != Phase.IDLE || tamed) {
            return false;
        }
        this.window = window;
        clean = 0;
        begin(nextCallStart(now), firstPhrase);
        phase = Phase.LEAD;
        return true;
    }

    private void begin(long start, int[] phraseNotes) {
        callStart = start;
        notes = phraseNotes.clone();
        sung = 0;
        int[][] pads = new int[NOTES][];
        int[] onsets = new int[NOTES];
        for (int i = 0; i < NOTES; i++) {
            pads[i] = new int[] {notes[i]};
            onsets[i] = i * BEAT;
        }
        judge = new ChoirJudge(new ChoirPhrase(pads, onsets, -1), answerStart(), window);
    }

    /**
     * One game tick. {@code grace} is the player's latency grace; {@code nextPhrase} gives the notes of the next call
     * when one is needed. Returns what happened.
     */
    public List<Event> tick(long now, int grace, PhraseSource nextPhrase) {
        List<Event> out = new ArrayList<>();
        switch (phase) {
            case IDLE -> {
            }
            case RETREAT -> {
                if (now >= retreatUntil) {
                    phase = Phase.IDLE;
                    out.add(new Event(Event.Kind.RETREAT_OVER, -1, -1));
                }
            }
            case LEAD, CALL -> {
                while (sung < NOTES && now >= callStart + (long) sung * BEAT) {
                    out.add(new Event(Event.Kind.SING, sung, notes[sung]));
                    sung++;
                    phase = Phase.CALL;
                }
                if (sung >= NOTES && now >= answerStart() - window) {
                    phase = Phase.ANSWER;
                }
            }
            case ANSWER -> {
                ChoirJudge j = judge;
                if (j != null && j.missed(now, grace)) {
                    out.add(new Event(Event.Kind.MISSED_BEAT, j.next(), notes[j.next()]));
                    retreat(now);
                } else if (j != null && j.done() && now >= answerBeat(NOTES - 1) + window + Math.max(0, grace)) {
                    clean++;
                    if (clean >= PHRASES_TO_TAME) {
                        tamed = true;
                        phase = Phase.IDLE;
                        out.add(new Event(Event.Kind.TAMED, -1, -1));
                    } else {
                        out.add(new Event(Event.Kind.PHRASE_CLEAN, clean, -1));
                        begin(answerStart() + BAR, nextPhrase.get());
                        phase = Phase.LEAD;
                    }
                }
            }
        }
        return out;
    }

    /** A Chime use at {@code now} by the player answering. */
    public Verdict chime(long now, int grace) {
        switch (phase) {
            case IDLE, RETREAT -> {
                return Verdict.IGNORED;
            }
            case LEAD -> {
                // before the first note of a call nothing is due yet: the use that started it, or a slip
                return now < callStart ? Verdict.IGNORED : miss(now);
            }
            case CALL -> {
                if (now < answerStart() - window) {
                    return miss(now); // over the manta's own song
                }
                phase = Phase.ANSWER;
                return answer(now, grace);
            }
            case ANSWER -> {
                return answer(now, grace);
            }
            default -> {
                return Verdict.IGNORED;
            }
        }
    }

    private Verdict answer(long now, int grace) {
        ChoirJudge j = judge;
        if (j == null || j.done()) {
            return miss(now); // one too many
        }
        int note = j.next();
        long offset = now - j.target(note);
        ChoirJudge.Verdict v = j.enter(now, notes[note], grace);
        if (v == ChoirJudge.Verdict.HIT || v == ChoirJudge.Verdict.DONE) {
            Verdict out = v == ChoirJudge.Verdict.DONE ? Verdict.DONE : Verdict.HIT;
            log.add(new long[] {now, note, offset, out.ordinal()});
            return out;
        }
        log.add(new long[] {now, note, offset, Verdict.MISS.ordinal()});
        return retreatMiss(now);
    }

    private Verdict miss(long now) {
        log.add(new long[] {now, judge == null ? -1 : judge.next(), Long.MIN_VALUE, Verdict.MISS.ordinal()});
        return retreatMiss(now);
    }

    private Verdict retreatMiss(long now) {
        retreat(now);
        return Verdict.MISS;
    }

    private void retreat(long now) {
        phase = Phase.RETREAT;
        retreatUntil = now + RETREAT_TICKS;
        clean = 0;
        judge = null;
    }

    /** Ends a retreat at once (a debug command). */
    public void calm() {
        if (phase == Phase.RETREAT) {
            phase = Phase.IDLE;
        }
    }

    /** Stops a call without a miss (the player left, or the manta was tamed some other way). */
    public void stop() {
        if (phase != Phase.RETREAT) {
            phase = Phase.IDLE;
        }
        judge = null;
        clean = 0;
    }

    /** Supplies the next phrase's pads. */
    @FunctionalInterface
    public interface PhraseSource {
        int[] get();
    }
}
