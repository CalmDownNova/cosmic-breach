package com.cosmicbreach.client.guardian.heliarch;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.guardian.heliarch.StarSeed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A Star Seed: a violet-white star turning in a haze of the Hollow's light, around a dark heart, bright enough to
 * follow across the whole arena. Drawn as light (no model).
 */
public class StarSeedRenderer extends EntityRenderer<StarSeed> {
    public StarSeedRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(StarSeed seed, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, seed.getBbHeight() / 2.0, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        double time = seed.tickCount + partialTick;
        pose.mulPose(Axis.ZP.rotation((float) (time * 0.12)));
        Matrix4f m = pose.last().pose();
        Vec3 r = new Vec3(1, 0, 0);
        Vec3 u = new Vec3(0, 1, 0);
        float throb = 0.85f + 0.15f * (float) Math.sin(time * 0.5);
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.shade(ShardDraw.GLOW)), m, Vec3.ZERO, r.scale(0.55), u.scale(0.55), 0.05f, 0.0f, 0.1f, 0.7f);
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), m, Vec3.ZERO, r.scale(2.6), u.scale(2.6), 0.6f, 0.25f, 1.0f,
                0.8f * throb);
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), m, Vec3.ZERO, r.scale(1.4), u.scale(1.4), 1.0f, 0.75f, 0.4f,
                0.6f * throb);
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.STAR)), m, Vec3.ZERO, r.scale(1.5), u.scale(1.5), 1.0f, 0.85f, 1.0f,
                1.0f);
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), m, Vec3.ZERO, r.scale(0.5), u.scale(0.5), 1f, 1f, 1f, 1.0f);
        pose.popPose();
        super.render(seed, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StarSeed seed) {
        return ShardDraw.GLOW;
    }
}
