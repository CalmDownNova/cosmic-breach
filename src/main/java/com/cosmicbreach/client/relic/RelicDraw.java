package com.cosmicbreach.client.relic;

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

/** Drawing helpers and palettes for the Heliarch's relics: camera-facing quads, ribbons, flat rings and sectors. Client. */
final class RelicDraw {
    static final ResourceLocation GLOW = FxRenderTypes.GLOW;
    static final ResourceLocation BEAM = FxRenderTypes.BEAM;
    static final ResourceLocation RING = CosmicBreach.id("textures/particle/ring.png");
    static final ResourceLocation RAYS = CosmicBreach.id("textures/fx/lastlight_rays.png");
    static final ResourceLocation NOTE = CosmicBreach.id("textures/fx/cantor_note.png");

    // Last Light: white-gold light
    static final int SUN_WHITE = 0xFFF8E6;
    static final int SUN_GOLD = 0xFFD470;
    static final int SUN_DEEP = 0xFFA83C;
    static final int DAWN_ROSE = 0xFFB27A;
    // the Umbra Cantor: shadow and violet
    static final int VIOLET = 0xB98CFF;
    static final int PALE_VIOLET = 0xE4D4FF;
    static final int DEEP_VIOLET = 0x6A3BD1;
    static final int SHADOW = 0x2A1E3A;

    private RelicDraw() {
    }

    /** A square facing the camera, drawn in a pose already turned to it (entity and block entity renderers). */
    static void facing(VertexConsumer out, PoseStack.Pose pose, float half, float roll, int color, float alpha) {
        float[] c = WorldFx.rgb(color);
        Matrix4f m = pose.pose();
        float cs = (float) Math.cos(roll) * half;
        float sn = (float) Math.sin(roll) * half;
        out.addVertex(m, -cs + sn, -sn - cs, 0f).setUv(0f, 1f).setColor(c[0], c[1], c[2], alpha);
        out.addVertex(m, -cs - sn, -sn + cs, 0f).setUv(0f, 0f).setColor(c[0], c[1], c[2], alpha);
        out.addVertex(m, cs - sn, sn + cs, 0f).setUv(1f, 0f).setColor(c[0], c[1], c[2], alpha);
        out.addVertex(m, cs + sn, sn - cs, 0f).setUv(1f, 1f).setColor(c[0], c[1], c[2], alpha);
    }

    /** A square facing the camera round {@code at} (camera-relative), turned {@code roll} radians. */
    static void billboard(VertexConsumer out, Camera camera, Vec3 at, float half, float roll, int color, float alpha) {
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        double cs = Math.cos(roll) * half;
        double sn = Math.sin(roll) * half;
        Vec3 left = new Vec3(l.x() * cs + u.x() * sn, l.y() * cs + u.y() * sn, l.z() * cs + u.z() * sn);
        Vec3 up = new Vec3(u.x() * cs - l.x() * sn, u.y() * cs - l.y() * sn, u.z() * cs - l.z() * sn);
        float[] c = WorldFx.rgb(color);
        WorldFx.vertex(out, at.subtract(left).subtract(up), 1f, 1f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, at.subtract(left).add(up), 1f, 0f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, at.add(left).add(up), 0f, 0f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, at.add(left).subtract(up), 0f, 1f, c[0], c[1], c[2], alpha);
    }

    /** A camera-facing ribbon from {@code a} to {@code b} (camera-relative), {@code half} wide, its colour fading along. */
    static void ribbon(VertexConsumer out, Camera camera, Vec3 a, Vec3 b, float half, int colorA, float alphaA, int colorB, float alphaB) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1e-8) {
            return;
        }
        Vec3 view = a.add(b).scale(0.5);
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1e-8) {
            Vector3f up = camera.getUpVector();
            side = new Vec3(up.x(), up.y(), up.z());
        }
        side = side.normalize().scale(half);
        float[] ca = WorldFx.rgb(colorA);
        float[] cb = WorldFx.rgb(colorB);
        WorldFx.vertex(out, a.subtract(side), 0f, 0f, ca[0], ca[1], ca[2], alphaA);
        WorldFx.vertex(out, a.add(side), 0f, 1f, ca[0], ca[1], ca[2], alphaA);
        WorldFx.vertex(out, b.add(side), 1f, 1f, cb[0], cb[1], cb[2], alphaB);
        WorldFx.vertex(out, b.subtract(side), 1f, 0f, cb[0], cb[1], cb[2], alphaB);
    }

    /**
     * A flat band lying level round {@code centre} (camera-relative), between radius {@code inner} and {@code outer}, from
     * {@code from} to {@code to} radians (Minecraft yaw sense: 0 south, growing to the west), in {@code steps} quads; the
     * texture's v runs across the band (the glow's soft middle along it), {@code alpha} fading to {@code alphaEnd} along.
     */
    static void sector(VertexConsumer out, Vec3 centre, double inner, double outer, double from, double to, int steps,
                       int color, float alpha, float alphaEnd) {
        float[] c = WorldFx.rgb(color);
        for (int i = 0; i < steps; i++) {
            double a0 = from + (to - from) * i / steps;
            double a1 = from + (to - from) * (i + 1) / steps;
            float al0 = alpha + (alphaEnd - alpha) * i / (float) steps;
            float al1 = alpha + (alphaEnd - alpha) * (i + 1) / (float) steps;
            Vec3 d0 = new Vec3(-Math.sin(a0), 0, Math.cos(a0));
            Vec3 d1 = new Vec3(-Math.sin(a1), 0, Math.cos(a1));
            WorldFx.vertex(out, centre.add(d0.scale(inner)), 0.5f, 0f, c[0], c[1], c[2], al0);
            WorldFx.vertex(out, centre.add(d0.scale(outer)), 0.5f, 1f, c[0], c[1], c[2], al0);
            WorldFx.vertex(out, centre.add(d1.scale(outer)), 0.5f, 1f, c[0], c[1], c[2], al1);
            WorldFx.vertex(out, centre.add(d1.scale(inner)), 0.5f, 0f, c[0], c[1], c[2], al1);
        }
    }

    /** A flat textured square lying level round {@code centre} (camera-relative), turned {@code turn} radians. */
    static void flat(VertexConsumer out, Vec3 centre, float half, float turn, int color, float alpha) {
        float[] c = WorldFx.rgb(color);
        double cs = Math.cos(turn) * half;
        double sn = Math.sin(turn) * half;
        Vec3 a = new Vec3(cs, 0, sn);
        Vec3 b = new Vec3(-sn, 0, cs);
        WorldFx.vertex(out, centre.subtract(a).subtract(b), 0f, 0f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, centre.subtract(a).add(b), 0f, 1f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, centre.add(a).add(b), 1f, 1f, c[0], c[1], c[2], alpha);
        WorldFx.vertex(out, centre.add(a).subtract(b), 1f, 0f, c[0], c[1], c[2], alpha);
    }

    /** 0xRRGGBB between {@code a} and {@code b}. */
    static int mix(int a, int b, float t) {
        float[] ca = WorldFx.rgb(a);
        float[] cb = WorldFx.rgb(b);
        int r = Math.round((ca[0] + (cb[0] - ca[0]) * t) * 255f);
        int g = Math.round((ca[1] + (cb[1] - ca[1]) * t) * 255f);
        int bl = Math.round((ca[2] + (cb[2] - ca[2]) * t) * 255f);
        return (r << 16) | (g << 8) | bl;
    }
}
