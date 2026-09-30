package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.astrolabe.ParallaxDecoy;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Parallax's decoy star: a violet star in a soft glow, fading over its short life (the afterimage is the effect's). */
public class DecoyRenderer extends EntityRenderer<ParallaxDecoy> {
    public DecoyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ParallaxDecoy decoy, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = decoy.tickCount + partialTick;
        float k = Mth.clamp(t / 2f, 0f, 1f) * Mth.clamp((16f - t) / 4f, 0f, 1f);
        if (k <= 0.01f) {
            return;
        }
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.GLOW));
        AstroDraw.facing(glow, pose.last(), 0.7f * k, 0f, AstroDraw.VIOLET, 0.5f * k);
        VertexConsumer star = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.STAR));
        AstroDraw.facing(star, pose.last(), 0.4f * k, t * 0.3f, 0xFFFFFF, 0.9f * k);
        AstroDraw.facing(star, pose.last(), 0.5f * k, -t * 0.2f, AstroDraw.VIOLET, 0.6f * k);
        pose.popPose();
        super.render(decoy, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ParallaxDecoy decoy) {
        return TextureAtlas.LOCATION_PARTICLES;
    }
}
