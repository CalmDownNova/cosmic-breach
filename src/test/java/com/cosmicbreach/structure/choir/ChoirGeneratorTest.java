package com.cosmicbreach.structure.choir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/**
 * GDD 6.3's tunes: 10,000 songs per difficulty and mode, every phrase fitting its round (notes, rhythm, the eighth
 * pair, the ghost, chords only in co-op), ending on the home note, never one pad three times running, every pad
 * sounded by round 3, and steps preferred over leaps about 70% of the time.
 */
class ChoirGeneratorTest {
    private static final int SONGS = 10_000;

    @Test
    void tenThousandSongsPerDifficultyAndModeKeepEveryRule() {
        long t0 = System.nanoTime();
        int songs = 0;
        int moves = 0;
        int steps = 0;
        for (ChoirDifficulty d : ChoirDifficulty.values()) {
            for (boolean coop : new boolean[] {false, true}) {
                for (int s = 0; s < SONGS; s++) {
                    long seed = s * 7919L + d.ordinal() * 1_000_003L + (coop ? 17 : 0);
                    ChoirSong song = ChoirGenerator.song(seed, d, coop);
                    songs++;
                    assertEquals(coop, song.coop());
                    assertEquals(0xFF, song.padMask(), () -> "every pad by round 3: " + describe(song));
                    for (int round = 0; round < ChoirRules.ROUNDS; round++) {
                        ChoirPhrase p = song.phrase(round);
                        String bad = broken(p, d, round, coop);
                        if (bad != null) {
                            fail(bad + " in round " + (round + 1) + " of " + d + (coop ? " co-op" : " solo") + ": " + p);
                        }
                        for (int i = 1; i < p.size(); i++) {
                            int interval = Math.abs(p.melody(i) - p.melody(i - 1));
                            moves++;
                            steps += interval == 1 || interval == 2 ? 1 : 0;
                        }
                    }
                }
            }
        }
        double share = steps / (double) moves;
        long micros = (System.nanoTime() - t0) / 1000;
        System.out.printf("choir generator: %d songs, %.1f us a song, steps %.1f%% of %d moves%n", songs, micros / (double) songs,
                share * 100, moves);
        assertTrue(share > 0.62 && share < 0.82, "steps should be about 70% of moves, were " + share);
    }

    /** The first rule {@code p} breaks for its round, or null. */
    static String broken(ChoirPhrase p, ChoirDifficulty d, int round, boolean coop) {
        int n = p.size();
        if (n != d.notes(round)) {
            return "notes " + n;
        }
        if (p.onsets()[0] != 0) {
            return "first onset";
        }
        if (p.melody(n - 1) != ChoirRules.HOME) {
            return "ends off the home note";
        }
        int pairs = 0;
        for (int i = 1; i < n; i++) {
            int gap = p.onsets()[i] - p.onsets()[i - 1];
            int dist = ChoirRules.ringDistance(p.melody(i - 1), p.melody(i));
            if (gap == ChoirRules.EIGHTH) {
                pairs++;
                if (dist != 1) {
                    return "eighth pair not between neighbours";
                }
                if (i >= 2 && p.onsets()[i - 1] - p.onsets()[i - 2] == ChoirRules.EIGHTH) {
                    return "pairs run together";
                }
            } else if (gap != ChoirRules.gapFor(dist)) {
                return "gap " + gap + " for a move of " + dist;
            }
            if (d.onePerBeat(round) && gap != ChoirRules.BEAT) {
                return "round 1 not one per beat";
            }
        }
        if (pairs != d.pairs(round)) {
            return "pairs " + pairs;
        }
        for (int pad = 0; pad < ChoirRules.PADS; pad++) {
            for (int i = 2; i < n; i++) {
                if (p.sounds(i, pad) && p.sounds(i - 1, pad) && p.sounds(i - 2, pad)) {
                    return "pad " + pad + " three times running";
                }
            }
        }
        if (d.ghost(round)) {
            int g = p.ghost();
            if (g <= 0 || g >= n - 1 || p.chord(g)) {
                return "ghost " + g;
            }
        } else if (p.ghost() != -1) {
            return "a ghost outside round 3";
        }
        if (!coop && p.chords() > 0) {
            return "a chord in a solo phrase";
        }
        if (coop && p.chords() != d.chords(round, true)) {
            return "chords " + p.chords();
        }
        for (int i = 0; i < n; i++) {
            if (p.chord(i)) {
                int[] c = p.pads()[i];
                if (c[0] == c[1] || ChoirRules.ringDistance(c[0], c[1]) < 3) {
                    return "a chord one player could sound alone";
                }
                if (i > 0 && p.chord(i - 1)) {
                    return "chords side by side";
                }
            }
            for (int pad : p.pads()[i]) {
                if (pad < 0 || pad >= ChoirRules.PADS) {
                    return "pad " + pad;
                }
            }
        }
        return null;
    }

    @Test
    void oneRoomOneTune() {
        for (long seed = 0; seed < 200; seed++) {
            ChoirSong a = ChoirGenerator.song(seed, ChoirDifficulty.CRYPT, false);
            ChoirSong b = ChoirGenerator.song(seed, ChoirDifficulty.CRYPT, false);
            for (int r = 0; r < 3; r++) {
                assertEquals(a.phrase(r), b.phrase(r));
            }
        }
    }

    @Test
    void phrasesSurviveTheirSyncEncoding() {
        for (long seed = 0; seed < 500; seed++) {
            ChoirSong song = ChoirGenerator.song(seed, ChoirDifficulty.SANCTUM, seed % 2 == 0);
            for (int r = 0; r < 3; r++) {
                ChoirPhrase p = song.phrase(r);
                ChoirPhrase back = ChoirPhrase.decode(p.encode());
                assertNotNull(back);
                assertEquals(p, back);
            }
        }
    }

    @Test
    void theSanctumAddsAPairToTheLastRound() {
        ChoirSong song = ChoirGenerator.song(42L, ChoirDifficulty.SANCTUM, false);
        assertEquals(1, song.phrase(2).pairs());
        assertEquals(0, ChoirGenerator.song(42L, ChoirDifficulty.CRYPT, false).phrase(2).pairs());
    }

    private static String describe(ChoirSong song) {
        return song.phrase(0) + " | " + song.phrase(1) + " | " + song.phrase(2);
    }
}
