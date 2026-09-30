package com.cosmicbreach.client.guardian;

import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.guardian.colossus.ColossusEffects;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/** The guardians on the client: renderers, telegraphs and effects, the boss bar's gauge and the boss music. */
public final class GuardianClient {
    private GuardianClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(GuardianRegistry.PRISM_COLOSSUS.get(), ColossusRenderer::new);
            event.registerEntityRenderer(GuardianRegistry.PRISM_SHARD.get(), PrismShardRenderer::new);
            event.registerEntityRenderer(GuardianRegistry.GUARDIAN_PART.get(), NoopRenderer::new);
            event.registerBlockEntityRenderer(GuardianRegistry.CROWN_CRYSTAL_ENTITY.get(), CrownCrystalRenderer::new);
        });
        ColossusEffects.install(new ColossusFx());
        com.cosmicbreach.client.guardian.unsung.UnsungClient.register(modBus, gameBus);
        gameBus.addListener(RenderLevelStageEvent.class, ColossusFx::render);
        gameBus.addListener(CustomizeGuiOverlayEvent.BossEventProgress.class, GuardianBarHud::onBossBar);
        gameBus.addListener(ClientTickEvent.Post.class, GuardianMusic::onClientTick);
        gameBus.addListener(EventPriority.HIGH, SelectMusicEvent.class, GuardianMusic::onSelectMusic);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            GuardianBarHud.clear();
            RefractionView.clear();
        });
    }
}
