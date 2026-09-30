package com.cosmicbreach.combat.server.effect;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The Comet Maul's server effects: the Gravity Well and the Impact Crater's Cratered ground. */
public final class MaulEffects {
    private MaulEffects() {
    }

    public static void register(IEventBus gameBus) {
        ServerMoveEffects.register(GravityWell.ID, new GravityWell());
        ServerMoveEffects.register(Crater.ID, new Crater());
        gameBus.addListener(ServerTickEvent.Post.class, Crater::onServerTick);
        gameBus.addListener(ServerStoppingEvent.class, Crater::onServerStopping);
    }
}
