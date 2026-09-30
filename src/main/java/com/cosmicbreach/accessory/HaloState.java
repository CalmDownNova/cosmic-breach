package com.cosmicbreach.accessory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The Halo of Nine's shards on one player: whether it is worn, and for each of its three shards the game time it is
 * back (0, or any time already past: it is there). Immutable; the server sets it on the player, which syncs it to them
 * and to everyone who sees them, and the clients draw the shards that are there from it and the game time.
 */
public record HaloState(boolean worn, long regrow0, long regrow1, long regrow2) {
    public static final HaloState NONE = new HaloState(false, 0L, 0L, 0L);

    public static final StreamCodec<ByteBuf, HaloState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HaloState::worn,
            ByteBufCodecs.VAR_LONG, HaloState::regrow0,
            ByteBufCodecs.VAR_LONG, HaloState::regrow1,
            ByteBufCodecs.VAR_LONG, HaloState::regrow2,
            HaloState::new);

    /** When shard {@code i} (0 to 2) is back. */
    public long regrowAt(int i) {
        return switch (i) {
            case 0 -> regrow0;
            case 1 -> regrow1;
            default -> regrow2;
        };
    }

    public boolean alive(int i, long now) {
        return AccessoryRules.shardAlive(regrowAt(i), now);
    }

    /** How many shards are there at {@code now}. */
    public int count(long now) {
        int n = 0;
        for (int i = 0; i < AccessoryRules.HALO_SHARDS; i++) {
            if (alive(i, now)) {
                n++;
            }
        }
        return n;
    }

    /** Shard {@code i} broke at {@code now}. */
    public HaloState broken(int i, long now) {
        long at = AccessoryRules.regrowAt(now);
        return new HaloState(worn, i == 0 ? at : regrow0, i == 1 ? at : regrow1, i == 2 ? at : regrow2);
    }

    public HaloState withWorn(boolean on) {
        return on ? new HaloState(true, regrow0, regrow1, regrow2) : NONE;
    }
}
