package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.fx.BladeTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads where a held combat weapon is drawn, for the effects that follow the blade (slash trails,
 * the charge's glow, full Resonance's sparks). Every held item passes through {@code renderItem}: a
 * player's in the world (third person, and the animated first person, which playerAnimator draws in
 * the world pass) and the vanilla first-person hand. By then the hand's pose, the animation's item
 * channel and the camera are all applied. Read only: it changes nothing about the drawing.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void cosmicbreach$trackBlade(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                         PoseStack poseStack, MultiBufferSource buffer, int seed, CallbackInfo callback) {
        BladeTracker.onItemRendered(entity, stack, context, leftHand, poseStack);
    }
}
