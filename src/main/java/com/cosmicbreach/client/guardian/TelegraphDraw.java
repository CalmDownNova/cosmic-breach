package com.cosmicbreach.client.guardian;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.guardian.Telegraphs;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Drawing for guardian telegraphs, shared by every guardian's effects (the telegraph language of GDD 4.1): floor
 * shapes (a filled {@link #sector} of an annulus for bands and rings, a {@link #curtain} of light standing on an arc so
 * a floor band reads at eye level), beams ({@link #ribbon}, facing the camera, optionally shifted sideways for a
 * fringe), warning lines that keep their thickness on screen ({@link #line} solid, {@link #taper} soft-edged, widths
 * from {@link #screenWidth}), soft {@link #glow}s that read on a bright sky or floor, and one-shot {@link Glint}s and
 * {@link Flash}es. Positions are camera-relative; the buffers are the caller's. Client thread.
 */
public final class TelegraphDraw {
    private static final Matrix4f IDENTITY = new Matrix4f();

    private TelegraphDraw() {
    }

    /**
     * A glow that reads on a noon sky or a white floor: the colour with ordinary blending (light added onto white
     * vanishes), then a smaller additive white-hot core over it.
     */
    public static void glow(MultiBufferSource buffers, Camera camera, Vec3 at, float half, int color, float alpha) {
        glow(buffers.getBuffer(ShardDraw.translucent(ShardDraw.GLOW, true)), camera, at, half, ShardDraw.rgb(color), alpha);
        glow(buffers.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, half * 0.5f, ShardDraw.rgb(0xFFFFFF), alpha);
    }

    /** A flat sector of an annulus round {@code centre}: angles in degrees from {@code yaw}, clockwise positive. */
    public static void sector(VertexConsumer out, Vec3 centre, double inner, double outer, float yaw, double from, double to, int segments,
                               float[] col, float alpha) {
        if (to <= from) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = from + (to - from) * i / segments;
            double a1 = from + (to - from) * (i + 1) / segments;
            Vec3 d0 = Telegraphs.forward((float) (yaw + a0));
            Vec3 d1 = Telegraphs.forward((float) (yaw + a1));
            ShardDraw.vertex(out, IDENTITY, centre.add(d0.scale(inner)), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, centre.add(d0.scale(outer)), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, centre.add(d1.scale(outer)), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, centre.add(d1.scale(inner)), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        }
    }

    /** A ribbon from {@code a} to {@code b} (camera-relative), {@code width} wide, turned to face the camera. */
    public static void ribbon(VertexConsumer out, Camera camera, Vec3 a, Vec3 b, double width, float[] col, float alpha) {
        ribbon(out, camera, a, b, width, 0.0, col, alpha);
    }

    /** {@link #ribbon} shifted {@code offset} blocks sideways (square to the line and to the view). */
    public static void ribbon(VertexConsumer out, Camera camera, Vec3 a, Vec3 b, double width, double offset, float[] col, float alpha) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1e-8) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = along.cross(mid);
        if (side.lengthSqr() < 1e-8) {
            Vector3f up = camera.getUpVector();
            side = along.cross(new Vec3(up.x(), up.y(), up.z()));
        }
        side = side.normalize();
        Vec3 shift = side.scale(offset);
        Vec3 half = side.scale(width / 2.0);
        Vec3 a0 = a.add(shift);
        Vec3 b0 = b.add(shift);
        ShardDraw.vertex(out, IDENTITY, a0.subtract(half), 0f, 1f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, a0.add(half), 1f, 1f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b0.add(half), 1f, 0f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b0.subtract(half), 0f, 0f, col[0], col[1], col[2], alpha);
    }

    /**
     * World width that shows about {@code pixels} tall on a 720-line screen at a 70 degree field of view, at
     * camera-relative {@code p}; never under {@code min}.
     */
    public static double screenWidth(Vec3 p, double pixels, double min) {
        return Math.max(min, p.length() * 1.4 / 720.0 * pixels);
    }

    /** The unit vector square to the line from a to b and to the view, or null for a line of no length. */
    private static Vec3 across(Camera camera, Vec3 a, Vec3 b) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1e-8) {
            return null;
        }
        Vec3 side = along.cross(a.add(b).scale(0.5));
        if (side.lengthSqr() < 1e-8) {
            Vector3f up = camera.getUpVector();
            side = along.cross(new Vec3(up.x(), up.y(), up.z()));
        }
        return side.lengthSqr() < 1e-12 ? null : side.normalize();
    }

    /**
     * A line of flat colour from {@code a} to {@code b} (camera-relative) facing the camera, {@code widthA} wide at a
     * and {@code widthB} at b: give each end its {@link #screenWidth} and the line keeps its thickness on screen.
     * Draw into a buffer whose texture is solid white in the middle ({@code ShardDraw.solid()}).
     */
    public static void line(VertexConsumer out, Camera camera, Vec3 a, Vec3 b, double widthA, double widthB, float[] col, float alpha) {
        quadAlong(out, camera, a, b, widthA, widthB, col, alpha, true);
    }

    /** {@link #line} with the texture across it (a beam texture gives soft sides): a halo under a line. */
    public static void taper(VertexConsumer out, Camera camera, Vec3 a, Vec3 b, double widthA, double widthB, float[] col, float alpha) {
        quadAlong(out, camera, a, b, widthA, widthB, col, alpha, false);
    }

    private static void quadAlong(VertexConsumer out, Camera camera, Vec3 a, Vec3 b, double widthA, double widthB, float[] col, float alpha,
                                  boolean solid) {
        Vec3 side = across(camera, a, b);
        if (side == null) {
            return;
        }
        Vec3 ha = side.scale(widthA / 2.0);
        Vec3 hb = side.scale(widthB / 2.0);
        float u0 = solid ? 0.5f : 0f;
        float u1 = solid ? 0.5f : 1f;
        float v0 = solid ? 0.5f : 0f;
        float v1 = solid ? 0.5f : 1f;
        ShardDraw.vertex(out, IDENTITY, a.subtract(ha), u0, v1, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, a.add(ha), u1, v1, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b.add(hb), u1, v0, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b.subtract(hb), u0, v0, col[0], col[1], col[2], alpha);
    }

    /**
     * A curtain of light standing on an arc of {@code radius} round {@code centre} (camera-relative, on the floor):
     * {@code height} tall, full at its foot and fading to nothing at its top (the glow texture sampled from its middle
     * out to its edge). Angles as {@link #sector}. A band on the floor seen at eye level is a sliver; its curtain is
     * seen face on.
     */
    public static void curtain(VertexConsumer out, Vec3 centre, double radius, float yaw, double from, double to, int segments, double height,
                               float[] col, float alpha) {
        if (to <= from) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = from + (to - from) * i / segments;
            double a1 = from + (to - from) * (i + 1) / segments;
            Vec3 p0 = centre.add(Telegraphs.forward((float) (yaw + a0)).scale(radius));
            Vec3 p1 = centre.add(Telegraphs.forward((float) (yaw + a1)).scale(radius));
            ShardDraw.vertex(out, IDENTITY, p0, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p1.add(0, height, 0), 0.5f, 0.03f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p0.add(0, height, 0), 0.5f, 0.03f, col[0], col[1], col[2], alpha);
        }
    }

    /** A soft round glow facing the camera. */
    public static void glow(VertexConsumer out, Camera camera, Vec3 at, float half, float[] col, float alpha) {
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        ShardDraw.quad(out, IDENTITY, at, new Vec3(l.x(), l.y(), l.z()).scale(half), new Vec3(u.x(), u.y(), u.z()).scale(half),
                col[0], col[1], col[2], alpha);
    }

    // ------------------------------------------------------------------ one-shot shapes (as the Shardling's, sized for a giant)

    /** A four-point star glint riding a point: coloured star with ordinary blending, a white-hot additive core. */
    public static final class Glint implements WorldFx.Effect {
        private final Supplier<Vec3> where;
        private final float[] color;
        private final float size;
        private final int life;
        private final float spin;
        private final double start = FxClock.ticks();

        public Glint(Supplier<Vec3> where, int color, float size, int life, float spin) {
            this.where = where;
            this.color = ShardDraw.rgb(color);
            this.size = size;
            this.life = Math.max(1, life);
            this.spin = spin;
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            double t = age / life;
            Vec3 world = where.get();
            if (t < 0.0 || t >= 1.0 || world == null) {
                return;
            }
            double grow = t < 0.25 ? 0.4 + 0.6 * t / 0.25 : 1.0 - 0.5 * (t - 0.25) / 0.75;
            float alpha = (float) (1.0 - t * t * t);
            Vec3 at = f.relative(world);
            double roll = age * spin;
            Vector3f l = f.camera().getLeftVector();
            Vector3f u = f.camera().getUpVector();
            Vec3 left = new Vec3(l.x(), l.y(), l.z());
            Vec3 up = new Vec3(u.x(), u.y(), u.z());
            Vec3 right = left.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
            Vec3 top = up.scale(Math.cos(roll)).subtract(left.scale(Math.sin(roll)));
            double s = size * grow;
            ShardDraw.quad(f.buffers().getBuffer(ShardDraw.translucent(ShardDraw.STAR, true)), IDENTITY, at, right.scale(s), top.scale(s),
                    color[0], color[1], color[2], alpha);
            ShardDraw.quad(f.buffers().getBuffer(FxRenderTypes.additive(ShardDraw.STAR)), IDENTITY, at, right.scale(s * 0.55),
                    top.scale(s * 0.55), 1f, 1f, 1f, alpha);
        }
    }

    /** A flash (soft glow or thin ring), facing the camera or flat, growing fast then slow and fading. */
    public static final class Flash implements WorldFx.Effect {
        private final Vec3 at;
        private final ResourceLocation texture;
        private final boolean flat;
        private final boolean additive;
        private final float from;
        private final float to;
        private final float[] color;
        private final float strength;
        private final int life;
        private final double start = FxClock.ticks();

        public Flash(Vec3 at, ResourceLocation texture, boolean flat, boolean additive, float from, float to, int color, float strength, int life) {
            this.at = at;
            this.texture = texture;
            this.flat = flat;
            this.additive = additive;
            this.from = from;
            this.to = to;
            this.color = ShardDraw.rgb(color);
            this.strength = strength;
            this.life = Math.max(1, life);
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            double t = age / life;
            if (t < 0.0 || t >= 1.0) {
                return;
            }
            float alpha = (float) (strength * Math.min(1.0, age / 0.6) * (1.0 - t) * (1.0 - t));
            double size = from + (to - from) * (1.0 - Math.pow(1.0 - t, 2.4));
            Vec3 centre = f.relative(at);
            VertexConsumer out = f.buffers().getBuffer(additive ? FxRenderTypes.additive(texture) : ShardDraw.translucent(texture, true));
            if (flat) {
                ShardDraw.quad(out, IDENTITY, centre, new Vec3(size, 0.0, 0.0), new Vec3(0.0, 0.0, size), color[0], color[1], color[2], alpha);
            } else {
                size = Math.min(size, centre.length() * 0.45);
                Vector3f l = f.camera().getLeftVector();
                Vector3f u = f.camera().getUpVector();
                ShardDraw.quad(out, IDENTITY, centre, new Vec3(l.x(), l.y(), l.z()).scale(size), new Vec3(u.x(), u.y(), u.z()).scale(size),
                        color[0], color[1], color[2], alpha);
            }
        }
    }
}
