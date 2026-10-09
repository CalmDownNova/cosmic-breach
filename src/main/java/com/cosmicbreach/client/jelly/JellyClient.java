package com.cosmicbreach.client.jelly;

import com.cosmicbreach.jelly.Jellies;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The drift jelly on the client: its renderer. (Skim's air acceleration is {@code JellyBounce}'s, which runs on the controlling client.) */
public final class JellyClient {
    private JellyClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(Jellies.DRIFT_JELLY.get(), JellyRenderer::new));
    }
}
