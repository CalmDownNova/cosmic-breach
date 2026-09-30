package com.cosmicbreach.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Zenith's pillar of light: a vertical beam ({@code textures/fx/beam.png}) standing on the ground,
 * turned about its axis to face the camera. It shoots up to its full height in {@value #GROW_TICKS}
 * ticks, holds, and fades over the last {@value #FADE_TICKS} of its {@value #LIFE_TICKS}; a soft halo
 * layer sits round a bright core. Sparks rise through it ({@link CombatEffects}).
 */
final class PillarEffect implements WorldFx.Effect {
    static final int GROW_TICKS = 2;
    static final int FADE_TICKS = 6;
    static final int LIFE_TICKS = 12;

    private final Vec3 base;
    private final double height;
    private final float width;
    private final float strength;
    private final float[] color;
    private final double start;
    private final Runnable eachTick;

    PillarEffect(Vec3 base, double height, float width, float strength, int color, Runnable eachTick) {
        this.base = base;
        this.height = height;
        this.width = width;
        this.strength = strength;
        this.color = WorldFx.rgb(color);
        this.start = FxClock.ticks();
        this.eachTick = eachTick;
    }

    @Override
    public boolean tick() {
        eachTick.run();
        return FxClock.ticks() - start < LIFE_TICKS;
    }

    @Override
    public void render(WorldFx.Frame f) {
        double age = f.now() - start;
        if (age < 0.0 || age >= LIFE_TICKS) {
            return;
        }
        double grown = Math.min(1.0, age / GROW_TICKS);
        grown = 1.0 - (1.0 - grown) * (1.0 - grown);
        double fade = Math.min(1.0, (LIFE_TICKS - age) / FADE_TICKS);
        float a = (float) (fade * fade) * strength;
        Vec3 bottom = f.relative(base);
        Vec3 top = bottom.add(0, height * grown, 0);
        Vec3 side = sideways(bottom, f);
        VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
        beam(out, bottom, top, side.scale(width * 1.35), color, a * 0.35f);
        beam(out, bottom, top, side.scale(width), color, a * 0.85f);
        float[] white = {1f, 1f, 1f};
        beam(out, bottom, top, side.scale(width * 0.35), white, a * 0.7f);
    }

    /** Unit horizontal vector across the beam as the camera sees it. */
    private static Vec3 sideways(Vec3 bottom, WorldFx.Frame f) {
        Vec3 toCamera = new Vec3(-bottom.x, 0, -bottom.z);
        if (toCamera.lengthSqr() < 1e-4) {
            Vector3f left = f.camera().getLeftVector();
            return new Vec3(left.x(), 0, left.z()).normalize();
        }
        Vec3 dir = toCamera.normalize();
        return new Vec3(dir.z, 0, -dir.x);
    }

    private static void beam(VertexConsumer out, Vec3 bottom, Vec3 top, Vec3 halfSide, float[] c, float a) {
        WorldFx.vertex(out, bottom.subtract(halfSide), 0f, 1f, c[0], c[1], c[2], a);
        WorldFx.vertex(out, bottom.add(halfSide), 1f, 1f, c[0], c[1], c[2], a);
        WorldFx.vertex(out, top.add(halfSide), 1f, 0f, c[0], c[1], c[2], a);
        WorldFx.vertex(out, top.subtract(halfSide), 0f, 0f, c[0], c[1], c[2], a);
    }
}
