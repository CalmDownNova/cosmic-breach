package com.cosmicbreach.entity.shardling;

/**
 * The client's effects for the Shardling and its shards, reached from their common code without
 * naming a client class: the client installs its handler at start-up; on a dedicated server nothing
 * is installed and every call is a no-op. Every method is called on the client thread only.
 */
public final class ShardlingEffects {
    /** What the client does. Defaults do nothing. */
    public interface Handler {
        /** Every client tick of a living Shardling: telegraph glints and the like, from its synced phase. */
        default void shardlingTick(Shardling shardling) {
        }

        /** A Shardling died: it shatters. */
        default void shardlingDied(Shardling shardling) {
        }

        /** Every client tick of a needle in flight. */
        default void needleTick(ShardNeedle needle) {
        }

        /** A needle struck something and broke. */
        default void needleImpact(ShardNeedle needle) {
        }

        /** A shard burst. */
        default void fragmentBurst(ShardFragment fragment) {
        }
    }

    private static final Handler NONE = new Handler() {
    };
    private static volatile Handler handler = NONE;

    private ShardlingEffects() {
    }

    /** Called once by the client at start-up. */
    public static void install(Handler client) {
        handler = client;
    }

    static Handler handler() {
        return handler;
    }
}
