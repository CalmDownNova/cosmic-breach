package com.cosmicbreach.client.fx;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Hooks the effects into the client: the shader, particle providers and item property at start-up;
 * then every tick the clock, the world effects, the particle count and the charges, and every frame
 * the world effects' drawing.
 */
public final class ClientFx {
    private ClientFx() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterShadersEvent.class, FxShaders::register);
        modBus.addListener(RegisterParticleProvidersEvent.class, FxParticles::registerProviders);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(WeaponGlow::registerItemProperties));

        gameBus.addListener(ClientTickEvent.Pre.class, event -> FxClock.tick(Minecraft.getInstance()));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (FxClock.running(mc)) {
                WorldFx.tick();
                Chargers.tick();
            }
            FxBudget.tick();
            if (mc.level != null && FxClock.ticks() % 20 == 0) {
                BladeTracker.prune(id -> mc.level.getEntity(id) != null);
            }
        });
        gameBus.addListener(EventPriority.HIGHEST, RenderFrameEvent.Pre.class, event -> BladeTracker.beginFrame());
        gameBus.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                BladeTracker.levelPass(true);
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                BladeTracker.levelPass(false);
            }
        });
        // First in its stage: it also puts back the default blend function our particles leave set.
        gameBus.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.class, WorldFx::render);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            WorldFx.clear();
            Chargers.clear();
            FxBudget.clear();
            BladeTracker.clear();
        });
    }
}
