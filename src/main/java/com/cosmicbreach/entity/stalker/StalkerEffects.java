package com.cosmicbreach.entity.stalker;

/**
 * The Hollow Stalker's client effects, reached from common code: the client sets the real handler at startup; the
 * server never calls these (the entity only calls them on its client side).
 */
public final class StalkerEffects {
    /** What the client does for a Stalker. */
    public interface Handler {
        /** Every client tick of a live Stalker: its upward-falling flakes, the Rend's glint timing. */
        default void tick(HollowStalker stalker) {
        }

        /** An entity event from the server (a step's departure and arrival, the glint, the Rend, the Grasp, death). */
        default void event(HollowStalker stalker, byte id) {
        }
    }

    private static Handler handler = new Handler() {
    };

    private StalkerEffects() {
    }

    public static Handler handler() {
        return handler;
    }

    public static void set(Handler h) {
        handler = h;
    }
}
