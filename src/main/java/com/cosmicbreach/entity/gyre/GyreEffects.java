package com.cosmicbreach.entity.gyre;

/**
 * The client's effects for the Gyre Knight, reached from common code without naming a client class: the client installs
 * its handler at start-up; on a dedicated server every call is a no-op. Client thread only.
 */
public final class GyreEffects {
    /** What the client does. Defaults do nothing. */
    public interface Handler {
        /** Every client tick of a Knight: its hum, sparks off its blades. */
        default void knightTick(GyreKnight knight) {
        }

        /** A one-shot event from the server ({@code GyreKnight.EVENT_*}). */
        default void knightEvent(GyreKnight knight, byte event) {
        }
    }

    private static final Handler NONE = new Handler() {
    };
    private static volatile Handler handler = NONE;

    private GyreEffects() {
    }

    public static void install(Handler client) {
        handler = client;
    }

    static Handler handler() {
        return handler;
    }
}
