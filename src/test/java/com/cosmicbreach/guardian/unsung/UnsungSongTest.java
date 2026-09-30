package com.cosmicbreach.guardian.unsung;

import static com.cosmicbreach.guardian.unsung.UnsungMoves.BEAT;
import static com.cosmicbreach.guardian.unsung.Voice.ALTO;
import static com.cosmicbreach.guardian.unsung.Voice.BASS;
import static com.cosmicbreach.guardian.unsung.Voice.TENOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.unsung.UnsungSong.Cue;
import com.cosmicbreach.guardian.unsung.UnsungSong.Part;
import com.cosmicbreach.world.VesperClock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The Unsung's song (Unsung design v1): the clock, the rotation on the bar line, the attacks on the beats, the broken-mask rules. */
class UnsungSongTest {
    private static final Set<Voice> ALL = EnumSet.allOf(Voice.class);

    // ------------------------------------------------------------------ the clock

    @Test
    void theFightStartsOnAnEightBeatLineOfVesperAfterAtLeastEightBeats() {
        for (long awake = 1_000; awake < 1_400; awake += 7) {
            long start = UnsungSong.fightStart(awake);
            assertEquals(0, Math.floorMod(start, VesperClock.TICKS_PER_BEAT), "on a beat");
            assertEquals(0, Math.floorMod(VesperClock.beat(start), 8), "on every other bar line of Vesper's clock");
            assertEquals(0, VesperClock.beatInBar(start), "so on a bar line");
            long firstBeat = Math.floorDiv(awake + BEAT - 1, BEAT) * BEAT;
            assertTrue(start - firstBeat >= 8 * BEAT, "at least 8 beats of intro, awake " + awake);
            assertTrue(start - firstBeat < 16 * BEAT, "at most 15, awake " + awake);
        }
    }

    @Test
    void harmonizeLandsEveryFortyEightBeatsAfterEightBeatsOfWarning() {
        List<Long> downbeats = new ArrayList<>();
        List<Long> warningLines = new ArrayList<>();
        int warningBeats = 0;
        for (long b = 0; b < 150; b++) {
            if (UnsungSong.downbeat(b)) {
                downbeats.add(b);
            }
            if (UnsungSong.warningLine(b)) {
                warningLines.add(b);
            }
            if (UnsungSong.warning(b)) {
                warningBeats++;
            }
        }
        assertEquals(List.of(48L, 96L, 144L), downbeats);
        assertEquals(List.of(40L, 88L, 136L), warningLines);
        assertEquals(3 * 8, warningBeats, "the last 8 beats of each cycle warn");
        assertFalse(UnsungSong.downbeat(0), "the fight doesn't open with a Harmonize");
        for (long b : warningLines) {
            assertTrue(UnsungSong.line(b), "the warning starts on a line");
            assertEquals(8, UnsungSong.downbeat(b + 8) ? 8 : -1, "and the downbeat is 8 beats later");
        }
    }

    // ------------------------------------------------------------------ the rotation

    /** Plays the song's lines like the Unsung does and lists who sang each turn. */
    private static List<Voice> singers(Set<Voice> living, int turns) {
        List<Voice> out = new ArrayList<>();
        Voice last = null;
        for (int t = 0; t < turns; t++) {
            Voice v = UnsungSong.upcoming(last, living, t * 8L);
            out.add(v);
            last = v;
        }
        return out;
    }

    @Test
    void theSongPassesAltoTenorBassOnTheLineAndStopsRotatingForTheWarning() {
        List<Voice> sung = singers(ALL, 12);
        // turns start on beats 0, 8, ..., 40 is the warning (the singer carries on), 48 the downbeat
        assertEquals(List.of(ALTO, TENOR, BASS, ALTO, TENOR, TENOR, BASS, ALTO, TENOR, BASS, ALTO, ALTO), sung);
    }

    @Test
    void theSongSkipsABrokenMaskAndTheLastOneSingsEveryTurn() {
        assertEquals(List.of(ALTO, BASS, ALTO, BASS, ALTO, ALTO, BASS), singers(EnumSet.of(ALTO, BASS), 7));
        assertEquals(List.of(TENOR, TENOR, TENOR, TENOR), singers(EnumSet.of(TENOR), 4));
        assertEquals(BASS, UnsungSong.next(TENOR, EnumSet.of(ALTO, BASS)), "a singer that broke passes the song on");
        assertEquals(ALTO, UnsungSong.next(null, ALL), "the Alto opens");
        assertNull(UnsungSong.next(ALTO, EnumSet.noneOf(Voice.class)));
        assertEquals(TENOR, UnsungSong.upcoming(ALTO, EnumSet.of(TENOR, BASS), 40),
                "a warning whose singer broke passes to the next");
    }

    @Test
    void theChordsCycleOncePerTurn() {
        assertEquals(0, UnsungSong.chord(0));
        assertEquals(0, UnsungSong.chord(7));
        assertEquals(1, UnsungSong.chord(8));
        assertEquals(3, UnsungSong.chord(31));
        assertEquals(0, UnsungSong.chord(32));
    }

    // ------------------------------------------------------------------ the attacks

    private static List<String> beats(List<Cue> cues, Part part) {
        return cues.stream().filter(c -> c.part() == part).map(c -> c.lands() / (double) BEAT)
                .map(d -> d % 1 == 0 ? Integer.toString(d.intValue()) : Double.toString(d)).collect(Collectors.toList());
    }

    @Test
    void eachMaskAttacksOnItsBeats() {
        List<Cue> alto = UnsungSong.plan(ALTO, ALL, 0, false);
        assertEquals(List.of(0, 24, 48, 72), alto.stream().map(Cue::tick).toList(), "a note forms on beats 0, 2, 4, 6");
        assertEquals(List.of("3", "5", "7", "9"), beats(alto, Part.NOTE), "and bursts three beats later");

        List<Cue> tenor = UnsungSong.plan(TENOR, ALL, 0, false);
        assertEquals(List.of("1", "5"), beats(tenor, Part.WAVE), "the wave leaves on beats 1 and 5");
        assertEquals(List.of(0, 48), tenor.stream().map(Cue::tick).toList(), "after a beat's inhale");

        List<Cue> bass = UnsungSong.plan(BASS, ALL, 0, false);
        assertEquals(List.of("0", "4"), beats(bass, Part.RIPPLE), "ripples on beats 1 and 5 of the bar pair");
        assertEquals(List.of("7"), beats(bass, Part.DROP), "one Bass Drop a turn, landing on beat 7");
        assertEquals(-UnsungMoves.HALF_BEAT, bass.get(0).tick(), "the floor darkens half a beat ahead");
    }

    @Test
    void everyAttackLandsOnABeatAndIsTelegraphedAtLeastHalfABeat() {
        for (Voice singer : Voice.values()) {
            for (Set<Voice> living : List.of(ALL, EnumSet.of(singer), EnumSet.complementOf(EnumSet.of(next(singer))))) {
                for (int turns = 0; turns < 3; turns++) {
                    for (boolean after : new boolean[] {false, true}) {
                        for (Cue c : UnsungSong.plan(singer, living, turns, after)) {
                            assertEquals(0, Math.floorMod(c.lands(), BEAT), singer + " " + c + " lands on a beat");
                            assertTrue(c.lands() - c.tick() >= UnsungMoves.HALF_BEAT, c + " is telegraphed half a beat or more");
                            assertTrue(c.lands() - c.tick() == UnsungSong.tell(c.part()));
                        }
                    }
                }
            }
        }
    }

    private static Voice next(Voice v) {
        return Voice.values()[(v.ordinal() + 1) % 3];
    }

    @Test
    void withTwoLeftEachAlsoUsesTheBrokenMasksAttackAtHalfRate() {
        Set<Voice> noTenor = EnumSet.of(ALTO, BASS);
        List<Cue> alto = UnsungSong.plan(ALTO, noTenor, 0, false);
        assertEquals(4, alto.stream().filter(c -> c.part() == Part.NOTE).count(), "its own notes at full rate");
        assertEquals(List.of("1"), beats(alto, Part.WAVE), "and one wave a turn instead of two");

        Set<Voice> noAlto = EnumSet.of(TENOR, BASS);
        assertEquals(List.of("5", "9"), beats(UnsungSong.plan(TENOR, noAlto, 0, false), Part.NOTE), "two notes instead of four");
        assertEquals(List.of("5", "9"), beats(UnsungSong.plan(BASS, noAlto, 0, false), Part.NOTE));

        Set<Voice> noBass = EnumSet.of(ALTO, TENOR);
        List<Cue> first = UnsungSong.plan(TENOR, noBass, 0, false);
        List<Cue> second = UnsungSong.plan(TENOR, noBass, 1, false);
        assertEquals(List.of("0"), beats(first, Part.RIPPLE), "one ripple instead of two");
        assertEquals(List.of("7"), beats(first, Part.DROP), "a borrowed Bass Drop");
        assertTrue(beats(second, Part.DROP).isEmpty(), "every other turn");
    }

    @Test
    void theLastMaskSingsAllThreeParts() {
        for (Voice last : Voice.values()) {
            Set<Part> parts = UnsungSong.plan(last, EnumSet.of(last), 0, false).stream().map(Cue::part)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Part.class)));
            assertEquals(EnumSet.allOf(Part.class), parts, last + " alone sings the notes, the wave, the ripples and the drop");
        }
        List<Cue> bass = UnsungSong.plan(BASS, EnumSet.of(BASS), 1, false);
        assertEquals(1, bass.stream().filter(c -> c.part() == Part.DROP).count(), "its own drop every turn");
    }

    @Test
    void theTurnAfterADownbeatLetsHarmonizeHaveTheFirstBeat() {
        for (Voice singer : Voice.values()) {
            for (Cue c : UnsungSong.plan(singer, EnumSet.of(singer), 0, true)) {
                assertTrue(c.tick() >= BEAT, c + " starts after the first beat");
            }
        }
        assertEquals(3, UnsungSong.plan(ALTO, ALL, 0, true).size(), "the Alto's notes on 2, 4 and 6 only");
    }
}
