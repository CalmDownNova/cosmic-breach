package com.cosmicbreach.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Gravity Well as it runs: a sphere of darkened dust spiralling in toward the plant, faster as it
 * nears the middle, where a dark core gathers; a faint pale rim where the sphere's edge bends the light
 * (seen from outside), and a faint ring on the ground at the same radius (the edge, seen from inside).
 * The dust is its own small system, moved on its spiral every tick and drawn as soft dark billboards
 * blended over the scene. The Collapse sucks every mote into the middle in a couple of ticks; a well
 * that ends without one fades.
 */
final class GravityWellEffect implements WorldFx.Effect {
    private static final int DUST = 0x160E22;
    private static final int RIM = 0xD9D2FF;
    private static final int COLLAPSE_TICKS = 3;
    private static final int FADE_TICKS = 6;
    /** The rim texture's circle sits at this share of its half size. */
    private static final float RING_SHARE = 0.8f;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Mote {
        final double phi;
        final float size;
        final float alpha;
        double r;
        double rO;
        double theta;
        double thetaO;
        int age;

        Mote(double r, double theta, double phi, float size, float alpha) {
            this.r = this.rO = r;
            this.theta = this.thetaO = theta;
            this.phi = phi;
            this.size = size;
            this.alpha = alpha;
        }
    }

    private final Vec3 centre;
    private final float radius;
    private final int pullTicks;
    private final double start;
    private final List<Mote> motes = new ArrayList<>();
    private double collapsedAt = Double.NaN;
    private double fadedAt = Double.NaN;

    GravityWellEffect(Vec3 centre, float radius, int pullTicks) {
        this.centre = centre;
        this.radius = radius;
        this.pullTicks = Math.max(1, pullTicks);
        this.start = FxClock.ticks();
    }

    /** The Collapse: every mote rushes to the middle. */
    void collapse() {
        if (Double.isNaN(collapsedAt)) {
            collapsedAt = FxClock.ticks();
        }
    }

    /** Ended without a Collapse: the dust thins out. */
    void fade() {
        if (Double.isNaN(fadedAt) && Double.isNaN(collapsedAt)) {
            fadedAt = FxClock.ticks();
        }
    }

    Vec3 centre() {
        return centre;
    }

    @Override
    public boolean tick() {
        double now = FxClock.ticks();
        boolean collapsing = !Double.isNaN(collapsedAt);
        if (collapsing && now - collapsedAt >= COLLAPSE_TICKS) {
            return false;
        }
        if (!Double.isNaN(fadedAt) && now - fadedAt >= FADE_TICKS) {
            return false;
        }
        if (now - start > pullTicks + 20) {
            return false; // no word from the server: don't hang around
        }
        if (!collapsing && Double.isNaN(fadedAt)) {
            spawn(FxBudget.count(7, centre, true));
        }
        Iterator<Mote> it = motes.iterator();
        while (it.hasNext()) {
            Mote m = it.next();
            m.rO = m.r;
            m.thetaO = m.theta;
            m.age++;
            if (collapsing) {
                m.r *= 0.3;
            } else {
                double depth = 1.0 - m.r / radius; // 0 at the rim, 1 at the middle
                m.r -= radius * (0.018 + 0.05 * depth);
                m.theta += 0.07 + 0.42 * depth * depth;
            }
            if (m.r < 0.25 || m.age > 60) {
                it.remove();
            }
        }
        return true;
    }

    private void spawn(int count) {
        for (int i = 0; i < count; i++) {
            // over the upper sphere and a little below the ground's line (the plant is on the ground)
            double phi = Math.acos(1.0 - RANDOM.nextDouble() * 1.15);
            double r = radius * (0.82 + RANDOM.nextDouble() * 0.18);
            motes.add(new Mote(r, RANDOM.nextDouble() * Math.PI * 2.0, phi, 0.22f + RANDOM.nextFloat() * 0.3f,
                    0.28f + RANDOM.nextFloat() * 0.3f));
        }
    }

    @Override
    public void render(WorldFx.Frame f) {
        double now = f.now();
        double age = now - start;
        float in = (float) Mth.clamp(age / 4.0, 0.0, 1.0);
        float out = 1f;
        if (!Double.isNaN(fadedAt)) {
            out = (float) Mth.clamp(1.0 - (now - fadedAt) / FADE_TICKS, 0.0, 1.0);
        } else if (!Double.isNaN(collapsedAt)) {
            out = (float) Mth.clamp(1.0 - (now - collapsedAt) / COLLAPSE_TICKS, 0.0, 1.0);
        }
        float strength = in * out;
        if (strength <= 0.01f) {
            return;
        }
        float[] dust = WorldFx.rgb(DUST);
        VertexConsumer shade = f.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.GLOW));
        for (Mote m : motes) {
            double r = Mth.lerp(f.partialTick(), m.rO, m.r);
            double theta = Mth.lerp(f.partialTick(), m.thetaO, m.theta);
            double sinPhi = Math.sin(m.phi);
            Vec3 p = centre.add(sinPhi * Math.cos(theta) * r, Math.cos(m.phi) * r + 0.3, sinPhi * Math.sin(theta) * r);
            float fadeIn = Math.min(1f, (m.age + f.partialTick()) / 4f);
            // closer in, the dust packs denser and darker
            float a = m.alpha * fadeIn * strength * (float) (0.7 + 0.6 * (1.0 - r / radius));
            WorldFx.billboard(shade, f.camera(), f.relative(p), m.size * (float) (0.6 + 0.4 * r / radius), dust[0], dust[1], dust[2],
                    Math.min(0.85f, a));
        }
        // the gathering core
        float pull = (float) Mth.clamp(age / pullTicks, 0.0, 1.0);
        Vec3 core = f.relative(centre.add(0, 0.8, 0));
        WorldFx.billboard(shade, f.camera(), core, 0.5f + 1.1f * pull, dust[0], dust[1], dust[2], 0.75f * strength);
        // the lens edge: a faint rim round the sphere from outside, a faint ring on the ground at its edge
        float[] rim = WorldFx.rgb(RIM);
        VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(GlowEffect.RING));
        float wobble = 1f + 0.015f * (float) Math.sin(now * 1.7);
        if (f.cameraPos().distanceTo(centre) > radius + 0.5) {
            WorldFx.billboard(glow, f.camera(), f.relative(centre.add(0, 0.3, 0)), radius * wobble / RING_SHARE,
                    rim[0], rim[1], rim[2], 0.22f * strength);
        }
        WorldFx.flat(glow, f.relative(centre.add(0, 0.06, 0)), radius * wobble / RING_SHARE, rim[0], rim[1], rim[2],
                0.3f * strength);
    }
}
