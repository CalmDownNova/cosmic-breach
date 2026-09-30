package com.cosmicbreach.client.familiar;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.familiar.BrazierBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * What sits in a Brazier of Solenne's bowl: the Star Egg (turning slowly, a warm glow over the bowl that grows as it
 * nears hatching) or the lantern it hatched into (bobbing, waiting to be taken).
 */
public class BrazierRenderer implements BlockEntityRenderer<BrazierBlockEntity> {
    public BrazierRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BrazierBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack held = be.held();
        if (held.isEmpty() || be.getLevel() == null) {
            return;
        }
        double time = be.getLevel().getGameTime() + partialTick;
        boolean egg = be.warming();
        float share = egg ? Math.min(1f, (be.progress() + partialTick) / (float) Math.max(1, BrazierBlockEntity.hatchTicks())) : 0f;
        pose.pushPose();
        double bob = egg ? 0.01 * Math.sin(time * 0.15) : 0.05 * Math.sin(time * 0.1);
        pose.translate(0.5, (egg ? 0.93 : 1.02) + bob, 0.5);
        // it faces whoever looks at it (an item is a flat picture: turning, it would show its edge), with a slow sway
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        pose.mulPose(Axis.YP.rotationDegrees(180f - cam.getYRot() + (float) (12.0 * Math.sin(time * 0.05))));
        if (egg && share > 0.85f) {
            // it rocks as it is about to hatch
            pose.mulPose(Axis.ZP.rotationDegrees((float) (7.0 * Math.sin(time * 0.9) * (share - 0.85f) / 0.15f)));
        }
        float s = egg ? 0.55f : 0.6f;
        pose.scale(s, s, s);
        int lit = egg ? LightTexture.pack(Math.max(12, LightTexture.block(light)), LightTexture.sky(light)) : light;
        Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.FIXED, lit, OverlayTexture.NO_OVERLAY, pose, buffers,
                be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
        if (egg) {
            glow(be, pose, buffers, share, time);
        }
    }

    /** A soft warm light over the bowl, brighter as the egg nears hatching. */
    private static void glow(BrazierBlockEntity be, PoseStack pose, MultiBufferSource buffers, float share, double time) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        float a = (0.18f + 0.35f * share) * (float) (0.85 + 0.15 * Math.sin(time * 0.2));
        pose.pushPose();
        pose.translate(0.5, 0.95, 0.5);
        com.mojang.blaze3d.vertex.PoseStack.Pose last = pose.last();
        org.joml.Vector3f left = camera.getLeftVector();
        org.joml.Vector3f up = camera.getUpVector();
        float half = 0.45f + 0.25f * share;
        float[][] corners = {{-1, -1, 1, 1}, {-1, 1, 1, 0}, {1, 1, 0, 0}, {1, -1, 0, 1}};
        for (float[] c : corners) {
            float x = (left.x() * c[0] + up.x() * c[1]) * half;
            float y = (left.y() * c[0] + up.y() * c[1]) * half;
            float z = (left.z() * c[0] + up.z() * c[1]) * half;
            out.addVertex(last, x, y, z).setUv(c[2], c[3]).setColor(1.0f, 0.72f, 0.32f, a);
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BrazierBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
