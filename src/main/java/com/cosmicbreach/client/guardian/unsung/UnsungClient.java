package com.cosmicbreach.client.guardian.unsung;

import com.cosmicbreach.guardian.unsung.UnsungEffects;
import com.cosmicbreach.guardian.unsung.UnsungRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/** The Unsung on the client: the masks and their notes, the telegraphs and effects, the choir's music, the Abyss's motes. */
public final class UnsungClient {
    private UnsungClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UnsungRegistry.UNSUNG.get(), NoopRenderer::new);
            event.registerEntityRenderer(UnsungRegistry.UNSUNG_MASK.get(), UnsungMaskRenderer::new);
            event.registerEntityRenderer(UnsungRegistry.SONG_NOTE.get(), SongNoteRenderer::new);
        });
        modBus.addListener(RegisterParticleProvidersEvent.class,
                event -> event.registerSpriteSet(UnsungRegistry.ABYSS_MOTE.get(), AbyssMote::provider));
        UnsungEffects.install(new UnsungFx());
        gameBus.addListener(RenderLevelStageEvent.class, UnsungFx::render);
        gameBus.addListener(ClientTickEvent.Post.class, UnsungMusic::onClientTick);
        gameBus.addListener(EventPriority.HIGH, SelectMusicEvent.class, UnsungMusic::onSelectMusic);
    }
}
