package com.cosmicbreach.client.weather;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticle;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.world.weather.WeatherNet;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor on the client, from the server's {@link WeatherNet.Meteor}: the red circle on the ground at once
 * (a dark red fill, a hot rim, and a ring closing in from the middle that reaches the rim as it lands), the
 * meteor itself for its last {@value #FALL_TICKS} ticks (a white-hot head in a red halo with a short trail,
 * shedding sparks, coming in from the shower's side), then the impact: a flash, a shock ring, a burst of
 * sparks and glowing shards, a scorch mark cooling for three seconds, and a jolt for a player close by. No
 * smoke.
 */
public final class MeteorFx {
    public static final ResourceLocation RING = CosmicBreach.id("textures/fx/meteor_ring.png");
    public static final ResourceLocation DISC = CosmicBreach.id("textures/fx/meteor_disc.png");
    static final int FALL_TICKS = 24;
    /** The flash and shock ring last this long after the impact; the scorch mark, {@link #MARK_TICKS}. */
    private static final int AFTER_TICKS = 10;
    private static final int MARK_TICKS = 60;
    private static final double TRAIL = 9.0;
    private static final RandomSource RANDOM = RandomSource.create();
    private static int live;

    private MeteorFx() {
    }

    /** A meteor is coming (client thread). */
    public static void onMeteor(WeatherNet.Meteor m) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        WorldFx.add(new Meteor(m));
    }

    /** Meteors (circles) on screen now (for checks). */
    public static int live() {
        return live;
    }

    private static final class Meteor implements WorldFx.Effect {
        private final Vec3 centre;
        private final Vec3 start;
        private final int delay;
        private final float radius;
        private int age;

        Meteor(WeatherNet.Meteor m) {
            this.centre = new Vec3(m.x(), m.y(), m.z());
            this.delay = Math.max(1, m.delay());
            this.radius = m.radius();
            // it comes in from the shower's heading, steeply
            this.start = centre.add(-Math.cos(m.heading()) * 26.0, 58.0, -Math.sin(m.heading()) * 26.0);
            live++;
        }

        @Override
        public boolean tick() {
            age++;
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                live--;
                return false;
            }
            if (age > delay - FALL_TICKS && age < delay) {
                shed(level);
            }
            if (age == delay) {
                burst(level);
            }
            if (age >= delay + MARK_TICKS) {
                live--;
                return false;
            }
            return true;
        }

        private Vec3 head(float t) {
            double s = Math.max(0.0, Math.min(1.0, (t - (delay - FALL_TICKS)) / FALL_TICKS));
            s = Math.pow(s, 1.25); // speeding up as it comes
            return start.lerp(centre, s);
        }

        private void shed(ClientLevel level) {
            if (!FxParticles.ready()) {
                return;
            }
            Vec3 at = head(age);
            Vec3 back = start.subtract(centre).normalize();
            int n = FxBudget.count(3, at, true);
            for (int i = 0; i < n; i++) {
                Vec3 v = back.scale(0.15 + RANDOM.nextDouble() * 0.2)
                        .add((RANDOM.nextDouble() - 0.5) * 0.12, (RANDOM.nextDouble() - 0.5) * 0.12, (RANDOM.nextDouble() - 0.5) * 0.12);
                FxParticle p = FxParticles.spark(level, at).velocity(v).color(1.0f, 0.45f, 0.18f)
                        .size(0.07f, 0.02f).life(8 + RANDOM.nextInt(6)).drag(0.9f);
                FxBudget.spawn(p);
            }
        }

        private void burst(ClientLevel level) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                double d = mc.player.position().distanceTo(centre);
                if (d < 14.0) {
                    ClientCombat.feelSlam(0.45 * (1.0 - d / 14.0), 2.0 * (1.0 - d / 14.0));
                }
            }
            if (!FxParticles.ready()) {
                return;
            }
            Vec3 at = centre.add(0, 0.3, 0);
            int sparks = FxBudget.count(48, at, true);
            for (int i = 0; i < sparks; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double out = 0.3 + RANDOM.nextDouble() * 0.6;
                Vec3 v = new Vec3(Math.cos(a) * out, 0.25 + RANDOM.nextDouble() * 0.6, Math.sin(a) * out);
                FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(1.0f, 0.55f, 0.22f)
                        .size(0.14f, 0.04f).life(10 + RANDOM.nextInt(12)).drag(0.86f).gravity(0.6f));
            }
            int shards = FxBudget.count(16, at, true);
            for (int i = 0; i < shards; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double out = 0.12 + RANDOM.nextDouble() * 0.3;
                Vec3 v = new Vec3(Math.cos(a) * out, 0.4 + RANDOM.nextDouble() * 0.4, Math.sin(a) * out);
                FxBudget.spawn(FxParticles.shard(level, at).velocity(v).color(1.0f, 0.3f, 0.12f)
                        .size(0.2f, 0.1f).life(24 + RANDOM.nextInt(14)).gravity(1.0f).spin(0.3f));
            }
        }

        @Override
        public void render(WorldFx.Frame frame) {
            float t = age + frame.partialTick();
            Vec3 ground = frame.relative(centre.add(0, 0.04, 0));
            if (t < delay) {
                circle(frame, ground, t);
            }
            if (t >= delay - FALL_TICKS && t < delay) {
                meteor(frame, t);
            }
            if (t >= delay) {
                impact(frame, ground, t - delay);
            }
        }

        private void circle(WorldFx.Frame frame, Vec3 ground, float t) {
            float warn = Math.min(1f, t / 4f);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.9f);
            float close = Math.min(1f, t / delay);
            VertexConsumer fill = frame.buffers().getBuffer(FxRenderTypes.shade(DISC));
            WorldFx.flat(fill, ground, radius, 0.55f, 0.02f, 0.0f, warn * (0.3f + 0.12f * pulse + 0.15f * close));
            VertexConsumer ring = frame.buffers().getBuffer(FxRenderTypes.additive(RING));
            WorldFx.flat(ring, ground.add(0, 0.01, 0), radius, 1.0f, 0.14f, 0.05f, warn * (0.75f + 0.25f * pulse));
            WorldFx.flat(ring, ground.add(0, 0.02, 0), Math.max(0.15f, radius * close), 1.0f, 0.5f, 0.25f, warn * 0.85f);
        }

        private void meteor(WorldFx.Frame frame, float t) {
            Vec3 headWorld = head(t);
            Vec3 dir = centre.subtract(start).normalize();
            Vec3 h = frame.relative(headWorld);
            Vec3 tail = frame.relative(headWorld.subtract(dir.scale(TRAIL)));
            float in = Math.min(1f, (t - (delay - FALL_TICKS)) / 4f);
            VertexConsumer trail = frame.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            ribbon(trail, tail, h, 1.3f, 1.0f, 0.22f, 0.06f, 0.8f * in);
            ribbon(trail, tail, h, 0.55f, 1.0f, 0.7f, 0.4f, in);
            VertexConsumer glow = frame.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            WorldFx.billboard(glow, frame.camera(), h, 3.2f, 1.0f, 0.3f, 0.1f, 0.8f * in);
            WorldFx.billboard(glow, frame.camera(), h, 1.3f, 1.0f, 0.92f, 0.75f, in);
        }

        private void impact(WorldFx.Frame frame, Vec3 ground, float since) {
            // a scorch mark that cools over three seconds
            float cool = 1f - since / MARK_TICKS;
            if (cool > 0f) {
                VertexConsumer mark = frame.buffers().getBuffer(FxRenderTypes.shade(DISC));
                WorldFx.flat(mark, ground, 1.9f, 0.12f, 0.03f, 0.01f, 0.55f * cool);
                VertexConsumer embers = frame.buffers().getBuffer(FxRenderTypes.additive(RING));
                WorldFx.flat(embers, ground.add(0, 0.01, 0), 1.6f, 1.0f, 0.3f, 0.08f, 0.6f * cool * cool);
            }
            float k = since / AFTER_TICKS;
            if (k >= 1f) {
                return;
            }
            VertexConsumer glow = frame.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            float flash = (1f - k) * (1f - k);
            WorldFx.billboard(glow, frame.camera(), ground.add(0, 1.0, 0), 3.0f + 8.0f * (float) Math.sqrt(k), 1.0f, 0.62f, 0.3f, flash);
            WorldFx.billboard(glow, frame.camera(), ground.add(0, 0.8, 0), 1.5f + 3.0f * (float) Math.sqrt(k), 1.0f, 0.95f, 0.85f, flash);
            VertexConsumer ring = frame.buffers().getBuffer(FxRenderTypes.additive(RING));
            WorldFx.flat(ring, ground.add(0, 0.05, 0), 1.0f + (radius + 4.0f) * (float) Math.sqrt(k), 1.0f, 0.45f, 0.18f, flash);
        }

        /** A camera-facing strip from {@code a} (faded out) to {@code b} (full), {@code width} across at the head. */
        private static void ribbon(VertexConsumer out, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
            Vec3 along = b.subtract(a);
            Vec3 mid = a.add(b).scale(0.5);
            Vec3 side = along.cross(mid).normalize();
            if (side.lengthSqr() < 1e-6) {
                return;
            }
            Vec3 wb = side.scale(width);
            Vec3 wa = side.scale(width * 0.3);
            WorldFx.vertex(out, a.subtract(wa), 0f, 0.15f, r, g, bl, 0f);
            WorldFx.vertex(out, b.subtract(wb), 0f, 0.85f, r, g, bl, alpha);
            WorldFx.vertex(out, b.add(wb), 1f, 0.85f, r, g, bl, alpha);
            WorldFx.vertex(out, a.add(wa), 1f, 0.15f, r, g, bl, 0f);
        }
    }
}
