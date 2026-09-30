package com.cosmicbreach.client.accessory;

import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The accessories' client side (G6a): the Halo's shards on every wearer ({@link HaloRenderer}), the Sunshard Compass's
 * needle and its place beside the hotbar ({@link CompassClient}), and the moments the server sends
 * ({@link AccessoryFx}, registered with the payload in {@code accessory.Accessories}).
 */
public final class AccessoriesClient {
    private AccessoriesClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(CompassClient::registerItemProperty));
        modBus.addListener(RegisterGuiLayersEvent.class, event ->
                event.registerAbove(VanillaGuiLayers.HOTBAR, CompassClient.LAYER, (LayeredDraw.Layer) CompassClient::render));
        game.addListener(RenderLevelStageEvent.class, HaloRenderer::render);
    }
}
