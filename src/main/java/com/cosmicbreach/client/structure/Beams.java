package com.cosmicbreach.client.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Beams drawn like a beacon's (GDD 6.2): a streaked texture scrolling along a spinning square tube, a solid core
 * and a faint wider glow, in any direction and of any length. Uses the beacon's render types (with a neutral
 * texture of our own), so every beam of a frame goes into the same two batched buffers.
 */
public final class Beams {
    private static final int FULL_BRIGHT = 15728880;
    /** Neutral white streaks: the vanilla beacon texture is cyan and would turn every colour green. */
    public static final net.minecraft.resources.ResourceLocation TEXTURE = com.cosmicbreach.CosmicBreach.id("textures/fx/lens_beam.png");

    private Beams() {
    }

    /** The colour of each beam colour, 0xRRGGBB: warm white-gold sunlight, deep gold, teal, magenta. */
    public static int rgb(int color) {
        return switch (color & 3) {
            case 1 -> 0xFFA914;
            case 2 -> 0x35E3D0;
            case 3 -> 0xFF4FD8;
            default -> 0xFFEDB8;
        };
    }

    /**
     * A beam from {@code from} to {@code to} (relative to the pose), core half-width {@code core}, glow half-width
     * {@code glow}, glow alpha {@code glowAlpha} (0 to 255); {@code time} in ticks drives the scroll and spin.
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, int rgb, float core, float glow,
            int glowAlpha, float time) {
        Vec3 d = to.subtract(from);
        float length = (float) d.length();
        if (length < 1.0e-3f) {
            return;
        }
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).div(length);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        float spin = Mth.positiveModulo(time, 160.0f);
        pose.mulPose(Axis.YP.rotationDegrees(spin * 2.25f - 45.0f));
        float scroll = Mth.frac(-time * 0.2f / 4.0f);
        tube(pose, buffers.getBuffer(RenderType.beaconBeam(TEXTURE, false)), 0xFF000000 | rgb, length, core,
                scroll, scroll + length * (0.5f / Math.max(core, 0.05f)));
        tube(pose, buffers.getBuffer(RenderType.beaconBeam(TEXTURE, true)), (glowAlpha & 0xFF) << 24 | rgb, length, glow,
                scroll, scroll + length);
        pose.popPose();
    }

    /** A square tube along +Y from 0 to {@code length}, half-width {@code r}. */
    private static void tube(PoseStack pose, VertexConsumer c, int argb, float length, float r, float v0, float v1) {
        PoseStack.Pose p = pose.last();
        float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) & 3];
            vertex(p, c, argb, a[0], length, a[1], 1.0f, v1);
            vertex(p, c, argb, a[0], 0.0f, a[1], 1.0f, v0);
            vertex(p, c, argb, b[0], 0.0f, b[1], 0.0f, v0);
            vertex(p, c, argb, b[0], length, b[1], 0.0f, v1);
        }
    }

    /** A flat glowing quad (both faces) with corners a, b, c, d, relative to the pose. */
    public static void quad(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        VertexConsumer v = buffers.getBuffer(RenderType.beaconBeam(TEXTURE, true));
        PoseStack.Pose p = pose.last();
        Vec3[] q = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            vertex(p, v, argb, (float) q[i].x, (float) q[i].y, (float) q[i].z, i < 2 ? 0.0f : 1.0f, i == 0 || i == 3 ? 0.0f : 1.0f);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(p, v, argb, (float) q[i].x, (float) q[i].y, (float) q[i].z, i < 2 ? 0.0f : 1.0f, i == 0 || i == 3 ? 0.0f : 1.0f);
        }
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer c, int argb, float x, float y, float z, float u, float v) {
        c.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(p, 0.0f, 1.0f, 0.0f);
    }
}
