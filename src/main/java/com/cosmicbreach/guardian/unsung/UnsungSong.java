package com.cosmicbreach.guardian.unsung;

import static com.cosmicbreach.guardian.unsung.UnsungMoves.BEAT;
import static com.cosmicbreach.guardian.unsung.UnsungMoves.CHORDS;
import static com.cosmicbreach.guardian.unsung.UnsungMoves.CYCLE_BEATS;
import static com.cosmicbreach.guardian.unsung.UnsungMoves.INTRO_BEATS;
import static com.cosmicbreach.guardian.unsung.UnsungMoves.TURN_BEATS;
import static com.cosmicbreach.guardian.unsung.UnsungMoves.WARNING_BEATS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * The song the Unsung fight follows (Unsung design v1), pure arithmetic on Vesper's beats so the server, the
 * client's music and the tests agree without syncing more than the fight's first tick.
 *
 * <p><b>The clock.</b> The fight's beat 0 is an 8-beat line of the Vesper clock (every other bar line), at least 8
 * beats after the awakening ({@link #fightStart}). A turn is 8 beats; the song passes on each turn's line, Alto,
 * Tenor, Bass, Alto, skipping broken masks ({@link #next}). Every 48 beats Harmonize lands on the line (the
 * downbeat); the 8 beats before it are its warning, when the song stops rotating: the voice that sang the turn
 * before keeps singing ({@link #upcoming}).
 *
 * <p><b>The attacks</b> are notes in the lines ({@link #plan}): the singer's own part at full rate and, for each
 * broken mask, that mask's part at half rate. Every attack lands on a beat, and none is telegraphed for less than
 * half a beat. Own parts, in beats from the turn's line:
 * <ul>
 *   <li>Alto: a Homing Note forms on beats 0, 2, 4 and 6 (it flies a beat later and bursts two beats after that).</li>
 *   <li>Tenor: inhales on 0 and 4; the Sweeping Wave leaves on 1 and 5.</li>
 *   <li>Bass: Ground Ripples on 0 and 4 (the floor darkens half a beat ahead); the Bass Drop rises on 5 and lands on
 *       7.</li>
 * </ul>
 * Borrowed at half rate: notes on 2 and 6; one wave, leaving on 1; one ripple, on 0, and the Bass Drop every other
 * turn the borrower sings. A turn that starts on a Harmonize downbeat skips its first beat's attacks.
 */
public final class UnsungSong {
    /** An attack of the choir. */
    public enum Part { NOTE, WAVE, RIPPLE, DROP }

    /**
     * One attack in a turn: which, whose part it is ({@code of}, the voice it belongs to; the singer performs it), and
     * the tick its telegraph starts, counted from the turn's line (negative: before the line).
     */
    public record Cue(int tick, Part part, Voice of) {
        /** The tick it lands, from the turn's line. */
        public int lands() {
            return tick + tell(part);
        }
    }

    /** How long each part is telegraphed before it lands. */
    public static int tell(Part part) {
        return switch (part) {
            case NOTE -> UnsungMoves.NOTE_FORM + UnsungMoves.NOTE_FLIGHT;
            case WAVE -> UnsungMoves.WAVE_INHALE;
            case RIPPLE -> UnsungMoves.RIPPLE_TELL;
            case DROP -> UnsungMoves.DROP_TELL;
        };
    }

    private UnsungSong() {
    }

    // ------------------------------------------------------------------ the clock

    /**
     * The first tick of the fight's first turn for an awakening at {@code awakeTick}: the first 8-beat line of the
     * Vesper clock at least {@value UnsungMoves#INTRO_BEATS} beats after the first beat at or after the awakening.
     */
    public static long fightStart(long awakeTick) {
        long firstBeat = Math.floorDiv(awakeTick + BEAT - 1, BEAT);
        long earliest = firstBeat + INTRO_BEATS;
        long line = Math.floorDiv(earliest + TURN_BEATS - 1, TURN_BEATS) * TURN_BEATS;
        return line * BEAT;
    }

    /**
     * {@link #fightStart} for an awakening whose wake sounds are over {@code soundsOver} ticks after it: the opener's first word is
     * a beat after the Alto's lift, which is {@value UnsungMoves#INTRO_BEATS} beats before the fight's first turn, and it must not
     * sound before they are over, so a lift that would come sooner takes one more turn (8 beats) first.
     */
    public static long fightStartAfterWake(long awakeTick, long soundsOver) {
        long start = fightStart(awakeTick);
        while (start - (long) INTRO_BEATS * BEAT + BEAT < awakeTick + soundsOver) {
            start += UnsungMoves.TURN_TICKS;
        }
        return start;
    }

    /** The fight's beat at {@code tick} (beat 0 starts at {@code fightStart}; negative in the intro). */
    public static long fightBeat(long tick, long fightStart) {
        return Math.floorDiv(tick - fightStart, BEAT);
    }

    /** True on the first tick of a fight beat. */
    public static boolean onBeat(long tick, long fightStart) {
        return Math.floorMod(tick - fightStart, BEAT) == 0;
    }

    /** True if fight beat {@code beat} starts a turn (a line). */
    public static boolean line(long beat) {
        return Math.floorMod(beat, TURN_BEATS) == 0;
    }

    /** Where {@code beat} falls in its Harmonize cycle, 0 to 47. */
    public static int inCycle(long beat) {
        return (int) Math.floorMod(beat, CYCLE_BEATS);
    }

    /** True through the 8 beats of a Harmonize warning. */
    public static boolean warning(long beat) {
        return beat >= 0 && inCycle(beat) >= CYCLE_BEATS - WARNING_BEATS;
    }

    /** True on the line a warning starts (the song stops rotating). */
    public static boolean warningLine(long beat) {
        return beat >= 0 && inCycle(beat) == CYCLE_BEATS - WARNING_BEATS;
    }

    /** True on the line Harmonize lands (beats 48, 96, ...). */
    public static boolean downbeat(long beat) {
        return beat > 0 && inCycle(beat) == 0;
    }

    /** The chord of the turn holding {@code beat}: 0 D minor, 1 B flat, 2 G minor, 3 A. */
    public static int chord(long beat) {
        return (int) Math.floorMod(Math.floorDiv(beat, TURN_BEATS), CHORDS);
    }

    // ------------------------------------------------------------------ the rotation

    /**
     * The voice that sings after {@code previous} among the {@code living}, in the order Alto, Tenor, Bass, Alto; the
     * first living voice when nobody sang yet; the only one left sings again; null when none is left.
     */
    public static @Nullable Voice next(@Nullable Voice previous, Set<Voice> living) {
        if (living.isEmpty()) {
            return null;
        }
        Voice[] all = Voice.values();
        int from = previous == null ? -1 : previous.ordinal();
        for (int i = 1; i <= all.length; i++) {
            Voice v = all[Math.floorMod(from + i, all.length)];
            if (living.contains(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Who sings the turn starting on the line {@code lineBeat}, given who sang the turn before ({@code lastSung},
     * null before the first): on a warning's line the song stops rotating and the last singer carries on, if it is
     * still whole; otherwise the song passes on.
     */
    public static @Nullable Voice upcoming(@Nullable Voice lastSung, Set<Voice> living, long lineBeat) {
        if (warningLine(lineBeat) && lastSung != null && living.contains(lastSung)) {
            return lastSung;
        }
        return next(lastSung, living);
    }

    // ------------------------------------------------------------------ the attacks

    /**
     * The attacks of a turn sung by {@code singer}, sorted by the tick they start: its own part and, for each broken
     * mask, that mask's part at half rate. {@code turnsSung} counts the singer's turns before this one (the borrowed
     * Bass Drop comes on its even turns); {@code afterDownbeat} drops the attacks of the turn's first beat (Harmonize
     * lands on it).
     */
    public static List<Cue> plan(Voice singer, Set<Voice> living, int turnsSung, boolean afterDownbeat) {
        List<Cue> cues = new ArrayList<>();
        own(singer, cues);
        for (Voice broken : EnumSet.complementOf(withSinger(living, singer))) {
            borrowed(broken, turnsSung, cues);
        }
        if (afterDownbeat) {
            cues.removeIf(c -> c.tick() < BEAT);
        }
        cues.sort(Comparator.comparingInt(Cue::tick).thenComparing(Cue::part));
        return cues;
    }

    private static EnumSet<Voice> withSinger(Set<Voice> living, Voice singer) {
        EnumSet<Voice> s = EnumSet.noneOf(Voice.class);
        s.addAll(living);
        s.add(singer);
        return s;
    }

    private static void own(Voice v, List<Cue> out) {
        switch (v) {
            case ALTO -> {
                for (int b = 0; b < TURN_BEATS; b += 2) {
                    out.add(new Cue(b * BEAT, Part.NOTE, v));
                }
            }
            case TENOR -> {
                out.add(new Cue(0, Part.WAVE, v));
                out.add(new Cue(4 * BEAT, Part.WAVE, v));
            }
            case BASS -> {
                out.add(new Cue(-UnsungMoves.RIPPLE_TELL, Part.RIPPLE, v));
                out.add(new Cue(4 * BEAT - UnsungMoves.RIPPLE_TELL, Part.RIPPLE, v));
                out.add(new Cue(5 * BEAT, Part.DROP, v));
            }
        }
    }

    private static void borrowed(Voice v, int turnsSung, List<Cue> out) {
        switch (v) {
            case ALTO -> {
                out.add(new Cue(2 * BEAT, Part.NOTE, v));
                out.add(new Cue(6 * BEAT, Part.NOTE, v));
            }
            case TENOR -> out.add(new Cue(0, Part.WAVE, v));
            case BASS -> {
                out.add(new Cue(-UnsungMoves.RIPPLE_TELL, Part.RIPPLE, v));
                if (turnsSung % 2 == 0) {
                    out.add(new Cue(5 * BEAT, Part.DROP, v));
                }
            }
        }
    }
}
