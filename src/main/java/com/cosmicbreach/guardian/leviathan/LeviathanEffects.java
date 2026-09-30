package com.cosmicbreach.guardian.leviathan;

/**
 * The client's effects for the Leviathan and its scales, reached from their common code without naming a client class:
 * the client installs its handler at start-up; on a dedicated server every call is a no-op. Client thread only.
 */
public final class LeviathanEffects {
    /** What the client does. Defaults do nothing. */
    public interface Handler {
        /** Every client tick of a Leviathan. */
        default void leviathanTick(ThalassineLeviathan leviathan) {
        }

        /** A one-shot event from the server ({@code ThalassineLeviathan.EVENT_*}). */
        default void leviathanEvent(ThalassineLeviathan leviathan, byte event) {
        }

        /** Every client tick of a shed scale. */
        default void scaleTick(ShedScale scale) {
        }

        /** A scale popped (broken or struck). */
        default void scalePopped(ShedScale scale) {
        }
    }

    private static final Handler NONE = new Handler() {
    };
    private static volatile Handler handler = NONE;

    private LeviathanEffects() {
    }

    public static void install(Handler client) {
        handler = client;
    }

    static Handler handler() {
        return handler;
    }
}
