package com.cosmicbreach.client.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.mount.Stable;
import com.cosmicbreach.mount.StableCrystalItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The Stable Crystal on the client: {@code cosmicbreach:stowed} is 1 while it holds a mount (its model lights up). */
public final class StableClient {
    public static final ResourceLocation STOWED = CosmicBreach.id("stowed");

    private StableClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> ItemProperties.register(Stable.STABLE_CRYSTAL.get(), STOWED,
                (stack, level, entity, seed) -> StableCrystalItem.stowed(stack) != null ? 1.0f : 0.0f)));
    }
}
