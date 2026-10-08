package com.cosmicbreach.mixin.client;

import com.cosmicbreach.client.gear.TrimLayers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A tier trim layer of a generated item model keeps its front and back faces only ({@link TrimLayers}): its edge walls
 * would depth-fight the base layer's. Element 0 of {@code processFrames} is the front and back pair. Both the top-level
 * bake and nested generated models (the Binary Edges' separate transforms) build through {@code generateBlockModel}, so
 * this covers every trimmed weapon, its glow stages and every set piece's icon. Wraps the call, so other mods' changes
 * to it still apply first.
 */
@Mixin(ItemModelGenerator.class)
public abstract class ItemModelGeneratorTrimMixin {
    @WrapOperation(method = "generateBlockModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/model/ItemModelGenerator;processFrames(ILjava/lang/String;Lnet/minecraft/client/renderer/texture/SpriteContents;)Ljava/util/List;"))
    private List<BlockElement> cosmicbreach$trimFacesOnly(ItemModelGenerator self, int tintIndex, String layer, SpriteContents sprite,
                                                          Operation<List<BlockElement>> original) {
        List<BlockElement> elements = original.call(self, tintIndex, layer, sprite);
        ResourceLocation name = sprite.name();
        if (!elements.isEmpty() && TrimLayers.facesOnly(tintIndex, name.getNamespace(), name.getPath())) {
            return new ArrayList<>(elements.subList(0, 1));
        }
        return elements;
    }
}
