package com.cosmicbreach.client.familiar;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.guardian.GuardianGlowLayer;
import com.cosmicbreach.familiar.FamiliarEntity;
import com.cosmicbreach.familiar.FamiliarKind;
import com.cosmicbreach.familiar.FamiliarMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A familiar through GeckoLib, its light added over it full bright ({@link GuardianGlowLayer}): the Emberwisp is
 * nearly all light (its body is lit as if in full light too, it is a sun), the Gravikin's chest crack and pebbles
 * glow violet-blue (brighter through a taunt), the Prism Moth's glass wings are see-through with rainbow edges that
 * glow. Passive familiars glow dimmer.
 */
public class FamiliarRenderer<T extends FamiliarEntity> extends GeoEntityRenderer<T> {
    private final FamiliarKind kind;

    public FamiliarRenderer(EntityRendererProvider.Context context, FamiliarKind kind) {
        super(context, new FamiliarModel<>(kind));
        this.kind = kind;
        ResourceLocation glow = CosmicBreach.id("textures/entity/" + kind.id() + "_glowmask.png");
        addRenderLayer(new GuardianGlowLayer<>(this, e -> glow, this::glow));
        if (kind == FamiliarKind.PRISM_MOTH) {
            withScale(0.5f); // built at twice the size so its glass wings get the texels
        } else if (kind == FamiliarKind.EMBERWISP) {
            withScale(0.8f); // a small sun: its corona a little over half a block across
        }
        this.shadowRadius = kind == FamiliarKind.GRAVIKIN ? 0.32f : 0.0f;
        this.shadowStrength = 0.6f;
    }

    /** How strongly its glow texture adds: a slow pulse, a flare through a taunt, dimmer when Passive. */
    private int glow(T e) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double t = e.level().getGameTime() + partial + e.getId() * 13.0;
        double k = switch (kind) {
            case EMBERWISP -> 0.86 + 0.14 * Math.sin(t * 0.31) * Math.sin(t * 0.13 + 1.0);
            case GRAVIKIN -> 0.75 + 0.1 * Math.sin(t * 0.08) + 0.35 * FamiliarFx.taunting(e, partial);
            case PRISM_MOTH -> 0.85 + 0.15 * Math.sin(t * 0.17);
        };
        if (e.mode() == FamiliarMode.PASSIVE) {
            k *= 0.6;
        }
        int v = (int) Math.round(255 * Math.max(0.0, Math.min(1.0, k)));
        return (v << 16) | (v << 8) | v;
    }

    /** The Emberwisp also shines: a soft warm halo facing the camera round its core. */
    @Override
    public void render(T entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (kind != FamiliarKind.EMBERWISP || entity.isInvisible()) {
            return;
        }
        double t = entity.level().getGameTime() + partialTick + entity.getId() * 13.0;
        float a = (float) ((0.55 + 0.1 * Math.sin(t * 0.23)) * (entity.mode() == FamiliarMode.PASSIVE ? 0.6 : 1.0));
        float half = 0.62f;
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() * 0.5, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = poseStack.last();
        com.mojang.blaze3d.vertex.VertexConsumer out = bufferSource.getBuffer(
                com.cosmicbreach.client.fx.FxRenderTypes.additive(com.cosmicbreach.client.fx.FxRenderTypes.GLOW));
        out.addVertex(pose, -half, -half, 0f).setUv(0f, 1f).setColor(1.0f, 0.68f, 0.28f, a);
        out.addVertex(pose, -half, half, 0f).setUv(0f, 0f).setColor(1.0f, 0.68f, 0.28f, a);
        out.addVertex(pose, half, half, 0f).setUv(1f, 0f).setColor(1.0f, 0.68f, 0.28f, a);
        out.addVertex(pose, half, -half, 0f).setUv(1f, 1f).setColor(1.0f, 0.68f, 0.28f, a);
        poseStack.popPose();
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return kind == FamiliarKind.PRISM_MOTH ? RenderType.entityTranslucent(texture) : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    /** A sun lights itself. */
    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return kind == FamiliarKind.EMBERWISP ? 15 : super.getBlockLightLevel(entity, pos);
    }
}
