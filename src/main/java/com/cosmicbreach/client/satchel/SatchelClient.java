package com.cosmicbreach.client.satchel;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.satchel.Satchels;
import com.cosmicbreach.satchel.SatchelMenu;
import com.cosmicbreach.satchel.SatchelNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;

/** The Satchel on the client: its screen, the open key (B) and the contents the server sends the open screen. */
public final class SatchelClient {
    public static final KeyMapping KEY = new KeyMapping("key.cosmicbreach.satchel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, ModKeyMappings.CATEGORY);

    private SatchelClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(KEY));
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(Satchels.MENU.get(), SatchelScreen::new));
        game.addListener(ClientTickEvent.Post.class, SatchelClient::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.player.isAlive() && !mc.player.isSpectator()) {
                PacketDistributor.sendToServer(new SatchelNet.OpenPayload());
            }
        }
    }

    public static void contents(SatchelNet.SatchelContentsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SatchelMenu menu && menu.containerId == payload.containerId()) {
            menu.setShown(payload.contents());
        }
    }
}
