package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.fx.GhostBodies;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * GeckoLib draws armor into the level's own buffers, whatever buffer the armor layer hands it. While a ghost of a
 * player is being recorded ({@link GhostBodies}), its armor goes to the ghost's recorder instead, like the rest of
 * the body; otherwise nothing changes. Wraps the one read, so another mod's change to it still applies.
 */
@Mixin(GeoArmorRenderer.class)
public abstract class GeoArmorRendererCaptureMixin {
    @WrapOperation(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"))
    private MultiBufferSource.BufferSource cosmicbreach$ghostCapture(RenderBuffers buffers, Operation<MultiBufferSource.BufferSource> original) {
        MultiBufferSource.BufferSource capture = GhostBodies.captureSource();
        return capture != null ? capture : original.call(buffers);
    }
}
