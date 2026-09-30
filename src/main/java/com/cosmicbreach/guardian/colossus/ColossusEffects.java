package com.cosmicbreach.guardian.colossus;

/**
 * The client's effects for the Prism Colossus and its shards, reached from their common code without naming a
 * client class: the client installs its handler at start-up; on a dedicated server every call is a no-op. Called on
 * the client thread only.
 */
public final class ColossusEffects {
    /** What the client does. Defaults do nothing. */
    public interface Handler {
        /** Every client tick of a Colossus: sounds and particles that follow its synced state. */
        default void colossusTick(PrismColossus colossus) {
        }

        /** A one-shot event from the server ({@code PrismColossus.EVENT_*}). */
        default void colossusEvent(PrismColossus colossus, byte event) {
        }

        /** Every client tick of a shard. */
        default void shardTick(PrismShard shard) {
        }

        /** A shard died. */
        default void shardDied(PrismShard shard) {
        }
    }

    private static final Handler NONE = new Handler() {
    };
    private static volatile Handler handler = NONE;

    private ColossusEffects() {
    }

    public static void install(Handler client) {
        handler = client;
    }

    static Handler handler() {
        return handler;
    }
}
