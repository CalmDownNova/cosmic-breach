package com.cosmicbreach.client.astrolabe;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Whose Astrolabe is being drawn: the living entity whose renderer is running (its held items are drawn inside it), so
 * the item renderer can spin the rings by that holder's Resonance. Client render thread only.
 */
final class AstrolabeHolder {
    private static @Nullable Entity current;

    private AstrolabeHolder() {
    }

    static void register(IEventBus gameBus) {
        gameBus.addListener(RenderLivingEvent.Pre.class, event -> current = event.getEntity());
        gameBus.addListener(RenderLivingEvent.Post.class, event -> current = null);
    }

    static @Nullable Entity current() {
        return current;
    }
}
