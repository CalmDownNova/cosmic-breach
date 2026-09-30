package com.cosmicbreach.client.onboarding;

import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.onboarding.OnboardingNet;
import com.cosmicbreach.onboarding.OnboardingRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * A Starfall's streak on the client ({@link OnboardingNet.Streak}): a white-gold head in a pale halo with a
 * long trail, crossing the sky in 3 s and speeding up as it comes, then a flash, a shock ring and sparks
 * where it lands, and the trail fading out over the next 2 s. The streak is sky, not terrain, so it is drawn
 * without fog (it starts far beyond the render distance); terrain in front still hides it. Its sound, a rushing
 * whistle, follows the head.
 */
public final class StarfallSky {
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
    private static final RandomSource RANDOM = RandomSource.create();
    /** The wake and tail linger this long after the landing. */
    private static final int FADE_TICKS = 70;
    /**
     * The bright tail behind the head, in blocks: most of the 3 s fall's path (W4's review found 110 short for a
     * fall that long), fading along its length.
     */
    private static final double TRAIL = 180.0;
    /** Sizes never shrink below these angles (radians, roughly): a star far off still reads as one. */
    private static final double HALO_ANGLE = 0.12;
    private static final double CORE_ANGLE = 0.03;
    private static final double TRAIL_ANGLE = 0.012;
    /** Soft glows strung down the tail. */
    private static final int TAIL_GLOWS = 56;
    private static int seen;

    private StarfallSky() {
    }

    static void add(OnboardingNet.Streak payload) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Streak s = new Streak(payload);
        STREAKS.add(s);
        seen++;
        Minecraft.getInstance().getSoundManager().play(new StreakSound(s));
    }

    static void tick() {
        if (Minecraft.getInstance().level == null) {
            STREAKS.clear();
            return;
        }
        STREAKS.removeIf(s -> !s.tick());
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STREAKS.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0e7f);
        RenderSystem.setShaderFogEnd(2.0e7f);
        try {
            for (Streak s : STREAKS) {
                s.render(camera, camera.getPosition(), partialTick);
            }
            BUFFERS.endBatch();
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Streaks on screen now (checks). */
    public static int live() {
        return STREAKS.size();
    }

    /** Streaks this client has been sent since it started (checks). */
    public static int seen() {
        return seen;
    }

    /** Where the newest streak's head is now, or null (checks). */
    public static Vec3 head() {
        return STREAKS.isEmpty() ? null : STREAKS.get(STREAKS.size() - 1).head(STREAKS.get(STREAKS.size() - 1).age);
    }

    private static final class Streak {
        final Vec3 from;
        final Vec3 to;
        final int ticks;
        int age;

        Streak(OnboardingNet.Streak p) {
            this.from = p.from();
            this.to = p.to();
            this.ticks = Math.max(1, p.ticks());
        }

        boolean tick() {
            age++;
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age < ticks) {
                shed(level);
            }
            if (level != null && age == ticks) {
                burst(level);
            }
            return age < ticks + FADE_TICKS;
        }

        Vec3 head(float t) {
            double s = Math.max(0.0, Math.min(1.0, t / ticks));
            return from.lerp(to, Math.pow(s, 1.3));
        }

        private void shed(ClientLevel level) {
            if (!FxParticles.ready()) {
                return;
            }
            Vec3 at = head(age);
            Vec3 back = from.subtract(to).normalize();
            int n = FxBudget.count(4, at, true);
            for (int i = 0; i < n; i++) {
                Vec3 v = back.scale(0.3 + RANDOM.nextDouble() * 0.4).add((RANDOM.nextDouble() - 0.5) * 0.3,
                        (RANDOM.nextDouble() - 0.5) * 0.3, (RANDOM.nextDouble() - 0.5) * 0.3);
                FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(1.0f, 0.9f, 0.6f).size(0.5f, 0.1f)
                        .life(14 + RANDOM.nextInt(10)).drag(0.92f));
            }
        }

        private void burst(ClientLevel level) {
            if (!FxParticles.ready()) {
                return;
            }
            int sparks = FxBudget.count(60, to, true);
            for (int i = 0; i < sparks; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double out = 0.3 + RANDOM.nextDouble() * 0.7;
                Vec3 v = new Vec3(Math.cos(a) * out, 0.35 + RANDOM.nextDouble() * 0.8, Math.sin(a) * out);
                FxBudget.spawn(FxParticles.spark(level, to).velocity(v).color(1.0f, 0.88f, 0.55f).size(0.18f, 0.05f)
                        .life(14 + RANDOM.nextInt(14)).drag(0.87f).gravity(0.6f));
            }
        }

        void render(Camera camera, Vec3 cam, float partialTick) {
            float t = age + partialTick;
            float fade = t <= ticks ? 1f : Math.max(0f, 1f - (t - ticks) / FADE_TICKS);
            Vec3 headWorld = head(Math.min(t, ticks));
            Vec3 dir = to.subtract(from).normalize();
            double length = Math.min(TRAIL, headWorld.distanceTo(from));
            Vec3 h = headWorld.subtract(cam);
            Vec3 tail = headWorld.subtract(dir.scale(length)).subtract(cam);
            double far = h.length();
            float in = Math.min(1f, t / 6f);
            float width = (float) Math.max(0.6, far * TRAIL_ANGLE);
            VertexConsumer trail = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            // the whole path so far, a faint warm wake that lingers after the landing
            Vec3 wakeStart = from.subtract(cam);
            ribbon(trail, wakeStart, h, width * 4.0f, 1.0f, 0.62f, 0.3f, 0.45f * in * fade, 0.3f);
            // the bright tail: a wide warm glow, a gold body, a white-hot core, twice over for the core so it
            // holds against a daylight sky
            ribbon(trail, tail, h, width * 9.0f, 1.0f, 0.55f, 0.2f, 0.8f * in * fade, 0.3f);
            ribbon(trail, tail, h, width * 4.0f, 1.0f, 0.82f, 0.45f, in * fade, 0.4f);
            ribbon(trail, tail, h, width * 1.6f, 1.0f, 1.0f, 0.95f, in * fade, 0.35f);
            ribbon(trail, tail, h, width * 1.6f, 1.0f, 1.0f, 0.95f, in * fade, 0.35f);
            VertexConsumer glow = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            // a chain of soft glows down the tail, shrinking and fading: the tail's body
            Vec3 step = h.subtract(tail).scale(1.0 / TAIL_GLOWS);
            for (int i = 1; i <= TAIL_GLOWS; i++) {
                float k = (float) i / TAIL_GLOWS;
                Vec3 at = h.subtract(step.scale(i));
                float size = width * (6.0f - 4.5f * k);
                WorldFx.billboard(glow, camera, at, size, 1.0f, 0.72f - 0.2f * k, 0.4f - 0.25f * k,
                        0.55f * (1f - k) * (1f - k) * in * fade);
            }
            if (t < ticks) {
                float halo = (float) Math.max(12.0, far * HALO_ANGLE);
                float core = (float) Math.max(3.0, far * CORE_ANGLE);
                WorldFx.billboard(glow, camera, h, halo, 1.0f, 0.62f, 0.3f, in);
                WorldFx.billboard(glow, camera, h, halo * 0.55f, 1.0f, 0.9f, 0.6f, in);
                WorldFx.billboard(glow, camera, h, core * 1.6f, 1.0f, 1.0f, 1.0f, in);
                WorldFx.billboard(glow, camera, h, core, 1.0f, 1.0f, 1.0f, in);
                // a four-pointed glint: the head reads as a star
                VertexConsumer beam = BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
                float spin = t * 0.05f;
                flare(beam, camera, h, core * 4.5f, core * 0.35f, spin, 1.0f, 0.95f, 0.8f, 0.9f * in);
                flare(beam, camera, h, core * 4.5f, core * 0.35f, spin + (float) (Math.PI / 2), 1.0f, 0.95f, 0.8f, 0.9f * in);
            } else {
                float k = (t - ticks) / 20f;
                if (k < 1f) {
                    float flash = (1f - k) * (1f - k);
                    Vec3 ground = to.subtract(cam);
                    WorldFx.billboard(glow, camera, ground.add(0, 1.5, 0), 6f + 22f * (float) Math.sqrt(k), 1.0f, 0.92f, 0.7f, flash);
                    WorldFx.billboard(glow, camera, ground.add(0, 1.0, 0), 2.5f + 6f * (float) Math.sqrt(k), 1.0f, 1.0f, 1.0f, flash);
                    VertexConsumer ring = BUFFERS.getBuffer(FxRenderTypes.additive(com.cosmicbreach.client.weather.MeteorFx.RING));
                    WorldFx.flat(ring, ground.add(0, 0.1, 0), 1.0f + 9.0f * (float) Math.sqrt(k), 1.0f, 0.9f, 0.6f, flash);
                }
            }
        }

        /**
         * A camera-facing strip from {@code a} to {@code b}, {@code width} across at the head {@code b}, a quarter
         * of that at the tail; its light falls to {@code tailShare} of {@code alpha} at the tail.
         */
        private static void ribbon(VertexConsumer out, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha,
                float tailShare) {
            Vec3 along = b.subtract(a);
            Vec3 mid = a.add(b).scale(0.5);
            Vec3 side = along.cross(mid);
            if (side.lengthSqr() < 1e-8) {
                return;
            }
            side = side.normalize();
            Vec3 wb = side.scale(width);
            Vec3 wa = side.scale(width * 0.25);
            float ta = alpha * tailShare;
            WorldFx.vertex(out, a.subtract(wa), 0f, 0.15f, r, g, bl, ta);
            WorldFx.vertex(out, b.subtract(wb), 0f, 0.85f, r, g, bl, alpha);
            WorldFx.vertex(out, b.add(wb), 1f, 0.85f, r, g, bl, alpha);
            WorldFx.vertex(out, a.add(wa), 1f, 0.15f, r, g, bl, ta);
        }

        /** A pair of glint arms: a thin quad through {@code at}, {@code length} each way, turned {@code angle} on screen. */
        private static void flare(VertexConsumer out, Camera camera, Vec3 at, float length, float thickness, float angle,
                float r, float g, float b, float alpha) {
            org.joml.Vector3f left = camera.getLeftVector();
            org.joml.Vector3f up = camera.getUpVector();
            double c = Math.cos(angle);
            double sn = Math.sin(angle);
            Vec3 l = new Vec3(left.x() * c + up.x() * sn, left.y() * c + up.y() * sn, left.z() * c + up.z() * sn);
            Vec3 u = new Vec3(-left.x() * sn + up.x() * c, -left.y() * sn + up.y() * c, -left.z() * sn + up.z() * c);
            Vec3 along = l.scale(length);
            Vec3 across = u.scale(thickness);
            WorldFx.vertex(out, at.subtract(along).subtract(across), 0f, 0.15f, r, g, b, 0f);
            WorldFx.vertex(out, at.subtract(along).add(across), 1f, 0.15f, r, g, b, 0f);
            WorldFx.vertex(out, at.add(across), 1f, 0.5f, r, g, b, alpha);
            WorldFx.vertex(out, at.subtract(across), 0f, 0.5f, r, g, b, alpha);
            WorldFx.vertex(out, at.subtract(across), 0f, 0.5f, r, g, b, alpha);
            WorldFx.vertex(out, at.add(across), 1f, 0.5f, r, g, b, alpha);
            WorldFx.vertex(out, at.add(along).add(across), 1f, 0.85f, r, g, b, 0f);
            WorldFx.vertex(out, at.add(along).subtract(across), 0f, 0.85f, r, g, b, 0f);
        }
    }

    /** The streak's rushing whistle, riding the head so it pans across the sky; not attenuated (it is loud). */
    private static final class StreakSound extends AbstractTickableSoundInstance {
        private final Streak streak;

        StreakSound(Streak streak) {
            super(OnboardingRegistry.STARFALL_STREAK.get(), SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.streak = streak;
            this.attenuation = Attenuation.NONE;
            this.looping = false;
            this.volume = 1.0f;
            place();
        }

        private void place() {
            Vec3 h = streak.head(streak.age);
            x = h.x;
            y = h.y;
            z = h.z;
            var player = Minecraft.getInstance().player;
            if (player != null) {
                double d = player.position().distanceTo(h);
                volume = (float) Math.max(0.25, Math.min(1.0, 1.25 - d / 300.0));
            }
        }

        @Override
        public void tick() {
            if (streak.age > streak.ticks + 10 || Minecraft.getInstance().level == null) {
                stop();
                return;
            }
            place();
        }
    }
}
