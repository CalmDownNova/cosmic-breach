package com.cosmicbreach.mixin.client;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A composite state's texture shard (read only). */
@Mixin(RenderType.CompositeState.class)
public interface CompositeStateTextureAccessor {
    @Accessor("textureState")
    RenderStateShard.EmptyTextureStateShard cosmicbreach$textureState();
}
