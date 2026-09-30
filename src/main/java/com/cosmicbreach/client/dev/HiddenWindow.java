package com.cosmicbreach.client.dev;

import com.mojang.logging.LogUtils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Hidden window mode for the test client (the {@code testClient} Gradle run sets
 * {@code -Dcosmicbreach.hiddenWindow=true}). The game still renders every frame into its
 * main render target, so screenshots work, but the window is never shown on the desktop.
 *
 * <p>Without the property nothing here does anything, and {@link HiddenWindowMixinPlugin}
 * does not even apply the mixins, so shipped builds are untouched.
 *
 * <p>How the window stays hidden (checked against NeoForge 21.1.252 and FML 4.0.44):
 * <ul>
 *   <li>FML's early loading window is the only code that calls {@code glfwShowWindow}
 *       ({@code DisplayWindow.initWindow}). It is off because {@code prepareTestClient}
 *       writes {@code earlyWindowControl = false} into {@code run-test/config/fml.toml};
 *       FML then uses its dummy provider and Minecraft creates its own window in
 *       {@code Window}'s constructor through {@code NoVizFallback.windowHandoff}.</li>
 *   <li>{@code WindowHiddenMixin} sets {@code GLFW_VISIBLE} to false just before that
 *       window is created.</li>
 *   <li>Going fullscreen ({@code glfwSetWindowMonitor} with a monitor) shows the window on
 *       Windows, so fullscreen is blocked.</li>
 *   <li>As a last line of defence, every frame checks the window's visibility and hides it
 *       again if anything showed it, logging an error.</li>
 *   <li>Native error dialogs (TinyFileDialogs message boxes) are logged instead of shown.</li>
 * </ul>
 */
public final class HiddenWindow {
    public static final String PROPERTY = "cosmicbreach.hiddenWindow";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static int timesReHidden;

    private HiddenWindow() {}

    public static boolean enabled() {
        return ENABLED;
    }

    /** Asks GLFW to create the next window hidden and not to take focus if it is ever shown. */
    public static void hintHidden() {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        LOGGER.info("[cosmicbreach] hidden window mode: creating the game window hidden");
    }

    /** Hides the window again if something made it visible. Cheap enough to call every frame. */
    public static void enforce(long window, String when) {
        if (window != 0L && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
            GLFW.glfwHideWindow(window);
            timesReHidden++;
            LOGGER.error("[cosmicbreach] hidden window mode: the window was visible ({}), hid it again (time {})", when, timesReHidden);
        }
    }

    /** How many times {@link #enforce} found the window visible. Zero on a healthy run. */
    public static int timesReHidden() {
        return timesReHidden;
    }

    /** Replaces a native message box: logs it and returns the answer a dismissed dialog gives. */
    public static boolean suppressDialog(CharSequence title, CharSequence message) {
        LOGGER.error("[cosmicbreach] hidden window mode: suppressed a native dialog '{}': {}", title, message);
        return false;
    }
}
