package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.sky.AetheriaFog;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla never draws a section whose height differs from the camera's by more than the render distance
 * ({@code getRelativeFrom}), so from a Reach island (Y 350) nothing of the Deep (Y 8 to 140) is ever drawn,
 * whatever the fog. In Aetheria the vertical reach covers the whole dimension so the layers below show
 * through the Breach and past island edges (see {@link AetheriaFog#verticalSections}). Only the vertical
 * check changes; the horizontal view distance, chunk loading and frustum are untouched.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {
    @ModifyExpressionValue(method = "getRelativeFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;getViewDistance()I"))
    private int cosmicbreach$aetheriaVerticalReach(int viewDistance) {
        return AetheriaFog.verticalSections(viewDistance);
    }
}
