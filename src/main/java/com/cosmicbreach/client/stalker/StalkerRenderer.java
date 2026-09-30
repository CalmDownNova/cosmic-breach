package com.cosmicbreach.client.stalker;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.entity.stalker.HollowStalker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Hollow Stalker through GeckoLib, drawn to be nearly invisible (GDD 7.1). Its body is translucent void (the
 * texture's own alpha: a darkening of what is behind it, black on black in the dark, a faint silhouette in lichen
 * light), casting no shadow. Only the porcelain mask reads: its bones glow faintly on their own (a light floor, so the
 * pale face shows in pitch dark), full bright while the mask flares; the magenta eye slits are added light
 * ({@link GuardianGlowLayer}), stronger in a flare.
 */
public class StalkerRenderer extends GeoEntityRenderer<HollowStalker> {
    static final ResourceLocation GLOW = CosmicBreach.id("textures/entity/hollow_stalker_glowmask.png");
    /** The mask's own glow: block light it never shows less than (pale in pitch dark). */
    static final int MASK_LIGHT = 11;

    public StalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new StalkerModel());
        addRenderLayer(new GuardianGlowLayer<>(this, s -> GLOW, StalkerRenderer::eyes));
        this.shadowRadius = 0.0f;
    }

    /** The body as the model draws it, then a faint glow round the mask that swells magenta when it flares. */
    @Override
    public void render(HollowStalker stalker, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(stalker, entityYaw, partialTick, poseStack, buffers, packedLight);
        if (!stalker.isAlive()) {
            return;
        }
        float flare = flare(stalker, partialTick);
        float yaw = net.minecraft.util.Mth.rotLerp(partialTick, stalker.yBodyRotO, stalker.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
        poseStack.pushPose();
        poseStack.translate(-net.minecraft.util.Mth.sin(yaw) * 0.18, com.cosmicbreach.entity.stalker.StalkerRules.MASK_Y,
                net.minecraft.util.Mth.cos(yaw) * 0.18);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer glow = buffers.getBuffer(com.cosmicbreach.client.fx.FxRenderTypes.additive(com.cosmicbreach.client.fx.FxRenderTypes.GLOW));
        org.joml.Matrix4f m = poseStack.last().pose();
        float half = 0.42f + 0.45f * flare;
        float r = 0.55f + 0.45f * flare;
        float g = 0.45f - 0.25f * flare;
        float b = 0.7f + 0.3f * flare;
        float a = 0.1f + 0.55f * flare;
        glow.addVertex(m, -half, -half, 0f).setUv(0f, 1f).setColor(r, g, b, a);
        glow.addVertex(m, -half, half, 0f).setUv(0f, 0f).setColor(r, g, b, a);
        glow.addVertex(m, half, half, 0f).setUv(1f, 0f).setColor(r, g, b, a);
        glow.addVertex(m, half, -half, 0f).setUv(1f, 1f).setColor(r, g, b, a);
        poseStack.popPose();
    }

    @Override
    public RenderType getRenderType(HollowStalker animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, HollowStalker animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        int light = packedLight;
        if (!isReRender && bone.getName().startsWith("mask")) {
            float flare = flare(animatable, partialTick);
            int block = Math.max(LightTexture.block(packedLight), Math.round(MASK_LIGHT + (15 - MASK_LIGHT) * flare));
            light = LightTexture.pack(block, LightTexture.sky(packedLight));
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, light,
                packedOverlay, colour);
    }

    /** How hard the mask flares now, 0 to 1: caught, or telling a Rend (rising to the glint). */
    static float flare(HollowStalker s, float partialTick) {
        return switch (s.state()) {
            case CAUGHT -> 1.0f;
            case REND_TELL -> Math.min(1.0f, 0.35f + 0.65f * (s.clientStateTicks() + partialTick) / 16f);
            case GRASPING -> 0.5f;
            default -> 0.0f;
        };
    }

    /** The eye slits' magenta: a low smoulder, full and pulsing while the mask flares. */
    private static int eyes(HollowStalker s) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float f = flare(s, partial);
        double t = s.tickCount + partial;
        double k = 0.55 + 0.1 * Math.sin(t * 0.21) + 0.45 * f * (0.8 + 0.2 * Math.sin(t * 1.3));
        int r = (int) Math.round(255 * Math.min(1.0, k));
        int g = (int) Math.round(60 * Math.min(1.0, k) + 90 * f);
        int b = (int) Math.round(215 * Math.min(1.0, k));
        return (r << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}
