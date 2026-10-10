package com.cosmicbreach.client.leviathan;

import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.client.guardian.TelegraphDraw;
import com.cosmicbreach.guardian.leviathan.LeviathanEffects;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanPathPayload;
import com.cosmicbreach.guardian.leviathan.LeviathanPaths;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics;
import com.cosmicbreach.guardian.leviathan.Moorage;
import com.cosmicbreach.guardian.leviathan.Polyline;
import com.cosmicbreach.guardian.leviathan.RiftLayout;
import com.cosmicbreach.guardian.leviathan.ShedScale;
import com.cosmicbreach.guardian.leviathan.SongPull;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The Leviathan's effects and telegraphs on the client (Thalassine Leviathan design v1, "Look and sound"; art review G5p):
 * <ul>
 *   <li>The Breach Dive's <b>wake</b>: a hot coral stream (a colour nothing else in the Rift uses) along the dive's path
 *       from its head to 30 ticks ahead of it, outlined dark so it reads against the pale sky, chevrons flowing along it
 *       toward where it lands, a bright point at its front, and a ring on the ground where it lands, standing a short
 *       curtain of light so it reads at eye level.</li>
 *   <li>The Song of Pulling's <b>rings</b>: thick aqua rings, each outlined dark, pulsing out of the mouth along the 60
 *       degree cone, 30 blocks long, inside a faint aqua wash of the cone itself and its eight edges; brighter and faster
 *       once it pulls. The singing core pulses with them.</li>
 *   <li>The Tail Flick's gold glint at tick 16 (the fan's gold is the model's light).</li>
 *   <li>The Moorage: a halo over each gland, and a shudder's ripple, a band of light running along the body from head
 *       to tail.</li>
 *   <li>The shed scales (fish scales of violet nacre, outlined, tumbling as they drift), the Break's and the tearing
 *       free's silver dust, the pearl of light that rises when it dies.</li>
 *   <li>The lair's <b>singing core</b>: its Rimeglass glows, breathing slowly while the Leviathan sleeps or swims and
 *       pulsing with each ring while it sings.</li>
 * </ul>
 */
public final class LeviathanFx implements LeviathanEffects.Handler {
    static final int SILVER = 0xE6EEF6;
    static final int SILVER_EDGE = 0x9FB4C8;
    static final int CYAN = 0x9EF4FF;
    static final int GOLD = 0xFFC845;
    static final int GLAND = 0xFFE9B0;
    static final int PEARL = 0xF4FFFF;
    /** The dive's wake: a hot coral, its chevrons and front a pale gold-white, its outline a deep red-brown. */
    static final int WAKE = 0xFF6A2B;
    static final int WAKE_HOT = 0xFFE3A6;
    static final int WAKE_DARK = 0x3A0B04;
    /** The song: bright aqua rings over a dark outline, the cone washed aqua. */
    static final int SONG = 0x52F4FF;
    static final int SONG_DARK = 0x04202C;
    static final int SONG_WASH = 0x1FC8EA;
    /** The shed scales: violet nacre with a white rib, a violet rim, a dark outline. */
    static final int SCALE_FILL = 0xC48CFF;
    static final int SCALE_RIM = 0x7A33E6;
    static final int SCALE_DARK = 0x1E0838;
    /** The singing core's light. */
    static final int CORE = 0x6FE6FF;

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    /** A dive's path as the client heard it: when the wake starts, how fast it runs, where it lands and how far along the path that is. */
    record DivePath(long start, double speed, Polyline path, Vec3 target, double landing) {
    }

    private static final Map<Integer, DivePath> DIVES = new HashMap<>();
    private static final Map<Integer, Long> PEARLS = new HashMap<>();
    private static final Map<Integer, Long> RIPPLES = new HashMap<>();
    private final RandomSource random = RandomSource.create();

    static void path(LeviathanPathPayload payload) {
        if (payload.points().size() >= 2) {
            Polyline path = new Polyline(payload.points());
            Vec3 through = payload.target().add(0, LeviathanPaths.DIVE_OVER, 0);
            double landing = 0;
            double best = Double.MAX_VALUE;
            for (double s = 0; s <= path.length(); s += 0.5) {
                double d = path.at(s).distanceToSqr(through);
                if (d < best) {
                    best = d;
                    landing = s;
                }
            }
            DIVES.put(payload.entity(), new DivePath(payload.start(), payload.speed(), path, payload.target(), landing));
        }
    }

    static void clear() {
        DIVES.clear();
        PEARLS.clear();
        RIPPLES.clear();
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static boolean ready() {
        return level() != null && FxParticles.ready();
    }

    private Vec3 randomUnit() {
        Vec3 v = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
        return v.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : v.normalize();
    }

    /**
     * The dive's wake at {@code time}: {from, to, alpha}, the whole path while it shows (the start of the tell, fading out
     * by {@link LeviathanMoves#wakeAlpha}); null when no dive shows or the wake has vanished.
     */
    static double[] wake(ThalassineLeviathan l, double time) {
        DivePath d = DIVES.get(l.getId());
        if (d == null || l.action() != LeviathanTactics.Attack.DIVE) {
            return null;
        }
        float alpha = LeviathanMoves.wakeAlpha(time - d.start());
        if (alpha <= 0.0f) {
            return null;
        }
        return new double[] {0.0, d.path().length(), alpha};
    }

    /** How strongly the dive's wake shows now (0 when it is not drawn at all). */
    static float wakeStrength(ThalassineLeviathan l, double time) {
        double[] w = wake(l, time);
        return w == null ? 0.0f : (float) w[2];
    }

    /** The spine {@code s} blocks of body behind the head's middle (render time), from the synced body points. */
    static Vec3 spine(ThalassineLeviathan l, double s, float partial) {
        double[] at = {0.0, LeviathanMoves.FOLLOW[0], LeviathanMoves.FOLLOW[1], LeviathanMoves.FOLLOW[2], LeviathanMoves.FOLLOW[3],
                LeviathanMoves.FOLLOW[4]};
        for (int i = 1; i < at.length; i++) {
            if (s <= at[i]) {
                double f = Mth.clamp((s - at[i - 1]) / (at[i] - at[i - 1]), 0.0, 1.0);
                return l.renderPoint(i - 1, partial).lerp(l.renderPoint(i, partial), f);
            }
        }
        return l.renderPoint(ThalassineLeviathan.FOLLOWERS, partial);
    }

    /** How bright the singing core is now (0 to 1): breathing slowly, swelling while it wakes, pulsing with each ring of a song. */
    static float coreLight(ThalassineLeviathan l, double time) {
        double breathe = 0.5 + 0.5 * Math.sin(time * 0.1);
        return switch (l.state()) {
            case DORMANT -> (float) (0.55 + 0.15 * breathe);
            case INTRO -> (float) (0.45 + 0.45 * Mth.clamp((time - l.stateStart()) / LeviathanMoves.INTRO, 0.0, 1.0) + 0.1 * breathe);
            case DYING -> (float) Math.max(0.15, 0.6 - (time - l.stateStart()) / 300.0);
            default -> {
                double k = 0.55 + 0.15 * breathe;
                if (l.action() == LeviathanTactics.Attack.SONG) {
                    double t = time - l.actionStart();
                    double period = t >= LeviathanMoves.SONG_TELL ? 6.0 : 10.0;
                    double since = t - Math.floor(t / period) * period;
                    k += 0.45 * Math.exp(-since / 2.5);         // a beat as each ring leaves the mouth
                }
                if (l.moored()) {
                    k += 0.15;
                }
                yield (float) Math.min(1.0, k);
            }
        };
    }

    // ------------------------------------------------------------------ per tick

    @Override
    public void leviathanTick(ThalassineLeviathan l) {
        if (!ready()) {
            return;
        }
        long now = level().getGameTime();
        // coral dust drifting off the wake
        double[] w = wake(l, now);
        DivePath d = DIVES.get(l.getId());
        if (w != null && d != null && w[1] > w[0]) {
            for (int k = 0; k < Math.round(9 * w[2]); k++) {
                double s = w[0] + random.nextDouble() * (w[1] - w[0]);
                Vec3 at = d.path().at(s).add(randomUnit().scale(1.2));
                if (FxBudget.count(1, at, false) > 0) {
                    FxBudget.spawn(FxParticles.glint(level(), at).velocity(randomUnit().scale(0.02)).drag(0.95f).size(0.2f, 0.05f)
                            .life(24 + random.nextInt(12)).color(random.nextBoolean() ? WAKE : WAKE_HOT));
                }
            }
        }
        // cyan motes off its flanks while awake
        if (l.state() != ThalassineLeviathan.State.DORMANT && now % 3 == 0) {
            Vec3 p = l.point(1 + random.nextInt(4)).add(randomUnit().scale(2.2));
            if (FxBudget.count(1, p, false) > 0) {
                FxBudget.spawn(FxParticles.glint(level(), p).velocity(randomUnit().scale(0.015).add(0, 0.01, 0)).drag(0.97f)
                        .size(0.12f, 0.02f).life(30).color(CYAN));
            }
        }
        // the glands spill warm light while moored
        if (l.glandsExposed() && now % 2 == 0) {
            for (int i = 0; i < ThalassineLeviathan.GLANDS; i++) {
                Vec3 g = l.glandPoint(i).add(randomUnit().scale(0.5));
                if (FxBudget.count(1, g, false) > 0) {
                    FxBudget.spawn(FxParticles.glint(level(), g).velocity(new Vec3(0, 0.035, 0)).drag(0.96f).size(0.14f, 0.02f).life(20)
                            .color(GLAND));
                }
            }
        }
        // the song's rings leave a mist of aqua in the cone, and the core breathes out motes with it
        if (l.action() == LeviathanTactics.Attack.SONG && now % 2 == 0) {
            SongPull.Cone cone = l.songCone();
            double dist = random.nextDouble() * LeviathanMoves.SONG_RANGE;
            Vec3 at = cone.mouth().add(cone.axis().scale(dist)).add(randomUnit().scale(dist * 0.4));
            if (FxBudget.count(1, at, false) > 0) {
                FxBudget.spawn(FxParticles.glint(level(), at).velocity(cone.axis().scale(-0.12)).drag(0.97f).size(0.15f, 0.03f).life(18)
                        .color(SONG));
            }
        }
        Vec3 home = l.home();
        if (home != null && now % 4 == 0 && l.state() != ThalassineLeviathan.State.DORMANT) {
            Vec3 at = home.add(randomUnit().scale(RiftLayout.CORE_RADIUS + 1.5));
            if (FxBudget.count(1, at, false) > 0) {
                FxBudget.spawn(FxParticles.glint(level(), at).velocity(at.subtract(home).normalize().scale(0.03)).drag(0.97f)
                        .size(0.18f, 0.03f).life(40).color(CORE));
            }
        }
        // the pearl rises
        Long pearl = PEARLS.get(l.getId());
        if (pearl != null && now - pearl < LeviathanMoves.PEARL + 40) {
            Vec3 at = l.point(0).add(0, (now - pearl) * 0.35, 0);
            if (FxBudget.count(2, at, true) > 0) {
                FxBudget.spawn(FxParticles.glint(level(), at.add(randomUnit().scale(0.6))).velocity(new Vec3(0, -0.02, 0)).size(0.25f, 0.02f)
                        .life(30).color(random.nextBoolean() ? PEARL : CYAN));
            }
        }
    }

    @Override
    public void leviathanEvent(ThalassineLeviathan l, byte event) {
        if (!ready()) {
            return;
        }
        long now = level().getGameTime();
        switch (event) {
            case ThalassineLeviathan.EVENT_GLINT -> WorldFx.add(new TelegraphDraw.Glint(() -> l.isRemoved() ? null : l.point(ThalassineLeviathan.FOLLOWERS)
                    .add(l.renderDirection(ThalassineLeviathan.FOLLOWERS, 0f).scale(-3.0)), GOLD, 3.2f, 14, 0.12f));
            case ThalassineLeviathan.EVENT_PARRIED -> {
                Vec3 tail = l.point(ThalassineLeviathan.FOLLOWERS);
                WorldFx.add(new TelegraphDraw.Flash(tail, ShardDraw.GLOW, false, true, 1.0f, 4.5f, GOLD, 1.0f, 12));
                burst(tail, 30, GOLD, 0.25);
            }
            case ThalassineLeviathan.EVENT_DIVE -> burst(l.point(0), 40, WAKE, 0.3);
            case ThalassineLeviathan.EVENT_BREAK -> {
                for (int i = 0; i <= ThalassineLeviathan.FOLLOWERS; i++) {
                    burst(l.point(i), 16, SILVER, 0.18);
                }
            }
            case ThalassineLeviathan.EVENT_COIL -> {
                for (int i = 0; i <= ThalassineLeviathan.FOLLOWERS; i++) {
                    burst(l.point(i), 10, SILVER_EDGE, 0.12);
                }
            }
            case ThalassineLeviathan.EVENT_RIPPLE -> RIPPLES.put(l.getId(), now);
            case ThalassineLeviathan.EVENT_SHUDDER -> {
                for (int i = 1; i < ThalassineLeviathan.FOLLOWERS; i++) {
                    burst(l.point(i).add(0, 2.0, 0), 14, CYAN, 0.2);
                }
            }
            case ThalassineLeviathan.EVENT_TEAR -> {
                for (int i = 0; i <= ThalassineLeviathan.FOLLOWERS; i++) {
                    burst(l.point(i), 40, SILVER, 0.45);
                }
            }
            case ThalassineLeviathan.EVENT_BITE -> {
                Vec3 m = l.mouth();
                WorldFx.add(new TelegraphDraw.Flash(m, ShardDraw.GLOW, false, true, 1.0f, 3.5f, CYAN, 1.0f, 10));
                burst(m, 20, CYAN, 0.3);
            }
            case ThalassineLeviathan.EVENT_SHED -> {
                for (int i = 1; i <= 4; i++) {
                    burst(l.point(i), 10, SCALE_RIM, 0.1);
                }
            }
            case ThalassineLeviathan.EVENT_DEATH -> burst(l.point(0), 50, CYAN, 0.2);
            case ThalassineLeviathan.EVENT_PEARL -> {
                PEARLS.put(l.getId(), now);
                WorldFx.add(new TelegraphDraw.Flash(l.point(0), ShardDraw.GLOW, false, true, 2.0f, 9.0f, PEARL, 1.0f, 30));
            }
            case ThalassineLeviathan.EVENT_GLAND -> {
                Vec3 best = l.glandPoint(0);
                Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                for (int i = 1; i < ThalassineLeviathan.GLANDS; i++) {
                    if (l.glandPoint(i).distanceToSqr(eye) < best.distanceToSqr(eye)) {
                        best = l.glandPoint(i);
                    }
                }
                burst(best, 8, GLAND, 0.15);
            }
            case ThalassineLeviathan.EVENT_AWAKEN -> burst(l.point(0), 30, CYAN, 0.2);
            default -> {
            }
        }
    }

    private void burst(Vec3 at, int n, int color, double speed) {
        int count = FxBudget.count(n, at, true);
        for (int i = 0; i < count; i++) {
            FxBudget.spawn(FxParticles.glint(level(), at.add(randomUnit().scale(0.8))).velocity(randomUnit().scale(speed * (0.4 + random.nextDouble())))
                    .drag(0.9f).size(0.22f, 0.03f).life(18 + random.nextInt(14)).color(color));
        }
    }

    @Override
    public void scaleTick(ShedScale scale) {
        if (ready() && level().getGameTime() % 2 == 0) {
            Vec3 at = scale.position().add(0, 0.45, 0);
            if (FxBudget.count(1, at, false) > 0) {
                FxBudget.spawn(FxParticles.glint(level(), at.add(randomUnit().scale(0.3))).size(0.12f, 0.02f).life(12)
                        .color(random.nextBoolean() ? SCALE_FILL : SCALE_RIM));
            }
        }
    }

    @Override
    public void scalePopped(ShedScale scale) {
        if (ready()) {
            Vec3 at = scale.position().add(0, 0.45, 0);
            int count = FxBudget.count(14, at, true);
            for (int i = 0; i < count; i++) {
                FxBudget.spawn(FxParticles.shard(level(), at).velocity(randomUnit().scale(0.18)).gravity(0.4f).size(0.16f, 0.05f).life(16)
                        .color(i % 2 == 0 ? SCALE_FILL : SCALE_RIM));
            }
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
            if (e instanceof ThalassineLeviathan l && l.isAlive() && e.distanceToSqr(cam) < 200 * 200) {
                draw(l, camera, cam, partial, time);
                any = true;
            } else if (e instanceof ShedScale s && s.isAlive()) {
                drawScale(s, camera, cam, partial, time);
                any = true;
            }
        }
        if (any) {
            BUFFERS.endBatch();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** A line segment of a telegraph, camera-relative. */
    private record Seg(Vec3 a, Vec3 b, float alpha) {
    }

    /**
     * Segments drawn as an outlined line in three passes (a dark outline, a soft halo of light, the bright core), so the
     * line reads both on the pale sky and on the charcoal rock; widths in pixels on a 720-line screen.
     */
    private static void outlined(Camera camera, List<Seg> segs, int dark, int light, double corePx, double outlinePx, double halo, float haloAlpha,
                                 double minWidth) {
        float[] d = ShardDraw.rgb(dark);
        float[] c = ShardDraw.rgb(light);
        for (Seg s : segs) {
            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, s.a(), s.b(), TelegraphDraw.screenWidth(s.a(), outlinePx, minWidth * 1.6),
                    TelegraphDraw.screenWidth(s.b(), outlinePx, minWidth * 1.6), d, 0.8f * s.alpha());
        }
        if (halo > 0) {
            for (Seg s : segs) {
                TelegraphDraw.taper(BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, s.a(), s.b(), halo, halo, c, haloAlpha * s.alpha());
            }
        }
        for (Seg s : segs) {
            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, s.a(), s.b(), TelegraphDraw.screenWidth(s.a(), corePx, minWidth),
                    TelegraphDraw.screenWidth(s.b(), corePx, minWidth), c, s.alpha());
        }
    }

    private static void draw(ThalassineLeviathan l, Camera camera, Vec3 cam, float partial, double time) {
        drawCore(l, camera, cam, time);
        drawWake(l, camera, cam, time);
        drawSong(l, camera, cam, time);
        // halos on the glands while moored
        if (l.glandsExposed()) {
            float breathe = 0.85f + 0.15f * (float) Math.sin(time * 0.25);
            for (int i = 0; i < ThalassineLeviathan.GLANDS; i++) {
                Vec3 at = l.glandPoint(i).subtract(cam);
                at = at.subtract(at.normalize().scale(0.4));
                TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 1.5f * breathe, ShardDraw.rgb(GLAND), 0.55f);
                TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 0.6f, ShardDraw.rgb(0xFFFFFF), 0.7f);
            }
        }
        drawRipple(l, camera, cam, partial, time);
        // the pearl of light
        Long pearl = PEARLS.get(l.getId());
        if (pearl != null) {
            double into = time - pearl;
            if (into >= 0 && into < LeviathanMoves.PEARL + 40) {
                Vec3 at = l.point(0).add(0, into * 0.35, 0).subtract(cam);
                float fade = (float) Math.min(1.0, (LeviathanMoves.PEARL + 40 - into) / 20.0);
                TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 2.4f, ShardDraw.rgb(CYAN), 0.6f * fade);
                TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 1.1f, ShardDraw.rgb(PEARL), 0.95f * fade);
            }
        }
    }

    /**
     * The lair's singing core: a wide glow over its Rimeglass, facing the eye. It stands just in front of the core's rock
     * and crystals (so none of them pokes through it), sized to cover what the core covers seen from the eye, and fades
     * out when the eye is on the core itself.
     */
    private static void drawCore(ThalassineLeviathan l, Camera camera, Vec3 cam, double time) {
        Vec3 home = l.home();
        if (home == null) {
            return;
        }
        double dist = cam.distanceTo(home);
        double front = RiftLayout.CORE_RADIUS + 3.0;   // in front of its rock (its crystal spikes glow on their own)
        float fade = (float) Mth.clamp((dist - front - 3.0) / 6.0, 0.0, 1.0);
        if (fade <= 0f || dist > 150) {
            return;
        }
        float k = coreLight(l, time) * fade;
        double shrink = (dist - front) / dist;
        Vec3 toEye = cam.subtract(home).normalize();
        Vec3 at = home.add(toEye.scale(front)).subtract(cam);
        TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, (float) (RiftLayout.CORE_RADIUS * 2.0 * shrink),
                ShardDraw.rgb(CORE), 0.42f * k);
        TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, (float) (RiftLayout.CORE_RADIUS * 0.95 * shrink),
                ShardDraw.rgb(0xDFFFFF), 0.3f * k);
    }

    /** The Breach Dive's wake, its chevrons and its landing ring. */
    private static void drawWake(ThalassineLeviathan l, Camera camera, Vec3 cam, double time) {
        double[] w = wake(l, time);
        DivePath d = DIVES.get(l.getId());
        if (w == null || d == null) {
            return;
        }
        Polyline path = d.path();
        if (w[1] > w[0] + 0.1) {
            List<Seg> segs = new ArrayList<>();
            double step = 0.75;
            for (double s = w[0]; s < w[1]; s += step) {
                double s1 = Math.min(w[1], s + step);
                Vec3 a = path.at(s).subtract(cam);
                Vec3 b = path.at(s1).subtract(cam);
                if (a.length() < 1.5 || b.length() < 1.5) {
                    continue; // never across the eye of a player standing in it
                }
                segs.add(new Seg(a, b, (float) (0.95 * w[2])));
            }
            outlined(camera, segs, WAKE_DARK, WAKE, 8.0, 14.0, 2.6, 0.45f, 0.3);
            // chevrons flowing along it toward where it lands
            List<Seg> chevrons = new ArrayList<>();
            double flow = (time * 0.5) % 3.0;
            for (double s = w[0] + flow; s < w[1] - 0.6; s += 3.0) {
                Vec3 p = path.at(s);
                Vec3 dir = path.direction(s);
                Vec3 rel = p.subtract(cam);
                if (rel.length() < 2.5) {
                    continue;
                }
                Vec3 side = dir.cross(rel);
                side = side.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : side.normalize();
                Vec3 tip = rel.add(dir.scale(0.6));
                chevrons.add(new Seg(rel.subtract(dir.scale(0.5)).add(side.scale(0.75)), tip, (float) w[2]));
                chevrons.add(new Seg(rel.subtract(dir.scale(0.5)).subtract(side.scale(0.75)), tip, (float) w[2]));
            }
            outlined(camera, chevrons, WAKE_DARK, WAKE_HOT, 5.0, 9.0, 0.0, 0f, 0.12);
        }
        // where it lands: a ring on the ground round the target's feet, vanishing with the wake
        {
            float pulse = (0.75f + 0.25f * (float) Math.sin(time * 1.1)) * (float) w[2];
            Vec3 c = d.target().add(0, 0.06, 0).subtract(cam);
            double r = LeviathanMoves.DIVE_REACH + 0.5;
            float[] dark = ShardDraw.rgb(WAKE_DARK);
            float[] core = ShardDraw.rgb(WAKE);
            float[] hot = ShardDraw.rgb(WAKE_HOT);
            ShardDraw.disc(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, c, r, 40, core[0], core[1], core[2], 0.14f * pulse);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, c, r - 0.42, r + 0.42, 48, dark[0], dark[1], dark[2], 0.75f * (float) w[2]);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, c.add(0, 0.01, 0), r - 0.24, r + 0.24, 48, core[0], core[1], core[2], 0.95f * (float) w[2]);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, c.add(0, 0.02, 0), r - 0.08, r + 0.08, 48, hot[0], hot[1], hot[2], 0.9f * pulse);
            TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), c, r, 0f, 0.0, 360.0, 48, 1.4, core, 0.55f * pulse);
        }
    }

    /** The Song of Pulling: its cone washed aqua, its eight edges, and thick outlined rings pulsing along it. */
    private static void drawSong(ThalassineLeviathan l, Camera camera, Vec3 cam, double time) {
        if (l.action() != LeviathanTactics.Attack.SONG) {
            return;
        }
        double t = time - l.actionStart();
        if (t < 0 || t >= LeviathanMoves.SONG_TELL + LeviathanMoves.SONG_PULL) {
            return;
        }
        boolean pulling = t >= LeviathanMoves.SONG_TELL;
        SongPull.Cone cone = l.songCone();
        Vec3 axis = cone.axis();
        Vec3 side = axis.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(axis).normalize();
        double tan = Math.tan(Math.toRadians(LeviathanMoves.SONG_CONE / 2.0));
        double range = LeviathanMoves.SONG_RANGE;
        Vec3 mouth = cone.mouth().subtract(cam);
        float grow = (float) Math.min(1.0, t / 12.0);
        // the cone's wash: a faint shell from the mouth out to its reach
        float[] wash = ShardDraw.rgb(SONG_WASH);
        int around = 28;
        double[] rings = {1.0, 6.0, 12.0, 18.0, 24.0, range};
        for (int j = 0; j + 1 < rings.length; j++) {
            double d0 = rings[j];
            double d1 = rings[j + 1];
            float a0 = (float) ((pulling ? 0.16 : 0.1) * (1.0 - d0 / range * 0.7)) * grow;
            float a1 = (float) ((pulling ? 0.16 : 0.1) * (1.0 - d1 / range * 0.7)) * grow;
            for (int k = 0; k < around; k++) {
                double q0 = Math.PI * 2 * k / around;
                double q1 = Math.PI * 2 * (k + 1) / around;
                Vec3 p00 = mouth.add(axis.scale(d0)).add(side.scale(Math.cos(q0) * d0 * tan)).add(up.scale(Math.sin(q0) * d0 * tan));
                Vec3 p01 = mouth.add(axis.scale(d0)).add(side.scale(Math.cos(q1) * d0 * tan)).add(up.scale(Math.sin(q1) * d0 * tan));
                Vec3 p10 = mouth.add(axis.scale(d1)).add(side.scale(Math.cos(q0) * d1 * tan)).add(up.scale(Math.sin(q0) * d1 * tan));
                Vec3 p11 = mouth.add(axis.scale(d1)).add(side.scale(Math.cos(q1) * d1 * tan)).add(up.scale(Math.sin(q1) * d1 * tan));
                var out = BUFFERS.getBuffer(ShardDraw.solid());
                ShardDraw.vertex(out, IDENTITY, p00, 0.5f, 0.5f, wash[0], wash[1], wash[2], a0);
                ShardDraw.vertex(out, IDENTITY, p01, 0.5f, 0.5f, wash[0], wash[1], wash[2], a0);
                ShardDraw.vertex(out, IDENTITY, p11, 0.5f, 0.5f, wash[0], wash[1], wash[2], a1);
                ShardDraw.vertex(out, IDENTITY, p10, 0.5f, 0.5f, wash[0], wash[1], wash[2], a1);
            }
        }
        // its eight edges
        List<Seg> edges = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            Vec3 dir = axis.add(side.scale(Math.cos(a) * tan)).add(up.scale(Math.sin(a) * tan));
            Vec3 a0 = mouth.add(dir.scale(1.5));
            Vec3 a1 = mouth.add(dir.scale(range));
            if (a0.length() > 1.3 && a1.length() > 1.3) {
                edges.add(new Seg(a0, a1, 0.7f * grow));
            }
        }
        outlined(camera, edges, SONG_DARK, SONG, 3.0, 6.0, 0.0, 0f, 0.06);
        // the rings, pulsing out along it
        double period = pulling ? 6.0 : 10.0;
        double speed = pulling ? 1.1 : 0.7;
        List<Seg> segs = new ArrayList<>();
        for (double born = Math.floor(t / period) * period; born > t - range / speed; born -= period) {
            double dist = (t - born) * speed + 1.0;
            if (dist > range || born < 0) {
                continue;
            }
            double r = dist * tan;
            float alpha = (float) ((pulling ? 1.0 : 0.85) * (1.0 - dist / range * 0.55));
            Vec3 c = mouth.add(axis.scale(dist));
            int n = 40;
            for (int k = 0; k < n; k++) {
                double a0 = Math.PI * 2 * k / n;
                double a1 = Math.PI * 2 * (k + 1) / n;
                Vec3 p0 = c.add(side.scale(Math.cos(a0) * r)).add(up.scale(Math.sin(a0) * r));
                Vec3 p1 = c.add(side.scale(Math.cos(a1) * r)).add(up.scale(Math.sin(a1) * r));
                if (p0.length() < 1.3 || p1.length() < 1.3) {
                    continue;
                }
                segs.add(new Seg(p0, p1, alpha));
            }
        }
        outlined(camera, segs, SONG_DARK, SONG, 9.0, 15.0, 1.8, 0.4f, 0.16);
    }

    /** A shudder's ripple: a band of light running along the body from head to tail through its 16 ticks. */
    private static void drawRipple(ThalassineLeviathan l, Camera camera, Vec3 cam, float partial, double time) {
        Long ripple = RIPPLES.get(l.getId());
        if (ripple == null || !l.moored()) {
            return;
        }
        double into = time - ripple;
        if (into < 0 || into > Moorage.SHUDDER_TELL + 3) {
            return;
        }
        double f = Math.min(1.0, into / Moorage.SHUDDER_TELL);
        double length = LeviathanMoves.FOLLOW[LeviathanMoves.FOLLOW.length - 1];
        double centre = f * length;
        float fade = (float) Math.min(1.0, (Moorage.SHUDDER_TELL + 3 - into) / 3.0);
        float[] light = ShardDraw.rgb(0xC8FAFF);
        float[] white = ShardDraw.rgb(0xFFFFFF);
        List<Seg> hot = new ArrayList<>();
        for (int k = -6; k <= 6; k++) {
            double s = centre + k * 0.35;
            if (s < 0 || s > length) {
                continue;
            }
            Vec3 p = spine(l, s, partial);
            Vec3 dir = spine(l, Math.max(0, s - 0.5), partial).subtract(spine(l, Math.min(length, s + 0.5), partial));
            dir = dir.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : dir.normalize();
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(dir).normalize();
            // the body's cross-section is a wide box over a tall one: the band follows their outline (an octagon), just outside
            double hw = LeviathanPaths.halfWidth(s) + 0.12;
            double hh = LeviathanPaths.halfHeight(s) + 0.12;
            double[][] hull = {{1, 0.7}, {0.7, 1}, {-0.7, 1}, {-1, 0.7}, {-1, -0.7}, {-0.7, -1}, {0.7, -1}, {1, -0.7}};
            float a = (float) (1.0 - Math.abs(k) / 7.0) * fade;
            Vec3 c = p.subtract(cam);
            for (int j = 0; j < hull.length; j++) {
                double[] h0 = hull[j];
                double[] h1 = hull[(j + 1) % hull.length];
                Vec3 p0 = c.add(side.scale(h0[0] * hw)).add(up.scale(h0[1] * hh));
                Vec3 p1 = c.add(side.scale(h1[0] * hw)).add(up.scale(h1[1] * hh));
                TelegraphDraw.taper(BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, p0, p1, 0.8, 0.8, light, 0.8f * a);
                if (k == 0) {
                    hot.add(new Seg(p0, p1, 0.95f * fade));
                }
            }
        }
        for (Seg s : hot) {
            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, s.a(), s.b(), TelegraphDraw.screenWidth(s.a(), 8.0, 0.16),
                    TelegraphDraw.screenWidth(s.b(), 8.0, 0.16), white, s.alpha());
        }
    }

    /** A shed scale: a fish scale of violet nacre (round-topped, pointed below, a pale rib), outlined, tumbling as it drifts. */
    private static void drawScale(ShedScale s, Camera camera, Vec3 cam, float partial, double time) {
        Vec3 at = s.getPosition(partial).add(0, 0.45, 0).subtract(cam);
        double spin = time * 0.12 + s.getId();
        Vec3 u = new Vec3(Math.cos(spin), 0.45, Math.sin(spin)).normalize();   // the scale's width
        Vec3 v = u.cross(new Vec3(0, 1, 0)).cross(u).normalize();             // its length (round end +v, point -v)
        v = v.scale(Math.signum(v.y) == 0 ? 1 : Math.signum(v.y));
        double size = 0.62;
        List<Vec3> rim = new ArrayList<>();
        for (int k = 0; k <= 8; k++) {
            double a = Math.PI * k / 8;
            rim.add(at.add(u.scale(Math.cos(a) * 0.5 * size)).add(v.scale((0.12 + Math.sin(a) * 0.5) * size)));
        }
        rim.add(at.add(u.scale(-0.36 * size)).add(v.scale(-0.3 * size)));
        rim.add(at.add(v.scale(-0.72 * size)));
        rim.add(at.add(u.scale(0.36 * size)).add(v.scale(-0.3 * size)));
        float[] fill = ShardDraw.rgb(SCALE_FILL);
        float[] edge = ShardDraw.rgb(SCALE_RIM);
        Vec3 heart = at.add(v.scale(0.05 * size));
        for (int k = 0; k < rim.size(); k++) {
            Vec3 p0 = rim.get(k);
            Vec3 p1 = rim.get((k + 1) % rim.size());
            var out = BUFFERS.getBuffer(ShardDraw.solid());
            ShardDraw.vertex(out, IDENTITY, heart, 0.5f, 0.5f, fill[0], fill[1], fill[2], 0.97f);
            ShardDraw.vertex(out, IDENTITY, p0, 0.5f, 0.5f, edge[0], edge[1], edge[2], 0.97f);
            ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, edge[0], edge[1], edge[2], 0.97f);
            ShardDraw.vertex(out, IDENTITY, heart, 0.5f, 0.5f, fill[0], fill[1], fill[2], 0.97f);
        }
        List<Seg> outline = new ArrayList<>();
        for (int k = 0; k < rim.size(); k++) {
            outline.add(new Seg(rim.get(k), rim.get((k + 1) % rim.size()), 1f));
        }
        float[] dark = ShardDraw.rgb(SCALE_DARK);
        for (Seg sg : outline) {
            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, sg.a(), sg.b(), TelegraphDraw.screenWidth(sg.a(), 3.5, 0.035),
                    TelegraphDraw.screenWidth(sg.b(), 3.5, 0.035), dark, 0.9f);
        }
        float[] rib = ShardDraw.rgb(0xFBF2FF);
        TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, at.add(v.scale(0.5 * size)), at.add(v.scale(-0.55 * size)),
                TelegraphDraw.screenWidth(at, 2.5, 0.03), TelegraphDraw.screenWidth(at, 2.5, 0.03), rib, 0.9f);
        if (at.length() > 6.0) {
            TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 0.7f, edge, 0.3f);
        }
    }
}
