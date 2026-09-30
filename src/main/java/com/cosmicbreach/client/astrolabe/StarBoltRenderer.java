package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.astrolabe.StarBolt;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
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
 * A star bolt (GDD 4.2: "small stars with short trails"): a bright four-point star turning in a soft glow, and a
 * short tapering trail of the last few positions, all added light. Gold-white for the chain, pale blue for the Triad,
 * warm and bigger for the Starfall, violet for the Parallax's decoy.
 */
public class StarBoltRenderer extends EntityRenderer<StarBolt> {
    public StarBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    static int color(byte style) {
        return switch (style) {
            case StarBolt.TRIAD -> AstroDraw.PALE_BLUE;
            case StarBolt.STARFALL -> AstroDraw.SUN;
            case StarBolt.DECOY -> AstroDraw.VIOLET;
            default -> AstroDraw.GOLD;
        };
    }

    static float size(byte style) {
        return style == StarBolt.STARFALL ? 1.5f : style == StarBolt.TRIAD ? 0.85f : 1.0f;
    }

    @Override
    public void render(StarBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        byte style = bolt.style();
        int color = color(style);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double near = bolt.getPosition(partialTick).distanceTo(cam);
        float s = size(style) * (float) Math.max(0.25, Math.min(1.0, (near - 0.6) / 2.0)); // no blinding flash at the hand
        float spin = (bolt.tickCount + partialTick) * 0.35f;
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.GLOW));
        AstroDraw.facing(glow, pose.last(), 0.42f * s, 0f, color, 0.55f);
        VertexConsumer star = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.STAR));
        AstroDraw.facing(star, pose.last(), 0.26f * s, spin, 0xFFFFFF, 0.95f);
        AstroDraw.facing(star, pose.last(), 0.34f * s, -spin * 0.6f + 0.4f, color, 0.6f);
        pose.popPose();
        trail(bolt, partialTick, color, s, buffers);
        super.render(bolt, yaw, partialTick, pose, buffers, light);
    }

    /** The trail: a ribbon from the oldest kept position to the star, thinning and fading toward its tail. */
    private void trail(StarBolt bolt, float partialTick, int color, float s, MultiBufferSource buffers) {
        List<Vec3> points = bolt.trail();
        if (points.size() < 2) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        Vec3 head = bolt.getPosition(partialTick);
        points.set(points.size() - 1, head);
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.additive(AstroDraw.BEAM));
        float[] c = WorldFx.rgb(color);
        int n = points.size();
        for (int i = 0; i < n - 1; i++) {
            float fa = (float) i / (n - 1);
            float fb = (float) (i + 1) / (n - 1);
            Vec3 a = points.get(i).subtract(cam);
            Vec3 b = points.get(i + 1).subtract(cam);
            AstroDraw.ribbon(out, camera, a, b, 0.07f * s * Mth.lerp(fb, 0.3f, 1f), c, 0.5f * fa * fa, c, 0.6f * fb * fb);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(StarBolt bolt) {
        return TextureAtlas.LOCATION_PARTICLES;
    }
}
