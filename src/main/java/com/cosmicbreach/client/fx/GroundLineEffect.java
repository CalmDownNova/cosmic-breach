package com.cosmicbreach.client.fx;

import com.cosmicbreach.combat.server.GroundWave;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * Meridian Line's mark: a razor-thin line of light on the ground along the wave's path
 * ({@code textures/fx/beam.png} laid flat, u across the line, v along it). It runs out with the
 * wave ({@link GroundWave#STEPS} ticks, a bright head on its front), lingers {@value #HOLD_TICKS}
 * ticks, then shatters: it fades over {@value #FADE_TICKS} ticks while motes rise off it.
 */
final class GroundLineEffect implements WorldFx.Effect {
    static final int GROW_TICKS = GroundWave.STEPS;
    static final int HOLD_TICKS = 10;
    static final int FADE_TICKS = 4;
    private static final float CORE_HALF_WIDTH = 0.17f;
    private static final float HALO_HALF_WIDTH = 0.45f;
    private static final float HEAD_SIZE = 0.32f;

    private final Vec3 start;
    private final Vec3 direction;
    private final double length;
    private final float[] color;
    private final double born;
    private final Runnable onShatter;
    private boolean shattered;

    GroundLineEffect(Vec3 start, Vec3 direction, double length, int color, Runnable onShatter) {
        this.start = start;
        this.direction = direction;
        this.length = length;
        this.color = WorldFx.rgb(color);
        this.born = FxClock.ticks();
        this.onShatter = onShatter;
    }

    @Override
    public boolean tick() {
        double age = FxClock.ticks() - born;
        if (!shattered && age >= GROW_TICKS + HOLD_TICKS) {
            shattered = true;
            onShatter.run();
        }
        return age < GROW_TICKS + HOLD_TICKS + FADE_TICKS;
    }

    @Override
    public void render(WorldFx.Frame f) {
        double age = f.now() - born;
        if (age < 0.0 || age >= GROW_TICKS + HOLD_TICKS + FADE_TICKS) {
            return;
        }
        double run = Math.min(1.0, age / GROW_TICKS);
        double fade = Math.min(1.0, (GROW_TICKS + HOLD_TICKS + FADE_TICKS - age) / FADE_TICKS);
        float a = (float) fade;
        Vec3 from = f.relative(start);
        Vec3 to = from.add(direction.scale(length * run));
        Vec3 across = new Vec3(-direction.z, 0, direction.x);
        VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
        strip(out, from, to, across.scale(HALO_HALF_WIDTH), color, a * 0.35f);
        strip(out, from, to, across.scale(CORE_HALF_WIDTH), color, a);
        strip(out, from, to, across.scale(CORE_HALF_WIDTH * 0.45), new float[] {1f, 1f, 1f}, a * 0.8f);
        if (run < 1.0) {
            // The wave's front: a bright head running along the ground, high enough that the ground doesn't cut it.
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            WorldFx.billboard(glow, f.camera(), to.add(0, HEAD_SIZE * 0.9, 0), HEAD_SIZE, color[0], color[1], color[2], 0.9f);
        }
    }

    private static void strip(VertexConsumer out, Vec3 from, Vec3 to, Vec3 halfAcross, float[] c, float a) {
        WorldFx.vertex(out, from.subtract(halfAcross), 0f, 0f, c[0], c[1], c[2], a);
        WorldFx.vertex(out, from.add(halfAcross), 1f, 0f, c[0], c[1], c[2], a);
        WorldFx.vertex(out, to.add(halfAcross), 1f, 1f, c[0], c[1], c[2], a);
        WorldFx.vertex(out, to.subtract(halfAcross), 0f, 1f, c[0], c[1], c[2], a);
    }
}
