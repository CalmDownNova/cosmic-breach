package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.combat.TwinBlades;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The held items a body draws (third person, and the animated first person, which draws the body): with a
 * weapon that has a second blade in the main hand ({@link TwinBlades}, the Binary Edges) the off hand draws that
 * blade instead of the off-hand slot, nothing while the blade is thrown, and neither hand draws a blade while
 * both are flung into an orbit. Every other weapon and item is drawn as vanilla draws it. Wraps the two reads,
 * so another mod's changes to them still apply first.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cosmicbreach$offHandBlade(LivingEntity entity, Operation<ItemStack> original) {
        return TwinBlades.offHandStack(entity, original.call(entity));
    }

    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cosmicbreach$mainHandBlade(LivingEntity entity, Operation<ItemStack> original) {
        return TwinBlades.mainHandStack(entity, original.call(entity));
    }
}
