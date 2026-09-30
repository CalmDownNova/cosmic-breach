package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.sky.AetheriaFog;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In Aetheria, uploads the fog shape index that tells our {@code fog.glsl} how much vertical distance
 * counts (see {@link AetheriaFog}). Only the value vanilla's shaders receive changes, and only while
 * Aetheria's terrain fog is set up; everywhere else vanilla's index passes through untouched.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceFogMixin {
    @ModifyExpressionValue(method = "setDefaultUniforms",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/FogShape;getIndex()I"))
    private int cosmicbreach$aetheriaFogShape(int vanilla) {
        return AetheriaFog.shapeIndexFor(vanilla);
    }
}
