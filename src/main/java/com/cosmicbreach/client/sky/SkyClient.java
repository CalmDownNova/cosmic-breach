package com.cosmicbreach.client.sky;

import com.cosmicbreach.world.AetheriaWorld;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Wires Aetheria's sky, fog and music into the client (W3a). See {@link AetheriaSkyRenderer} for the sky,
 * {@link AetheriaFog} for the fog, {@link AetheriaMusic} for the music, {@link SkyWeather} for the hooks the
 * weather task uses, and {@link com.cosmicbreach.world.VesperClock} for the shared beat.
 */
public final class SkyClient {
    private SkyClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(RegisterDimensionSpecialEffectsEvent.class,
                event -> event.register(AetheriaWorld.EFFECTS, new AetheriaSkyEffects()));
        modBus.addListener(RegisterShadersEvent.class, SkyShaders::register);
        game.addListener(ViewportEvent.ComputeFogColor.class, AetheriaFog::onComputeFogColor);
        game.addListener(ViewportEvent.RenderFog.class, AetheriaFog::onRenderFog);
        game.addListener(SelectMusicEvent.class, AetheriaMusic::onSelectMusic);
    }
}
