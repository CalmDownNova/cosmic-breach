package com.cosmicbreach.client.shrine;

import com.cosmicbreach.shrine.ShrineRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The shrines on the client: their GeckoLib renderer. */
public final class ShrinesClient {
    private ShrinesClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(ShrineRegistry.SHRINE_ENTITY.get(), ShrineRenderer::new));
    }
}
