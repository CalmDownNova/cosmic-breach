package com.cosmicbreach.client.guardian.heliarch;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.guardian.heliarch.ReliquaryBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A Reliquary's light: a column of warm light over each one still closed, bright over the one that is yours, and its
 * owner's name above it, so a party finds their own at a glance.
 */
public class ReliquaryRenderer implements BlockEntityRenderer<ReliquaryBlockEntity> {
    private final Font font;

    public ReliquaryRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ReliquaryBlockEntity r, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (r.opened() || r.getLevel() == null) {
            return;
        }
        boolean yours = r.yoursToOpen();
        double time = r.getLevel().getGameTime() + partialTick;
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 0.15);
        pose.pushPose();
        pose.translate(0.5, 1.2, 0.5);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        Matrix4f m = pose.last().pose();
        float size = yours ? 1.1f : 0.6f;
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), m, Vec3.ZERO, new Vec3(size, 0, 0), new Vec3(0, size * 1.6, 0),
                1f, 0.75f, 0.3f, (yours ? 0.7f : 0.35f) * pulse);
        if (yours) {
            ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.STAR)), m, new Vec3(0, 0.1, 0), new Vec3(0.45, 0, 0),
                    new Vec3(0, 0.45, 0), 1f, 0.95f, 0.8f, 0.9f * pulse);
        }
        pose.popPose();
        if (!r.ownerName().isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 1.75, 0.5);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            pose.scale(0.025f, -0.025f, 0.025f);
            Component name = Component.literal(r.ownerName());
            float x = -font.width(name) / 2f;
            int colour = yours ? 0xFFFFE7A6 : 0xFFC9B98A;
            font.drawInBatch(name, x, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x40000000, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ReliquaryBlockEntity r) {
        return true;
    }
}
