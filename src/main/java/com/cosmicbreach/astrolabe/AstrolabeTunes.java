package com.cosmicbreach.astrolabe;

/**
 * What the Astrolabe plays (GDD 4.2: every bolt is a chime from a pentatonic scale, so any combo sounds like a phrase).
 * The scale is D major pentatonic; notes are semitones from D5, the chime's own pitch, and play at the pitch that
 * turns D5 into them. A loop of the chain is one bar: L1 and L2 are its two melody notes and the Triad its chord;
 * each L1 starts the next of four bars, so the chain plays a four-bar phrase. The Starfall plays the bar's second
 * note an octave down, the Parallax's decoy two high notes, the Constellation's beam an arpeggio up the chord, one
 * note a target. Pure: both sides count bars the same way from the same moves.
 */
public final class AstrolabeTunes {
    /** Semitones of D major pentatonic within an octave: D, E, F#, A, B. */
    private static final int[] SCALE = {0, 2, 4, 7, 9};
    /** Each bar: L1's note, L2's note, then the Triad's chord. */
    private static final int[][][] BARS = {
            {{-5}, {0}, {0, 4, 7}},     // A4, D5; D major
            {{-3}, {2}, {2, 7, 9}},     // B4, E5; E, A, B
            {{4}, {7}, {-3, 2, 4}},     // F#5, A5; B, E, F#
            {{2}, {-3}, {-5, 0, 2}},    // E5, B4; A, D, E
    };
    public static final int[] PARALLAX = {9, 12};
    public static final int[] ARPEGGIO = {-5, 0, 4, 7, 12};
    public static final int BARS_IN_PHRASE = BARS.length;

    private AstrolabeTunes() {
    }

    /** The playback pitch that turns the D5 chime into {@code semitones} from D5 (vanilla's 0.5 to 2 range). */
    public static float pitch(int semitones) {
        return (float) Math.max(0.5, Math.min(2.0, Math.pow(2.0, semitones / 12.0)));
    }

    /** True if {@code semitones} from D5 is in D major pentatonic. */
    public static boolean inScale(int semitones) {
        int s = Math.floorMod(semitones, 12);
        for (int n : SCALE) {
            if (n == s) {
                return true;
            }
        }
        return false;
    }

    /** L1's note in bar {@code bar}. */
    public static int first(int bar) {
        return BARS[Math.floorMod(bar, BARS.length)][0][0];
    }

    /** L2's note in bar {@code bar}. */
    public static int second(int bar) {
        return BARS[Math.floorMod(bar, BARS.length)][1][0];
    }

    /** The Triad's chord in bar {@code bar}. */
    public static int[] chord(int bar) {
        return BARS[Math.floorMod(bar, BARS.length)][2].clone();
    }

    /** The Starfall's note in bar {@code bar}: the bar's second note, an octave down when that stays in range. */
    public static int starfall(int bar) {
        int n = second(bar) - 12;
        return n < -12 ? second(bar) : n;
    }

    /** The Constellation's note for the {@code index}-th target (from 0). */
    public static int arpeggio(int index) {
        return ARPEGGIO[Math.max(0, Math.min(ARPEGGIO.length - 1, index))];
    }
}
