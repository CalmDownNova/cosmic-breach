package com.cosmicbreach.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * Ground cracks where a heavy blow landed ({@code textures/fx/crack.png}, lying on the ground, turned at
 * random): the fissures drawn dark over the ground, and inside them a hot glow that cools in its first
 * {@value #GLOW_SHARE} of the life. The dark cracks hold, then fade out by the end of the life (the GDD's
 * 3 s for a slam; a crater's cracks last as long as the crater).
 */
final class CrackDecal implements WorldFx.Effect {
    private static final float GLOW_SHARE = 0.3f;
    /** Share of the life the dark cracks hold before fading. */
    private static final float HOLD_SHARE = 0.35f;
    private static final int DARK = 0x1C130D;

    private final Vec3 at;
    private final float radius;
    private final float cos;
    private final float sin;
    private final float[] glow;
    private final float darkness;
    private final double start;
    private final int life;

    /**
     * Cracks {@code radius} blocks from the centre {@code at} (on the ground's surface), glowing
     * {@code glowColor} at first, lasting {@code life} ticks, at most {@code darkness} opaque.
     */
    CrackDecal(Vec3 at, float radius, float angle, int glowColor, float darkness, int life) {
        this.at = at.add(0, 0.012 + radius * 0.001, 0);
        this.radius = radius;
        this.cos = (float) Math.cos(angle);
        this.sin = (float) Math.sin(angle);
        this.glow = WorldFx.rgb(glowColor);
        this.darkness = darkness;
        this.start = FxClock.ticks();
        this.life = Math.max(1, life);
    }

    @Override
    public boolean tick() {
        return FxClock.ticks() - start < life;
    }

    @Override
    public void render(WorldFx.Frame f) {
        float t = (float) ((f.now() - start) / life);
        if (t >= 1f) {
            return;
        }
        Vec3 c = f.relative(at);
        float dark = darkness * (t < HOLD_SHARE ? 1f : 1f - (t - HOLD_SHARE) / (1f - HOLD_SHARE));
        if (dark > 0.01f) {
            float[] d = WorldFx.rgb(DARK);
            quad(f.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.CRACK)), c, d[0], d[1], d[2], dark);
        }
        if (t < GLOW_SHARE) {
            float heat = 1f - t / GLOW_SHARE;
            quad(f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.CRACK)), c.add(0, 0.004, 0),
                    glow[0], glow[1], glow[2], heat * heat);
        }
    }

    /** The crack square, turned about the vertical, facing up. */
    private void quad(VertexConsumer out, Vec3 c, float r, float g, float b, float a) {
        float ax = radius * cos;
        float az = radius * sin;
        // corners (-1,-1), (-1,1), (1,1), (1,-1) of the unturned square, turned by the angle
        WorldFx.vertex(out, c.add(-ax + az, 0, -az - ax), 0f, 0f, r, g, b, a);
        WorldFx.vertex(out, c.add(-ax - az, 0, -az + ax), 0f, 1f, r, g, b, a);
        WorldFx.vertex(out, c.add(ax - az, 0, az + ax), 1f, 1f, r, g, b, a);
        WorldFx.vertex(out, c.add(ax + az, 0, az - ax), 1f, 0f, r, g, b, a);
    }
}
