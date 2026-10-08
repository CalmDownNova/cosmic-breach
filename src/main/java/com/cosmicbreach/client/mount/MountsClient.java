package com.cosmicbreach.client.mount;

import com.cosmicbreach.mount.MountFxPayload;
import com.cosmicbreach.mount.Mounts;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The celestial mounts on the client: renderers, the inventory screen with its tack slot, the blink's visuals. */
public final class MountsClient {
    private MountsClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(Mounts.LUMEN_STAG.get(), LumenStagRenderer::new);
            event.registerEntityRenderer(Mounts.DRIFT_MANTA.get(), DriftMantaRenderer::new);
        });
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.<net.minecraft.world.inventory.HorseInventoryMenu, MountScreen>register(
                Mounts.MENU.get(), MountScreen::new));
        StableClient.register(modBus);
    }

    /** A {@link MountFxPayload} (main thread). */
    public static void fx(MountFxPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(payload.entityId());
        if (payload.kind() == MountFxPayload.BLINK && e != null) {
            MountFx.blink(e, payload.from(), payload.to());
        }
    }
}
