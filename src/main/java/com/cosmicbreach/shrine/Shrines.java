package com.cosmicbreach.shrine;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The boss shrines (1.1 design section 9), entry point: registrations, keeping everything on a death while saved
 * ({@link ShrineKeep}), placing the shrines at the lairs ({@link ShrinePlacer}), the debug commands. Client side:
 * {@code client.shrine.ShrinesClient}.
 */
public final class Shrines {
    private Shrines() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ShrineRegistry.register(modBus);
        ShrineKeep.register(game);
        ShrinePlacer.register(game);
        game.addListener(RegisterCommandsEvent.class, event -> ShrineCommands.register(event.getDispatcher()));
    }
}
