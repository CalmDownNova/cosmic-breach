package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Drawing helpers shared by the Astrolabe's effects: its textures, camera-facing stars, beams and spheres. Client only. */
final class AstroDraw {
    static final ResourceLocation STAR = CosmicBreach.id("textures/fx/astrolabe_star.png");
    static final ResourceLocation CORONA = CosmicBreach.id("textures/fx/astrolabe_corona.png");
    static final ResourceLocation GLOW = FxRenderTypes.GLOW;
    static final ResourceLocation BEAM = FxRenderTypes.BEAM;
    static final ResourceLocation RING = CosmicBreach.id("textures/particle/ring.png");

    static final int GOLD = 0xFFE3A0;
    static final int WHITE_GOLD = 0xFFF6DC;
    static final int PALE_BLUE = 0xCFEBFF;
    static final int VIOLET = 0xD9C2FF;
    static final int SUN = 0xFFC45C;
    static final int DEEP_VIOLET = 0x6B2BD9;

    private AstroDraw() {
    }

    /** A square facing the camera in a pose already turned to it (entity renderers), {@code half} across, turned {@code roll}. */
    static void facing(VertexConsumer out, PoseStack.Pose pose, float half, float roll, int color, float alpha) {
        float[] c = WorldFx.rgb(color);
        Matrix4f m = pose.pose();
        float cs = (float) Math.cos(roll) * half;
        float sn = (float) Math.sin(roll) * half;
        vertex(out, m, -cs + sn, -sn - cs, 0f, 1f, c, alpha);
        vertex(out, m, -cs - sn, -sn + cs, 0f, 0f, c, alpha);
        vertex(out, m, cs - sn, sn + cs, 1f, 0f, c, alpha);
        vertex(out, m, cs + sn, sn - cs, 1f, 1f, c, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float u, float v, float[] c, float a) {
        out.addVertex(m, x, y, 0f).setUv(u, v).setColor(c[0], c[1], c[2], a);
    }

    /** A camera-facing ribbon from {@code a} to {@code b} (camera-relative), {@code half} wide. */
    static void ribbon(VertexConsumer out, Camera camera, Vec3 a, Vec3 b, float half, float[] ca, float alphaA, float[] cb, float alphaB) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1e-8) {
            return;
        }
        Vec3 view = a.add(b).scale(0.5); // from the camera (at the origin) to the middle
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1e-8) {
            Vector3f up = camera.getUpVector();
            side = new Vec3(up.x(), up.y(), up.z());
        }
        side = side.normalize().scale(half);
        WorldFx.vertex(out, a.subtract(side), 0f, 0f, ca[0], ca[1], ca[2], alphaA);
        WorldFx.vertex(out, a.add(side), 0f, 1f, ca[0], ca[1], ca[2], alphaA);
        WorldFx.vertex(out, b.add(side), 1f, 1f, cb[0], cb[1], cb[2], alphaB);
        WorldFx.vertex(out, b.subtract(side), 1f, 0f, cb[0], cb[1], cb[2], alphaB);
    }

    /**
     * A sphere of {@code radius} round {@code centre} (camera-relative), its faces brighter toward the rim seen from
     * the camera (a shell of light): {@code rings} x {@code segments} quads.
     */
    static void shell(VertexConsumer out, Vec3 centre, double radius, int rings, int segments, float[] c, float alpha) {
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings;
            double t1 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < segments; j++) {
                double p0 = 2 * Math.PI * j / segments;
                double p1 = 2 * Math.PI * (j + 1) / segments;
                Vec3 a = point(t0, p0);
                Vec3 b = point(t1, p0);
                Vec3 d = point(t1, p1);
                Vec3 e = point(t0, p1);
                shellVertex(out, centre, radius, a, c, alpha);
                shellVertex(out, centre, radius, b, c, alpha);
                shellVertex(out, centre, radius, d, c, alpha);
                shellVertex(out, centre, radius, e, c, alpha);
            }
        }
    }

    private static Vec3 point(double theta, double phi) {
        return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
    }

    private static void shellVertex(VertexConsumer out, Vec3 centre, double radius, Vec3 n, float[] c, float alpha) {
        Vec3 p = centre.add(n.scale(radius));
        Vec3 toCamera = p.scale(-1).normalize();
        float rim = (float) (1.0 - Math.abs(n.dot(toCamera)));
        float a = alpha * (0.25f + 0.75f * rim * rim);
        WorldFx.vertex(out, p, 0.5f, 0.5f, c[0], c[1], c[2], a);
    }
}
