package com.cosmicbreach.client.tooltip;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.item.ItemTooltips;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds the what / from / used lines to every item of this mod ({@link ItemTooltips}). Shift expands them. */
public final class TooltipClient {
    private TooltipClient() {
    }

    public static void register(IEventBus game) {
        game.addListener(EventPriority.LOW, TooltipClient::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!CosmicBreach.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        event.getToolTip().addAll(ItemTooltips.lines(id.getPath(), Screen.hasShiftDown(), I18n::exists));
    }
}
