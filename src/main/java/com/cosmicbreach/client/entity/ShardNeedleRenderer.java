package com.cosmicbreach.client.entity;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.entity.shardling.ShardNeedle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A needle in flight: the thin shard sprite stretched along its flight and turned to face the camera,
 * solid turquoise so it reads against a white floor or a bright sky, with an additive glow streak
 * trailing it and a small glint at its point so it still shows from across the arena.
 */
public class ShardNeedleRenderer extends EntityRenderer<ShardNeedle> {
    static final int CRYSTAL = 0x48DCCF;
    static final int PALE = 0xC4F8F0;
    private static final double LENGTH = 0.9;
    private static final double WIDTH = 0.36;

    public ShardNeedleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(ShardNeedle needle) {
        return ShardDraw.NEEDLE;
    }

    @Override
    public void render(ShardNeedle needle, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        Camera camera = entityRenderDispatcher.camera;
        Vec3 along = direction(needle, partialTick);
        Vec3 centre = new Vec3(0.0, needle.getBbHeight() * 0.5, 0.0);
        Vec3 view = needle.getPosition(partialTick).add(centre).subtract(camera.getPosition());
        Vec3 side = view.cross(along);
        if (side.lengthSqr() < 1e-8) {
            Vector3f left = camera.getLeftVector();
            side = new Vec3(left.x(), left.y(), left.z());
        } else {
            side = side.normalize();
        }
        Matrix4f pose = poseStack.last().pose();
        float[] core = ShardDraw.rgb(CRYSTAL);
        float[] pale = ShardDraw.rgb(PALE);
        ShardDraw.diamond(buffers.getBuffer(ShardDraw.translucent(ShardDraw.NEEDLE, false)), pose, centre, along, side,
                LENGTH, WIDTH, core[0], core[1], core[2], 1.0f);
        ShardDraw.streak(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.SPARK)), pose, centre, along, side,
                1.2, 0.35, 0.2, pale[0], pale[1], pale[2], 0.8f);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        double s = 0.2;
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.STAR)), pose, centre.add(along.scale(LENGTH * 0.4)),
                new Vec3(left.x(), left.y(), left.z()).scale(s), new Vec3(up.x(), up.y(), up.z()).scale(s), 1.0f, 1.0f, 1.0f, 0.9f);
        super.render(needle, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** Along its flight: its velocity, or its rotation when it has none (projectile yaw is atan2(x, z)). */
    private static Vec3 direction(ShardNeedle needle, float partialTick) {
        Vec3 v = needle.getDeltaMovement();
        if (v.lengthSqr() > 1e-6) {
            return v.normalize();
        }
        float yaw = Mth.lerp(partialTick, needle.yRotO, needle.getYRot()) * Mth.DEG_TO_RAD;
        float pitch = Mth.lerp(partialTick, needle.xRotO, needle.getXRot()) * Mth.DEG_TO_RAD;
        return new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }
}
