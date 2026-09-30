package com.cosmicbreach.client.fx;

import com.cosmicbreach.CosmicBreach;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * A round shape of light at a point, drawn with smooth filtering: a soft glow
 * ({@code textures/fx/glow.png}) or a thin ring ({@code textures/particle/ring.png}), facing the camera
 * (a flash, a shockwave) or lying on the ground (under a landing, at the foot of a pillar). It flares
 * in about a tick, grows (fast, then slow) and fades over its life. Facing the camera, it never grows
 * past {@value #MAX_SHARE_OF_DISTANCE} of its distance from the camera, so a flash right in front of
 * the player's eyes stays a flash and doesn't cover the screen.
 */
final class GlowEffect implements WorldFx.Effect {
    static final ResourceLocation RING = CosmicBreach.id("textures/particle/ring.png");
    private static final double MAX_SHARE_OF_DISTANCE = 0.45;

    private final Vec3 at;
    private final boolean flat;
    private final ResourceLocation texture;
    private final float sizeFrom;
    private final float sizeTo;
    private final float[] color;
    private final float strength;
    private final double start;
    private final int life;

    private GlowEffect(Vec3 at, boolean flat, ResourceLocation texture, float sizeFrom, float sizeTo, int color, float strength, int life) {
        this.at = at;
        this.flat = flat;
        this.texture = texture;
        this.sizeFrom = sizeFrom;
        this.sizeTo = sizeTo;
        this.color = WorldFx.rgb(color);
        this.strength = strength;
        this.start = FxClock.ticks();
        this.life = Math.max(1, life);
    }

    /** A soft flash facing the camera, {@code size} blocks from its centre to its edge. */
    static void flash(Vec3 at, float size, int color, float strength, int life) {
        WorldFx.add(new GlowEffect(at, false, FxRenderTypes.GLOW, size * 0.7f, size, color, strength, life));
    }

    /** A soft glow on the ground under {@code at}. */
    static void ground(Vec3 at, float size, int color, float strength, int life) {
        WorldFx.add(new GlowEffect(at.add(0, 0.04, 0), true, FxRenderTypes.GLOW, size * 0.6f, size, color, strength, life));
    }

    /** A ring expanding from {@code from} to {@code to} blocks, facing the camera. */
    static void ring(Vec3 at, float from, float to, int color, float strength, int life) {
        WorldFx.add(new GlowEffect(at, false, RING, from, to, color, strength, life));
    }

    /** A ring expanding on the ground under {@code at}. */
    static void groundRing(Vec3 at, float from, float to, int color, float strength, int life) {
        WorldFx.add(new GlowEffect(at.add(0, 0.05, 0), true, RING, from, to, color, strength, life));
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
        double in = Math.min(1.0, age / 0.6);
        float a = (float) (strength * in * (1.0 - t) * (1.0 - t));
        double grown = 1.0 - Math.pow(1.0 - t, 2.4);
        float size = (float) (sizeFrom + (sizeTo - sizeFrom) * grown);
        Vec3 centre = f.relative(at);
        VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(texture));
        if (flat) {
            WorldFx.flat(out, centre, size, color[0], color[1], color[2], a);
        } else {
            size = (float) Math.min(size, centre.length() * MAX_SHARE_OF_DISTANCE);
            WorldFx.billboard(out, f.camera(), centre, size, color[0], color[1], color[2], a);
        }
    }
}
