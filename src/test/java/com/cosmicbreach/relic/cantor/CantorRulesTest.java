package com.cosmicbreach.relic.cantor;

import com.cosmicbreach.relic.cantor.CantorRules.Note;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Umbra Cantor's pure rules against GDD 7.3: notes, chords, the triangle, the pulses, Cadence. */
class CantorRulesTest {
    private static Note note(int id, double x, double z, long placed) {
        return new Note(id, x, 64.0, z, placed);
    }

    @Test
    void notesWalkUpDMajorPentatonic() {
        int[] tones = new int[7];
        for (int i = 0; i < tones.length; i++) {
            tones[i] = CantorRules.tone(i);
            assertTrue(CantorRules.inScale(tones[i]), "note " + i);
        }
        assertArrayEquals(new int[] {0, 2, 4, 7, 9, 0, 2}, tones, "D, E, F#, A, B, then round again");
        assertEquals(1.0f, CantorRules.pitch(0), 1e-6f);
        assertEquals((float) Math.pow(2, 7 / 12.0), CantorRules.pitch(7), 1e-6f, "A5 over D5");
        for (int n : CantorRules.TRIAD) {
            assertTrue(CantorRules.inScale(n), "the triad's tones are the scale's");
        }
        assertArrayEquals(new int[] {0, 4, 7}, CantorRules.TRIAD, "D, F#, A: a major triad");
    }

    @Test
    void aPhraseStartsOverOnDWhenNoNoteIsLeft() {
        assertEquals(0, CantorRules.nextIndex(0, 0));
        assertEquals(3, CantorRules.nextIndex(3, 2), "two notes still sound: the phrase walks on");
        assertEquals(0, CantorRules.nextIndex(7, 0), "all faded or taken by a chord: D again");
        assertEquals(0, CantorRules.tone(CantorRules.nextIndex(4, 0)));
    }

    @Test
    void aNoteLivesAHundredTicks() {
        assertFalse(CantorRules.expired(1000, 1099));
        assertTrue(CantorRules.expired(1000, 1100));
    }

    @Test
    void threeNotesWithinEightBlocksOfEachOtherAreAChord() {
        Note a = note(1, 0, 0, 0);
        Note b = note(2, 8, 0, 1);
        Note c = note(3, 4, 6.9, 2);
        assertTrue(CantorRules.chord(a, b, c));
        assertFalse(CantorRules.chord(a, note(2, 8.01, 0, 1), c), "one pair a hair over 8 apart");
        assertFalse(CantorRules.chord(a, b, note(3, 4, 7.0, 2)), "the far corner more than 8 from both ends");
        assertTrue(CantorRules.chord(a, a, a), "three notes on one spot still ring");
    }

    @Test
    void theWidestTriangleWinsThenTheNewestPair() {
        Note placed = note(9, 0, 0, 50);
        List<Note> loose = List.of(note(1, 2, 0, 10), note(2, 0, 2, 20), note(3, 5, 0, 30), note(4, 0, 5, 40), note(5, 30, 0, 45));
        int[] pair = CantorRules.pick(placed, loose);
        assertArrayEquals(new int[] {2, 3}, pair, "notes 3 and 4 make the widest triangle; the far one is out of reach");
        List<Note> twins = List.of(note(1, 3, 0, 10), note(2, 0, 3, 20), note(3, 3, 0, 30), note(4, 0, 3, 40));
        assertArrayEquals(new int[] {2, 3}, CantorRules.pick(placed, twins), "equal areas: the newest pair");
        assertNull(CantorRules.pick(placed, List.of(note(1, 2, 0, 10))), "one loose note is no chord");
        assertNull(CantorRules.pick(placed, List.of(note(1, 2, 0, 10), note(2, 20, 0, 20))));
    }

    @Test
    void aPlayerKeepsSixLooseNotesAndTheOldestFades() {
        List<Note> loose = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            loose.add(note(i, i * 20, 0, 100 - i));
        }
        assertEquals(-1, CantorRules.evict(loose), "room for a sixth");
        loose.add(note(5, 200, 0, 7));
        assertEquals(5, CantorRules.evict(loose), "full: the one placed first goes");
    }

    @Test
    void theTriangleWithItsMarginIsTheChordsGround() {
        double[] xs = {0, 6, 0};
        double[] zs = {0, 0, 6};
        double[] ys = {64, 64, 64};
        assertTrue(CantorRules.inside(xs, ys, zs, 1.5, 64.0, 1.5), "inside");
        assertTrue(CantorRules.inside(xs, ys, zs, 3.0, 64.0, -0.9), "under an edge by less than a block");
        assertFalse(CantorRules.inside(xs, ys, zs, 3.0, 64.0, -1.1), "more than a block out");
        assertFalse(CantorRules.inside(xs, ys, zs, 4.0, 64.0, 4.0), "past the long edge by 1.41");
        assertTrue(CantorRules.inside(xs, ys, zs, 1.5, 62.6, 1.5), "1.4 under the notes");
        assertFalse(CantorRules.inside(xs, ys, zs, 1.5, 62.4, 1.5), "1.6 under");
        assertTrue(CantorRules.inside(xs, ys, zs, 1.5, 66.4, 1.5), "2.4 over");
        assertFalse(CantorRules.inside(xs, ys, zs, 1.5, 66.6, 1.5), "2.6 over");
        double[] row = {0, 3, 6};
        double[] flat = {0, 0, 0};
        assertTrue(CantorRules.inside(row, ys, flat, 4.0, 64.0, 0.8), "notes in a row make a strip");
        assertFalse(CantorRules.inside(row, ys, flat, 4.0, 64.0, 1.2));
    }

    @Test
    void aChordPulsesFourDamageEveryTwentyTicksFiveTimes() {
        int pulses = 0;
        for (int age = 0; age <= 120; age++) {
            if (CantorRules.pulses(age)) {
                pulses++;
                assertEquals(0, age % 20, "on the twentieth ticks");
            }
        }
        assertEquals(5, pulses, "20, 40, 60, 80, 100");
        assertFalse(CantorRules.pulses(0), "not the moment it forms");
        assertEquals(4.0, CantorRules.pulseDamage(1.0, 1.0), 1e-12, "zero stats, unlock tier");
        assertEquals(4.0 * 1.4, CantorRules.pulseDamage(1.4, 1.0), 1e-12, "the Cantor's scaling applies");
    }

    @Test
    void cadenceLoosesThreeShotsInTwelveTicks() {
        List<Integer> shots = new ArrayList<>();
        for (int t = 0; t < 30; t++) {
            if (CantorRules.cadenceShot(t)) {
                shots.add(t);
            }
        }
        assertEquals(List.of(0, 5, 10), shots);
        assertTrue(shots.get(2) - shots.get(0) < 12, "within 12 ticks");
        assertEquals(1, CantorRules.cadenceIndex(5));
        assertEquals(-1, CantorRules.cadenceIndex(6));
        assertArrayEquals(new double[] {-8.0, 0.0}, CantorRules.cadenceOffset(0, 8.0, 2.5));
        assertArrayEquals(new double[] {0.0, 2.5}, CantorRules.cadenceOffset(1, 8.0, 2.5), "the middle a little higher, so further");
        assertArrayEquals(new double[] {8.0, 0.0}, CantorRules.cadenceOffset(2, 8.0, 2.5));
    }
}
