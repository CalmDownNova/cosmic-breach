package com.cosmicbreach.voice.boss;

import java.util.List;
import java.util.Map;

/**
 * The boss sounds a line must not sit under: the wake sounds (under the openers) and the phase sounds (under the lines that
 * answer them), with the ticks after each starts until its loud part is over (the Colossus's Fracture is loud for its first
 * 2.1 s of 3), and the Leviathan's song in its intro (whole: it plays in full, her opener waits for it). A line's first word waits for the end of the last one the boss has played, plus
 * {@value VoiceDirector#QUIET_MARGIN} ticks ({@link VoiceDirector.Gate#wordsMayStartAt}). An attack's own sound is never on this
 * list: those are the fight's warnings, and a line gives way to them, not the other way round. Pure.
 */
public final class BossVoiceSounds {
    private static final Map<String, Integer> CLEAR = Map.of(
            "leviathan/awaken", 100,
            "leviathan/song", 84,
            "unsung/awaken", 80,
            "unsung/dim", 55,
            "colossus/fracture", 42,
            "colossus/shatter", 52);

    private BossVoiceSounds() {
    }

    /** Ticks from the start of the sound at {@code path} (a sound event's path) until a line may speak, or 0 if it is not watched. */
    public static int clearTicks(String path) {
        return CLEAR.getOrDefault(path, 0);
    }

    /** Every watched sound's path (checks). */
    public static List<String> names() {
        return List.copyOf(CLEAR.keySet());
    }
}
