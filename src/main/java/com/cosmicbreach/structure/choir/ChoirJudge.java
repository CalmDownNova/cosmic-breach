package com.cosmicbreach.structure.choir;

import java.util.ArrayList;
import java.util.List;

/**
 * Judges one answer on the server's tick (GDD 6.3): note {@code i} is due at {@code start + onsets[i]}, and a
 * step counts when a player's feet enter one of its pads within {@code window} ticks of that tick, early or late,
 * plus that player's latency grace on the late side ({@link #within}). Notes are taken in order; a chord's pads
 * may come in either order (two players), each within the window.
 *
 * <p>What an entry is ({@link #enter}): a pad of the next note inside its window sounds it ({@link Verdict#HIT},
 * or {@link Verdict#DONE} for the last); the same pad outside the window, a pad of the note just played or the
 * note after, or a chord pad already sounded, is {@link Verdict#IGNORED} (a stumble or an early step: the window
 * judges the note itself); any other pad is {@link Verdict#WRONG}. A note whose window closes unsounded is a
 * missed beat ({@link #missed}). Pure.
 */
public final class ChoirJudge {
    public enum Verdict { HIT, DONE, IGNORED, WRONG }

    private final ChoirPhrase phrase;
    private final long start;
    private final int window;
    private final boolean[][] sounded;
    private int next;
    /** Every step that counted: {note, pad, offset in ticks (negative early)}. */
    private final List<int[]> hits = new ArrayList<>();

    public ChoirJudge(ChoirPhrase phrase, long start, int window) {
        this.phrase = phrase;
        this.start = start;
        this.window = window;
        this.sounded = new boolean[phrase.size()][];
        for (int i = 0; i < phrase.size(); i++) {
            sounded[i] = new boolean[phrase.pads()[i].length];
        }
    }

    /** True if a step {@code offset} ticks from its beat counts with this window and grace. */
    public static boolean within(long offset, int window, int grace) {
        return offset >= -window && offset <= window + Math.max(0, grace);
    }

    public ChoirPhrase phrase() {
        return phrase;
    }

    public long start() {
        return start;
    }

    public int window() {
        return window;
    }

    /** The tick note {@code i} is due. */
    public long target(int i) {
        return start + phrase.onsets()[i];
    }

    /** The next note to sound, or the phrase's size when all have. */
    public int next() {
        return next;
    }

    public boolean done() {
        return next >= phrase.size();
    }

    public List<int[]> hits() {
        return hits;
    }

    /** A player's feet entered pad {@code pad} on tick {@code tick}; {@code grace} is their latency grace. */
    public Verdict enter(long tick, int pad, int grace) {
        if (done()) {
            return Verdict.IGNORED;
        }
        int[] want = phrase.pads()[next];
        for (int k = 0; k < want.length; k++) {
            if (want[k] != pad) {
                continue;
            }
            if (sounded[next][k]) {
                return Verdict.IGNORED;
            }
            long offset = tick - target(next);
            if (!within(offset, window, grace)) {
                return Verdict.IGNORED;
            }
            sounded[next][k] = true;
            hits.add(new int[] {next, pad, (int) offset});
            if (allSounded(next)) {
                next++;
            }
            return done() ? Verdict.DONE : Verdict.HIT;
        }
        if (next > 0 && phrase.sounds(next - 1, pad) || next + 1 < phrase.size() && phrase.sounds(next + 1, pad)) {
            return Verdict.IGNORED;
        }
        return Verdict.WRONG;
    }

    /** True if, at {@code tick}, the next note's window has closed without it sounding (grace: the most any player on the floor has). */
    public boolean missed(long tick, int grace) {
        return !done() && tick - target(next) > window + Math.max(0, grace);
    }

    private boolean allSounded(int i) {
        for (boolean b : sounded[i]) {
            if (!b) {
                return false;
            }
        }
        return true;
    }
}
