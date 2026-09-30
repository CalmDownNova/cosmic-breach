package com.cosmicbreach.client.gyre;

import com.cosmicbreach.entity.gyre.GyreEffects;
import com.cosmicbreach.entity.gyre.GyreKnights;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** The Gyre Knight on the client: its renderer, its hum and its telegraphs ({@link GyreFx}). */
public final class GyreClient {
    private GyreClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(GyreKnights.GYRE_KNIGHT.get(), GyreKnightRenderer::new));
        GyreEffects.install(new GyreFx());
        gameBus.addListener(RenderLevelStageEvent.class, GyreFx::render);
    }
}
