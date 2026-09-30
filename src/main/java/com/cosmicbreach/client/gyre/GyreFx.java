package com.cosmicbreach.client.gyre;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.client.guardian.TelegraphDraw;
import com.cosmicbreach.entity.gyre.GyreEffects;
import com.cosmicbreach.entity.gyre.GyreKnight;
import com.cosmicbreach.entity.gyre.GyreKnights;
import com.cosmicbreach.entity.gyre.GyreModes;
import com.cosmicbreach.entity.gyre.GyreOrbit;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * The Gyre Knight on the client (GDD 7.1, "Sound and particles"; art review G5p): its gyroscopic hum, whose pitch tells
 * the mode ({@link GyreModes#humPitch}); the blades' rings drawn round the core at their orbit radius, so their size tells
 * the mode too (tight, expanding for a sweep), thick and outlined dark so they read against a pale sky; silver sparks
 * trailing moving blades; gold glints on the blades before a sweep cuts; red aim lines from each blade to its target
 * through a Lance Volley's telegraphs, and from each blade back through where the target stood to the core through a
 * Recall Crash's, outlined too, with a bright red flare at each aimed blade's tip that faces the eye (a lance aimed at you
 * is end on: its line shrinks to a point, the flare doesn't). Lines keep at least 5 px on screen.
 */
public final class GyreFx implements GyreEffects.Handler {
    static final int SILVER = 0xDDE8F4;
    static final int RING = 0x9ADFFF;
    static final int GOLD = 0xFFC845;
    static final int RED = 0xFF2A22;
    static final int FLARE = 0xFF5A2E;
    static final int OUTLINE = 0x0A1B2C;
    static final int RED_OUTLINE = 0x2A0402;
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 17));
    private static final Map<GyreKnight, Hum> HUMS = new WeakHashMap<>();
    private final RandomSource random = RandomSource.create();

    /** The hum: a seamless loop following the Knight, its pitch and volume set every tick by its mode. */
    static final class Hum extends AbstractTickableSoundInstance {
        private final GyreKnight knight;

        Hum(GyreKnight knight) {
            super(GyreKnights.HUM.get(), SoundSource.HOSTILE, RandomSource.create());
            this.knight = knight;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.8f;
            this.pitch = 0.75f;
            follow();
        }

        @Override
        public void tick() {
            if (knight.isRemoved() || !knight.isAlive()) {
                stop();
                return;
            }
            refresh();
        }

        /** Follows the Knight and takes its mode's pitch and volume (also every client tick of the Knight, played or muted). */
        void refresh() {
            follow();
            double t = knight.level().getGameTime() - knight.modeStart();
            pitch = GyreModes.humPitch(knight.mode(), t, knight.approachEnd());
            volume = knight.mode() == GyreModes.Mode.STUNNED ? 0.25f : knight.mode() == GyreModes.Mode.SHIELD ? 0.7f : 1.0f;
        }

        boolean done() {
            return isStopped();
        }

        private void follow() {
            x = knight.getX();
            y = knight.getY() + GyreKnight.CORE_Y;
            z = knight.getZ();
        }
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    /** True while this client keeps a hum for {@code knight} (tests; the test client's sound is muted, so it may not be audible). */
    public static boolean humming(GyreKnight knight) {
        Hum h = HUMS.get(knight);
        return h != null && !h.done();
    }

    /** The pitch of this client's hum for {@code knight} (tests), or 0. */
    public static float humPitch(GyreKnight knight) {
        Hum h = HUMS.get(knight);
        return h == null ? 0f : h.getPitch();
    }

    private Vec3 randomUnit() {
        Vec3 v = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
        return v.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : v.normalize();
    }

    @Override
    public void knightTick(GyreKnight k) {
        if (level() == null) {
            return;
        }
        Hum hum = HUMS.get(k);
        if ((hum == null || hum.isStopped()) && k.isAlive() && k.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) < 40 * 40) {
            hum = new Hum(k);
            HUMS.put(k, hum);
            Minecraft.getInstance().getSoundManager().play(hum);
        }
        if (hum != null) {
            hum.refresh();
        }
        if (!FxParticles.ready()) {
            return;
        }
        GyreModes.Mode mode = k.mode();
        long t = level().getGameTime() - k.modeStart();
        boolean cutting = mode == GyreModes.Mode.SWEEP && GyreModes.sweepCutting(t, k.approachEnd());
        for (int i = 0; i < GyreOrbit.BLADES; i++) {
            GyreKnight.Blade b = k.blade(i);
            if (!cutting && b != GyreKnight.Blade.FLY && b != GyreKnight.Blade.RIP && level().getGameTime() % 4 != 0) {
                continue;
            }
            Vec3 at = k.renderBlade(i, 0f);
            Vec3 tip = at.add(k.renderBladeDirection(i, 0f).scale(GyreOrbit.BLADE_LENGTH / 2.0));
            if (FxBudget.count(1, tip, false) > 0) {
                FxBudget.spawn(FxParticles.spark(level(), tip).velocity(randomUnit().scale(0.05)).size(0.07f, 0.01f).life(8)
                        .color(cutting ? GOLD : SILVER));
            }
        }
    }

    @Override
    public void knightEvent(GyreKnight k, byte event) {
        if (level() == null || !FxParticles.ready()) {
            return;
        }
        switch (event) {
            case GyreKnight.EVENT_GLINT -> {
                for (int i = 0; i < GyreOrbit.BLADES; i++) {
                    int blade = i;
                    WorldFx.add(new TelegraphDraw.Glint(() -> k.isRemoved() ? null : k.renderBlade(blade, 0f)
                            .add(k.renderBladeDirection(blade, 0f).scale(GyreOrbit.BLADE_LENGTH / 2.0)), GOLD, 1.3f, 12, 0.2f));
                }
            }
            case GyreKnight.EVENT_PARRIED -> burst(k.core(), 18, GOLD, 0.25);
            case GyreKnight.EVENT_STUN -> burst(k.core(), 26, SILVER, 0.2);
            case GyreKnight.EVENT_DEFLECT -> burst(k.core().add(Vec3.directionFromRotation(0f, k.getYRot()).scale(1.4)), 12, SILVER, 0.3);
            case GyreKnight.EVENT_FIRE -> burst(k.core(), 6, RED, 0.12);
            case GyreKnight.EVENT_RIP -> burst(k.recallMark(), 14, RED, 0.2);
            default -> {
            }
        }
    }

    private void burst(Vec3 at, int n, int color, double speed) {
        int count = FxBudget.count(n, at, true);
        for (int i = 0; i < count; i++) {
            FxBudget.spawn(FxParticles.spark(level(), at).velocity(randomUnit().scale(speed * (0.5 + random.nextDouble()))).size(0.08f, 0.01f)
                    .life(10 + random.nextInt(8)).color(color));
        }
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
            if (e instanceof GyreKnight k && k.isAlive() && e.distanceToSqr(cam) < 96 * 96) {
                draw(k, camera, cam, partial, time);
                any = true;
            }
        }
        if (any) {
            BUFFERS.endBatch();
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * A red warning line (camera-relative ends) in pieces about a block long: a dark outline, a soft red halo, a solid core
     * at least 6 px wide; the pieces within 1.3 blocks of the eye are left out, so a line ending at this client's own
     * player never fills the screen.
     */
    static void safeLine(Camera camera, Vec3 a, Vec3 b, float[] red, float strength) {
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) Math.ceil(len));
        List<Vec3[]> pieces = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            Vec3 p0 = a.lerp(b, k / (double) n);
            Vec3 p1 = a.lerp(b, (k + 1) / (double) n);
            if (p0.length() >= 1.3 && p1.length() >= 1.3) {
                pieces.add(new Vec3[] {p0, p1});
            }
        }
        float[] dark = ShardDraw.rgb(RED_OUTLINE);
        for (Vec3[] p : pieces) {
            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, p[0], p[1], TelegraphDraw.screenWidth(p[0], 10.0, 0.09),
                    TelegraphDraw.screenWidth(p[1], 10.0, 0.09), dark, 0.75f * strength);
        }
        for (Vec3[] p : pieces) {
            TelegraphDraw.taper(BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, p[0], p[1], 0.6, 0.6, red, 0.4f * strength);
        }
        for (Vec3[] p : pieces) {
            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, p[0], p[1], TelegraphDraw.screenWidth(p[0], 6.0, 0.06),
                    TelegraphDraw.screenWidth(p[1], 6.0, 0.06), red, 0.95f * strength);
        }
    }

    /**
     * A flare at an aimed blade's tip (camera-relative), facing the eye: a red glow round a white-hot heart and a four
     * pointed star, sized so it keeps about 40 px on screen however far off.
     */
    static void flare(Camera camera, Vec3 tip, float strength, double time) {
        if (tip.length() < 1.5) {
            return;
        }
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 2.2);
        float half = (float) Math.max(0.55, tip.length() * 1.4 / 720.0 * 20.0) * pulse;
        TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, tip, half * 1.8f, ShardDraw.rgb(FLARE), 0.75f * strength);
        TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, tip, half * 0.7f, ShardDraw.rgb(0xFFF1DC), 0.95f * strength);
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z()).scale(half * 2.2);
        Vec3 up = new Vec3(u.x(), u.y(), u.z()).scale(half * 2.2);
        float[] white = ShardDraw.rgb(0xFFF6EE);
        for (Vec3 arm : new Vec3[] {left, up}) {
            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, tip.subtract(arm), tip.add(arm), TelegraphDraw.screenWidth(tip, 3.0, 0.04),
                    TelegraphDraw.screenWidth(tip, 3.0, 0.04), white, 0.9f * strength);
        }
    }

    private static void draw(GyreKnight k, Camera camera, Vec3 cam, float partial, double time) {
        GyreModes.Mode mode = k.mode();
        double t = time - k.modeStart();
        Vec3 core = k.getPosition(partial).add(0, GyreKnight.CORE_Y, 0);
        // the rings: each blade's orbit at its radius, their size telling the mode; thick, outlined dark
        if (mode != GyreModes.Mode.STUNNED) {
            double r = GyreModes.ringRadius(mode, t, k.approachEnd());
            boolean gold = mode == GyreModes.Mode.SWEEP && k.approachEnd() >= 0 && t >= k.approachEnd();
            float[] col = ShardDraw.rgb(gold ? GOLD : RING);
            float[] dark = ShardDraw.rgb(gold ? RED_OUTLINE : OUTLINE);
            float alpha = gold ? 0.95f : 0.8f;
            List<Vec3[]> segs = new ArrayList<>();
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                Vec3 prev = null;
                for (int s = 0; s <= 40; s++) {
                    Vec3 p = core.add(GyreOrbit.offset(i, r, Math.PI * 2 * s / 40)).subtract(cam);
                    if (prev != null) {
                        segs.add(new Vec3[] {prev, p});
                    }
                    prev = p;
                }
            }
            for (Vec3[] sg : segs) {
                TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, sg[0], sg[1], TelegraphDraw.screenWidth(sg[0], 10.0, 0.07),
                        TelegraphDraw.screenWidth(sg[1], 10.0, 0.07), dark, 0.6f);
            }
            for (Vec3[] sg : segs) {
                TelegraphDraw.taper(BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, sg[0], sg[1], 0.4, 0.4, col, 0.35f);
            }
            for (Vec3[] sg : segs) {
                TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, sg[0], sg[1], TelegraphDraw.screenWidth(sg[0], 6.0, 0.045),
                        TelegraphDraw.screenWidth(sg[1], 6.0, 0.045), col, alpha);
            }
        }
        // red aim lines: each blade to its target through its telegraph
        float[] red = ShardDraw.rgb(RED);
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 1.6);
        if (mode == GyreModes.Mode.LANCE) {
            Entity target = k.targetEntity();
            if (target != null) {
                Vec3 chest = target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0);
                for (int i = 0; i < GyreOrbit.BLADES; i++) {
                    int fire = GyreModes.fireTick(i);
                    if (k.blade(i) != GyreKnight.Blade.ORBIT || t < fire - GyreModes.BLADE_TELL || t > fire + 1) {
                        continue;
                    }
                    Vec3 blade = k.renderBlade(i, partial);
                    safeLine(camera, blade.subtract(cam), chest.subtract(cam), red, pulse);
                    flare(camera, blade.add(k.renderBladeDirection(i, partial).scale(GyreOrbit.BLADE_LENGTH / 2.0)).subtract(cam), 1.0f, time);
                }
            }
            // a lance in flight keeps its flare
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                if (k.blade(i) == GyreKnight.Blade.FLY) {
                    Vec3 blade = k.renderBlade(i, partial);
                    flare(camera, blade.add(k.renderBladeDirection(i, partial).scale(GyreOrbit.BLADE_LENGTH / 2.0)).subtract(cam), 0.8f, time);
                }
            }
        }
        // Recall Crash: from each blade back through where the target stood to the core
        if (mode == GyreModes.Mode.RECALL) {
            Vec3 mark = k.recallMark();
            for (int i = 0; i < GyreOrbit.BLADES; i++) {
                GyreKnight.Blade b = k.blade(i);
                if (b != GyreKnight.Blade.OUT && b != GyreKnight.Blade.AIMED) {
                    continue;
                }
                Vec3 a = k.renderBlade(i, partial).subtract(cam);
                Vec3 m = mark.subtract(cam);
                Vec3 c = core.subtract(cam);
                float strength = b == GyreKnight.Blade.AIMED ? 1.0f : 0.5f;
                safeLine(camera, a, m, red, pulse * strength);
                safeLine(camera, m, c, red, pulse * strength);
                if (b == GyreKnight.Blade.AIMED) {
                    flare(camera, k.renderBlade(i, partial).add(k.renderBladeDirection(i, partial).scale(GyreOrbit.BLADE_LENGTH / 2.0)).subtract(cam),
                            0.9f, time);
                }
            }
        }
        // the core's halo, brighter while exposed
        Vec3 at = core.subtract(cam);
        at = at.subtract(at.normalize().scale(0.3));
        boolean exposed = k.coreExposed();
        TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, exposed ? 1.1f : 0.7f,
                ShardDraw.rgb(exposed ? 0xFFFFFF : RING), exposed ? 0.7f : 0.4f);
    }
}
