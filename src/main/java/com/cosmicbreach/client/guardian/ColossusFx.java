package com.cosmicbreach.client.guardian;

import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.guardian.colossus.ColossusEffects;
import com.cosmicbreach.guardian.colossus.ColossusMoves;
import com.cosmicbreach.guardian.colossus.CrownArena;
import com.cosmicbreach.guardian.colossus.PrismColossus;
import com.cosmicbreach.guardian.colossus.PrismShard;
import com.cosmicbreach.guardian.colossus.RefractionPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The Prism Colossus's telegraphs and effects on the client, in the engine's telegraph language (GDD 4.1): gold is
 * parryable, white is a floor band to leave or dash through, red is a line to step off.
 *
 * <p>Drawn every frame from the Colossus's synced state (so a warning is exactly where the hit will be): a slam's
 * ring filling under its target (gold when it can be parried, pale when not) and the fist's tether of light; the
 * Facet Sweep's white band and the sweep across it; Refraction's red lines, then the prismatic beam (a white-gold
 * core with a rainbow fringe over the red); the white warning ring of a Prism Burst; the open core's glow in a
 * Break; while it is awake, a halo on its core and its eye and motes of light drifting off the core. One-shot
 * effects answer the server's events: the gold glint, slam impacts, the parry's stuck fist, the core hit, the
 * Fracture, the Shatter (a {@link ShatterBurst}, the boss loop ducked under it), the re-forming and the death.
 *
 * <p>Readability on a pale floor under a noon sky (checked in screenshots): the gold slam ring is the only bright gold
 * underfoot and fills strongly; a white ring gets light added over it and a cool blue edge; Refraction's red lines
 * keep about four pixels on screen at any distance (solid, saturated, over a soft red halo) and pulse faster toward
 * the moment they fire; the Facet Sweep's band stands a curtain of light on its edges so it reads at eye level.
 * Client thread.
 */
final class ColossusFx implements ColossusEffects.Handler {
    static final int GOLD = 0xFFC23A;
    static final int PALE_GOLD = 0xFFE7A6;
    static final int WHITE = 0xFFFFFF;
    static final int PALE = 0xE6FBF8;
    static final int RED = 0xFF3526;
    /** Refraction's warning lines: a saturated red that holds on a pale floor. */
    static final int RED_LINE = 0xFF1E14;
    static final int TURQ = 0x48DCCF;
    static final int[] SHARD_COLORS = {0xFF3B3B, 0x3BFF6E, 0x3B7BFF};
    /** The burning beam's body width, and its rainbow fringe (outermost first) and each band's width. */
    static final double BEAM_WIDTH = 0.8;
    static final int[] FRINGE = {0xFF3B3B, 0xFFB13B, 0xF5F03B, 0x4BE36B, 0x3B8BFF, 0x9B5BFF};
    static final double FRINGE_W = 0.07;
    /** The Facet Sweep's band: a cool white that shows on the pale floor, with a blue edge. */
    static final int BAND = 0xE4F4FF;
    static final int BAND_EDGE = 0x5AA9E6;
    /** The Prism Burst's reach: a cool white-cyan fill and a cyan edge (the slam's ring is amber). */
    static final int BURST_FILL = 0xC4F2FF;
    static final int BURST_EDGE = 0x22BFE6;

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    private final RandomSource random = RandomSource.create();

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static boolean ready() {
        return level() != null && FxParticles.ready();
    }

    // ------------------------------------------------------------------ per tick

    @Override
    public void colossusTick(PrismColossus c) {
        if (!ready() || c.arena() == null) {
            return;
        }
        long now = level().getGameTime();
        // light drifts off the chest core while it is awake
        if (awake(c) && now % 2 == 0) {
            Vec3 core = ColossusRenderer.point(c, "chest_core");
            if (core != null && FxBudget.count(1, core, false) > 0) {
                Vec3 ahead = CrownArena.forward(c.yBodyRot);
                Vec3 v = ahead.scale(0.045).add(randomUnit().scale(0.025)).add(0, 0.012, 0);
                int color = random.nextFloat() < 0.5f ? WHITE : random.nextBoolean() ? PALE_GOLD : 0xBFF8F0;
                FxBudget.spawn(FxParticles.glint(level(), core.add(ahead.scale(0.45)).add(randomUnit().scale(0.3))).velocity(v).drag(0.96f)
                        .size(0.11f, 0.02f).life(18 + random.nextInt(10)).color(color));
            }
        }
        // the eye gathers light through a Refraction charge
        if (c.action() == PrismColossus.Action.REFRACTION && now - c.actionStart() < ColossusMoves.REFRACTION_CHARGE) {
            Vec3 eye = point(c, "eye", c.arena().eye(c.getYRot()));
            if (FxBudget.count(1, eye, false) > 0) {
                Vec3 from = eye.add(randomUnit().scale(1.6));
                FxBudget.spawn(FxParticles.glint(level(), from).velocity(eye.subtract(from).scale(0.12)).drag(0.9f)
                        .size(0.14f, 0.04f).life(8).color(random.nextBoolean() ? PALE_GOLD : WHITE));
            }
        }
        // a broken core sparks
        if (c.isBroken() && now % 3 == 0) {
            Vec3 core = point(c, "chest_core", c.arena().core(c.getYRot(), true));
            if (FxBudget.count(1, core, false) > 0) {
                Vec3 v = randomUnit().scale(0.08).add(0, 0.05, 0);
                FxBudget.spawn(FxParticles.spark(level(), core).velocity(v).size(0.05f, 0.01f).life(10).color(PALE_GOLD).gravity(0.3f));
            }
        }
        // flying fists leave streaks
        for (boolean right : new boolean[] {true, false}) {
            PrismColossus.FistMode mode = c.fistMode(right);
            if (mode == PrismColossus.FistMode.SLAM || mode == PrismColossus.FistMode.SWEEP || mode == PrismColossus.FistMode.RETURNING) {
                Vec3 fist = c.fistPosition(right, now);
                if (FxBudget.count(1, fist, false) > 0) {
                    FxBudget.spawn(FxParticles.glint(level(), fist.add(randomUnit().scale(0.5))).size(0.12f, 0.02f).life(6)
                            .color(c.fistGlints(right) ? PALE_GOLD : PALE));
                }
            }
        }
    }

    @Override
    public void colossusEvent(PrismColossus c, byte event) {
        if (!ready() || c.arena() == null) {
            return;
        }
        CrownArena arena = c.arena();
        long now = level().getGameTime();
        switch (event) {
            case PrismColossus.EVENT_GLINT_RIGHT, PrismColossus.EVENT_GLINT_LEFT -> {
                boolean right = event == PrismColossus.EVENT_GLINT_RIGHT;
                Supplier<Vec3> at = () -> c.isRemoved() ? null : c.fistPosition(right, level().getGameTime() + partial());
                WorldFx.add(new TelegraphDraw.Glint(at, GOLD, 1.5f, 10, 0.12f));
                WorldFx.add(new TelegraphDraw.Glint(at, PALE_GOLD, 0.8f, 7, -0.2f));
            }
            case PrismColossus.EVENT_SLAM_RIGHT, PrismColossus.EVENT_SLAM_LEFT -> {
                boolean right = event == PrismColossus.EVENT_SLAM_RIGHT;
                Vec3 ring = c.fistParam(right);
                Vec3 floor = new Vec3(ring.x, arena.floorY(), ring.z);
                WorldFx.add(new TelegraphDraw.Flash(floor.add(0, 0.04, 0), ShardDraw.RING, true, false, 0.6f, (float) ColossusMoves.SLAM_RADIUS * 1.25f,
                        PALE, 0.9f, 10));
                WorldFx.add(new TelegraphDraw.Flash(floor.add(0, 0.6, 0), ShardDraw.GLOW, false, true, 1.0f, 2.6f, PALE, 0.8f, 7));
                shards(floor.add(0, 0.4, 0), 16, 0.3, 0.12f, TURQ, PALE);
                shake(floor, 0.35);
            }
            case PrismColossus.EVENT_PARRIED -> {
                for (boolean right : new boolean[] {true, false}) {
                    if (c.fistMode(right) == PrismColossus.FistMode.REST) {
                        Vec3 fist = c.fistPosition(right, now);
                        WorldFx.add(new TelegraphDraw.Flash(fist, ShardDraw.GLOW, false, true, 0.8f, 2.4f, GOLD, 0.9f, 10));
                        WorldFx.add(new TelegraphDraw.Flash(new Vec3(fist.x, arena.floorY() + 0.05, fist.z), ShardDraw.RING, true, false, 0.5f, 2.2f,
                                GOLD, 0.95f, 14));
                        sparks(fist, 14, GOLD);
                    }
                }
            }
            case PrismColossus.EVENT_SWEEP -> {
                Vec3 fist = c.fistPosition(false, now);
                WorldFx.add(new TelegraphDraw.Flash(fist, ShardDraw.GLOW, false, true, 0.8f, 2.0f, WHITE, 0.8f, 6));
            }
            case PrismColossus.EVENT_BREAK -> {
                Vec3 core = arena.core(c.getYRot(), true);
                WorldFx.add(new TelegraphDraw.Flash(core, ShardDraw.GLOW, false, true, 1.0f, 4.0f, WHITE, 1.0f, 12));
                shards(core, 20, 0.3, 0.14f, TURQ, WHITE);
                shake(core, 0.4);
            }
            case PrismColossus.EVENT_CORE_HIT -> {
                Vec3 core = point(c, "chest_core", arena.core(c.getYRot(), false));
                WorldFx.add(new TelegraphDraw.Flash(core, ShardDraw.GLOW, false, true, 1.2f, 5.0f, PALE_GOLD, 1.0f, 14));
                WorldFx.add(new TelegraphDraw.Glint(() -> core, WHITE, 2.4f, 12, 0.1f));
                shards(core, 24, 0.35, 0.14f, WHITE, PALE_GOLD);
                shake(core, 0.5);
            }
            case PrismColossus.EVENT_FRACTURE -> {
                Vec3 chest = arena.centre().add(0, 4.5, 0);
                for (int i = 0; i < 8; i++) {
                    Vec3 at = chest.add(randomUnit().multiply(1.6, 2.4, 1.6));
                    WorldFx.after(i * 3, () -> WorldFx.add(new TelegraphDraw.Glint(() -> at, WHITE, 0.9f, 10, 0.2f)));
                }
                WorldFx.add(new TelegraphDraw.Flash(chest, ShardDraw.GLOW, false, true, 2.0f, 7.0f, WHITE, 0.9f, 20));
                shake(chest, 0.6);
            }
            case PrismColossus.EVENT_SHATTER -> {
                // the peak of the fight: a flash that reads against the sun, prismatic rings, crystal flying everywhere,
                // and the boss loop pulled down so the shatter sounds alone
                Vec3 chest = arena.centre().add(0, CrownArena.CORE_HEIGHT, 0);
                WorldFx.add(new ShatterBurst(chest, arena.floorY(), random, 72));
                for (int color : SHARD_COLORS) {
                    shards(chest, 12, 0.45, 0.18f, color, WHITE);
                }
                shake(chest, 0.9);
                GuardianMusic.duck(30);
            }
            case PrismColossus.EVENT_REFORM -> {
                Vec3 chest = arena.centre().add(0, CrownArena.CORE_HEIGHT, 0);
                for (int i = 0; i < 16; i++) {
                    Vec3 from = chest.add(randomUnit().scale(5.0));
                    int color = SHARD_COLORS[i % 3];
                    if (FxBudget.count(1, from, true) > 0) {
                        FxBudget.spawn(FxParticles.glint(level(), from).velocity(chest.subtract(from).scale(0.07)).drag(0.95f)
                                .size(0.3f, 0.1f).life(20).color(color));
                    }
                }
                WorldFx.after(22, () -> WorldFx.add(new TelegraphDraw.Flash(chest, ShardDraw.GLOW, false, true, 1.0f, 6.0f, WHITE, 1.0f, 16)));
            }
            case PrismColossus.EVENT_DEATH -> {
                Vec3 base = arena.centre().add(0, 1.2, 0);
                WorldFx.add(new TelegraphDraw.Flash(base.add(0, 3, 0), ShardDraw.GLOW, false, true, 2.0f, 9.0f, PALE_GOLD, 1.0f, 30));
                for (int i = 0; i < 20; i++) {
                    Vec3 at = base.add(randomUnit().multiply(2.0, 0.5, 2.0));
                    WorldFx.after(i * 2, () -> {
                        if (FxBudget.count(1, at, true) > 0) {
                            FxBudget.spawn(FxParticles.glint(level(), at).velocity(new Vec3(0, 0.12, 0)).drag(0.98f).size(0.3f, 0.05f)
                                    .life(40).color(PALE_GOLD));
                        }
                    });
                }
            }
            case PrismColossus.EVENT_AWAKEN -> {
                Vec3 eye = arena.eye(c.getYRot());
                WorldFx.add(new TelegraphDraw.Flash(eye, ShardDraw.GLOW, false, true, 0.5f, 3.0f, WHITE, 1.0f, 30));
            }
            case PrismColossus.EVENT_BURST -> {
                Vec3 floor = arena.centre().add(0, 0.05, 0);
                WorldFx.add(new TelegraphDraw.Flash(floor, ShardDraw.RING, true, false, 1.0f, (float) ColossusMoves.BURST_RADIUS * 1.6f, WHITE, 1.0f, 10));
                WorldFx.add(new TelegraphDraw.Flash(arena.centre().add(0, 3.5, 0), ShardDraw.GLOW, false, true, 2.0f, 6.0f, WHITE, 1.0f, 10));
                shards(arena.centre().add(0, 3.0, 0), 20, 0.4, 0.12f, WHITE, TURQ);
                shake(floor, 0.4);
            }
            default -> {
            }
        }
    }

    @Override
    public void shardTick(PrismShard shard) {
        if (!ready()) {
            return;
        }
        if (shard.phase() == PrismShard.Phase.TELL && shard.tickCount % 2 == 0) {
            Vec3 head = shard.position().add(CrownArena.forward(shard.getYRot()).scale(1.2)).add(0, 0.75, 0);
            WorldFx.add(new TelegraphDraw.Glint(() -> head, GOLD, 0.5f, 5, 0.15f));
        }
        if (shard.phase() == PrismShard.Phase.HOME || shard.phase() == PrismShard.Phase.LAND) {
            Vec3 at = shard.position().add(0, 0.8, 0);
            if (FxBudget.count(1, at, false) > 0) {
                FxBudget.spawn(FxParticles.glint(level(), at).size(0.2f, 0.05f).life(8).color(SHARD_COLORS[Math.floorMod(shard.color(), 3)]));
            }
        }
    }

    @Override
    public void shardDied(PrismShard shard) {
        if (!ready()) {
            return;
        }
        Vec3 at = shard.position().add(0, 0.8, 0);
        int color = SHARD_COLORS[Math.floorMod(shard.color(), 3)];
        shards(at, 18, 0.28, 0.12f, color, WHITE);
        WorldFx.add(new TelegraphDraw.Flash(at, ShardDraw.GLOW, false, true, 0.6f, 1.8f, color, 0.9f, 8));
    }

    // ------------------------------------------------------------------ helpers

    /** Awake and whole: its core and eye burn (not a statue, not shattered, not falling). */
    private static boolean awake(PrismColossus c) {
        PrismColossus.State s = c.state();
        return !c.hidden() && s != PrismColossus.State.DORMANT && s != PrismColossus.State.DYING && s != PrismColossus.State.SHATTERED;
    }

    private static Vec3 point(PrismColossus c, String bone, Vec3 fallback) {
        Vec3 p = ColossusRenderer.point(c, bone);
        return p == null ? fallback : p;
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private void shake(Vec3 at, double trauma) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double d = mc.player.position().distanceTo(at);
            if (d < 16) {
                ClientCombat.shake().addTrauma(trauma * (1.0 - d / 16.0));
            }
        }
    }

    private void shards(Vec3 centre, int wanted, double speed, float size, int color, int alt) {
        int n = FxBudget.count(wanted, centre, true);
        for (int i = 0; i < n; i++) {
            Vec3 out = randomUnit();
            Vec3 v = new Vec3(out.x, Math.abs(out.y) * 0.8 + 0.3, out.z).normalize().scale(speed * (0.5 + random.nextDouble() * 0.7));
            FxBudget.spawn(FxParticles.shard(level(), centre.add(out.scale(0.3))).velocity(v)
                    .color(random.nextFloat() < 0.65f ? color : alt).size(size, size * 0.6f).life(12 + random.nextInt(8))
                    .gravity(1.0f).drag(0.96f).spin(0.3f));
        }
    }

    private void sparks(Vec3 centre, int wanted, int color) {
        int n = FxBudget.count(wanted, centre, false);
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(0.15 + random.nextDouble() * 0.15);
            FxBudget.spawn(FxParticles.spark(level(), centre).velocity(v).size(0.04f, 0.01f).life(6 + random.nextInt(4))
                    .gravity(0.4f).drag(0.85f).color(color).streak(1.6f, 0.08f));
        }
    }

    private Vec3 randomUnit() {
        Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return v.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : v.normalize();
    }

    // ------------------------------------------------------------------ the telegraphs, every frame

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
            if (e instanceof PrismColossus c && c.isAlive() && c.arena() != null && e.distanceToSqr(cam) < 120 * 120) {
                draw(c, camera, cam, time);
                any = true;
            }
        }
        if (any) {
            BUFFERS.endBatch();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** A halo of light on the core and a smaller one on the eye, a little toward the camera so the body does not hide them. */
    private static void halos(PrismColossus c, Camera camera, Vec3 cam, double time) {
        float breathe = 0.85f + 0.15f * (float) Math.sin(time * 0.15);
        Vec3 core = ColossusRenderer.point(c, "chest_core");
        if (core != null) {
            Vec3 at = core.subtract(cam);
            at = at.subtract(at.normalize().scale(0.6));
            TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 1.2f * breathe, ShardDraw.rgb(PALE_GOLD), 0.45f);
            TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 0.5f, ShardDraw.rgb(WHITE), 0.6f);
        }
        Vec3 eye = ColossusRenderer.point(c, "eye");
        if (eye != null) {
            Vec3 at = eye.subtract(cam);
            at = at.subtract(at.normalize().scale(0.5));
            TelegraphDraw.glow(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), camera, at, 0.55f * breathe, ShardDraw.rgb(0xBFF8F0), 0.5f);
        }
    }

    private static void draw(PrismColossus c, Camera camera, Vec3 cam, double time) {
        CrownArena arena = c.arena();
        if (awake(c)) {
            halos(c, camera, cam, time);
        }
        if (c.state() != PrismColossus.State.FIGHT) {
            return;
        }
        // slams: the ring fills under its target, gold when it can be parried
        for (boolean right : new boolean[] {true, false}) {
            PrismColossus.FistMode mode = c.fistMode(right);
            if (mode == PrismColossus.FistMode.SLAM) {
                double t = time - c.fistStart(right);
                Vec3 ring = c.fistParam(right);
                Vec3 at = new Vec3(ring.x, arena.floorY() + 0.03, ring.z).subtract(cam);
                boolean gold = c.fistGlints(right);
                double fill = Math.max(0.0, Math.min(1.0, t / ColossusMoves.SLAM_TELL));
                double r = ColossusMoves.SLAM_RADIUS;
                float pulse = t >= ColossusMoves.SLAM_GLINT ? 0.5f + 0.5f * (float) Math.sin(t * 2.2) : 0f;
                if (gold) {
                    // the only bright gold underfoot (the floor's inlay is muted brass): a strong fill, pulsing once it glints
                    float[] col = ShardDraw.rgb(GOLD);
                    ShardDraw.disc(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, at, r * fill, 40, col[0], col[1], col[2],
                            0.4f + 0.3f * (float) fill + 0.15f * pulse);
                    ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, at.add(0, 0.005, 0), r - 0.24, r, 48, col[0], col[1], col[2], 1.0f);
                    if (fill > 0.05) {
                        ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, at.add(0, 0.008, 0), r * fill - 0.14, r * fill, 40, 1f, 0.93f, 0.7f,
                                0.95f);
                    }
                } else {
                    // white on a pale floor: a white fill with light added over it, and a cool blue edge outside so it shows
                    float[] edge = ShardDraw.rgb(BAND_EDGE);
                    ShardDraw.disc(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, at, r * fill, 40, 1f, 1f, 1f, 0.5f + 0.25f * (float) fill);
                    ShardDraw.disc(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), IDENTITY, at.add(0, 0.004, 0), r * fill, 40, 1f, 1f, 1f,
                            0.35f + 0.25f * (float) fill);
                    ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, at.add(0, 0.006, 0), r - 0.24, r, 48, 1f, 1f, 1f, 1.0f);
                    ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, at.add(0, 0.007, 0), r, r + 0.14, 48, edge[0], edge[1], edge[2], 0.95f);
                    if (fill > 0.05) {
                        ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, at.add(0, 0.008, 0), r * fill - 0.14, r * fill, 40, edge[0], edge[1],
                                edge[2], 0.8f);
                    }
                }
            }
            if (mode != PrismColossus.FistMode.ATTACHED) {
                Vec3 wrist = ColossusRenderer.point(c, right ? "wrist_ring_right" : "wrist_ring_left");
                if (wrist == null) {
                    wrist = ColossusMoves.wrist(arena.centre(), c.getYRot(), right);
                }
                Vec3 fist = c.fistPosition(right, time);
                boolean gold = c.fistGlints(right);
                TelegraphDraw.ribbon(BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, wrist.subtract(cam), fist.subtract(cam), 0.28,
                        ShardDraw.rgb(gold ? PALE_GOLD : PALE), 0.55f);
                TelegraphDraw.ribbon(BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, wrist.subtract(cam), fist.subtract(cam), 0.09,
                        ShardDraw.rgb(WHITE), 0.9f);
            }
        }
        // the Facet Sweep: a white band 3 to 9 out across 200 degrees, then the sweep crossing it
        if (c.action() == PrismColossus.Action.SWEEP) {
            double t = time - c.actionStart();
            float yaw = c.sweepYaw();
            Vec3 centre = new Vec3(arena.x(), arena.floorY() + 0.035, arena.z()).subtract(cam);
            double half = ColossusMoves.SWEEP_ARC / 2.0;
            if (t < ColossusMoves.SWEEP_TELL + ColossusMoves.SWEEP_SWING) {
                // a pale band on a pale floor: a cool blue-white fill that grows through the tell, bright double edges,
                // and bars of light running across it
                float glow = (float) Math.min(1.0, t / ColossusMoves.SWEEP_TELL);
                float flicker = 0.05f * (float) Math.sin(t * 1.3);
                float[] fill = ShardDraw.rgb(BAND);
                TelegraphDraw.sector(BUFFERS.getBuffer(ShardDraw.solid()), centre, ColossusMoves.SWEEP_INNER, ColossusMoves.SWEEP_OUTER, yaw, -half, half, 50, fill,
                        0.4f + 0.3f * glow + flicker);
                for (int k = 0; k < 6; k++) {
                    double a0 = -half + ((t * 6.0 + k * 34.0) % ColossusMoves.SWEEP_ARC);
                    TelegraphDraw.sector(BUFFERS.getBuffer(ShardDraw.solid()), centre.add(0, 0.003, 0), ColossusMoves.SWEEP_INNER, ColossusMoves.SWEEP_OUTER,
                            yaw, a0, Math.min(half, a0 + 3.0), 2, ShardDraw.rgb(WHITE), 0.45f + 0.4f * glow);
                }
                for (double edge : new double[] {ColossusMoves.SWEEP_INNER, ColossusMoves.SWEEP_OUTER - 0.22}) {
                    TelegraphDraw.sector(BUFFERS.getBuffer(ShardDraw.solid()), centre.add(0, 0.004, 0), edge, edge + 0.22, yaw, -half, half, 50,
                            ShardDraw.rgb(WHITE), 1.0f);
                    TelegraphDraw.sector(BUFFERS.getBuffer(ShardDraw.solid()), centre.add(0, 0.005, 0), edge + 0.22, edge + 0.3, yaw, -half, half, 50,
                            ShardDraw.rgb(BAND_EDGE), 0.9f);
                }
                // at eye level the band is a sliver: a curtain of light stands on each edge, seen face on
                for (double edge : new double[] {ColossusMoves.SWEEP_INNER, ColossusMoves.SWEEP_OUTER}) {
                    double tall = edge == ColossusMoves.SWEEP_OUTER ? 0.8 + 0.5 * glow : 0.5 + 0.3 * glow;
                    TelegraphDraw.curtain(BUFFERS.getBuffer(ShardDraw.solid()), centre, edge, yaw, -half, half, 50, tall, ShardDraw.rgb(BAND_EDGE),
                            0.3f + 0.4f * glow);
                    TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), centre, edge, yaw, -half, half, 50, tall * 0.7,
                            ShardDraw.rgb(WHITE), 0.3f + 0.3f * glow);
                }
            }
            if (t >= ColossusMoves.SWEEP_TELL && t < ColossusMoves.SWEEP_TELL + ColossusMoves.SWEEP_SWING + 4) {
                double now = ColossusMoves.sweepAngle(t);
                float fade = (float) Math.max(0.0, 1.0 - (t - ColossusMoves.SWEEP_TELL - ColossusMoves.SWEEP_SWING) / 4.0);
                TelegraphDraw.sector(BUFFERS.getBuffer(ShardDraw.solid()), centre.add(0, 0.01, 0), ColossusMoves.SWEEP_INNER,
                        ColossusMoves.SWEEP_OUTER, yaw, Math.max(-half, now - 50.0), now, 24, ShardDraw.rgb(WHITE), 0.95f * fade);
            }
        }
        // Refraction: red lines while it charges, then the beam
        RefractionView.Beams beams = RefractionView.of(c.getId());
        if (beams != null) {
            boolean firing = beams.mode() == RefractionPayload.FIRING;
            double t = time - beams.start();
            for (int i = 0; i < beams.paths().size(); i++) {
                List<Vec3> pts = beams.paths().get(i);
                for (int j = 0; j + 1 < pts.size(); j++) {
                    Vec3 a = pts.get(j).subtract(cam);
                    Vec3 b = pts.get(j + 1).subtract(cam);
                    if (!firing) {
                        // a solid saturated line about four pixels thick at any distance over a soft red halo, pulsing
                        // faster toward the moment it fires (one pulse every 12 ticks at first, every 3 at the end), a
                        // white-hot thread in it at the last
                        double charge = ColossusMoves.REFRACTION_CHARGE;
                        double u = Math.max(0.0, Math.min(1.0, t / charge));
                        double phase = 2.0 * Math.PI * (t / 12.0 + (1.0 / 3.0 - 1.0 / 12.0) * t * t / (2.0 * charge));
                        float p = 0.5f + 0.5f * (float) Math.cos(phase);
                        double wa = TelegraphDraw.screenWidth(a, 4.0, 0.1);
                        double wb = TelegraphDraw.screenWidth(b, 4.0, 0.1);
                        float[] red = ShardDraw.rgb(RED_LINE);
                        double halo = 3.0 + 1.5 * u;
                        TelegraphDraw.taper(BUFFERS.getBuffer(ShardDraw.translucent(FxRenderTypes.BEAM, true)), camera, a, b, wa * halo, wb * halo, red,
                                0.2f + 0.4f * p * (float) (0.5 + 0.5 * u));
                        double core = 1.0 + 0.35 * p * u;
                        TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, a, b, wa * core, wb * core, red, 0.92f + 0.08f * p);
                        if (u > 0.75) {
                            float hot = (float) ((u - 0.75) / 0.25) * p;
                            TelegraphDraw.line(BUFFERS.getBuffer(ShardDraw.solid()), camera, a, b, wa * 0.4, wb * 0.4, ShardDraw.rgb(0xFFE6D8), hot);
                        }
                    } else {
                        // the red telegraph stays under it; a pale gold body; a rainbow fringe split out to each side
                        // like light through a prism (red outermost); a white-hot core
                        float flick = 0.88f + 0.12f * (float) Math.sin(t * 3.1 + j);
                        double w = BEAM_WIDTH * (0.94 + 0.06 * Math.sin(t * 2.3 + j * 1.7));
                        VertexConsumer solid = BUFFERS.getBuffer(ShardDraw.solid());
                        TelegraphDraw.ribbon(solid, camera, a, b, w * 1.25, 0.0, ShardDraw.rgb(RED), 0.55f);
                        TelegraphDraw.ribbon(solid, camera, a, b, w, 0.0, ShardDraw.rgb(PALE_GOLD), 0.8f * flick);
                        for (int side = -1; side <= 1; side += 2) {
                            for (int f = 0; f < FRINGE.length; f++) {
                                double off = side * (w * 0.5 + FRINGE_W * (FRINGE.length - f - 0.5));
                                TelegraphDraw.ribbon(solid, camera, a, b, FRINGE_W, off, ShardDraw.rgb(FRINGE[f]), 0.85f * flick);
                            }
                        }
                        TelegraphDraw.ribbon(solid, camera, a, b, w * 0.45, 0.0, ShardDraw.rgb(WHITE), 1.0f);
                        TelegraphDraw.ribbon(BUFFERS.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM)), camera, a, b, w * 1.6, 0.0,
                                ShardDraw.rgb(PALE_GOLD), 0.6f * flick);
                    }
                }
                for (int j = 1; j < pts.size(); j++) {
                    Vec3 node = pts.get(j).subtract(cam);
                    TelegraphDraw.glow(BUFFERS, camera, node, firing ? 1.2f : 0.6f, firing ? PALE_GOLD : RED_LINE, 0.8f);
                }
            }
            Vec3 eye = ColossusRenderer.point(c, "eye");
            if (eye != null) {
                float grow = firing ? 1.0f : (float) Math.min(1.0, t / ColossusMoves.REFRACTION_CHARGE);
                TelegraphDraw.glow(BUFFERS, camera, eye.subtract(cam), 0.4f + 1.2f * grow, firing ? PALE_GOLD : GOLD, 0.45f + 0.45f * grow);
            }
        }
        // Prism Burst's warning: its body glows white, and a white ring round it marks the reach
        if (c.glowing()) {
            double t = time - c.actionStart();
            float grow = (float) Math.min(1.0, t / ColossusMoves.BURST_TELL);
            Vec3 chest = new Vec3(arena.x(), arena.floorY() + 3.6, arena.z()).subtract(cam);
            TelegraphDraw.glow(BUFFERS, camera, chest, 2.0f + 2.5f * grow, WHITE, 0.35f + 0.4f * grow);
            // the reach, as heavy as the slam's ring but cool (white-cyan, not amber): a tinted fill with light added
            // over it, a thick white rim with a cyan edge, and a curtain of light on the rim that reads at eye level
            Vec3 centre = new Vec3(arena.x(), arena.floorY() + 0.04, arena.z()).subtract(cam);
            double r = ColossusMoves.BURST_RADIUS;
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * (1.2 + 1.2 * grow));
            float[] fill = ShardDraw.rgb(BURST_FILL);
            float[] edge = ShardDraw.rgb(BURST_EDGE);
            ShardDraw.disc(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre, r, 48, fill[0], fill[1], fill[2], 0.45f + 0.2f * grow + 0.1f * pulse);
            ShardDraw.disc(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), IDENTITY, centre.add(0, 0.003, 0), r, 48, 1f, 1f, 1f,
                    0.25f + 0.15f * pulse);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre.add(0, 0.005, 0), r - 0.45, r, 56, 1f, 1f, 1f, 1.0f);
            ShardDraw.band(BUFFERS.getBuffer(ShardDraw.solid()), IDENTITY, centre.add(0, 0.006, 0), r, r + 0.2, 56, edge[0], edge[1], edge[2], 0.95f);
            double tall = 1.0 + 0.6 * grow;
            TelegraphDraw.curtain(BUFFERS.getBuffer(ShardDraw.solid()), centre, r, 0f, -180.0, 180.0, 56, tall, edge, 0.35f + 0.35f * grow);
            TelegraphDraw.curtain(BUFFERS.getBuffer(FxRenderTypes.additive(ShardDraw.GLOW)), centre, r, 0f, -180.0, 180.0, 56, tall * 0.7,
                    ShardDraw.rgb(WHITE), 0.3f + 0.3f * grow);
        }
        // a Break: the open core glows gold, low and in front
        if (c.isBroken()) {
            Vec3 core = ColossusRenderer.point(c, "chest_core");
            if (core == null) {
                core = arena.core(c.getYRot(), true);
            }
            float pulse = 0.75f + 0.25f * (float) Math.sin(time * 0.5);
            TelegraphDraw.glow(BUFFERS, camera, core.subtract(cam), 1.5f * pulse, GOLD, 0.8f);
        }
    }
}
