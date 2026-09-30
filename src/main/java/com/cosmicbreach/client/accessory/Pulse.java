package com.cosmicbreach.client.accessory;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A round shape of light for the accessories' moments: a soft glow or a thin ring, facing the camera or lying flat,
 * growing from one size to another (fast, then slow) and fading over its life; {@code dark} blends it over the scene
 * instead of adding light (the black hole's core). Client only.
 */
final class Pulse implements WorldFx.Effect {
    static final ResourceLocation RING = CosmicBreach.id("textures/particle/ring.png");

    private final Vec3 at;
    private final boolean flat;
    private final boolean dark;
    private final ResourceLocation texture;
    private final float from;
    private final float to;
    private final float[] color;
    private final float strength;
    private final double start;
    private final int life;
    private final float hold;

    private Pulse(Vec3 at, boolean flat, boolean dark, ResourceLocation texture, float from, float to, int color, float strength,
                  int life, float hold) {
        this.at = at;
        this.flat = flat;
        this.dark = dark;
        this.texture = texture;
        this.from = from;
        this.to = to;
        this.color = WorldFx.rgb(color);
        this.strength = strength;
        this.start = FxClock.ticks();
        this.life = Math.max(1, life);
        this.hold = hold;
    }

    /** A flash of glow facing the camera. */
    static void flash(Vec3 at, float size, int color, float strength, int life) {
        WorldFx.add(new Pulse(at, false, false, FxRenderTypes.GLOW, size * 0.6f, size, color, strength, life, 0f));
    }

    /** A ring racing out, facing the camera. */
    static void ring(Vec3 at, float from, float to, int color, float strength, int life) {
        WorldFx.add(new Pulse(at, false, false, RING, from, to, color, strength, life, 0f));
    }

    /** A ring racing out along the ground. */
    static void groundRing(Vec3 at, float from, float to, int color, float strength, int life) {
        WorldFx.add(new Pulse(at.add(0, 0.05, 0), true, false, RING, from, to, color, strength, life, 0f));
    }

    /** A glow lying on the ground. */
    static void ground(Vec3 at, float size, int color, float strength, int life) {
        WorldFx.add(new Pulse(at.add(0, 0.04, 0), true, false, FxRenderTypes.GLOW, size * 0.8f, size, color, strength, life, 0f));
    }

    /** A dark round core that holds its size for {@code hold} of its life, then shrinks away. */
    static void darkCore(Vec3 at, float size, int life) {
        WorldFx.add(new Pulse(at, false, true, FxRenderTypes.GLOW, size, size, 0x05030A, 0.95f, life, 0.8f));
    }

    @Override
    public boolean tick() {
        return FxClock.ticks() - start < life;
    }

    @Override
    public void render(WorldFx.Frame frame) {
        double t = (frame.now() - start) / life;
        if (t < 0 || t >= 1) {
            return;
        }
        float grow = (float) (1.0 - Math.pow(1.0 - t, 3));
        float size;
        float alpha;
        if (hold > 0f) {
            float late = (float) Math.max(0.0, (t - hold) / (1.0 - hold));
            size = from * (1.0f - 0.8f * late);
            alpha = strength * Math.min(1f, (float) t * 8f) * (1f - late * late);
        } else {
            size = Mth.lerp(grow, from, to);
            alpha = strength * Math.min(1f, (float) t * 10f) * (float) Math.pow(1.0 - t, 1.5);
        }
        if (alpha <= 0.005f || size <= 0.01f) {
            return;
        }
        VertexConsumer out = frame.buffers().getBuffer(dark ? FxRenderTypes.shade(texture) : FxRenderTypes.additive(texture));
        Vec3 rel = frame.relative(at);
        float half = size;
        if (flat) {
            WorldFx.flat(out, rel, half, color[0], color[1], color[2], alpha);
        } else {
            WorldFx.billboard(out, frame.camera(), rel, half, color[0], color[1], color[2], alpha);
        }
    }
}
