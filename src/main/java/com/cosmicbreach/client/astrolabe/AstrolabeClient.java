package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.astrolabe.Astrolabes;
import com.cosmicbreach.astrolabe.PocketStar;
import com.cosmicbreach.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The Choir Astrolabe on the client: the item's spinning-ring renderer ({@link AstrolabeItemRenderer}), the bolt, star
 * and decoy renderers, the Pocket Star's hum, and the effects and chimes ({@link AstrolabeFx}).
 */
public final class AstrolabeClient {
    private AstrolabeClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(Astrolabes.STAR_BOLT.get(), StarBoltRenderer::new);
            event.registerEntityRenderer(Astrolabes.POCKET_STAR.get(), PocketStarRenderer::new);
            event.registerEntityRenderer(Astrolabes.PARALLAX_DECOY.get(), DecoyRenderer::new);
        });
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return AstrolabeItemRenderer.get();
            }
        }, ModItems.CHOIR_ASTROLABE.get()));
        gameBus.addListener(EntityJoinLevelEvent.class, event -> {
            if (event.getLevel().isClientSide() && event.getEntity() instanceof PocketStar star) {
                Minecraft.getInstance().getSoundManager().play(new StarHum(star));
            }
        });
        AstrolabeHolder.register(gameBus);
        AstrolabeFx.register();
    }
}
