package com.cosmicbreach.mixin.client;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A composite render type's state, to read which texture it draws with (the ghosts redraw a body with its own textures). */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface RenderTypeTextureAccessor {
    @Invoker("state")
    RenderType.CompositeState cosmicbreach$state();
}
