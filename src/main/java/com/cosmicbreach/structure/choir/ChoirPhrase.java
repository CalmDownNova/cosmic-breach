package com.cosmicbreach.structure.choir;

import java.util.Arrays;

/**
 * One round's phrase: note {@code i} sounds {@code pads[i]} (one pad, or two for a chord) at {@code onsets[i]}
 * ticks after the phrase starts (the first at 0). {@code ghost} is the index of the note played dim and only once
 * (round 3), or -1. Pure and immutable (the arrays are never handed out for writing).
 */
public record ChoirPhrase(int[][] pads, int[] onsets, int ghost) {
    public int size() {
        return onsets.length;
    }

    /** Ticks from the phrase's start to the first beat at least a beat after its last note (always whole beats). */
    public int length() {
        return (int) ChoirRules.nextBeat(onsets[onsets.length - 1] + ChoirRules.BEAT);
    }

    /** The melody pad of note {@code i} (a chord's first pad). */
    public int melody(int i) {
        return pads[i][0];
    }

    public boolean chord(int i) {
        return pads[i].length > 1;
    }

    public int chords() {
        int n = 0;
        for (int[] p : pads) {
            n += p.length > 1 ? 1 : 0;
        }
        return n;
    }

    /** Indexes of the notes {@code EIGHTH} ticks after the one before. */
    public int pairs() {
        int n = 0;
        for (int i = 1; i < onsets.length; i++) {
            n += onsets[i] - onsets[i - 1] == ChoirRules.EIGHTH ? 1 : 0;
        }
        return n;
    }

    /** True if note {@code i} sounds pad {@code pad}. */
    public boolean sounds(int i, int pad) {
        for (int p : pads[i]) {
            if (p == pad) {
                return true;
            }
        }
        return false;
    }

    /** A bit per pad this phrase sounds. */
    public int padMask() {
        int mask = 0;
        for (int[] p : pads) {
            for (int q : p) {
                mask |= 1 << q;
            }
        }
        return mask;
    }

    /** Flattened for syncing: {size, ghost, then per note its onset, pad count and pads}. */
    public int[] encode() {
        int[] out = new int[2 + onsets.length * 4];
        int k = 0;
        out[k++] = onsets.length;
        out[k++] = ghost;
        for (int i = 0; i < onsets.length; i++) {
            out[k++] = onsets[i];
            out[k++] = pads[i].length;
            out[k++] = pads[i][0];
            out[k++] = pads[i].length > 1 ? pads[i][1] : -1;
        }
        return out;
    }

    /** The phrase {@link #encode} wrote, or null for an empty or broken array. */
    public static ChoirPhrase decode(int[] in) {
        if (in == null || in.length < 2 || in[0] <= 0 || in.length != 2 + in[0] * 4) {
            return null;
        }
        int n = in[0];
        int[][] pads = new int[n][];
        int[] onsets = new int[n];
        for (int i = 0; i < n; i++) {
            int b = 2 + i * 4;
            onsets[i] = in[b];
            pads[i] = in[b + 1] > 1 ? new int[] {in[b + 2], in[b + 3]} : new int[] {in[b + 2]};
        }
        return new ChoirPhrase(pads, onsets, in[1]);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChoirPhrase p && p.ghost == ghost && Arrays.equals(p.onsets, onsets) && Arrays.deepEquals(p.pads, pads);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(onsets) + Arrays.deepHashCode(pads) + ghost;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < onsets.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(onsets[i]).append(':');
            sb.append(ChoirRules.NOTE_NAMES[pads[i][0]]);
            if (pads[i].length > 1) {
                sb.append('+').append(ChoirRules.NOTE_NAMES[pads[i][1]]);
            }
            if (i == ghost) {
                sb.append("(ghost)");
            }
        }
        return sb.toString();
    }
}
