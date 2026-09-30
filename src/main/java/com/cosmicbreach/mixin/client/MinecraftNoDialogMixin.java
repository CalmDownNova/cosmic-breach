package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.dev.HiddenWindow;
import net.minecraft.client.Minecraft;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * In hidden window mode, logs the two native message boxes Minecraft's constructor can show
 * (off-heap memory failure, unsupported resolution) instead of putting them on the desktop.
 * Only applied in hidden window mode (see {@link com.cosmicbreach.client.dev.HiddenWindowMixinPlugin}).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftNoDialogMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/util/tinyfd/TinyFileDialogs;tinyfd_messageBox(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"))
    private boolean cosmicbreach$noStartupDialog(
            CharSequence title, CharSequence message, CharSequence dialogType, CharSequence iconType, boolean defaultButton) {
        if (HiddenWindow.enabled()) {
            return HiddenWindow.suppressDialog(title, message);
        }
        return TinyFileDialogs.tinyfd_messageBox(title, message, dialogType, iconType, defaultButton);
    }
}
