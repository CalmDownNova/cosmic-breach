package com.cosmicbreach.client.relic;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.relic.cantor.UmbraArrow;
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
import net.minecraft.world.phys.Vec3;

/**
 * An arrow of the Umbra Cantor: a shaft of shadow (drawn dark over the scene) with a violet head of light and a violet
 * trail of its last positions. A note arrow's head is bigger and paler; a heavy shot's trail is wider.
 */
public class UmbraArrowRenderer extends EntityRenderer<UmbraArrow> {
    public UmbraArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(UmbraArrow arrow, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        Vec3 head = arrow.getPosition(partialTick);
        Vec3 dir = arrow.getDeltaMovement();
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        byte style = arrow.style();
        boolean note = style == UmbraArrow.NOTE;
        float near = (float) Math.max(0.3, Math.min(1.0, (head.distanceTo(cam) - 0.6) / 2.0));
        Vec3 h = head.subtract(cam);
        Vec3 tail = h.subtract(dir.scale(0.95));
        VertexConsumer dark = buffers.getBuffer(FxRenderTypes.shade(RelicDraw.GLOW));
        RelicDraw.ribbon(dark, camera, tail, h, 0.05f, RelicDraw.SHADOW, 0.7f, RelicDraw.SHADOW, 0.95f);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
        RelicDraw.ribbon(glow, camera, tail, h, 0.02f, RelicDraw.DEEP_VIOLET, 0.2f, RelicDraw.VIOLET, 0.8f);
        int color = note ? RelicDraw.PALE_VIOLET : RelicDraw.VIOLET;
        float size = (note ? 0.3f : 0.17f) * near;
        RelicDraw.billboard(glow, camera, h, size * 1.6f, 0f, color, note ? 0.7f : 0.55f);
        RelicDraw.billboard(glow, camera, h, size * 0.55f, 0f, 0xFFFFFF, 0.9f);
        List<Vec3> points = arrow.trail();
        if (points.size() >= 2) {
            points.set(points.size() - 1, head);
            int n = points.size();
            float width = style == UmbraArrow.HEAVY ? 0.1f : note ? 0.09f : 0.06f;
            for (int i = 0; i < n - 1; i++) {
                float fa = (float) i / (n - 1);
                float fb = (float) (i + 1) / (n - 1);
                RelicDraw.ribbon(glow, camera, points.get(i).subtract(cam), points.get(i + 1).subtract(cam), width * (0.3f + 0.7f * fb),
                        RelicDraw.DEEP_VIOLET, 0.45f * fa * fa, color, 0.6f * fb * fb);
            }
        }
        if (note) {
            VertexConsumer glyph = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.NOTE));
            RelicDraw.billboard(glyph, camera, h, 0.16f * near, 0f, RelicDraw.PALE_VIOLET, 0.9f);
        }
        super.render(arrow, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(UmbraArrow arrow) {
        return TextureAtlas.LOCATION_PARTICLES;
    }
}
