package com.cosmicbreach.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Dev only (applied in hidden window mode, see {@code HiddenWindowMixinPlugin}): sets the cursor position the game
 * reads, the two fields GLFW's cursor callback sets, so a test can hover a point on a screen. The hidden test window
 * never has focus, and GLFW ignores {@code glfwSetCursorPos} for an unfocused window.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void cosmicbreach$setXpos(double xpos);

    @Accessor("ypos")
    void cosmicbreach$setYpos(double ypos);
}
