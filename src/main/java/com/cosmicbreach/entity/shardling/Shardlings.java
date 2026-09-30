package com.cosmicbreach.entity.shardling;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * What every Shardling on a server shares: the attack tokens, so at most two Shardlings of any
 * number of packs attack one player at once. Tokens are keyed by entity ids, which only mean
 * something within one server run, so they are cleared when a server starts and stops. Server thread.
 */
public final class Shardlings {
    private static final AttackTokens TOKENS = new AttackTokens();

    private Shardlings() {
    }

    public static AttackTokens tokens() {
        return TOKENS;
    }

    public static void register(IEventBus game) {
        game.addListener(ServerStartingEvent.class, event -> TOKENS.clear());
        game.addListener(ServerStoppedEvent.class, event -> TOKENS.clear());
    }
}
