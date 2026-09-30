package com.cosmicbreach.mixin.client;

import java.util.Optional;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The texture a texture shard binds (read only). */
@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface TextureShardAccessor {
    @Invoker("cutoutTexture")
    Optional<ResourceLocation> cosmicbreach$cutoutTexture();
}
