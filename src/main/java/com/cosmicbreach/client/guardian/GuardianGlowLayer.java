package com.cosmicbreach.client.guardian;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Light a guardian gives off, shared by every guardian: the model drawn again right after itself, in the same pose,
 * with a glow texture whose colour is ADDED over the lit model, full bright and unshaded.
 *
 * <p>Why not GeckoLib's own glowing layer: its emissive render type still runs vanilla's directional entity shading,
 * so a white core or eye came out a flat grey (about 188 of 255 on a face turned from the light) and a white-hot body
 * read as grey stone. This uses the eyes shader (texture times colour, no shading, no lightmap) with additive blending,
 * pulled a hair toward the camera (the layering vanilla's eyes and the armor glint use) so it wins the depth test
 * against the model it covers. A texel's colour is how much light it adds: black adds none, white saturates to white.
 */
public class GuardianGlowLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private static final Function<ResourceLocation, RenderType> TYPE = Util.memoize(texture -> RenderType.create(
            "cosmicbreach_guardian_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false)));

    private final Function<T, @Nullable ResourceLocation> texture;
    private final ToIntFunction<T> colour;

    /**
     * @param texture the glow texture for the animatable now, or null for none this frame
     * @param colour  0xRRGGBB the texture is multiplied by (its strength); 0 draws nothing
     */
    public GuardianGlowLayer(GeoRenderer<T> renderer, Function<T, @Nullable ResourceLocation> texture, ToIntFunction<T> colour) {
        super(renderer);
        this.texture = texture;
        this.colour = colour;
    }

    /** The additive, unshaded, full-bright render type for a glow texture. */
    public static RenderType renderType(ResourceLocation texture) {
        return TYPE.apply(texture);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation tex = texture.apply(animatable);
        int rgb = colour.applyAsInt(animatable) & 0xFFFFFF;
        if (tex == null || rgb == 0) {
            return;
        }
        RenderType type = renderType(tex);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, 0xFF000000 | rgb);
    }
}
