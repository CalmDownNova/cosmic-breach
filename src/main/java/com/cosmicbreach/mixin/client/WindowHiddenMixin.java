package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.dev.HiddenWindow;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the test client's window hidden. Only applied in hidden window mode (see
 * {@link com.cosmicbreach.client.dev.HiddenWindowMixinPlugin}), and every hook checks the
 * mode again, so a normal game behaves exactly as vanilla.
 */
@Mixin(Window.class)
public abstract class WindowHiddenMixin {
    @Shadow
    @Final
    private long window;

    @Shadow
    private boolean fullscreen;

    // NeoForge's Window creates the GLFW window through ImmediateWindowHandler.setupMinecraftWindow;
    // with earlyWindowControl off that ends in NoVizFallback.windowHandoff -> glfwCreateWindow,
    // which uses the hints set here.
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private void cosmicbreach$createHidden(CallbackInfo ci) {
        if (HiddenWindow.enabled()) {
            HiddenWindow.hintHidden();
        }
    }

    // If the window came from somewhere else already visible (the early loading window), hide it.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cosmicbreach$checkHiddenAfterCreate(CallbackInfo ci) {
        if (HiddenWindow.enabled()) {
            HiddenWindow.enforce(this.window, "after creation");
        }
    }

    // Runs once per frame: hides the window again if anything showed it.
    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void cosmicbreach$keepHidden(CallbackInfo ci) {
        if (HiddenWindow.enabled()) {
            HiddenWindow.enforce(this.window, "frame");
        }
    }

    // glfwSetWindowMonitor with a monitor (fullscreen) shows the window on Windows.
    @Inject(method = "toggleFullScreen", at = @At("HEAD"), cancellable = true)
    private void cosmicbreach$noFullscreenToggle(CallbackInfo ci) {
        if (HiddenWindow.enabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "setMode", at = @At("HEAD"))
    private void cosmicbreach$stayWindowed(CallbackInfo ci) {
        if (HiddenWindow.enabled()) {
            this.fullscreen = false;
        }
    }

    // A GLFW error during startup would pop up a native message box.
    @Redirect(
            method = "bootCrash",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/util/tinyfd/TinyFileDialogs;tinyfd_messageBox(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"))
    private static boolean cosmicbreach$noBootDialog(
            CharSequence title, CharSequence message, CharSequence dialogType, CharSequence iconType, boolean defaultButton) {
        if (HiddenWindow.enabled()) {
            return HiddenWindow.suppressDialog(title, message);
        }
        return TinyFileDialogs.tinyfd_messageBox(title, message, dialogType, iconType, defaultButton);
    }
}
