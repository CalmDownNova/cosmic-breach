package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.combat.TwinBlades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla's first-person hands: with a weapon that has a second blade in the main hand ({@link TwinBlades}, the
 * Binary Edges) the off hand holds that blade (nothing while it is thrown) instead of the off-hand slot. The
 * main hand is vanilla's. Only the item handed to the off hand's drawing changes.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererOffHandMixin {
    @ModifyArg(method = "renderHandsWithItems",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 5)
    private ItemStack cosmicbreach$offHandBlade(ItemStack original) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? original : TwinBlades.offHandStack(mc.player, original);
    }
}
