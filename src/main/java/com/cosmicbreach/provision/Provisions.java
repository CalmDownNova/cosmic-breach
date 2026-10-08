package com.cosmicbreach.provision;

import com.cosmicbreach.registry.ModCreativeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** The levels' provisions (1.1): registrations and the creative tab. What they are: {@link ProvisionRegistry}. */
public final class Provisions {
    private Provisions() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ProvisionRegistry.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                ProvisionRegistry.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
            }
        });
    }
}
