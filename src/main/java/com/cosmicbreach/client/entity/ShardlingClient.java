package com.cosmicbreach.client.entity;

import com.cosmicbreach.entity.shardling.ShardlingEffects;
import com.cosmicbreach.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The Shardling on the client: its renderers and effects. Called from the client entry point. */
public final class ShardlingClient {
    private ShardlingClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(ModEntities.SHARDLING.get(), ShardlingRenderer::new);
            event.registerEntityRenderer(ModEntities.SHARD_NEEDLE.get(), ShardNeedleRenderer::new);
            event.registerEntityRenderer(ModEntities.SHARD_FRAGMENT.get(), ShardFragmentRenderer::new);
        });
        ShardlingEffects.install(new ShardlingFx());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ShardlingPoints.clear());
    }
}
