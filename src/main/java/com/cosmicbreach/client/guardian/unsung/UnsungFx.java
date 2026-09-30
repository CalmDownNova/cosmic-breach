package com.cosmicbreach.client.guardian.unsung;

import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.client.guardian.TelegraphDraw;
import com.cosmicbreach.guardian.unsung.ChoirArena;
import com.cosmicbreach.guardian.unsung.HarmonizeRules;
import com.cosmicbreach.guardian.unsung.SongNote;
import com.cosmicbreach.guardian.unsung.Unsung;
import com.cosmicbreach.guardian.unsung.UnsungEffects;
import com.cosmicbreach.guardian.unsung.UnsungMask;
import com.cosmicbreach.guardian.unsung.UnsungMoves;
import com.cosmicbreach.guardian.unsung.Voice;
import com.cosmicbreach.world.VesperClock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Unsung's telegraphs and effects on the client (Unsung design v1), in the engine's telegraph language: gold is
 * parryable, white is where to stand (or a floor band to jump or leave), the mask's own colour is its light.
 *
 * <p>Drawn every frame from the choir's synced state, so a warning is exactly where the hit will be:
 * <ul>
 *   <li>under each mask a pool of its light on the floor (the singer's bright, the others' faint) and a halo behind the
 *       singer's face: the fight is lit by the singers;</li>
 *   <li>the Tenor's inhale: a silver ring drawing in round the dais for a beat; then the Sweeping Wave, a knee-high
 *       curtain of silver running out to the wall with a white edge on the floor;</li>
 *   <li>Ground Ripples: half a beat ahead the floor under the Bass goes dark in rings, each ring rimmed in bright
 *       magenta (so it reads on the dark basalt), the outer rim marking the reach; then a magenta shockwave;</li>
 *   <li>the Bass Drop: a gold ring filling under its target for two beats, a gold curtain standing on it so it reads at
 *       eye level, pulsing after the glint; the landing a gold burst;</li>
 *   <li>Harmonize's warning: columns of white light standing on the lit circles of silence, pulsing on each beat and
 *       growing to the downbeat; the downbeat a white wave across the floor.</li>
 * </ul>
 * One-shot effects answer the server's events: the porcelain tink, a mask shattering, a Break, a parry, the first
 * notes, a note's burst or its breaking, the last chord. Client thread.
 */
final class UnsungFx implements UnsungEffects.Handler {
    static final int WHITE = 0xFFFFFF;
    static final int SILVER = 0xD8F4FF;
    static final int SILVER_EDGE = 0x7FD8FF;
    static final int GOLD = 0xFFC23A;
    static final int PALE_GOLD = 0xFFE7A6;
    static final int MAGENTA = 0xFF3FC0;
    static final int SHADOW = 0x0A0610;
    static final int PORCELAIN = 0xF2EEE6;
    /** The circles of silence's pale inlay. */
    static final int INLAY = 0xD9D2F2;

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    private final RandomSource random = RandomSource.create();

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static boolean ready() {
        return level() != null && FxParticles.ready();
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    /** A disc of light on the floor, full at its middle and fading to nothing at its rim (no square edges). */
    static void softDisc(VertexConsumer out, Vec3 centre, double radius, int segments, float[] col, float alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            Vec3 p0 = centre.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3 p1 = centre.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            ShardDraw.vertex(out, IDENTITY, centre, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p0, 0.5f, 0.5f, col[0], col[1], col[2], 0f);
            ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, col[0], col[1], col[2], 0f);
            ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, col[0], col[1], col[2], 0f);
        }
    }

    /**
     * A puff of the void cloth's smoke, drifting down and out like ink in water: a dark soft blot with a faint light of
     * the mask's colour in it, swelling and fading.
     */
    static final class Puff implements WorldFx.Effect {
        private final Vec3 start;
        private final Vec3 velocity;
        private final float[] dark;
        private final float[] light;
        private final float size;
        private final int life;
        private final double born = com.cosmicbreach.client.fx.FxClock.ticks();

        Puff(Vec3 start, Vec3 velocity, int accent, float size, int life) {
            this.start = start;
            this.velocity = velocity;
            float[] a = ShardDraw.rgb(accent);
            this.dark = new float[] {0.05f + a[0] * 0.08f, 0.03f + a[1] * 0.06f, 0.07f + a[2] * 0.1f};
            this.light = a;
            this.size = size;
            this.life = life;
        }

        @Override
        public boolean tick() {
            return com.cosmicbreach.client.fx.FxClock.ticks() - born < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - born;
            double u = age / life;
            if (u < 0 || u >= 1) {
                return;
            }
            Vec3 at = f.relative(start.add(velocity.scale(age)));
            float swell = (float) (size * (0.55 + 1.1 * u));
            float a = (float) Math.sin(Math.PI * Math.min(1.0, u * 1.3));
            TelegraphDraw.glow(f.buffers().getBuffer(ShardDraw.translucent(ShardDraw.GLOW, true)), f.camera(), at, swell, dark, 0.55f * a);
            TelegraphDraw.glow(f.buffers().getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), f.camera(), at, swell * 0.7f, light, 0.07f * a);
        }
    }

    /** Harmonize's shock ring: a white wall of light running out over the floor and sinking as it goes. */
    static final class Shock implements WorldFx.Effect {
        private final Vec3 centre;
        private final double from;
        private final double to;
        private final int life;
        private final double born = com.cosmicbreach.client.fx.FxClock.ticks();

        Shock(Vec3 centre, double from, double to, int life) {
            this.centre = centre;
            this.from = from;
            this.to = to;
            this.life = life;
        }

        @Override
        public boolean tick() {
            return com.cosmicbreach.client.fx.FxClock.ticks() - born < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double u = (f.now() - born) / life;
            if (u < 0 || u >= 1) {
                return;
            }
            double r = from + (to - from) * (1.0 - Math.pow(1.0 - u, 2.2));
            float a = (float) (1.0 - u);
            Vec3 c = f.relative(centre);
            float[] w = ShardDraw.rgb(WHITE);
            float[] e = ShardDraw.rgb(SILVER_EDGE);
            TelegraphDraw.curtain(f.buffers().getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), c, r, 0f, -180.0, 180.0, 96, 3.0 * (1.0 - 0.6 * u),
                    w, 0.9f * a);
            ShardDraw.band(f.buffers().getBuffer(ShardDraw.solid()), IDENTITY, c, Math.max(0.0, r - 0.7), r, 96, w[0], w[1], w[2], 0.9f * a);
            ShardDraw.band(f.buffers().getBuffer(ShardDraw.solid()), IDENTITY, c.add(0, 0.004, 0), r, r + 0.3, 96, e[0], e[1], e[2], a);
        }
    }

    /** The masks of a choir this client knows. */
    static List<UnsungMask> masks(Unsung u) {
        List<UnsungMask> out = new ArrayList<>();
        ClientLevel level = level();
        if (level == null) {
            return out;
        }
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof UnsungMask m && m.choir() == u) {
                out.add(m);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ every frame

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = mc.level.getGameTime() + partial;
        boolean any = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Unsung u && u.arena() != null && e.distanceToSqr(cam) < 96 * 96) {
                restingCircles(u, cam);
                if (u.state() != Unsung.State.DORMANT) {
                    draw(u, camera, cam, time, partial);
                }
                any = true;
            }
        }
        if (any) {
            BUFFERS.endBatch();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void draw(Unsung u, Camera camera, Vec3 cam, double time, float partial) {
        ChoirArena a = u.arena();
        long now = (long) Math.floor(time);
        float beatPulse = VesperClock.pulse(now, partial);
        // the singers' light
        for (UnsungMask m : masks(u)) {
            if (m.shattered()) {
                continue;
            }
            Vec3 face = m.getPosition(partial).add(0, UnsungMoves.FACE_UP, 0);
            float[] c = ShardDraw.rgb(m.voice().glow);
            boolean sings = (m.singing() && m.mode() != UnsungMask.Mode.FALLEN) || m.mode() == UnsungMask.Mode.DROP;
            float strength = sings ? 0.55f + 0.2f * beatPulse : m.mode() == UnsungMask.Mode.REST ? 0.05f : 0.16f;
            double ground = a.groundY(face.x, face.z) + 0.03;
            double height = Math.max(0.0, face.y - ground);
            float pool = (float) (3.2 + 0.25 * Math.max(0.0, 6.0 - height));
            softDisc(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), new Vec3(face.x, ground, face.z).subtract(cam), pool, 40, c,
                    strength * 0.55f);
            if (sings) {
                // the singer's halo in its voice's colour, behind its hood: who sings reads at a glance
                Vec3 at = face.add(0, 0.15, 0).subtract(cam);
                at = at.add(at.normalize().scale(0.9));
                TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 2.6f + 0.3f * beatPulse, c, 0.42f);
                TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(SongNoteRenderer.RING)), camera, at, 2.05f + 0.08f * beatPulse, c,
                        0.75f + 0.25f * beatPulse);
                TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(SongNoteRenderer.RING)), camera, at, 2.0f, ShardDraw.rgb(WHITE),
                        0.25f + 0.2f * beatPulse);
            }
        }
        if (u.state() != Unsung.State.FIGHT) {
            return;
        }
        wave(u, a, camera, cam, time);
        ripple(u, camera, cam, time);
        drop(u, camera, cam, time);
        harmonize(u, a, camera, cam, time, beatPulse);
    }

    /** The eight circles of silence at rest, asleep or awake: each a faint pale inlaid ring, so the ring of eight reads. */
    private static void restingCircles(Unsung u, Vec3 cam) {
        ChoirArena a = u.arena();
        float[] p = ShardDraw.rgb(INLAY);
        for (int k = 0; k < HarmonizeRules.CIRCLES; k++) {
            if (u.warning() && lit(u, k)) {
                continue;
            }
            Vec3 c = a.circleCentre(k).add(0, 0.02, 0).subtract(cam);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, c, ChoirArena.CIRCLE_RADIUS + 0.05, ChoirArena.CIRCLE_RADIUS + 0.2, 48,
                    p[0], p[1], p[2], 0.3f);
            ShardDraw.band(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), IDENTITY, c.add(0, 0.003, 0), ChoirArena.CIRCLE_RADIUS - 0.05,
                    ChoirArena.CIRCLE_RADIUS + 0.3, 48, p[0], p[1], p[2], 0.06f);
        }
    }

    private static boolean lit(Unsung u, int circle) {
        for (int k : u.litCircles()) {
            if (k == circle) {
                return true;
            }
        }
        return false;
    }

    /** The Tenor's inhale, then the Sweeping Wave running out from the dais. */
    private static void wave(Unsung u, ChoirArena a, Camera camera, Vec3 cam, double time) {
        double t = time - u.waveRelease();
        Vec3 centre = a.centre().add(0, 0.02, 0).subtract(cam);
        if (t >= -UnsungMoves.WAVE_INHALE && t < 0) {
            double k = 1.0 + t / UnsungMoves.WAVE_INHALE;          // 0 .. 1 through the inhale
            double r = ChoirArena.STEP_RADIUS + 1.5 - 1.2 * k;
            float[] s = ShardDraw.rgb(SILVER);
            float[] e = ShardDraw.rgb(SILVER_EDGE);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre, r - 0.18, r + 0.18, 64, s[0], s[1], s[2], (float) (0.5 + 0.5 * k));
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre.add(0, 0.003, 0), r + 0.18, r + 0.32, 64, e[0], e[1], e[2],
                    (float) (0.6 + 0.4 * k));
            TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), centre, r, 0f, -180.0, 180.0, 64,
                    UnsungMoves.WAVE_HEIGHT * (0.5 + k), s, (float) (0.35 + 0.4 * k));
            return;
        }
        if (t < 0) {
            return;
        }
        double r = ChoirArena.waveFront(t);
        if (r > ChoirArena.FLOOR_RADIUS + 1.0) {
            return;
        }
        float fade = (float) Math.min(1.0, (ChoirArena.FLOOR_RADIUS + 1.0 - r) / 3.0);
        float[] s = ShardDraw.rgb(SILVER);
        float[] e = ShardDraw.rgb(SILVER_EDGE);
        // the wave stands on the ground under it: over the dais it runs a block higher
        double lift = r < ChoirArena.DAIS_RADIUS ? 1.0 : r < ChoirArena.STEP_RADIUS ? 0.5 : 0.0;
        Vec3 base = centre.add(0, lift, 0);
        double h = UnsungMoves.WAVE_HEIGHT;
        TelegraphDraw.curtain(BUFFERS.getBuffer(ShardDraw.solid()), base, r, 0f, -180.0, 180.0, 96, h, s, 0.85f * fade);
        TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), base, r, 0f, -180.0, 180.0, 96, h * 1.6, s,
                0.7f * fade);
        ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, base.add(0, 0.004, 0), Math.max(0.0, r - 0.7), r, 96, s[0], s[1], s[2],
                0.9f * fade);
        ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, base.add(0, 0.006, 0), r, r + 0.18, 96, e[0], e[1], e[2], fade);
    }

    /** Ground Ripples: dark rings rimmed in magenta under the Bass half a beat ahead, then the shockwave. */
    private static void ripple(Unsung u, Camera camera, Vec3 cam, double time) {
        double land = u.rippleLand();
        double t = time - (land - UnsungMoves.RIPPLE_TELL);
        Vec3 centre = u.rippleCentre().add(0, 0.03, 0);
        ChoirArena a = u.arena();
        centre = new Vec3(centre.x, a.groundY(centre.x, centre.z) + 0.03, centre.z).subtract(cam);
        float[] m = ShardDraw.rgb(MAGENTA);
        float[] d = ShardDraw.rgb(SHADOW);
        double reach = UnsungMoves.RIPPLE_RADIUS;
        if (t >= 0 && t < UnsungMoves.RIPPLE_TELL) {
            double k = t / UnsungMoves.RIPPLE_TELL;
            ShardDraw.disc(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre, reach, 64, d[0], d[1], d[2], (float) (0.35 + 0.35 * k));
            for (int i = 1; i <= 4; i++) {
                double r = reach * (i - 1 + k) / 4.0;
                if (r < 0.3) {
                    continue;
                }
                ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre.add(0, 0.002 * i, 0), r - 0.45, r, 64, d[0], d[1], d[2], 0.85f);
                ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre.add(0, 0.002 * i + 0.001, 0), r, r + 0.16, 64,
                        m[0], m[1], m[2], (float) (0.55 + 0.45 * k));
            }
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre.add(0, 0.012, 0), reach - 0.22, reach, 72, m[0], m[1], m[2], 1.0f);
            TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), centre, reach, 0f, -180.0, 180.0, 72, 0.9, m,
                    (float) (0.35 + 0.4 * k));
            return;
        }
        double s = time - land;
        if (s >= 0 && s < 10) {
            double r = 0.5 + (reach + 1.0) * (1.0 - Math.pow(1.0 - s / 10.0, 2.0));
            float alpha = (float) (1.0 - s / 10.0);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre.add(0, 0.01, 0), Math.max(0, r - 0.5), r, 72, m[0], m[1], m[2], alpha);
            TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), centre, r, 0f, -180.0, 180.0, 72, 1.1, m, alpha * 0.7f);
        }
    }

    /** The Bass Drop's gold ring under its target, filling for two beats. */
    private static void drop(Unsung u, Camera camera, Vec3 cam, double time) {
        double land = u.dropLand();
        double t = time - (land - UnsungMoves.DROP_TELL);
        Vec3 point = u.dropPoint().add(0, 0.035, 0).subtract(cam);
        float[] g = ShardDraw.rgb(GOLD);
        double r = UnsungMoves.DROP_RADIUS;
        if (t >= 0 && t < UnsungMoves.DROP_TELL) {
            double fill = Math.min(1.0, t / UnsungMoves.DROP_TELL);
            float pulse = t >= UnsungMoves.DROP_GLINT ? 0.5f + 0.5f * (float) Math.sin(t * 2.4) : 0f;
            ShardDraw.disc(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, point, r * fill, 48, g[0], g[1], g[2], 0.42f + 0.3f * (float) fill + 0.15f * pulse);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, point.add(0, 0.004, 0), r - 0.28, r, 64, g[0], g[1], g[2], 1.0f);
            if (fill > 0.05) {
                ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, point.add(0, 0.008, 0), r * fill - 0.15, r * fill, 48, 1f, 0.94f, 0.72f, 0.95f);
            }
            TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), point, r, 0f, -180.0, 180.0, 64,
                    0.8 + 0.6 * fill, g, 0.35f + 0.35f * (float) fill + 0.2f * pulse);
            double since = t - UnsungMoves.DROP_GLINT;
            if (since >= 0) {
                glint(camera, point, r, since);
            }
            return;
        }
        double s = time - land;
        if (s >= 0 && s < 12) {
            float alpha = (float) (1.0 - s / 12.0);
            double rr = r * (0.8 + 0.9 * s / 12.0);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, point.add(0, 0.01, 0), rr - 0.35, rr, 64, g[0], g[1], g[2], alpha);
        }
    }

    /**
     * The Bass Drop's glint, the parry cue: a white four-point star flashing on the ring's far rim (as the camera sees
     * it) and a smaller one on the near rim a moment later, turning, while the whole ring flashes white.
     */
    private static void glint(Camera camera, Vec3 point, double r, double since) {
        float flash = (float) Math.max(0.0, 1.0 - since / 6.0);
        if (flash <= 0f) {
            return;
        }
        float[] w = ShardDraw.rgb(WHITE);
        ShardDraw.band(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), IDENTITY, point.add(0, 0.012, 0), r - 0.34, r + 0.06, 64,
                w[0], w[1], w[2], 0.9f * flash);
        Vector3f l = camera.getLeftVector();
        Vector3f lk = camera.getLookVector();
        Vec3 left = new Vec3(l.x(), 0, l.z());
        Vec3 look = new Vec3(lk.x(), 0, lk.z());
        if (left.lengthSqr() < 1e-6 || look.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 far = left.normalize().add(look.normalize()).normalize().scale(r);
        float pop = (float) (1.0 + 0.5 * Math.max(0.0, 1.0 - since / 2.0));
        star(camera, point.add(far).add(0, 0.3, 0), 1.7f * pop, (float) (since * 0.12), flash);
        star(camera, point.subtract(far).add(0, 0.25, 0), 1.0f * pop, (float) (-since * 0.1), flash * 0.8f);
    }

    /** A white four-point star facing the camera, turned {@code roll} radians. */
    private static void star(Camera camera, Vec3 at, float half, float roll, float alpha) {
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z());
        Vec3 up = new Vec3(u.x(), u.y(), u.z());
        Vec3 right = left.scale(Math.cos(roll)).add(up.scale(Math.sin(roll))).scale(half);
        Vec3 top = up.scale(Math.cos(roll)).subtract(left.scale(Math.sin(roll))).scale(half);
        // twice over, added: the thin arms read at a distance
        ShardDraw.quad(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.STAR)), IDENTITY, at, right, top, 1f, 1f, 1f, alpha);
        ShardDraw.quad(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.STAR)), IDENTITY, at, right.scale(0.8), top.scale(0.8), 1f, 1f, 1f,
                alpha);
        ShardDraw.quad(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), IDENTITY, at, right.scale(0.5), top.scale(0.5), 1f, 0.97f,
                0.9f, alpha);
    }

    /** Harmonize's warning: columns of white light on the lit circles, growing to the downbeat. */
    private static void harmonize(Unsung u, ChoirArena a, Camera camera, Vec3 cam, double time, float beatPulse) {
        if (!u.warning()) {
            return;
        }
        double k = Math.max(0.0, Math.min(1.0, (time - u.warningStart()) / (UnsungMoves.WARNING_BEATS * UnsungMoves.BEAT)));
        float[] w = ShardDraw.rgb(WHITE);
        float[] e = ShardDraw.rgb(SILVER_EDGE);
        for (int circle : u.litCircles()) {
            Vec3 c = a.circleCentre(circle).add(0, 0.04, 0).subtract(cam);
            double r = ChoirArena.CIRCLE_RADIUS;
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, c, r - 0.3, r, 64, w[0], w[1], w[2], 1.0f);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, c.add(0, 0.002, 0), r, r + 0.18, 64, e[0], e[1], e[2], 0.95f);
            softDisc(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), c.add(0, 0.01, 0), r * 1.35, 48, w, 0.28f + 0.22f * beatPulse);
            double tall = 2.0 + 6.0 * k;
            TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), c, r, 0f, -180.0, 180.0, 64, tall, w,
                    (float) (0.22 + 0.25 * k + 0.2 * beatPulse));
            TelegraphDraw.curtain(BUFFERS.getBuffer(ShardDraw.solid()), c, r, 0f, -180.0, 180.0, 64, 0.4 + 0.6 * k, w, 0.3f + 0.15f * beatPulse);
        }
    }

    // ------------------------------------------------------------------ one-shot events

    private void shake(Vec3 at, double trauma, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double d = mc.player.position().distanceTo(at);
            if (d < range) {
                ClientCombat.shake().addTrauma(trauma * (1.0 - d / range));
            }
        }
    }

    private Vec3 randomUnit() {
        Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return v.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : v.normalize();
    }

    private void sparks(Vec3 at, int wanted, int color, double speed) {
        int n = FxBudget.count(wanted, at, false);
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(speed * (0.5 + random.nextDouble()));
            FxBudget.spawn(FxParticles.spark(level(), at).velocity(v).size(0.05f, 0.01f).life(7 + random.nextInt(5)).gravity(0.35f).drag(0.86f)
                    .color(color));
        }
    }

    private void shards(Vec3 at, int wanted, double speed, int color, int alt) {
        int n = FxBudget.count(wanted, at, true);
        for (int i = 0; i < n; i++) {
            Vec3 out = randomUnit();
            Vec3 v = new Vec3(out.x, Math.abs(out.y) * 0.7 + 0.25, out.z).normalize().scale(speed * (0.5 + random.nextDouble() * 0.7));
            FxBudget.spawn(FxParticles.shard(level(), at.add(out.scale(0.3))).velocity(v).color(random.nextFloat() < 0.7f ? color : alt)
                    .size(0.1f, 0.06f).life(14 + random.nextInt(10)).gravity(1.0f).drag(0.96f).spin(0.3f));
        }
    }

    @Override
    public void choirEvent(Unsung u, byte event) {
        if (!ready() || u.arena() == null) {
            return;
        }
        ChoirArena a = u.arena();
        Vec3 floor = a.centre().add(0, 0.05, 0);
        switch (event) {
            case Unsung.EVENT_AWAKEN -> {
                WorldFx.add(new TelegraphDraw.Flash(floor, ShardDraw.RING, true, false, 1.0f, 16.0f, 0x2A1840, 0.8f, 40));
                shake(floor, 0.2, 24);
            }
            case Unsung.EVENT_BREAK -> {
                WorldFx.add(new TelegraphDraw.Flash(floor.add(0, 0.02, 0), ShardDraw.RING, true, false, 2.0f, 14.0f, WHITE, 0.9f, 14));
                for (UnsungMask m : masks(u)) {
                    if (!m.shattered()) {
                        Vec3 f = m.position().add(0, UnsungMoves.FACE_UP, 0);
                        WorldFx.add(new TelegraphDraw.Flash(f, ShardDraw.GLOW, false, true, 1.0f, 3.0f, m.voice().glow, 0.9f, 10));
                        shards(f, 10, 0.25, PORCELAIN, m.voice().glow);
                    }
                }
                shake(floor, 0.45, 24);
            }
            case Unsung.EVENT_WARNING -> {
                for (int k : u.litCircles()) {
                    Vec3 c = a.circleCentre(k).add(0, 0.05, 0);
                    WorldFx.add(new TelegraphDraw.Flash(c, ShardDraw.RING, true, false, 0.5f, 3.2f, WHITE, 1.0f, 16));
                    WorldFx.add(new TelegraphDraw.Flash(c.add(0, 1.0, 0), ShardDraw.GLOW, false, true, 0.8f, 2.8f, WHITE, 0.8f, 12));
                }
            }
            case Unsung.EVENT_HARMONIZE -> {
                // the downbeat: a white flash over the whole floor and a shock ring running out from the dais to the wall
                WorldFx.add(new TelegraphDraw.Flash(floor.add(0, 3.0, 0), ShardDraw.GLOW, false, true, 8.0f, 26.0f, WHITE, 1.0f, 10));
                WorldFx.add(new Shock(floor.add(0, 0.03, 0), 1.5, ChoirArena.FLOOR_RADIUS + 1.0, 14));
                WorldFx.add(new TelegraphDraw.Flash(floor.add(0, 0.03, 0), ShardDraw.RING, true, true, 1.0f, 18.0f, WHITE, 1.0f, 16));
                WorldFx.add(new TelegraphDraw.Flash(floor.add(0, 0.02, 0), ShardDraw.GLOW, true, true, 2.0f, 17.0f, 0xE8E4FF, 0.6f, 12));
                for (int k : u.litCircles()) {
                    Vec3 c = a.circleCentre(k).add(0, 1.5, 0);
                    WorldFx.add(new TelegraphDraw.Flash(c, ShardDraw.GLOW, false, true, 1.0f, 3.5f, WHITE, 1.0f, 12));
                }
                shake(floor, 0.6, 30);
            }
            case Unsung.EVENT_RIPPLE -> {
                Vec3 c = u.rippleCentre();
                for (int i = 0; i < 10; i++) {
                    double ang = random.nextDouble() * Math.PI * 2.0;
                    double r = 0.5 + random.nextDouble() * (UnsungMoves.RIPPLE_RADIUS - 0.5);
                    Vec3 at = new Vec3(c.x + Math.cos(ang) * r, a.groundY(c.x, c.z) + 0.25, c.z + Math.sin(ang) * r);
                    WorldFx.add(new Puff(at, new Vec3(Math.cos(ang) * 0.05, 0.01, Math.sin(ang) * 0.05), Voice.BASS.accent, 0.6f, 24));
                }
                shake(c, 0.25, 12);
            }
            case Unsung.EVENT_DROP -> {
                Vec3 p = u.dropPoint().add(0, 0.05, 0);
                WorldFx.add(new TelegraphDraw.Flash(p, ShardDraw.RING, true, false, 0.6f, (float) UnsungMoves.DROP_RADIUS * 1.4f, GOLD, 0.95f, 12));
                WorldFx.add(new TelegraphDraw.Flash(p.add(0, 0.8, 0), ShardDraw.GLOW, false, true, 1.0f, 3.2f, PALE_GOLD, 0.8f, 8));
                shards(p.add(0, 0.3, 0), 12, 0.3, 0x3A2D48, GOLD);
                shake(p, 0.4, 14);
            }
            case Unsung.EVENT_WAVE -> sparks(floor.add(0, 0.6, 0), 12, SILVER, 0.35);
            case Unsung.EVENT_DEATH -> {
                for (UnsungMask m : masks(u)) {
                    Vec3 f = m.position().add(0, UnsungMoves.FACE_UP * 0.4, 0);
                    int n = FxBudget.count(12, f, true);
                    for (int i = 0; i < n; i++) {
                        FxBudget.spawn(FxParticles.glint(level(), f.add(randomUnit().scale(0.8))).velocity(new Vec3(0, 0.03 + random.nextDouble() * 0.03, 0))
                                .size(0.12f, 0.02f).life(40 + random.nextInt(20)).drag(0.99f).color(m.voice().glow));
                    }
                }
            }
            case Unsung.EVENT_CHORD -> WorldFx.add(new TelegraphDraw.Flash(floor.add(0, 4.0, 0), ShardDraw.GLOW, false, true, 2.0f, 9.0f, 0xFFF4E0, 0.55f, 60));
            default -> {
            }
        }
    }

    @Override
    public void maskEvent(UnsungMask m, byte event) {
        if (!ready()) {
            return;
        }
        Vec3 face = m.position().add(0, UnsungMoves.FACE_UP, 0);
        Voice v = m.voice();
        switch (event) {
            case UnsungMask.EVENT_TINK -> {
                Vec3 toward = Minecraft.getInstance().player == null ? face : face.add(Minecraft.getInstance().player.getEyePosition().subtract(face)
                        .normalize().scale(0.7));
                WorldFx.add(new TelegraphDraw.Flash(toward, ShardDraw.RING, false, true, 0.2f, 0.9f, WHITE, 0.9f, 6));
                sparks(toward, 5, WHITE, 0.25);
            }
            case UnsungMask.EVENT_SHATTER -> {
                WorldFx.add(new TelegraphDraw.Flash(face, ShardDraw.GLOW, false, true, 1.0f, 4.5f, v.glow, 1.0f, 14));
                WorldFx.add(new TelegraphDraw.Flash(face, ShardDraw.RING, false, true, 0.5f, 3.5f, WHITE, 0.9f, 10));
                shards(face, 28, 0.35, PORCELAIN, v.glow);
                sparks(face, 14, v.glow, 0.4);
                shake(face, 0.5, 24);
            }
            case UnsungMask.EVENT_FIRST_NOTE -> {
                WorldFx.add(new TelegraphDraw.Flash(face, ShardDraw.GLOW, false, true, 0.8f, 3.2f, v.glow, 0.9f, 16));
                WorldFx.add(new TelegraphDraw.Flash(face, ShardDraw.RING, false, true, 0.4f, 2.6f, v.accent, 0.8f, 12));
            }
            case UnsungMask.EVENT_LAND -> shards(face.subtract(0, 1.0, 0), 8, 0.2, 0x3A2D48, v.glow);
            case UnsungMask.EVENT_PARRIED -> {
                WorldFx.add(new TelegraphDraw.Flash(face, ShardDraw.GLOW, false, true, 0.8f, 3.4f, GOLD, 1.0f, 12));
                sparks(face, 16, PALE_GOLD, 0.45);
                shake(face, 0.3, 12);
            }
            default -> {
            }
        }
    }

    @Override
    public void maskTick(UnsungMask m) {
        if (!ready() || m.shattered()) {
            return;
        }
        long now = level().getGameTime();
        int every = m.mode() == UnsungMask.Mode.REST ? 12 : m.singing() ? 2 : 4;
        if ((now + m.getId()) % every != 0) {
            return;
        }
        // the void cloth sheds smoke like ink in water from the ends of its tails
        double ang = random.nextDouble() * Math.PI * 2.0;
        double r = 0.15 + random.nextDouble() * 0.45;
        Vec3 at = m.position().add(Math.cos(ang) * r, 0.25 + random.nextDouble() * 0.9, Math.sin(ang) * r);
        Vec3 v = new Vec3(Math.cos(ang) * 0.012, -0.012 - random.nextDouble() * 0.012, Math.sin(ang) * 0.012);
        WorldFx.add(new Puff(at, v, m.voice().accent, 0.32f + random.nextFloat() * 0.2f, 34 + random.nextInt(16)));
    }

    @Override
    public void noteEvent(SongNote note, byte event) {
        if (!ready()) {
            return;
        }
        Vec3 c = note.centre();
        int gold = note.voice().glow;
        if (event == SongNote.EVENT_BURST) {
            WorldFx.add(new TelegraphDraw.Flash(c, ShardDraw.GLOW, false, true, 0.6f, 2.4f, gold, 1.0f, 8));
            WorldFx.add(new TelegraphDraw.Flash(c, SongNoteRenderer.RING, false, true, 0.4f, 2.2f, PALE_GOLD, 0.9f, 8));
            sparks(c, 8, gold, 0.3);
        } else {
            WorldFx.add(new TelegraphDraw.Flash(c, ShardDraw.GLOW, false, true, 0.4f, 1.6f, WHITE, 0.9f, 6));
            shards(c, 8, 0.25, gold, WHITE);
        }
    }
}
