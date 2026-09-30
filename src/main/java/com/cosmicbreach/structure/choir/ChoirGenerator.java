package com.cosmicbreach.structure.choir;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Choir Floor's tunes (GDD 6.3): a melodic Markov chain over the eight pads (scale degrees, D4 up to F#5)
 * that prefers steps of one or two degrees ({@link #INTERVAL}: 70% of moves) over leaps, never sounds one pad
 * three times running, and ends every phrase on the home note. Phrases are drawn backward from the home note
 * (the chain's moves are symmetric, so the tune reads the same forward), then given their rhythm by
 * {@link ChoirRules#gapFor}, the round's eighth-note pair, ghost note and, when two or more players woke the floor,
 * its chords. A whole song (three rounds) sounds every pad at least once by round 3. Everything comes from the
 * seed: one room, one tune. Pure; a song takes microseconds.
 */
public final class ChoirGenerator {
    /**
     * P(|interval| = d scale degrees), d = 0 to 7: repeats 8%, steps (1 or 2) 65%, leaps 27%. Near the ends of the
     * scale some leaps have no pad to land on, which lifts the steps' share of the moves actually drawn to about 70%.
     */
    static final double[] INTERVAL = {0.08, 0.41, 0.24, 0.11, 0.07, 0.05, 0.03, 0.01};
    private static final int PHRASE_TRIES = 400;
    private static final int COVER_TRIES = 64;

    private ChoirGenerator() {
    }

    /** The song of a room: its three rounds, solo or co-op (with chords). */
    public static ChoirSong song(long seed, ChoirDifficulty difficulty, boolean coop) {
        Random r = new Random(mix(seed, difficulty.ordinal(), coop));
        ChoirPhrase[] rounds = new ChoirPhrase[ChoirRules.ROUNDS];
        rounds[0] = phrase(r, difficulty, 0, coop);
        rounds[1] = phrase(r, difficulty, 1, coop);
        int earlier = rounds[0].padMask() | rounds[1].padMask();
        for (int t = 0; t < COVER_TRIES; t++) {
            ChoirPhrase last = phrase(r, difficulty, 2, coop);
            if ((earlier | last.padMask()) == 0xFF) {
                rounds[2] = last;
                return new ChoirSong(rounds, coop);
            }
        }
        rounds[2] = scaleDown(r, difficulty, coop);
        return new ChoirSong(rounds, coop);
    }

    private static long mix(long seed, int difficulty, boolean coop) {
        long h = seed * 0x9E3779B97F4A7C15L + difficulty * 0x632BE59BD9B4E019L + (coop ? 0x5DEECE66DL : 0x2545F491L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    /** One round's phrase, fitting every rule of the round (or, after many tries, a plain one that does). */
    static ChoirPhrase phrase(Random r, ChoirDifficulty difficulty, int round, boolean coop) {
        int n = difficulty.notes(round);
        for (int t = 0; t < PHRASE_TRIES; t++) {
            ChoirPhrase p = dress(r, melody(r, n), difficulty, round, coop);
            if (p != null) {
                return p;
            }
        }
        return fallback(r, difficulty, round, coop);
    }

    /** {@code n} notes drawn backward from the home note. */
    static int[] melody(Random r, int n) {
        int[] m = new int[n];
        m[n - 1] = ChoirRules.HOME;
        for (int i = n - 2; i >= 0; i--) {
            boolean noRepeat = i + 2 < n && m[i + 1] == m[i + 2];
            m[i] = neighbour(r, m[i + 1], noRepeat);
        }
        return m;
    }

    /**
     * One move of the chain from {@code from}: an interval drawn by {@link #INTERVAL} (a distance with no pad on
     * either side drops out), then up or down at even odds where both exist.
     */
    static int neighbour(Random r, int from, boolean noRepeat) {
        double total = 0;
        double[] w = new double[ChoirRules.PADS];
        for (int v = 0; v < ChoirRules.PADS; v++) {
            int d = Math.abs(v - from);
            if (d == 0 && noRepeat) {
                continue;
            }
            int sides = d == 0 ? 1 : (from - d >= 0 ? 1 : 0) + (from + d < ChoirRules.PADS ? 1 : 0);
            w[v] = INTERVAL[d] / sides;
            total += w[v];
        }
        double x = r.nextDouble() * total;
        for (int v = 0; v < ChoirRules.PADS; v++) {
            x -= w[v];
            if (x < 0 && w[v] > 0) {
                return v;
            }
        }
        for (int v = ChoirRules.PADS - 1; v >= 0; v--) {
            if (w[v] > 0) {
                return v;
            }
        }
        return from;
    }

    /** Gives a melody its rhythm, pair, ghost and chords, or null if it can't fit the round's rules. */
    static ChoirPhrase dress(Random r, int[] m, ChoirDifficulty difficulty, int round, boolean coop) {
        int n = m.length;
        int[] gaps = new int[n];
        for (int i = 1; i < n; i++) {
            int d = ChoirRules.ringDistance(m[i - 1], m[i]);
            if (difficulty.onePerBeat(round) && d > 2) {
                return null;
            }
            gaps[i] = ChoirRules.gapFor(d);
        }
        // the eighth-note pair: two neighbouring pads, half a beat apart
        List<Integer> pairAt = new ArrayList<>();
        for (int k = 0; k < difficulty.pairs(round); k++) {
            List<Integer> can = new ArrayList<>();
            for (int i = 1; i < n; i++) {
                if (gaps[i] == ChoirRules.BEAT && ChoirRules.ringDistance(m[i - 1], m[i]) == 1
                        && !pairAt.contains(i) && !pairAt.contains(i - 1) && !pairAt.contains(i + 1)) {
                    can.add(i);
                }
            }
            if (can.isEmpty()) {
                return null;
            }
            int i = can.get(r.nextInt(can.size()));
            pairAt.add(i);
            gaps[i] = ChoirRules.EIGHTH;
        }
        int[] onsets = new int[n];
        for (int i = 1; i < n; i++) {
            onsets[i] = onsets[i - 1] + gaps[i];
        }
        int ghost = -1;
        if (difficulty.ghost(round)) {
            List<Integer> can = new ArrayList<>();
            for (int i = 1; i < n - 1; i++) {
                if (!inPair(pairAt, i)) {
                    can.add(i);
                }
            }
            if (can.isEmpty()) {
                return null;
            }
            ghost = can.get(r.nextInt(can.size()));
        }
        int[][] pads = new int[n][];
        for (int i = 0; i < n; i++) {
            pads[i] = new int[] {m[i]};
        }
        int chords = difficulty.chords(round, coop);
        if (chords > 0) {
            List<Integer> can = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (i != ghost && !inPair(pairAt, i)) {
                    can.add(i);
                }
            }
            if (can.size() < chords) {
                return null;
            }
            for (int k = 0; k < chords; k++) {
                if (can.isEmpty()) {
                    return null;
                }
                int i = can.remove(r.nextInt(can.size()));
                can.remove(Integer.valueOf(i - 1)); // no two chords side by side
                can.remove(Integer.valueOf(i + 1));
                int h = harmony(r, m, i);
                if (h < 0) {
                    return null;
                }
                pads[i] = new int[] {m[i], h};
            }
        }
        return new ChoirPhrase(pads, onsets, ghost);
    }

    private static boolean inPair(List<Integer> pairAt, int i) {
        return pairAt.contains(i) || pairAt.contains(i + 1);
    }

    /**
     * A chord's second pad: a fourth, fifth or sixth from the melody (three or four scale degrees), so it lies
     * three or four pads round the ring (too far for one player to sound both), and never the melody's pad just
     * before or after it (so no pad sounds three times running); -1 if there is none.
     */
    static int harmony(Random r, int[] m, int i) {
        List<Integer> can = new ArrayList<>(4);
        for (int d : new int[] {3, 4, -3, -4}) {
            int h = m[i] + d;
            boolean nearby = i > 0 && m[i - 1] == h || i + 1 < m.length && m[i + 1] == h;
            if (h >= 0 && h < ChoirRules.PADS && !nearby) {
                can.add(h);
            }
        }
        return can.isEmpty() ? -1 : can.get(r.nextInt(can.size()));
    }

    /** A plain phrase that fits the round: a short step-wise descent home. */
    static ChoirPhrase fallback(Random r, ChoirDifficulty difficulty, int round, boolean coop) {
        int[] m = switch (round) {
            case 0 -> new int[] {2, 1, 1, 0};
            case 1 -> new int[] {4, 3, 2, 1, 1, 0};
            default -> new int[] {7, 6, 5, 4, 3, 2, 1, 0};
        };
        for (int t = 0; t < PHRASE_TRIES; t++) {
            ChoirPhrase p = dress(r, m, difficulty, round, coop);
            if (p != null) {
                return p;
            }
        }
        throw new IllegalStateException("no plain phrase fits round " + round);
    }

    /** Round 3 as the scale walking down from the top pad: it sounds every pad. */
    static ChoirPhrase scaleDown(Random r, ChoirDifficulty difficulty, boolean coop) {
        return fallback(r, difficulty, 2, coop);
    }
}
