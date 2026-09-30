package com.cosmicbreach.progression;

import com.cosmicbreach.progression.net.ProgressionNetworking;
import com.cosmicbreach.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Attunement: XP, levels 1 to 50, stat points and the four attributes (GDD sections 3.2 to 3.4).
 * Common entry point: registrations, payloads, server hooks, debug commands and the kill rewards.
 */
public final class Progression {
    private Progression() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ProgressionRegistry.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ProgressionNetworking::register);
        ProgressionEvents.register(game);
        game.addListener(RegisterCommandsEvent.class, ProgressionCommands::onRegisterCommands);

        KillRewards.register(ModEntities.SHARDLING, XpSource.TRASH_MOB.at(XpSource.LayerTier.REACH));
    }
}
