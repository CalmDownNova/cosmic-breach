package com.cosmicbreach.client.entity;

import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * The Shardling's own shaped effects, drawn by {@link WorldFx} each frame (camera-relative, so with an
 * identity matrix): telegraph glints that ride a moving point, and flashes and rings.
 */
final class ShardFx {
    private static final Matrix4f IDENTITY = new Matrix4f();

    private ShardFx() {
    }

    /**
     * A four-point star glint in a telegraph colour: the star drawn with ordinary blending in its colour
     * (light added onto a noon sky or a white floor turns white, and gold must stay gold), with a smaller
     * white-hot additive core on top. It pops in over a quarter of its life, then shrinks and fades.
     * It rides {@code where} (for example a spine tip as the spine moves) and skips frames where it gives null.
     */
    static final class Glint implements WorldFx.Effect {
        private final Supplier<Vec3> where;
        private final float[] color;
        private final float size;
        private final int life;
        private final float spin;
        private final double start = FxClock.ticks();

        Glint(Supplier<Vec3> where, int color, float size, int life, float spin) {
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
            if (at.lengthSqr() > 0.04) {
                at = at.subtract(at.normalize().scale(0.12)); // a little toward the camera, out of the crystal
            }
            double roll = age * spin;
            Vector3f l = f.camera().getLeftVector();
            Vector3f u = f.camera().getUpVector();
            Vec3 left = new Vec3(l.x(), l.y(), l.z());
            Vec3 up = new Vec3(u.x(), u.y(), u.z());
            Vec3 right = left.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
            Vec3 top = up.scale(Math.cos(roll)).subtract(left.scale(Math.sin(roll)));
            double s = size * grow;
            ShardDraw.quad(f.buffers().getBuffer(ShardDraw.translucent(ShardDraw.STAR, true)), IDENTITY, at,
                    right.scale(s), top.scale(s), color[0], color[1], color[2], alpha);
            ShardDraw.quad(f.buffers().getBuffer(FxRenderTypes.additive(ShardDraw.STAR)), IDENTITY, at,
                    right.scale(s * 0.55), top.scale(s * 0.55), 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    /**
     * A round shape of light at a point: a soft flash ({@code glow.png}) or a thin ring ({@code ring.png}),
     * facing the camera or flat on the ground, additive or (for colours that must read on white)
     * ordinary blending. It grows fast then slow and fades over its life.
     */
    static final class Flash implements WorldFx.Effect {
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

        Flash(Vec3 at, ResourceLocation texture, boolean flat, boolean additive, float from, float to, int color, float strength, int life) {
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
