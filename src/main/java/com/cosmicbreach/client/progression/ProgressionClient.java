package com.cosmicbreach.client.progression;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Set;

/** The progression's client side: the Attunement key and screen, the XP line and the level-up feedback. */
public final class ProgressionClient {
    private ProgressionClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(RegisterKeyMappingsEvent.class, ProgressionKeys::register);
        modBus.addListener(RegisterGuiLayersEvent.class, AttunementHud::register);
        game.addListener(ClientTickEvent.Post.class, ProgressionClient::onClientTick);
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> AttunementHud.reset());
        game.addListener(RenderGuiLayerEvent.Pre.class, ProgressionClient::onRenderLayer);
    }

    /** HUD text that would show through the screen's panel; it waits while the screen is open. */
    private static final Set<ResourceLocation> HIDDEN_UNDER_SCREEN = Set.of(VanillaGuiLayers.CHAT,
            VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.OVERLAY_MESSAGE, AttunementHud.LAYER);

    private static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof AttunementScreen && HIDDEN_UNDER_SCREEN.contains(event.getName())) {
            event.setCanceled(true);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (ProgressionKeys.ATTUNEMENT.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                AttunementScreen.open();
            }
        }
        AttunementHud.tick(mc);
    }
}
