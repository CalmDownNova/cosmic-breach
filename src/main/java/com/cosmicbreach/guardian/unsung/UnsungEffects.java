package com.cosmicbreach.guardian.unsung;

/**
 * The client's effects for the Unsung, its masks and their notes, reached from their common code without naming a
 * client class: the client installs its handler at start-up; on a dedicated server every call is a no-op. Called on
 * the client thread only.
 */
public final class UnsungEffects {
    /** What the client does. Defaults do nothing. */
    public interface Handler {
        /** A one-shot event of the choir ({@code Unsung.EVENT_*}). */
        default void choirEvent(Unsung unsung, byte event) {
        }

        /** A one-shot event of a mask ({@code UnsungMask.EVENT_*}). */
        default void maskEvent(UnsungMask mask, byte event) {
        }

        /** Every client tick of a mask: its shroud's smoke. */
        default void maskTick(UnsungMask mask) {
        }

        /** A note burst on its beat, or broke on a hit ({@code SongNote.EVENT_*}). */
        default void noteEvent(SongNote note, byte event) {
        }
    }

    private static final Handler NONE = new Handler() {
    };
    private static volatile Handler handler = NONE;

    private UnsungEffects() {
    }

    public static void install(Handler client) {
        handler = client;
    }

    static Handler handler() {
        return handler;
    }
}
