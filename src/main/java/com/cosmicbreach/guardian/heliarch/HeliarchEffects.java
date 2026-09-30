package com.cosmicbreach.guardian.heliarch;

/**
 * The client's effects for the Heliarch and its Star Seeds, reached from their common code without naming a client
 * class: the client installs its handler at start-up; on a dedicated server every call is a no-op. Client thread.
 */
public final class HeliarchEffects {
    /** What the client does. Defaults do nothing. */
    public interface Handler {
        /** A one-shot event of the fight ({@code HollowHeliarch.EVENT_*}). */
        default void event(HollowHeliarch heliarch, byte event) {
        }

        /** Every client tick of the Heliarch: embers, smoke, the tendrils' void. */
        default void tick(HollowHeliarch heliarch) {
        }

        /** A Star Seed burst or broke ({@code StarSeed.EVENT_*}). */
        default void seedEvent(StarSeed seed, byte event) {
        }
    }

    private static final Handler NONE = new Handler() {
    };
    private static volatile Handler handler = NONE;

    private HeliarchEffects() {
    }

    public static void install(Handler client) {
        handler = client;
    }

    static Handler handler() {
        return handler;
    }
}
