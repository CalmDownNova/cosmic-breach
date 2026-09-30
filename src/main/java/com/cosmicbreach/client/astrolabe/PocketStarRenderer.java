package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.astrolabe.PocketStar;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Pocket Star (GDD 4.2): "a miniature sun (a billboard with a corona) with orbiting motes". A white-hot core, a
 * slowly turning corona that breathes, a wide warm halo, and five motes on tilted orbits; it swells in over its first
 * ticks and gutters in its last ten. A Singularity (inside a Gravity Well) is ringed by a dark lens and burns violet.
 */
public class PocketStarRenderer extends EntityRenderer<PocketStar> {
    private static final int MOTES = 5;

    public PocketStarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(PocketStar star, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = star.tickCount + partialTick;
        int life = star.life();
        float in = Mth.clamp(t / 5f, 0f, 1f);
        float left = life - t;
        float out = left < 10f ? Mth.clamp(left / 10f, 0f, 1f) * (0.75f + 0.25f * (float) Math.sin(t * 2.3)) : 1f;
        float k = in * out;
        if (k <= 0.01f) {
            return;
        }
        boolean singular = star.singular();
        int corona = singular ? AstroDraw.VIOLET : AstroDraw.SUN;
        float breathe = 1f + 0.08f * (float) Math.sin(t * 0.5);
        pose.pushPose();
        pose.translate(0, 0.25, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        // one buffer at a time: a shared buffer source ends a batch when the next type is asked for
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.GLOW));
        AstroDraw.facing(glow, pose.last(), 2.2f * k * breathe, 0f, corona, 0.35f * k);
        AstroDraw.facing(glow, pose.last(), 0.55f * k, 0f, 0xFFFFFF, 0.95f * k);
        VertexConsumer cor = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.CORONA));
        AstroDraw.facing(cor, pose.last(), 1.05f * k * breathe, t * 0.02f, corona, 0.85f * k);
        AstroDraw.facing(cor, pose.last(), 0.9f * k, -t * 0.035f + 0.7f, AstroDraw.WHITE_GOLD, 0.5f * k);
        VertexConsumer core = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.STAR));
        AstroDraw.facing(core, pose.last(), 0.42f * k, t * 0.05f, 0xFFFFFF, k);
        if (singular) {
            VertexConsumer lens = buffers.getBuffer(FxRenderTypes.shade(AstroDraw.RING));
            AstroDraw.facing(lens, pose.last(), 1.6f * k * breathe, t * 0.03f, 0x05020A, 0.8f * k);
        }
        pose.popPose();
        motes(star, t, k, corona, buffers, partialTick);
        super.render(star, yaw, partialTick, pose, buffers, light);
    }

    /** Five motes on tilted circles round the sun, each a small star. */
    private static void motes(PocketStar star, float t, float k, int color, MultiBufferSource buffers, float partialTick) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 centre = star.getPosition(partialTick).add(0, 0.25, 0).subtract(camera.getPosition());
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.STAR));
        float[] c = WorldFx.rgb(color);
        for (int i = 0; i < MOTES; i++) {
            double a = t * (0.09 + 0.02 * i) + i * 2.1;
            double tilt = 0.5 + 0.35 * i;
            double r = 0.75 + 0.12 * i;
            Vec3 p = new Vec3(Math.cos(a) * r, Math.sin(a) * r * Math.sin(tilt), Math.sin(a) * r * Math.cos(tilt));
            WorldFx.billboard(out, camera, centre.add(p), 0.09f * k, c[0], c[1], c[2], 0.9f * k);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PocketStar star) {
        return TextureAtlas.LOCATION_PARTICLES;
    }

    @Override
    public boolean shouldRender(PocketStar star, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }
}
