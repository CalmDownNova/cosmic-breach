package com.cosmicbreach.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The subtitle overlay, so the Starfall's voice can keep its subtitle up while a line lasts (vanilla shows a
 * subtitle for 3 s from the sound's start; the lines run 4 to 7 s). Read-only access, nothing changed.
 */
@Mixin(Gui.class)
public interface GuiSubtitleAccessor {
    @Accessor("subtitleOverlay")
    SubtitleOverlay cosmicbreach$subtitleOverlay();
}
