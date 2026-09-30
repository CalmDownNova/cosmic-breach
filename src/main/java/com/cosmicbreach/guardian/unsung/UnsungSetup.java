package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.combat.server.CombatHooks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The Unsung and the Silent Nave (Unsung design v1): registrations and server hooks, called from
 * {@code Guardians.register}. The guardian itself is {@code GuardianTypes.UNSUNG}; the client side is
 * {@code client.guardian.unsung.UnsungClient}.
 */
public final class UnsungSetup {
    private UnsungSetup() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        UnsungRegistry.register(modBus);
        // its first kill also attunes to the Breach Sanctum
        com.cosmicbreach.guardian.GuardianPayouts.register(com.cosmicbreach.guardian.GuardianTypes.UNSUNG, UnsungLoot.TABLE,
                player -> com.cosmicbreach.world.LayerAttunement.grantSanctum(player));
        CombatHooks.register(Silenced.HOOK);
        game.addListener(RegisterCommandsEvent.class, event -> UnsungCommands.register(event.getDispatcher()));
    }
}
