package com.cosmicbreach.client.dev;

import com.cosmicbreach.mixin.client.MouseHandlerAccessor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * Dev only: puts the game's cursor on a GUI point, where the next frame's screen sees the mouse (hover highlights,
 * tooltips). The hidden test window never has focus, so the real cursor can't be moved; this sets the position the
 * game reads, as GLFW's cursor callback does. Needs hidden window mode, where its accessor is applied.
 */
public final class DevMouse {
    private DevMouse() {
    }

    /** The cursor over GUI point ({@code guiX}, {@code guiY}) at the current GUI scale. */
    public static void moveTo(double guiX, double guiY) {
        // checked before the accessor type is touched: outside hidden window mode the mixin plugin leaves that mixin out,
        // and naming a mixin-package class that was never applied fails with a Mixin error instead of this message
        if (!HiddenWindow.enabled()) {
            throw new IllegalStateException("DevMouse needs the hidden test client (its accessor is only applied there)");
        }
        Minecraft mc = Minecraft.getInstance();
        if (!((Object) mc.mouseHandler instanceof MouseHandlerAccessor mouse)) {
            throw new IllegalStateException("DevMouse: the hidden test client's mouse accessor was not applied");
        }
        Window window = mc.getWindow();
        mouse.cosmicbreach$setXpos(guiX * window.getScreenWidth() / window.getGuiScaledWidth());
        mouse.cosmicbreach$setYpos(guiY * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    /** The cursor in the screen's top left corner, away from any panel. */
    public static void park() {
        moveTo(1, 1);
    }
}
