package com.cosmicbreach.client.entity;

import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.entity.shardling.ShardFragment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A shard waiting to burst: a small faceted turquoise crystal, spinning and bobbing, with a soft glow
 * that flares on every chime; and on the ground under it the telegraph language's decal: a red
 * outline at the burst's reach ({@value ShardFragment#RADIUS} blocks) with a fill that grows to the
 * edge as the fuse burns. When it is full, it bursts.
 *
 * <p>The crystal is real geometry, a bipyramid with its waist low (a long point up, a short one
 * down), drawn as an ordinary solid entity with face normals: the game's own entity lighting shades
 * its facets, so it holds up as a crystal right in front of the camera (a flat sprite didn't). It is
 * full bright, since it glows. Each shard leans and stretches a little differently.
 */
public class ShardFragmentRenderer extends EntityRenderer<ShardFragment> {
    static final int DANGER = 0xE8433A;
    private static final int CRYSTAL = 0x48DCCF;
    private static final int PALE = 0xC4F8F0;
    private static final int DEEP = 0x1A8B8D;
    private static final int GLOW = 0x60E8D8;
    /** The crystal: this far up to its point and down to its foot from the waist, and this wide there. */
    private static final float POINT = 0.2f;
    private static final float FOOT = 0.09f;
    private static final float GIRTH = 0.085f;
    private static final double LIFT = 0.22;
    private static final double DECAL_UP = 0.03;
    /** How far down to look for the ground under a shard still in the air. */
    private static final double GROUND_SEARCH = 4.0;

    public ShardFragmentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(ShardFragment fragment) {
        return ShardDraw.GLOW;
    }

    /** Its decal reaches well past its own box: keep drawing while the decal is in view. */
    @Override
    public boolean shouldRender(ShardFragment fragment, Frustum frustum, double camX, double camY, double camZ) {
        return fragment.shouldRender(camX, camY, camZ)
                && frustum.isVisible(fragment.getBoundingBox().inflate(ShardFragment.RADIUS + 0.5, 1.0, ShardFragment.RADIUS + 0.5));
    }

    @Override
    public void render(ShardFragment fragment, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        Camera camera = entityRenderDispatcher.camera;
        Matrix4f pose = poseStack.last().pose();
        float age = fragment.tickCount + partialTick;
        float fuse = fragment.fuse(partialTick);
        float pulse = chimePulse(age);

        Vec3 ground = groundBelow(fragment, partialTick);
        if (ground != null) {
            VertexConsumer solid = buffers.getBuffer(ShardDraw.solid());
            float[] red = ShardDraw.rgb(DANGER);
            double reach = ShardFragment.RADIUS;
            double fill = reach * fuse;
            ShardDraw.disc(solid, pose, ground, fill, 32, red[0], red[1], red[2], 0.22f + 0.18f * fuse);
            ShardDraw.band(solid, pose, ground.add(0.0, 0.002, 0.0), Math.max(0.0, fill - 0.06), fill, 32, red[0], red[1], red[2], 0.7f);
            ShardDraw.band(solid, pose, ground.add(0.0, 0.004, 0.0), reach - 0.09, reach, 48, red[0], red[1], red[2], 0.9f);
        }

        double bob = Math.sin(age * 0.3) * 0.03;
        crystal(fragment, poseStack, buffers, age, bob, pulse);

        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        Vec3 centre = new Vec3(0.0, LIFT + bob + 0.04, 0.0);
        double seen = fragment.getPosition(partialTick).add(centre).distanceTo(camera.getPosition());
        double glow = Math.min(0.26 + 0.2 * fuse + 0.18 * pulse, seen * 0.35);
        float[] g = ShardDraw.rgb(GLOW);
        ShardDraw.quad(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), pose, centre,
                new Vec3(l.x(), l.y(), l.z()).scale(glow), new Vec3(u.x(), u.y(), u.z()).scale(glow),
                g[0], g[1], g[2], 0.4f + 0.45f * pulse);
        super.render(fragment, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** The crystal: spinning about its lean, flaring toward white on each chime. */
    private static void crystal(ShardFragment fragment, PoseStack poseStack, MultiBufferSource buffers, float age, double bob,
                                float pulse) {
        int seed = fragment.getId() * 0x9E3779B1;
        float lean = 12f + (seed >>> 8 & 15);                   // 12 to 27 degrees
        float leanAround = (seed >>> 12 & 255) / 255f * 360f;    // which way it leans
        float stretch = 0.9f + (seed >>> 20 & 7) * 0.04f;        // 0.9 to 1.18
        poseStack.pushPose();
        poseStack.translate(0.0, LIFT + bob, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(leanAround));
        poseStack.mulPose(Axis.ZP.rotationDegrees(lean));
        poseStack.mulPose(Axis.YP.rotation(age * 0.16f));
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer out = buffers.getBuffer(RenderType.entitySolid(ShardDraw.GLOW));
        Vector3f top = new Vector3f(0f, POINT * stretch, 0f);
        Vector3f bottom = new Vector3f(0f, -FOOT, 0f);
        Vector3f[] waist = {
                new Vector3f(GIRTH, 0f, 0f), new Vector3f(0f, 0f, -GIRTH * 0.8f),
                new Vector3f(-GIRTH, 0f, 0f), new Vector3f(0f, 0f, GIRTH * 0.8f)};
        float[] upper = mix(ShardDraw.rgb(CRYSTAL), ShardDraw.rgb(PALE), 0.35f + 0.65f * pulse);
        float[] upperAlt = mix(ShardDraw.rgb(CRYSTAL), ShardDraw.rgb(PALE), 0.1f + 0.6f * pulse);
        float[] lower = mix(ShardDraw.rgb(DEEP), ShardDraw.rgb(CRYSTAL), 0.55f + 0.45f * pulse);
        for (int i = 0; i < 4; i++) {
            Vector3f a = waist[i];
            Vector3f b = waist[(i + 1) % 4];
            facet(out, pose, a, b, top, i % 2 == 0 ? upper : upperAlt);
            facet(out, pose, b, a, bottom, lower);
        }
        poseStack.popPose();
    }

    /** One triangular facet, wound to face outward (the crystal is convex round its origin), as a quad. */
    private static void facet(VertexConsumer out, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, float[] rgb) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        Vector3f centroid = new Vector3f(a).add(b).add(c);
        if (normal.dot(centroid) < 0f) {
            Vector3f swap = a;
            a = b;
            b = swap;
            normal.negate();
        }
        for (Vector3f v : new Vector3f[] {a, b, c, c}) {
            out.addVertex(pose, v.x, v.y, v.z).setColor(rgb[0], rgb[1], rgb[2], 1.0f).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, normal.x, normal.y, normal.z);
        }
    }

    private static float[] mix(float[] a, float[] b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    /** 1 on a chime, fading to 0 over 4 ticks. */
    private static float chimePulse(float age) {
        float since = Float.MAX_VALUE;
        for (int chime : ShardFragment.CHIMES) {
            if (chime <= age) {
                since = age - chime;
            }
        }
        return Math.max(0.0f, 1.0f - since / 4.0f);
    }

    /** The floor under the shard, relative to it, just above the surface; null if there is none close. */
    private static @Nullable Vec3 groundBelow(ShardFragment fragment, float partialTick) {
        if (fragment.onGround()) {
            return new Vec3(0.0, DECAL_UP, 0.0);
        }
        Vec3 from = fragment.getPosition(partialTick).add(0.0, 0.05, 0.0);
        Vec3 to = from.subtract(0.0, GROUND_SEARCH, 0.0);
        BlockHitResult hit = fragment.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, fragment));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return new Vec3(0.0, hit.getLocation().y - fragment.getPosition(partialTick).y + DECAL_UP, 0.0);
    }
}
