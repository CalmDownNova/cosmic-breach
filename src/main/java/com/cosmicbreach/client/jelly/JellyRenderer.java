package com.cosmicbreach.client.jelly;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.jelly.DriftJelly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The drift jelly through GeckoLib. The model is translucent (the bell's glass is the texture's own alpha), so the
 * render type is the translucent one; a glowmask (the core and every marking) is added over it as light
 * ({@link GuardianGlowLayer}), and the whole animal shows a faint light of its own in the dark (a floor of block light
 * {@value #BODY_LIGHT}, the core's full). The tendrils shorten to fit the room under the bell
 * ({@link DriftJelly#tendrilScale()}), so a low jelly never hangs them into the ground. It lies flat when it dies (it
 * does not tip over: the model deflates and sinks).
 */
public class JellyRenderer extends GeoEntityRenderer<DriftJelly> {
    static final ResourceLocation GLOW = CosmicBreach.id("textures/entity/drift_jelly_glowmask.png");
    /** Block light the body never shows less than: it is a little alight. */
    static final int BODY_LIGHT = 10;
    static final int GLOW_RANGE = 56;

    public JellyRenderer(EntityRendererProvider.Context context) {
        super(context, new JellyModel());
        addRenderLayer(new GuardianGlowLayer<>(this, j -> GLOW, JellyRenderer::glow));
        this.shadowRadius = 0.0f;
    }

    /** The glow is a second draw of the whole model: it is for jellies within {@value #GLOW_RANGE} blocks, where its markings can be seen. */
    private static int glow(DriftJelly jelly) {
        double d2 = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(jelly.getX(), jelly.getY(), jelly.getZ());
        return d2 > GLOW_RANGE * GLOW_RANGE ? 0 : 0xFFFFFF;
    }

    @Override
    protected float getDeathMaxRotation(DriftJelly animatable) {
        return 0.0f;
    }

    @Override
    public void render(DriftJelly jelly, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), BODY_LIGHT), LightTexture.sky(packedLight));
        super.render(jelly, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public RenderType getRenderType(DriftJelly animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, DriftJelly jelly, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender) {
            float scale = jelly.tendrilScale();
            for (int i = 0; i < 8; i++) {
                int tendril = i;
                model.getBone("t" + tendril + "_0").ifPresent(bone -> bone.setScaleY(scale));
            }
        }
        super.preRender(poseStack, jelly, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, DriftJelly animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        int light = packedLight;
        if (!isReRender && bone.getName().equals("core")) {
            light = LightTexture.FULL_BRIGHT;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, light, packedOverlay, colour);
    }
}
