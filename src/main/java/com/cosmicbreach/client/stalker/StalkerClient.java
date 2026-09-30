package com.cosmicbreach.client.stalker;

import com.cosmicbreach.entity.stalker.StalkerEffects;
import com.cosmicbreach.entity.stalker.Stalkers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The Hollow Stalker on the client: its renderer and its effects ({@link StalkerFx}). */
public final class StalkerClient {
    private StalkerClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(Stalkers.HOLLOW_STALKER.get(), StalkerRenderer::new));
        StalkerEffects.set(new StalkerFx());
    }
}
