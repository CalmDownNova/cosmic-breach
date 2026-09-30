package com.cosmicbreach.client.relic;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.relic.crown.CrownBlockEntity;
import com.cosmicbreach.relic.crown.HeliarchsCrownBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

/**
 * The Heliarch's Crown's small sun (GDD 7.3): a bright white-gold point in a golden glow with slow turning rays,
 * orbiting the crown once every {@value HeliarchsCrownBlock#ORBIT_TICKS} ticks and bobbing a little, a faint wake of
 * light behind it; and a soft glow over the sun gem at the crown's heart.
 */
public class CrownRenderer implements BlockEntityRenderer<CrownBlockEntity> {
    private static final int WAKE = 5;

    public CrownRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CrownBlockEntity crown, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = crown.getLevel();
        if (level == null) {
            return;
        }
        double time = level.getGameTime() + partialTick;
        Quaternionf facing = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        double r = HeliarchsCrownBlock.ORBIT_RADIUS / 16.0;
        double y = HeliarchsCrownBlock.ORBIT_Y / 16.0;
        // one buffer at a time: every glow first (the wake, the sun, the gem), then the sun's rays
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
        for (int i = WAKE; i >= 0; i--) {
            sun(pose, crown, time - i * 3.0, r, y, facing);
            if (i == 0) {
                RelicDraw.facing(glow, pose.last(), 0.24f, 0f, RelicDraw.SUN_GOLD, 0.85f);
                RelicDraw.facing(glow, pose.last(), 0.08f, 0f, 0xFFFFFF, 1.0f);
            } else {
                float fade = 1f - i / (float) (WAKE + 1);
                RelicDraw.facing(glow, pose.last(), 0.1f * fade + 0.02f, 0f, RelicDraw.SUN_DEEP, 0.35f * fade);
            }
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0.5, 11.5 / 16.0, 0.5);
        pose.mulPose(facing);
        RelicDraw.facing(glow, pose.last(), 0.2f + 0.02f * (float) Math.sin(time * 0.15), 0f, RelicDraw.SUN_GOLD, 0.45f);
        pose.popPose();
        VertexConsumer rays = buffers.getBuffer(FxRenderTypes.additive(RelicDraw.RAYS));
        sun(pose, crown, time, r, y, facing);
        RelicDraw.facing(rays, pose.last(), 0.2f, (float) (time * 0.05), RelicDraw.SUN_WHITE, 0.9f);
        pose.popPose();
    }

    /** Pushes the pose to the sun's place at {@code time} (turned to face the camera); the caller pops it. */
    private static void sun(PoseStack pose, CrownBlockEntity crown, double time, double r, double y, Quaternionf facing) {
        double a = HeliarchsCrownBlock.orbitAngle(time, crown.getBlockPos());
        pose.pushPose();
        pose.translate(0.5 + Math.cos(a) * r, y + 0.03 * Math.sin(time * 0.1), 0.5 + Math.sin(a) * r);
        pose.mulPose(facing);
    }
}
