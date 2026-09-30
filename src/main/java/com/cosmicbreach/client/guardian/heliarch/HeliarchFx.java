package com.cosmicbreach.client.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.entity.ShardDraw;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.client.guardian.TelegraphDraw;
import com.cosmicbreach.guardian.heliarch.CollapseSchedule;
import com.cosmicbreach.guardian.heliarch.CoronaSweep;
import com.cosmicbreach.guardian.heliarch.EclipseCover;
import com.cosmicbreach.guardian.heliarch.HaloShed;
import com.cosmicbreach.guardian.heliarch.HeliarchArena;
import com.cosmicbreach.guardian.heliarch.HeliarchEffects;
import com.cosmicbreach.guardian.heliarch.HeliarchMoves;
import com.cosmicbreach.guardian.heliarch.HeliarchPose;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.Action;
import com.cosmicbreach.guardian.heliarch.HollowHeliarch.State;
import com.cosmicbreach.guardian.heliarch.StarSeed;
import com.cosmicbreach.structure.sanctum.SanctumArena;
import com.cosmicbreach.structure.sanctum.SanctumLayout;
import com.cosmicbreach.world.VesperClock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Heliarch's telegraphs and effects on the client, in the engine's telegraph language (GDD 4.1): gold is parryable
 * (Sunderfall's ring, a lash's glint), red is a path to leave (the lance tracking, the plates' return), white is locked
 * and about to fire, magenta is the void (the lash lines on the floor), violet-black is the eclipse. Drawn every frame
 * from the Heliarch's synced state with {@link HeliarchPose} and the pure rules, so each warning is exactly where its
 * hit will be. One-shot effects answer the server's events. Client thread.
 */
final class HeliarchFx implements HeliarchEffects.Handler {
    static final int GOLD = 0xFFC23A;
    static final int PALE_GOLD = 0xFFE7A6;
    static final int WHITE = 0xFFFFFF;
    static final int RED = 0xFF2E24;
    static final int EMBER = 0xFF8A2A;
    static final int MAGENTA = 0xFF3FD2;
    static final int VIOLET = 0x9A4CFF;
    static final int VOID = 0x0B0714;

    static final ResourceLocation GLOW = ShardDraw.GLOW;
    static final ResourceLocation RING = ShardDraw.RING;
    static final ResourceLocation STAR = ShardDraw.STAR;
    static final ResourceLocation BEAM = FxRenderTypes.BEAM;
    static final ResourceLocation CRACK = FxRenderTypes.CRACK;
    static final ResourceLocation CORONA = CosmicBreach.id("textures/fx/heliarch_corona.png");
    static final ResourceLocation RIFT = CosmicBreach.id("textures/fx/heliarch_rift.png");
    /**
     * Each tendril its own (four alike read as a trident from above): how far it turns round the eclipse from its
     * crack's side, how far out and down on the eclipse it meets it, how far it bows out, where its knee is, how thick.
     */
    private static final double[] TENDRIL_TURN = {24.0, -30.0, 10.0, -16.0};
    private static final double[] TENDRIL_REACH = {0.72, 0.42, 0.60, 0.82};
    private static final double[] TENDRIL_DROP = {0.30, 0.74, 0.52, 0.16};
    private static final double[] TENDRIL_BOW = {4.2, 1.2, 2.6, 5.0};
    private static final double[] TENDRIL_KNEE = {0.55, 0.35, 0.62, 0.45};
    private static final double[] TENDRIL_WIDTH = {3.3, 2.3, 3.0, 2.6};

    /** The eclipse's radius at the sun's size (high in phase 2 it is {@link HeliarchArena#ECLIPSE_BIG} times this). */
    static final double ECLIPSE_R = 2.2;
    static final ResourceLocation RIFT_RIM = CosmicBreach.id("textures/fx/heliarch_rift_rim.png");
    static final ResourceLocation SUN = CosmicBreach.id("textures/fx/heliarch_sun.png");

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 19));
    private static final RandomSource RANDOM = RandomSource.create();
    private static WeakReference<HollowHeliarch> current = new WeakReference<>(null);
    private static final List<Rain> RAIN = new ArrayList<>();
    private static final List<Falling> FALLS = new ArrayList<>();
    private static int frames;
    /** Frames each kind of telegraph was drawn (for checks). */
    static final java.util.Map<String, Integer> DRAWN = new java.util.TreeMap<>();

    private record Rain(List<Vec3> circles, long land) {
    }

    /** A segment of the floor falling away: its blocks as they were, dropping and tumbling into the Breach. */
    private record Falling(List<BlockPos> blocks, List<BlockState> states, Vec3 middle, long start, boolean restore) {
    }

    static @Nullable HollowHeliarch heliarch() {
        HollowHeliarch h = current.get();
        return h == null || h.isRemoved() ? null : h;
    }

    static void clear() {
        current = new WeakReference<>(null);
        RAIN.clear();
        FALLS.clear();
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static void mark(String what) {
        DRAWN.merge(what, 1, Integer::sum);
    }

    /** Frames the Heliarch's effects were drawn (for checks). */
    static int frames() {
        return frames;
    }

    // ------------------------------------------------------------------ messages

    static void rain(List<Vec3> circles, long land) {
        RAIN.add(new Rain(circles, land));
    }

    static void fall(int ring, int segment, boolean restore) {
        ClientLevel level = level();
        if (level == null || ring < 0 || ring >= SanctumArena.Ring.values().length) {
            return;
        }
        SanctumArena.Ring r = SanctumArena.Ring.values()[ring];
        List<BlockPos> blocks = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<BlockPos> all = new ArrayList<>(SanctumArena.segmentBlocks(r, segment));
        if (r == SanctumArena.Ring.OUTER) {
            all.addAll(SanctumArena.pillarBlocks(Math.floorMod(segment, SanctumArena.PILLARS)));
        }
        for (BlockPos p : all) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() || restore) {
                blocks.add(p);
                states.add(s);
            }
        }
        double mid = (r.inner + r.outer) / 2.0;
        Vec3 middle = HeliarchArena.at(22.5 + 45.0 * segment, mid);
        if (!restore) {
            FALLS.add(new Falling(blocks, states, middle, level.getGameTime(), false));
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.position().distanceTo(middle) < 20) {
                ClientCombat.feelSlam(0.5, 1.0);
            }
        } else {
            for (int i = 0; i < 24; i++) {
                Vec3 at = middle.add(RANDOM.nextGaussian() * 2.5, 0.2, RANDOM.nextGaussian() * 2.5);
                FxBudget.spawn(FxParticles.glint(level, at).velocity(new Vec3(0, 0.12, 0)).color(PALE_GOLD).size(0.3f, 0.05f).life(30));
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick(HollowHeliarch h) {
        current = new WeakReference<>(h);
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        long now = level.getGameTime();
        State st = h.state();
        Vec3 core = h.core(now);
        switch (st) {
            case INTRO, REGENT -> {
                if (st == State.INTRO && now - h.stateStart() < 40) {
                    break;
                }
                // embers lifting off the sun
                for (int i = 0; i < 2; i++) {
                    Vec3 at = core.add(RANDOM.nextGaussian() * 0.9, RANDOM.nextGaussian() * 0.9, RANDOM.nextGaussian() * 0.9);
                    FxBudget.spawn(FxParticles.spark(level, at).velocity(new Vec3(RANDOM.nextGaussian() * 0.02, 0.05 + RANDOM.nextDouble() * 0.05,
                            RANDOM.nextGaussian() * 0.02)).color(RANDOM.nextBoolean() ? PALE_GOLD : EMBER).size(0.14f, 0.02f).life(22 + RANDOM.nextInt(10))
                            .drag(0.97f).gravity(-0.02f));
                }
                if (h.brokenNow(now)) {
                    Vec3 at = core.add(RANDOM.nextGaussian() * 0.8, 0.3, RANDOM.nextGaussian() * 0.8);
                    FxBudget.spawn(FxParticles.spark(level, at).velocity(new Vec3(0, -0.02, 0)).color(GOLD).size(0.12f, 0.02f).life(16).gravity(0.06f));
                }
                if (st == State.INTRO && now - h.stateStart() < HeliarchPose.INTRO_ASSEMBLED) {
                    HeliarchPose.Input in = h.poseInput();
                    for (int k = 0; k < 6; k++) {
                        Vec3 p = HeliarchPose.plate(in, h.facing(), k, now);
                        if (p != null && RANDOM.nextInt(3) == 0) {
                            FxBudget.spawn(FxParticles.spark(level, p.add(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.5, RANDOM.nextGaussian()))
                                    .velocity(new Vec3(0, 0.04, 0)).color(EMBER).size(0.1f, 0.02f).life(18));
                        }
                    }
                }
            }
            case HOLLOWING -> {
                long s = now - h.stateStart();
                if (s > 50 && s < 95) {
                    // the hands come apart into void, and the tendrils rise out of the dais
                    for (int i = 0; i < 4; i++) {
                        Vec3 a = HeliarchArena.tendrilAnchor(i).add(RANDOM.nextGaussian() * 0.4, 0.2, RANDOM.nextGaussian() * 0.4);
                        FxBudget.spawn(FxParticles.spark(level, a).velocity(new Vec3(0, 0.12, 0)).color(VIOLET).size(0.2f, 0.05f).life(20));
                    }
                }
            }
            case HOLLOW, COLLAPSE -> {
                // the eclipse's rim sheds embers; void smoke from the tendrils' cracks
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double er = eclipseRadius(h, now);
                Vec3 rim = core.add(Math.cos(a) * er, Math.sin(a) * er, 0);
                FxBudget.spawn(FxParticles.spark(level, rim).velocity(new Vec3(Math.cos(a) * 0.04, Math.sin(a) * 0.04 + 0.02, 0)).color(EMBER)
                        .size(0.12f, 0.02f).life(18));
                for (int i = 0; i < 4; i++) {
                    if (h.tendrilSilent(i) || RANDOM.nextInt(3) != 0) {
                        continue;
                    }
                    Vec3 c = HeliarchArena.tendrilAnchor(i).add(RANDOM.nextGaussian() * 0.5, 0.1, RANDOM.nextGaussian() * 0.5);
                    FxBudget.spawn(FxParticles.spark(level, c).velocity(new Vec3(0, 0.05, 0)).color(MAGENTA).size(0.14f, 0.03f).life(20));
                }
                if (h.action() == Action.NOVA) {
                    // light drawn in from the arena toward the swelling core
                    for (int i = 0; i < 3; i++) {
                        Vec3 from = core.add(HeliarchArena.dir(RANDOM.nextDouble() * 360).scale(10 + RANDOM.nextDouble() * 6))
                                .add(0, RANDOM.nextGaussian() * 2, 0);
                        Vec3 v = core.subtract(from).scale(1.0 / 18.0);
                        FxBudget.spawn(FxParticles.spark(level, from).velocity(v).color(PALE_GOLD).size(0.16f, 0.04f).life(18).streak(1.5f, 0.2f));
                    }
                }
                if (h.inverted(now)) {
                    Vec3 at = HeliarchArena.CENTRE.add(RANDOM.nextGaussian() * 12, 0.3, RANDOM.nextGaussian() * 12);
                    FxBudget.spawn(FxParticles.glint(level, at).velocity(new Vec3(0, 0.08, 0)).color(VIOLET).size(0.2f, 0.05f).life(40));
                }
                if (st == State.COLLAPSE) {
                    shakeDust(h, level, now);
                }
            }
            default -> {
            }
        }
    }

    private static void shakeDust(HollowHeliarch h, ClientLevel level, long now) {
        CollapseSchedule c = new CollapseSchedule(h.side());
        long t = now - h.collapseStart();
        for (SanctumArena.Ring ring : new SanctumArena.Ring[] {SanctumArena.Ring.RIM, SanctumArena.Ring.OUTER, SanctumArena.Ring.MID}) {
            for (int s = 0; s < 8; s++) {
                if (c.state(ring, s, t) != CollapseSchedule.State.SHAKING) {
                    continue;
                }
                Vec3 m = HeliarchArena.at(22.5 + 45.0 * s + RANDOM.nextGaussian() * 12.0, (ring.inner + ring.outer) / 2.0 + RANDOM.nextGaussian());
                double u = (t - c.crackAt(ring, s)) / (double) HeliarchMoves.SHAKE_TICKS;
                FxBudget.spawn(FxParticles.spark(level, m.add(0, -1.2, 0)).velocity(new Vec3(0, -0.1, 0)).color(0xB8A98A).size(0.15f, 0.08f)
                        .life(30).gravity(0.04f));
                FxBudget.spawn(FxParticles.spark(level, m.add(0, 0.05, 0)).velocity(new Vec3(0, 0.03, 0)).color(EMBER).size(0.1f, 0.02f).life(14));
                // dust rising off it, thicker as the fall comes
                for (int k = 0; k < 1 + (int) (3 * u); k++) {
                    Vec3 d = HeliarchArena.at(22.5 + 45.0 * s + RANDOM.nextGaussian() * 13.0, ring.inner + RANDOM.nextDouble() * (ring.outer - ring.inner));
                    FxBudget.spawn(FxParticles.spark(level, d.add(0, 0.15, 0)).velocity(new Vec3(RANDOM.nextGaussian() * 0.01,
                            0.04 + 0.05 * RANDOM.nextDouble(), RANDOM.nextGaussian() * 0.01)).color(0xE6C9A0).size(0.22f, 0.06f).life(34));
                }
                Vec3 me = Minecraft.getInstance().player == null ? Vec3.ZERO : Minecraft.getInstance().player.position();
                if (SanctumLayout.ring((int) Math.floor(me.x), (int) Math.floor(me.z)) == ring.ordinal()
                        && SanctumLayout.segment((int) Math.floor(me.x), (int) Math.floor(me.z)) == s && now % 6 == 0) {
                    ClientCombat.feelSlam(0.12, 0.3);
                }
            }
        }
    }

    // ------------------------------------------------------------------ one-shot events

    @Override
    public void event(HollowHeliarch h, byte id) {
        ClientLevel level = level();
        if (level == null) {
            return;
        }
        current = new WeakReference<>(h);
        long now = level.getGameTime();
        Vec3 core = h.core(now);
        int e = id & 0xFF;
        if (e >= (HollowHeliarch.EVENT_LASH_PARRIED & 0xFF) && e < (HollowHeliarch.EVENT_LASH_PARRIED & 0xFF) + 4) {
            Vec3 end = h.lashEnd(e - (HollowHeliarch.EVENT_LASH_PARRIED & 0xFF));
            WorldFx.add(new TelegraphDraw.Flash(end.add(0, 1.0, 0), SUN, false, true, 1.0f, 4.5f, GOLD, 1.0f, 16));
            return;
        }
        if (e >= (HollowHeliarch.EVENT_CUT & 0xFF) && e < (HollowHeliarch.EVENT_CUT & 0xFF) + 4) {
            Vec3 a = HeliarchArena.tendrilAnchor(e - (HollowHeliarch.EVENT_CUT & 0xFF)).add(0, 2.0, 0);
            WorldFx.add(new TelegraphDraw.Flash(a, GLOW, false, true, 1.0f, 5.0f, MAGENTA, 1.0f, 14));
            burst(level, a, VIOLET, 30, 0.35);
            return;
        }
        if (e >= (HollowHeliarch.EVENT_LASH & 0xFF) && e < (HollowHeliarch.EVENT_LASH & 0xFF) + 4) {
            int i = e - (HollowHeliarch.EVENT_LASH & 0xFF);
            Vec3 a = HeliarchArena.tendrilAnchor(i);
            Vec3 end = h.lashEnd(i);
            for (int k = 0; k < 16; k++) {
                Vec3 p = a.add(end.subtract(a).scale(RANDOM.nextDouble())).add(0, 0.1, 0);
                FxBudget.spawn(FxParticles.spark(level, p).velocity(new Vec3(RANDOM.nextGaussian() * 0.05, 0.12, RANDOM.nextGaussian() * 0.05))
                        .color(MAGENTA).size(0.18f, 0.03f).life(16).gravity(0.05f));
            }
            shakeNear(a.add(end).scale(0.5), 8, 0.3);
            return;
        }
        if (e >= (HollowHeliarch.EVENT_SHATTER & 0xFF) && e < (HollowHeliarch.EVENT_SHATTER & 0xFF) + 6) {
            Vec3 m = HeliarchPose.monolithMiddle(e - (HollowHeliarch.EVENT_SHATTER & 0xFF));
            WorldFx.add(new TelegraphDraw.Flash(m, GLOW, false, true, 2.0f, 7.0f, PALE_GOLD, 1.0f, 14));
            burst(level, m, GOLD, 40, 0.4);
            shakeNear(m, 14, 0.4);
            return;
        }
        if (e >= (HollowHeliarch.EVENT_PIP & 0xFF) && e < (HollowHeliarch.EVENT_PIP & 0xFF) + 6) {
            Vec3 m = HeliarchPose.monolithMiddle(e - (HollowHeliarch.EVENT_PIP & 0xFF)).add(0, 1.2, 0);
            burst(level, m, GOLD, 16, 0.25);
            WorldFx.add(new TelegraphDraw.Flash(m, SUN, false, true, 0.8f, 2.6f, WHITE, 1.0f, 10));
            return;
        }
        switch (id) {
            case HollowHeliarch.EVENT_IGNITE -> {
                WorldFx.add(new TelegraphDraw.Flash(core, GLOW, false, true, 2.0f, 14.0f, PALE_GOLD, 1.0f, 30));
                shakeNear(core, 40, 0.35);
            }
            case HollowHeliarch.EVENT_GLINT -> WorldFx.add(new TelegraphDraw.Glint(() -> {
                Vec3 hand = HeliarchPose.hand(h.poseInput(), h.facing(), sunderRight(h), level.getGameTime() + partial());
                return hand == null ? null : hand.add(0, 0.8, 0);
            }, GOLD, 2.4f, 12, 0.25f));
            case HollowHeliarch.EVENT_SLAM -> {
                Vec3 ring = h.actionPos();
                WorldFx.add(new TelegraphDraw.Flash(ring.add(0, 0.08, 0), RING, true, true, 1.0f, (float) HeliarchMoves.SUNDER_RADIUS + 1.5f, PALE_GOLD, 1.0f, 12));
                WorldFx.add(new TelegraphDraw.Flash(ring.add(0, 1.0, 0), GLOW, false, true, 1.5f, 5.0f, GOLD, 0.8f, 8));
                burst(level, ring.add(0, 0.2, 0), 0xE8DCC0, 26, 0.35);
                shakeNear(ring, 16, 0.6);
            }
            case HollowHeliarch.EVENT_PARRIED -> {
                Vec3 at = h.actionPos().add(0, 1.4, 0);
                WorldFx.add(new TelegraphDraw.Flash(at, SUN, false, true, 1.5f, 7.0f, GOLD, 1.0f, 18));
                WorldFx.add(new TelegraphDraw.Flash(at, GLOW, false, true, 2.0f, 9.0f, PALE_GOLD, 0.8f, 14));
                burst(level, at, PALE_GOLD, 30, 0.35);
            }
            case HollowHeliarch.EVENT_BREAK -> {
                WorldFx.add(new TelegraphDraw.Flash(core, GLOW, false, true, 2.0f, 10.0f, WHITE, 1.0f, 16));
                burst(level, core, GOLD, 40, 0.3);
                shakeNear(core, 30, 0.5);
            }
            case HollowHeliarch.EVENT_FLARE -> {
                WorldFx.add(new TelegraphDraw.Flash(core, SUN, false, true, 2.5f, 12.0f, WHITE, 1.0f, 12));
                WorldFx.add(new TelegraphDraw.Flash(core, GLOW, false, true, 3.0f, 14.0f, PALE_GOLD, 0.8f, 16));
                burst(level, core, PALE_GOLD, 40, 0.45);
                shakeNear(core, HeliarchMoves.FLARE_RADIUS + 4, 0.4);
            }
            case HollowHeliarch.EVENT_SWEEP -> {
                Vec3 at = HeliarchArena.at(h.actionAngle(), 13.0).add(0, 0.6, 0);
                WorldFx.add(new TelegraphDraw.Flash(at, GLOW, false, true, 1.5f, 6.0f, EMBER, 1.0f, 10));
            }
            case HollowHeliarch.EVENT_LANCE -> {
                Vec3[] line = HollowHeliarch.lanceLine(level, core, h.actionPos(), h);
                WorldFx.add(new TelegraphDraw.Flash(line[1], GLOW, false, true, 1.5f, 5.0f, PALE_GOLD, 1.0f, 10));
                shakeNear(line[1], 10, 0.3);
            }
            case HollowHeliarch.EVENT_CLANG -> {
                Vec3 me = Minecraft.getInstance().player == null ? core : Minecraft.getInstance().player.getEyePosition();
                Vec3 face = core.add(me.subtract(core).normalize().scale(1.7));
                for (int i = 0; i < 8; i++) {
                    FxBudget.spawn(FxParticles.spark(level, face).velocity(new Vec3(RANDOM.nextGaussian() * 0.15, RANDOM.nextDouble() * 0.15,
                            RANDOM.nextGaussian() * 0.15)).color(PALE_GOLD).size(0.12f, 0.02f).life(8).gravity(0.08f));
                }
            }
            case HollowHeliarch.EVENT_MONOLITHS -> {
                for (int k = 0; k < 6; k++) {
                    Vec3 m = HeliarchPose.monolithMiddle(k);
                    WorldFx.add(new TelegraphDraw.Flash(m.subtract(0, 1.9, 0), RING, true, true, 1.0f, 6.0f, PALE_GOLD, 1.0f, 14));
                    burst(level, m.subtract(0, 1.5, 0), 0xD8C8A0, 20, 0.3);
                }
                shakeNear(HeliarchArena.CENTRE, 40, 0.7);
            }
            case HollowHeliarch.EVENT_NOVA_BLAST -> {
                WorldFx.add(new TelegraphDraw.Flash(core, GLOW, false, true, 4.0f, 60.0f, WHITE, 1.0f, 26));
                WorldFx.add(new TelegraphDraw.Flash(HeliarchArena.CENTRE.add(0, 0.1, 0), RING, true, true, 2.0f, 34.0f, PALE_GOLD, 1.0f, 24));
                HeliarchSky.flash(1.0f);
                shakeNear(core, 60, 1.0);
            }
            case HollowHeliarch.EVENT_NOVA_BREAK -> {
                WorldFx.add(new TelegraphDraw.Flash(core, SUN, false, true, 3.0f, 12.0f, GOLD, 1.0f, 20));
                burst(level, core, GOLD, 60, 0.5);
                shakeNear(core, 40, 0.6);
            }
            case HollowHeliarch.EVENT_INVERSION -> {
                WorldFx.add(new TelegraphDraw.Flash(HeliarchArena.CENTRE.add(0, 0.1, 0), RING, true, true, 30.0f, 2.0f, VIOLET, 0.9f, 24));
                shakeNear(core, 40, 0.3);
            }
            case HollowHeliarch.EVENT_DEATH -> {
                WorldFx.add(new TelegraphDraw.Flash(core, GLOW, false, true, 3.0f, 30.0f, WHITE, 1.0f, 40));
                HeliarchSky.flash(0.8f);
                shakeNear(core, 60, 0.6);
            }
            case HollowHeliarch.EVENT_WITHDRAW -> WorldFx.add(new TelegraphDraw.Flash(core, GLOW, false, true, 8.0f, 0.5f, VIOLET, 1.0f, 20));
            case HollowHeliarch.EVENT_HOLLOWING -> {
                WorldFx.add(new TelegraphDraw.Flash(core, GLOW, false, true, 2.0f, 16.0f, VIOLET, 1.0f, 24));
                shakeNear(core, 40, 0.4);
            }
            case HollowHeliarch.EVENT_COLLAPSE -> {
                HeliarchSky.flash(0.4f);
                shakeNear(core, 60, 0.8);
            }
            case HollowHeliarch.EVENT_BEAM -> shakeNear(core, 40, 0.25);
            case HollowHeliarch.EVENT_HURT -> {
                Vec3 at = core.add(RANDOM.nextGaussian() * 0.6, RANDOM.nextGaussian() * 0.6, RANDOM.nextGaussian() * 0.6);
                WorldFx.add(new TelegraphDraw.Flash(at, GLOW, false, true, 0.5f, 2.2f, h.state() == State.REGENT ? WHITE : EMBER, 0.8f, 6));
            }
            case HollowHeliarch.EVENT_RELIQUARIES -> WorldFx.add(new TelegraphDraw.Flash(HeliarchArena.CENTRE.add(0, 0.1, 0), RING, true, true,
                    1.0f, 9.0f, PALE_GOLD, 1.0f, 30));
            default -> {
            }
        }
    }

    private static boolean sunderRight(HollowHeliarch h) {
        return h.poseInput().sunderRight();
    }

    private static void burst(ClientLevel level, Vec3 at, int colour, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6 + 0.4, RANDOM.nextGaussian()).normalize().scale(speed * (0.4 + RANDOM.nextDouble()));
            FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(colour).size(0.18f, 0.03f).life(14 + RANDOM.nextInt(10)).drag(0.92f)
                    .gravity(0.03f));
        }
    }

    private static void shakeNear(Vec3 at, double within, double trauma) {
        var p = Minecraft.getInstance().player;
        if (p != null && p.position().distanceTo(at) <= within) {
            ClientCombat.feelSlam(trauma, trauma * 2.0);
        }
    }

    @Override
    public void seedEvent(StarSeed seed, byte event) {
        ClientLevel level = level();
        if (level == null) {
            return;
        }
        Vec3 c = seed.centre();
        if (event == StarSeed.EVENT_BURST) {
            WorldFx.add(new TelegraphDraw.Flash(c, GLOW, false, true, 0.6f, 3.5f, VIOLET, 1.0f, 10));
            burst(level, c, MAGENTA, 16, 0.25);
        } else {
            WorldFx.add(new TelegraphDraw.Flash(c, STAR, false, true, 0.6f, 2.5f, WHITE, 1.0f, 8));
            burst(level, c, PALE_GOLD, 12, 0.2);
        }
    }

    // ------------------------------------------------------------------ drawing

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        ClientLevel level = level();
        if (level == null) {
            return;
        }
        Camera camera = event.getCamera();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = level.getGameTime() + partial;
        HollowHeliarch h = heliarch();
        try {
            drawFalls(camera, time, event.getPoseStack());
            drawRain(camera, time);
            if (h != null && h.level() == level) {
                frames++;
                Frame f = new Frame(camera, camera.getPosition(), time, h);
                drawCore(f);
                drawRegent(f);
                drawHollow(f);
                drawCollapse(f);
            }
            BUFFERS.endBatch();
        } finally {
            RenderSystem.defaultBlendFunc();
        }
    }

    private record Frame(Camera camera, Vec3 cam, double time, HollowHeliarch h) {
        Vec3 rel(Vec3 world) {
            return world.subtract(cam);
        }
    }

    private static VertexConsumer add(ResourceLocation tex) {
        return BUFFERS.getBuffer(FxRenderTypes.additive(tex));
    }

    private static VertexConsumer shade(ResourceLocation tex) {
        return BUFFERS.getBuffer(FxRenderTypes.shade(tex));
    }

    private static VertexConsumer solid() {
        return BUFFERS.getBuffer(ShardDraw.solid());
    }

    private static float[] rgb(int c) {
        return ShardDraw.rgb(c);
    }

    private static float beat(double time) {
        long t = (long) Math.floor(time);
        return (float) Math.exp(-(VesperClock.tickInBeat(t) + (time - t)) / VesperClock.PULSE_DECAY);
    }

    // ------------------------------------------------------------------ the core: a sun, then an eclipse

    private static void drawCore(Frame f) {
        HollowHeliarch h = f.h();
        State st = h.state();
        Vec3 core = h.core(f.time());
        Vec3 c = f.rel(core);
        double s = f.time() - h.stateStart();
        float pulse = 0.85f + 0.15f * beat(f.time());
        if (st == State.INTRO || st == State.REGENT || st == State.HOLLOWING && s < 60) {
            float grow = st == State.INTRO ? (float) HeliarchPose.smooth((s - 40) / 60.0) : st == State.HOLLOWING
                    ? (float) (1.0 - HeliarchPose.smooth((s - 30) / 30.0)) : 1f;
            double open = HeliarchPose.openness(h.poseInput(), f.time());
            float broken = h.brokenNow((long) f.time()) ? 0.6f : 1f;
            // a sun's light round it: a wide warm halo, then the corona, then a white heart (brighter open)
            TelegraphDraw.glow(add(GLOW), f.camera(), c, (float) (9.0 + 4.0 * open) * grow, rgb(EMBER), (0.45f + 0.25f * (float) open) * pulse * grow * broken);
            TelegraphDraw.glow(add(GLOW), f.camera(), c, (float) (4.5 + 1.5 * open) * grow, rgb(GOLD), (float) (0.4 + 0.5 * open) * pulse * grow);
            TelegraphDraw.glow(add(CORONA), f.camera(), c, (float) (3.1 + 0.5 * open) * grow, rgb(PALE_GOLD), (float) (0.35 + 0.65 * open) * pulse * grow);
            TelegraphDraw.glow(add(GLOW), f.camera(), c, 2.0f * grow, rgb(WHITE), (float) (0.45 + 0.5 * open) * grow);
            // the halo itself: a ring of light round the crown's foot, turning with it
            if (st == State.REGENT || st == State.INTRO && s > HeliarchPose.INTRO_ASSEMBLED) {
                double rr = HeliarchPose.lerp(HeliarchPose.CROWN_CLOSED_R + 0.4, HeliarchPose.CROWN_OPEN_R + 1.2, open);
                Vec3 ring = f.rel(core.add(0, HeliarchPose.lerp(HeliarchPose.CROWN_CLOSED_H, HeliarchPose.CROWN_OPEN_H, open), 0));
                TelegraphDraw.sector(add(GLOW), ring, rr - 0.9, rr + 0.9, 0f, 0, 360, 48, rgb(GOLD), 0.3f * pulse * grow);
                TelegraphDraw.sector(add(GLOW), ring, rr - 0.18, rr + 0.18, 0f, 0, 360, 48, rgb(PALE_GOLD), 0.85f * pulse * grow);
            }
            mark("sun");
            return;
        }
        if (st == State.HOLLOWING || st == State.HOLLOW || st == State.COLLAPSE || st == State.DYING) {
            double form = st == State.HOLLOWING ? HeliarchPose.smooth((s - 40) / 40.0) : 1.0;
            if (st == State.DYING) {
                form = 1.0 - HeliarchPose.smooth((s - 10) / 50.0);
            }
            long now = (long) f.time();
            boolean nova = h.novaStart() != Long.MIN_VALUE && now >= h.novaStart();
            double novaU = nova ? HeliarchPose.clamp((f.time() - h.novaStart()) / HeliarchMoves.NOVA_CHANNEL) : 0;
            boolean stunned = now < h.stunUntil();
            double r = (ECLIPSE_R + 0.8 * novaU) * HeliarchPose.eclipseScale(h.poseInput(), f.time()) * form;
            if (r > 0.02) {
                // the eclipse: a black disc, a burning rim, a violet haze round it
                TelegraphDraw.glow(add(GLOW), f.camera(), c, (float) (r * 3.4), rgb(VIOLET), 0.32f * (float) form * pulse);
                float rimHeat = (float) (0.7 + 0.3 * pulse + (nova ? 0.8 * novaU : 0));
                int rimColour = stunned ? 0xFF4A2A : nova ? PALE_GOLD : EMBER;
                TelegraphDraw.glow(add(CORONA), f.camera(), c, (float) (r * 2.1), rgb(rimColour), Math.min(1f, rimHeat) * (float) form);
                TelegraphDraw.glow(add(CORONA), f.camera(), c, (float) (r * 1.75), rgb(WHITE), 0.5f * (float) form);
                // solid, depth written: nothing behind it (a monolith's pips, a tendril) shows through
                solidDisc(f, c, r, 48, rgb(stunned ? 0x2A0804 : 0x020104));
                if (stunned) {
                    float beatS = 0.5f + 0.5f * (float) Math.sin(f.time() * 0.6);
                    TelegraphDraw.glow(add(GLOW), f.camera(), c, (float) (r * 1.1), rgb(0xFF5A30), 0.6f * beatS);
                }
                mark("eclipse");
            }
            if (st == State.DYING && s > 10) {
                // the light comes back: a warm wash where the eclipse broke, fading (no bead: it read as a second sun)
                double u = HeliarchPose.smooth((s - 10) / 30.0);
                double fade = 1.0 - HeliarchPose.smooth((s - 40) / 60.0);
                TelegraphDraw.glow(add(GLOW), f.camera(), c, (float) (6.0 + 10.0 * u), rgb(PALE_GOLD), (float) (0.35 * u * fade));
                mark("dawn");
            }
        }
    }

    /** A disc facing the camera, flat colour. */
    /** A disc facing the camera, opaque and depth written (the entity format, full-bright). */
    private static void solidDisc(Frame f, Vec3 c, double radius, int segments, float[] col) {
        VertexConsumer out = BUFFERS.getBuffer(RenderType.entityCutoutNoCull(RIFT));
        Vector3f l = f.camera().getLeftVector();
        Vector3f u = f.camera().getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z());
        Vec3 up = new Vec3(u.x(), u.y(), u.z());
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            Vec3 p0 = c.add(left.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(left.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            // the rift texture is opaque at its middle: every corner samples there
            entityVertex(out, c, 0.5f, 0.5f, col);
            entityVertex(out, p0, 0.5f, 0.5f, col);
            entityVertex(out, p1, 0.5f, 0.5f, col);
            entityVertex(out, p1, 0.5f, 0.5f, col);
        }
    }

    static void facingDisc(VertexConsumer out, Camera camera, Vec3 c, double radius, int segments, float[] col, float alpha) {
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z());
        Vec3 up = new Vec3(u.x(), u.y(), u.z());
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            Vec3 p0 = c.add(left.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(left.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            ShardDraw.vertex(out, IDENTITY, c, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p0, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        }
    }

    // ------------------------------------------------------------------ phase 1's telegraphs

    private static void drawRegent(Frame f) {
        HollowHeliarch h = f.h();
        if (h.state() != State.REGENT) {
            return;
        }
        double a = f.time() - h.actionStart();
        switch (h.action()) {
            case SUNDERFALL -> drawSunderfall(f, a);
            case CORONA_SWEEP -> drawSweep(f, a);
            case SOLAR_LANCE -> drawLance(f, a);
            case HALO_SHED -> drawShed(f, a);
            case CORONA_FLARE -> drawFlare(f, a);
            default -> {
            }
        }
    }

    /**
     * Corona Flare: for its 20 ticks a white-gold light fills the floor from the core out to the ring's edge (radius 6),
     * the edge a hard white line with a low curtain that rises as it comes; then the flare, a white wall racing outward.
     */
    private static void drawFlare(Frame f, double a) {
        Vec3 c = f.rel(HeliarchArena.CENTRE.add(0, 0.07, 0));
        double r = HeliarchMoves.FLARE_RADIUS;
        if (a < HeliarchMoves.FLARE_TELL) {
            double u = HeliarchPose.clamp(a / HeliarchMoves.FLARE_TELL);
            float pulse = 0.8f + 0.2f * (float) Math.sin(a * 0.9);
            TelegraphDraw.sector(solid(), c, 0, r, 0f, 0, 360, 48, rgb(0x3A2A10), 0.35f);
            TelegraphDraw.sector(add(GLOW), c, 0, r * u, 0f, 0, 360, 48, rgb(PALE_GOLD), (0.35f + 0.35f * (float) u) * pulse);
            TelegraphDraw.sector(solid(), c, r - 0.18, r + 0.18, 0f, 0, 360, 72, rgb(0xFFF6DC), 0.95f);
            TelegraphDraw.curtain(add(GLOW), c, r, 0f, 0, 360, 72, 0.6 + 1.2 * u, rgb(PALE_GOLD), 0.55f * pulse);
            mark("flare tell");
            return;
        }
        double w = a - HeliarchMoves.FLARE_TELL;
        if (w < 8) {
            double out = r * (0.5 + 0.8 * w / 8.0);
            float fade = (float) (1.0 - w / 8.0);
            TelegraphDraw.sector(add(GLOW), c, Math.max(0, out - 1.4), out, 0f, 0, 360, 72, rgb(WHITE), 0.9f * fade);
            TelegraphDraw.curtain(add(GLOW), c, out, 0f, 0, 360, 72, 2.8 * fade, rgb(PALE_GOLD), 0.75f * fade);
            mark("flare");
        }
    }

    /** A gold ring (radius 4) filling under the target, a gold curtain on it; brighter from the glint. */
    private static void drawSunderfall(Frame f, double a) {
        if (a > HeliarchMoves.SUNDER_TELL + 2) {
            return;
        }
        Vec3 ring = f.rel(f.h().actionPos().add(0, 0.06, 0));
        double u = HeliarchPose.clamp(a / HeliarchMoves.SUNDER_TELL);
        boolean glint = a >= HeliarchMoves.SUNDER_GLINT;
        float[] gold = rgb(GOLD);
        float pulse = glint ? 1.0f : 0.75f + 0.25f * (float) Math.sin(a * 0.9);
        double rad = HeliarchMoves.SUNDER_RADIUS;
        // the fill grows from the middle to the edge as the hand comes down
        TelegraphDraw.sector(solid(), ring, 0.0, rad * u, 0f, 0, 360, 40, gold, 0.28f * pulse);
        TelegraphDraw.sector(add(GLOW), ring, 0.0, rad * u, 0f, 0, 360, 40, gold, 0.25f * pulse);
        // the edge: a band a third of a block wide, solid gold (the telegraph is the hitbox)
        TelegraphDraw.sector(solid(), ring, rad - 0.3, rad + 0.05, 0f, 0, 360, 48, gold, 0.95f);
        TelegraphDraw.sector(add(GLOW), ring, rad - 0.5, rad + 0.25, 0f, 0, 360, 48, rgb(PALE_GOLD), 0.6f * pulse);
        TelegraphDraw.curtain(add(GLOW), ring, rad, 0f, 0, 360, 48, glint ? 2.6 : 1.8, gold, 0.4f * pulse);
        mark("sunderfall");
    }

    /** The band between 10 and 16 glows white-gold; then a knee-high wall of fire runs round it clockwise. */
    private static void drawSweep(Frame f, double a) {
        HollowHeliarch h = f.h();
        Vec3 centre = f.rel(HeliarchArena.CENTRE.add(0, 0.06, 0));
        float start = (float) HeliarchArena.yawOf(h.actionAngle());
        if (a < HeliarchMoves.SWEEP_TELL) {
            double u = HeliarchPose.clamp(a / HeliarchMoves.SWEEP_TELL);
            float alpha = (float) (0.25 + 0.5 * u);
            TelegraphDraw.sector(solid(), centre, HeliarchMoves.SWEEP_INNER, HeliarchMoves.SWEEP_OUTER, 0f, 0, 360, 96, rgb(PALE_GOLD), 0.28f * alpha);
            TelegraphDraw.sector(add(GLOW), centre, HeliarchMoves.SWEEP_INNER, HeliarchMoves.SWEEP_OUTER, 0f, 0, 360, 96, rgb(WHITE), 0.3f * alpha);
            // its edges stand up as low curtains so the band reads at eye level
            TelegraphDraw.curtain(add(GLOW), centre, HeliarchMoves.SWEEP_INNER, 0f, 0, 360, 96, 1.1, rgb(PALE_GOLD), 0.55f * alpha);
            TelegraphDraw.curtain(add(GLOW), centre, HeliarchMoves.SWEEP_OUTER, 0f, 0, 360, 96, 1.1, rgb(PALE_GOLD), 0.55f * alpha);
            TelegraphDraw.sector(solid(), centre, HeliarchMoves.SWEEP_INNER - 0.12, HeliarchMoves.SWEEP_INNER + 0.12, 0f, 0, 360, 96, rgb(GOLD), 0.9f);
            TelegraphDraw.sector(solid(), centre, HeliarchMoves.SWEEP_OUTER - 0.12, HeliarchMoves.SWEEP_OUTER + 0.12, 0f, 0, 360, 96, rgb(GOLD), 0.9f);
            // where the wall will start: a bright marker across the band
            TelegraphDraw.sector(add(GLOW), centre, HeliarchMoves.SWEEP_INNER, HeliarchMoves.SWEEP_OUTER, start, -2, 2, 2, rgb(WHITE), (float) u);
            mark("corona band");
            return;
        }
        double w = a - HeliarchMoves.SWEEP_TELL;
        if (w > HeliarchMoves.SWEEP_TICKS + 4) {
            return;
        }
        double wallAt = CoronaSweep.wallAngle(h.actionAngle(), Math.min(w, HeliarchMoves.SWEEP_TICKS));
        float wall = (float) HeliarchArena.yawOf(wallAt);
        double trail = Math.min(70.0, 18.0 * Math.min(w, HeliarchMoves.SWEEP_TICKS));
        float fade = (float) HeliarchPose.clamp((HeliarchMoves.SWEEP_TICKS + 4 - w) / 4.0);
        // the band it has burned: scorched dark, embers glowing in it (peach on the ivory floor didn't read)
        TelegraphDraw.sector(solid(), centre, HeliarchMoves.SWEEP_INNER, HeliarchMoves.SWEEP_OUTER, wall, -trail, 0, 24, rgb(0x2A0800),
                0.55f * fade);
        TelegraphDraw.sector(add(GLOW), centre, HeliarchMoves.SWEEP_INNER, HeliarchMoves.SWEEP_OUTER, wall, -trail, 0, 24, rgb(0xFF3A0A),
                0.6f * fade);
        // the wall: saturated fire solid to a jump's height across the band (what burns), flames above it in tongues
        // to three blocks, flickering, so it reads from the rim
        double t = f.time();
        for (int k = 2; k >= 0; k--) {
            double ang = wallAt - 2.0 * k;
            Vec3 in = HeliarchArena.at(ang, HeliarchMoves.SWEEP_INNER - 0.3);
            Vec3 out = HeliarchArena.at(ang, HeliarchMoves.SWEEP_OUTER + 0.3);
            standing(solid(), f, in, out, HeliarchMoves.SWEEP_CLEAR, rgb(k == 0 ? 0xFF7A12 : 0xE0400A), (0.95f - 0.25f * k) * fade,
                    (0.9f - 0.25f * k) * fade);
        }
        Vec3 lip0 = f.rel(HeliarchArena.at(wallAt, HeliarchMoves.SWEEP_INNER - 0.3).add(0, HeliarchMoves.SWEEP_CLEAR, 0));
        Vec3 lip1 = f.rel(HeliarchArena.at(wallAt, HeliarchMoves.SWEEP_OUTER + 0.3).add(0, HeliarchMoves.SWEEP_CLEAR, 0));
        TelegraphDraw.line(solid(), f.camera(), lip0, lip1, TelegraphDraw.screenWidth(lip0, 4, 0.06), TelegraphDraw.screenWidth(lip1, 4, 0.06),
                rgb(0xFFE070), fade);
        // the flames stand in a wake behind the hand that drives them, tallest at the wall, so they show past the hand
        int tongues = 14;
        for (int pass = 0; pass < 3; pass++) {
            // one pass per buffer (an immediate buffer source ends a batch when another is asked for)
            VertexConsumer out = pass == 0 ? solid() : add(GLOW);
            for (int j = 0; j < 5; j++) {
                double behind = wallAt - 1.0 - 3.0 * j;
                float keep = (float) (1.0 - 0.17 * j);
                for (int k = 0; k < tongues; k++) {
                    double r0 = HeliarchMoves.SWEEP_INNER - 0.3 + (HeliarchMoves.SWEEP_OUTER - HeliarchMoves.SWEEP_INNER + 0.6) * k / tongues;
                    double r1 = HeliarchMoves.SWEEP_INNER - 0.3 + (HeliarchMoves.SWEEP_OUTER - HeliarchMoves.SWEEP_INNER + 0.6) * (k + 1) / tongues;
                    double flick = 0.6 + 0.4 * Math.sin(t * 1.7 + k * 2.3 + j) * Math.sin(t * 0.9 + k * 1.1 + j * 0.7);
                    double tall = (1.8 + 1.8 * flick) * fade * keep;
                    Vec3 a0 = HeliarchArena.at(behind, r0);
                    Vec3 a1 = HeliarchArena.at(behind, r1);
                    switch (pass) {
                        case 0 -> standing(out, f, a0, a1, tall * 0.7, rgb(0xFF8A1E), 0.75f * fade * keep, 0f);
                        case 1 -> standing(out, f, a0, a1, tall, rgb(0xFF5A14), 0.8f * fade * keep, 0f);
                        default -> standing(out, f, a0, a1, tall * 0.6, rgb(0xFFB030), 0.55f * fade * keep, 0f);
                    }
                }
            }
        }
        TelegraphDraw.sector(add(GLOW), centre, HeliarchMoves.SWEEP_INNER - 0.4, HeliarchMoves.SWEEP_OUTER + 0.4, wall, -5, 2, 4, rgb(WHITE), 0.9f * fade);
        mark("corona wall");
    }

    /** One standing quad from floor point {@code a} to {@code b} (world), {@code height} tall, its alpha from foot to top. */
    private static void standing(VertexConsumer out, Frame f, Vec3 a, Vec3 b, double height, float[] col, float foot, float top) {
        Vec3 p0 = f.rel(a.add(0, 0.02, 0));
        Vec3 p1 = f.rel(b.add(0, 0.02, 0));
        Vec3 up = new Vec3(0, height, 0);
        ShardDraw.vertex(out, IDENTITY, p0, 0.5f, 0.5f, col[0], col[1], col[2], foot);
        ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, col[0], col[1], col[2], foot);
        ShardDraw.vertex(out, IDENTITY, p1.add(up), 0.5f, 0.05f, col[0], col[1], col[2], top);
        ShardDraw.vertex(out, IDENTITY, p0.add(up), 0.5f, 0.05f, col[0], col[1], col[2], top);
    }

    /** A ring facing the camera round {@code at} (camera-relative), 3 px or more wide. */
    private static void reticle(Frame f, Vec3 at, double radius, float[] col, float alpha) {
        Vector3f l = f.camera().getLeftVector();
        Vector3f u = f.camera().getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z());
        Vec3 up = new Vec3(u.x(), u.y(), u.z());
        int segs = 24;
        double w = TelegraphDraw.screenWidth(at, 3.5, 0.05);
        for (int k = 0; k < segs; k++) {
            double a0 = Math.PI * 2 * k / segs;
            double a1 = Math.PI * 2 * (k + 1) / segs;
            Vec3 q0 = at.add(left.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius));
            Vec3 q1 = at.add(left.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius));
            TelegraphDraw.line(solid(), f.camera(), q0, q1, w, w, col, alpha);
        }
    }

    /** A red line follows the target; locked it turns white; then a searing beam. */
    private static void drawLance(Frame f, double a) {
        HollowHeliarch h = f.h();
        ClientLevel level = level();
        Vec3 core = h.core(f.time());
        // the aim is the server's: it moves it onto its target's chest every tick while it tracks (a client's own lookup of
        // the target could land on a camera or nothing and aim the line at the throne)
        Vec3 aim = h.actionPos();
        Vec3[] line = HollowHeliarch.lanceLine(level, core, aim, h);
        Vec3 p0 = f.rel(line[0]);
        Vec3 p1 = f.rel(line[1]);
        int fire = HeliarchMoves.LANCE_TRACK + HeliarchMoves.LANCE_LOCK;
        if (a < HeliarchMoves.LANCE_TRACK) {
            // a red line from the core to the target's chest, following it, and a red ring round the target
            float flick = 0.85f + 0.15f * (float) Math.sin(a * 2.1);
            Vec3 t1 = f.rel(aim);
            TelegraphDraw.line(solid(), f.camera(), p0, t1, TelegraphDraw.screenWidth(p0, 8, 0.1), TelegraphDraw.screenWidth(t1, 8, 0.1),
                    rgb(0x2A0000), 0.8f);
            TelegraphDraw.line(solid(), f.camera(), p0, t1, TelegraphDraw.screenWidth(p0, 4.5, 0.06), TelegraphDraw.screenWidth(t1, 4.5, 0.06),
                    rgb(RED), 0.95f * flick);
            TelegraphDraw.taper(add(BEAM), f.camera(), p0, t1, TelegraphDraw.screenWidth(p0, 16, 0.25), TelegraphDraw.screenWidth(t1, 16, 0.25),
                    rgb(RED), 0.55f);
            TelegraphDraw.line(solid(), f.camera(), t1, p1, TelegraphDraw.screenWidth(t1, 3, 0.04), TelegraphDraw.screenWidth(p1, 3, 0.04),
                    rgb(RED), 0.35f * flick);
            reticle(f, t1, 0.9, rgb(RED), 0.9f * flick);
            mark("lance tracking");
        } else if (a < fire) {
            float flick = (Math.floorMod((long) a, 2) == 0) ? 1f : 0.75f;
            TelegraphDraw.line(solid(), f.camera(), p0, p1, TelegraphDraw.screenWidth(p0, 5, 0.08), TelegraphDraw.screenWidth(p1, 5, 0.08),
                    rgb(WHITE), flick);
            TelegraphDraw.taper(add(BEAM), f.camera(), p0, p1, TelegraphDraw.screenWidth(p0, 16, 0.3), TelegraphDraw.screenWidth(p1, 16, 0.3),
                    rgb(PALE_GOLD), 0.6f);
            mark("lance locked");
        } else if (a < fire + HeliarchMoves.LANCE_BURN + 4) {
            double u = (a - fire) / (HeliarchMoves.LANCE_BURN + 4);
            float alpha = (float) (1.0 - u * u);
            TelegraphDraw.ribbon(add(BEAM), f.camera(), p0, p1, 4.2, rgb(0xFF6A1A), 0.55f * alpha);
            TelegraphDraw.ribbon(add(BEAM), f.camera(), p0, p1, 2.6, rgb(PALE_GOLD), alpha);
            TelegraphDraw.ribbon(add(BEAM), f.camera(), p0, p1, 1.2, rgb(WHITE), alpha);
            TelegraphDraw.glow(add(GLOW), f.camera(), p1, 3.6f, rgb(GOLD), alpha);
            TelegraphDraw.glow(add(GLOW), f.camera(), p0, 3.0f, rgb(WHITE), 0.8f * alpha);
            mark("lance fire");
        }
    }

    /** The plates flash, fly out overhead, hang at the rim; red trails show their way back for 16 ticks; they return. */
    private static void drawShed(Frame f, double a) {
        HollowHeliarch h = f.h();
        HeliarchPose.Input in = h.poseInput();
        if (a < HaloShed.FLASH) {
            float on = (Math.floorMod((long) (a / 3), 2) == 0) ? 1f : 0.35f;
            for (int k = 0; k < 6; k++) {
                Vec3 p = HeliarchPose.plate(in, h.facing(), k, f.time());
                if (p != null) {
                    TelegraphDraw.glow(add(GLOW), f.camera(), f.rel(p), 2.2f, rgb(WHITE), 0.6f * on);
                }
            }
            mark("shed flash");
        }
        if (HaloShed.trails(a)) {
            double u = HeliarchPose.clamp((a - (HaloShed.RETURN_START - HaloShed.TRAILS)) / HaloShed.TRAILS);
            for (int k = 0; k < 6; k++) {
                Vec3[] line = HaloShed.returnLine(h.actionAngle(), k);
                Vec3 p0 = f.rel(line[0]);
                Vec3 p1 = f.rel(line[1]);
                Vec3 f0 = f.rel(line[0].subtract(0, HaloShed.LOW - 0.06, 0));
                Vec3 f1 = f.rel(line[1].subtract(0, HaloShed.LOW - 0.06, 0));
                float alpha = (float) (0.55 + 0.45 * u);
                // the path at chest height, and its shadow on the floor as a stripe as wide as the plate's reach
                TelegraphDraw.line(solid(), f.camera(), p0, p1, TelegraphDraw.screenWidth(p0, 4, 0.08), TelegraphDraw.screenWidth(p1, 4, 0.08),
                        rgb(RED), alpha);
                floorStripe(solid(), f0, f1, HaloShed.PLATE_REACH * 2.0, rgb(RED), 0.3f * alpha);
                floorStripe(add(GLOW), f0, f1, HaloShed.PLATE_REACH * 2.0, rgb(RED), 0.25f * alpha);
            }
            mark("shed trails");
        }
        if (a >= HaloShed.FLASH && a < HaloShed.TOTAL) {
            for (int k = 0; k < 6; k++) {
                Vec3 p = HeliarchPose.plate(in, h.facing(), k, f.time());
                Vec3 q = HeliarchPose.plate(in, h.facing(), k, f.time() - 1.5);
                if (p != null && q != null && p.distanceToSqr(q) > 0.04) {
                    TelegraphDraw.ribbon(add(BEAM), f.camera(), f.rel(q), f.rel(p), 1.4, rgb(HaloShed.returning(a) ? RED : PALE_GOLD), 0.5f);
                }
            }
        }
    }

    /** A flat strip on the floor from {@code a} to {@code b} (camera-relative), {@code width} wide. */
    static void floorStripe(VertexConsumer out, Vec3 a, Vec3 b, double width, float[] col, float alpha) {
        Vec3 d = new Vec3(b.x - a.x, 0, b.z - a.z);
        if (d.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(width / 2.0);
        ShardDraw.vertex(out, IDENTITY, a.subtract(side), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, a.add(side), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b.add(side), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b.subtract(side), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
    }

    // ------------------------------------------------------------------ phase 2

    private static void drawHollow(Frame f) {
        HollowHeliarch h = f.h();
        State st = h.state();
        double s = f.time() - h.stateStart();
        boolean tendrilsUp = st == State.HOLLOW || st == State.COLLAPSE || st == State.HOLLOWING && s > 55;
        // the pips first: the tendrils write no depth, so a pip drawn after one would shine through it
        if (st == State.HOLLOW || st == State.COLLAPSE || st == State.HOLLOWING && s >= HeliarchMoves.HOLLOWING_SLAM) {
            drawPips(f);
        }
        if (tendrilsUp) {
            for (int i = 0; i < 4; i++) {
                drawTendril(f, i, st == State.HOLLOWING ? HeliarchPose.smooth((s - 55) / 40.0) : 1.0);
            }
        }
        if (st == State.HOLLOWING && s >= HeliarchPose.TEAR && s < HeliarchMoves.HOLLOWING_SLAM) {
            // where the plates will slam: six gold marks in the mid ring
            for (int k = 0; k < 6; k++) {
                HeliarchArena.Monolith m = HeliarchArena.monoliths().get(k);
                Vec3 c = f.rel(m.middle().add(0, 0.06, 0));
                TelegraphDraw.sector(solid(), c, 0, 2.3, 0f, 0, 360, 24, rgb(GOLD), 0.35f);
                TelegraphDraw.sector(solid(), c, 2.1, 2.4, 0f, 0, 360, 24, rgb(GOLD), 0.9f);
                mark("monolith marks");
            }
        }
        if (st != State.HOLLOW && st != State.COLLAPSE) {
            return;
        }
        double a = f.time() - h.actionStart();
        if (h.action() == Action.ECLIPSE_BEAM) {
            drawBeam(f, a);
        }
        if (h.action() == Action.NOVA) {
            drawNova(f, a);
        }
        long now = (long) f.time();
        if (now < h.stunUntil()) {
            // the heart exposed: a red-gold pulse on the floor under it too, so it reads from anywhere
            Vec3 c = f.rel(HeliarchArena.CENTRE.add(0, 0.06, 0));
            TelegraphDraw.sector(add(GLOW), c, 0, 4.0, 0f, 0, 360, 32, rgb(0xFF6A30), 0.35f + 0.2f * (float) Math.sin(f.time() * 0.5));
            mark("heart exposed");
        }
    }

    /**
     * A void tendril: a torn rift of darkness rising from its crack, thick at the foot and tapering to a ragged point,
     * its edges burning magenta. It rears back through its lash's warning (the floor cracks glow magenta along the line),
     * whips down along it, lies there a moment, and rises again. Cut, it sinks into its crack.
     */
    private static void drawTendril(Frame f, int i, double rise) {
        HollowHeliarch h = f.h();
        Vec3 anchor = HeliarchArena.tendrilAnchor(i);
        double t = f.time();
        long ls = h.lashStart(i);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 out = new Vec3(anchor.x - HeliarchArena.CX, 0, anchor.z - HeliarchArena.CZ).normalize();
        double sway = Math.sin(t * 0.07 + i * 1.7);
        Vec3 swayV = new Vec3(-out.z, 0, out.x).scale(0.9 * sway);
        // at rest it rises from its crack to the eclipse's rim, arching outward: the roots it hangs by
        Vec3 core = h.core(t);
        double er = eclipseRadius(h, t);
        Vec3 rim = HeliarchArena.dir(HeliarchArena.TENDRIL_ANGLES[i] + TENDRIL_TURN[i]);
        Vec3 attach = core.add(rim.scale(er * TENDRIL_REACH[i])).add(0, -er * TENDRIL_DROP[i], 0);
        Vec3 tip = HeliarchPose.lerp(anchor.add(0, 1.0, 0), attach, rise).add(swayV.scale(0.25));
        Vec3 ctrl = anchor.add(out.scale(TENDRIL_BOW[i] * rise)).add(0, TENDRIL_KNEE[i] * (tip.y - anchor.y), 0).add(swayV);
        double width = TENDRIL_WIDTH[i] * Math.max(0.35, rise);
        if (h.tendrilSilent(i)) {
            tip = anchor.add(0, 0.3, 0);
            ctrl = anchor.add(0, 0.15, 0);
            width = 0.6;
        }
        if (ls != Long.MIN_VALUE && t >= ls) {
            double a = t - ls;
            Vec3 end = h.lashEnd(i);
            Vec3 dir = end.subtract(anchor).multiply(1, 0, 1).normalize();
            Vec3 rear = anchor.add(up.scale(8.5)).subtract(dir.scale(3.0));
            Vec3 lying = end.add(0, 0.25, 0);
            Vec3 midLow = anchor.add(end).scale(0.5).add(0, 0.9, 0);
            if (a < HeliarchMoves.LASH_TELL) {
                double u = HeliarchPose.smooth(a / 10.0);
                tip = HeliarchPose.lerp(tip, rear, u);
                ctrl = HeliarchPose.lerp(ctrl, anchor.add(up.scale(3.5)).subtract(dir.scale(0.8)), u);
                drawLashTell(f, anchor, end, a);
            } else if (a < HeliarchMoves.LASH_TELL + 4) {
                double u = (a - HeliarchMoves.LASH_TELL) / 4.0;
                Vec3 over = anchor.add(dir.scale(6.0)).add(up.scale(5.0));
                tip = u < 0.5 ? HeliarchPose.lerp(rear, over, u * 2) : HeliarchPose.lerp(over, lying, (u - 0.5) * 2);
                ctrl = HeliarchPose.lerp(anchor.add(up.scale(3.5)), midLow, u);
            } else if (a < HeliarchMoves.LASH_TELL + 4 + HeliarchMoves.LASH_REST) {
                tip = lying;
                ctrl = midLow;
            } else {
                double u = HeliarchPose.smooth((a - HeliarchMoves.LASH_TELL - 4 - HeliarchMoves.LASH_REST) / 18.0);
                tip = HeliarchPose.lerp(lying, tip, u);
                ctrl = HeliarchPose.lerp(midLow, ctrl, u);
            }
        }
        rift(f, anchor, ctrl, tip, width);
        mark("tendril");
    }

    /**
     * A tapering rift along a curve from {@code a} (its foot) by {@code c} to {@code b} (its tip), facing the camera: a
     * solid void body (opaque, cut out of the rift's shape, depth written so nothing shows through it), then its
     * burning rim (magenta at the edge, an ember glow outside it).
     */
    private static void rift(Frame f, Vec3 a, Vec3 c, Vec3 b, double width) {
        int n = 18;
        Vec3[] pts = new Vec3[n + 1];
        for (int k = 0; k <= n; k++) {
            double u = k / (double) n;
            Vec3 p = a.scale((1 - u) * (1 - u)).add(c.scale(2 * u * (1 - u))).add(b.scale(u * u));
            pts[k] = f.rel(p);
        }
        float[] body = rgb(0x07030D);
        float[] magenta = rgb(MAGENTA);
        float[] ember = rgb(0xFF7A2E);
        float glowPulse = 0.8f + 0.2f * (float) Math.sin(f.time() * 0.23);
        // one pass per buffer (an immediate buffer source ends a batch when another is asked for)
        for (int pass = 0; pass < 3; pass++) {
            VertexConsumer out = pass == 0 ? BUFFERS.getBuffer(RenderType.entityCutoutNoCull(RIFT)) : add(RIFT_RIM);
            for (int k = 0; k < n; k++) {
                Vec3 p0 = pts[k];
                Vec3 p1 = pts[k + 1];
                Vec3 along = p1.subtract(p0);
                if (along.lengthSqr() < 1e-8) {
                    continue;
                }
                Vec3 side = along.cross(p0.add(p1).scale(0.5));
                if (side.lengthSqr() < 1e-8) {
                    continue;
                }
                side = side.normalize();
                // the rift's texture holds its taper: map it along the whole length (foot at the texture's bottom)
                float v0 = 1f - k / (float) n;
                float v1 = 1f - (k + 1) / (float) n;
                if (pass == 0) {
                    Vec3 w = side.scale(width / 2.0);
                    solidQuad(out, p0.subtract(w), p0.add(w), p1.add(w), p1.subtract(w), v0, v1, body);
                } else {
                    // the rim just before the body (toward the camera), so the two never fight over depth
                    Vec3 w = side.scale(width / 2.0 * (pass == 1 ? 1.06 : 1.35));
                    Vec3 q0 = p0.subtract(p0.normalize().scale(0.04));
                    Vec3 q1 = p1.subtract(p1.normalize().scale(0.04));
                    quadUv(out, q0.subtract(w), q0.add(w), q1.add(w), q1.subtract(w), v0, v1, pass == 1 ? magenta : ember,
                            (pass == 1 ? 0.95f : 0.45f) * glowPulse);
                }
            }
        }
    }

    /** One quad of the void body, in the entity format (full-bright, no overlay). */
    private static void solidQuad(VertexConsumer out, Vec3 a0, Vec3 a1, Vec3 b1, Vec3 b0, float v0, float v1, float[] col) {
        entityVertex(out, a0, 0f, v0, col);
        entityVertex(out, a1, 1f, v0, col);
        entityVertex(out, b1, 1f, v1, col);
        entityVertex(out, b0, 0f, v1, col);
    }

    private static void entityVertex(VertexConsumer out, Vec3 p, float u, float v, float[] col) {
        out.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(col[0], col[1], col[2], 1f).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    /** The eclipse's radius now: its disc, large while it hangs high, back to the heart's size laid bare. */
    static double eclipseRadius(HollowHeliarch h, double t) {
        return ECLIPSE_R * HeliarchPose.eclipseScale(h.poseInput(), t);
    }

    private static void quadUv(VertexConsumer out, Vec3 a0, Vec3 a1, Vec3 b1, Vec3 b0, float v0, float v1, float[] col, float alpha) {
        ShardDraw.vertex(out, IDENTITY, a0, 0f, v0, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, a1, 1f, v0, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b1, 1f, v1, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, b0, 0f, v1, col[0], col[1], col[2], alpha);
    }

    /** Magenta cracks glowing on the floor along the lash's line (1.5 wide, 12 long), brighter as it comes. */
    private static void drawLashTell(Frame f, Vec3 anchor, Vec3 end, double a) {
        double u = HeliarchPose.clamp(a / HeliarchMoves.LASH_TELL);
        Vec3 a0 = f.rel(anchor.add(0, 0.07, 0));
        Vec3 a1 = f.rel(end.add(0, 0.07, 0));
        float pulse = 0.7f + 0.3f * (float) Math.sin(a * 1.1);
        floorStripe(solid(), a0, a1, HeliarchMoves.LASH_WIDTH, rgb(0x2A0A30), 0.55f);
        floorStripe(add(GLOW), a0, a1, HeliarchMoves.LASH_WIDTH, rgb(MAGENTA), (float) (0.25 + 0.5 * u) * pulse);
        // both edges as solid magenta lines, 3 px or more, so the line's width reads
        Vec3 d = new Vec3(a1.x - a0.x, 0, a1.z - a0.z).normalize();
        Vec3 side = new Vec3(-d.z, 0, d.x).scale(HeliarchMoves.LASH_WIDTH / 2.0);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 e0 = a0.add(side.scale(s));
            Vec3 e1 = a1.add(side.scale(s));
            TelegraphDraw.line(solid(), f.camera(), e0, e1, TelegraphDraw.screenWidth(e0, 3.5, 0.05), TelegraphDraw.screenWidth(e1, 3.5, 0.05),
                    rgb(MAGENTA), 0.95f);
        }
        TelegraphDraw.line(solid(), f.camera(), a1.subtract(side), a1.add(side), TelegraphDraw.screenWidth(a1, 3.5, 0.05),
                TelegraphDraw.screenWidth(a1, 3.5, 0.05), rgb(MAGENTA), 0.95f);
        mark("lash tell");
    }

    /** Each standing monolith's integrity: three suns high on both its faces, lit for each pip left. */
    private static void drawPips(Frame f) {
        int[] pips = f.h().pips();
        for (HeliarchArena.Monolith m : HeliarchArena.monoliths()) {
            int left = pips[m.index()];
            if (left <= 0) {
                continue;
            }
            Vec3 mid = m.middle();
            Vec3 along = m.alongX() ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            Vec3 normal = m.alongX() ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < HeliarchArena.PIPS; k++) {
                    Vec3 p = mid.add(along.scale((k - 1) * 0.9)).add(0, 3.3, 0).add(normal.scale(side * 0.53));
                    Vec3 c = f.rel(p);
                    boolean lit = k < left;
                    Vec3 r = along.scale(0.36);
                    Vec3 u = new Vec3(0, 0.36, 0);
                    if (lit) {
                        ShardDraw.quad(add(SUN), IDENTITY, c, r, u, 1f, 0.85f, 0.4f, 0.95f);
                        ShardDraw.quad(add(GLOW), IDENTITY, c, r.scale(2.0), u.scale(2.0), 1f, 0.7f, 0.25f, 0.35f);
                    } else {
                        ShardDraw.quad(shade(SUN), IDENTITY, c, r, u, 0.05f, 0.02f, 0.08f, 0.85f);
                    }
                }
            }
        }
        mark("pips");
    }

    /**
     * The Eclipse Beam. Warning (40 ticks): the eclipse's rim flares on the side the beam starts, a dark ray reaches
     * across the floor there. Sweep (60 ticks): a wedge of eclipse-light turns half round the arena at chest height,
     * stopped by every monolith and pillar (the shadow behind them is the only safe place), its leading edge white-hot.
     */
    private static void drawBeam(Frame f, double a) {
        HollowHeliarch h = f.h();
        double start = h.actionAngle();
        int dir = h.beamDir();
        Vec3 core = h.core(f.time());
        if (a < HeliarchMoves.BEAM_TELL) {
            double u = HeliarchPose.clamp(a / HeliarchMoves.BEAM_TELL);
            Vec3 lead = core.add(HeliarchArena.dir(start).scale(1.4));
            TelegraphDraw.glow(add(GLOW), f.camera(), f.rel(lead), (float) (1.5 + 3.0 * u), rgb(PALE_GOLD), (float) (0.4 + 0.6 * u));
            TelegraphDraw.glow(add(CORONA), f.camera(), f.rel(core), (float) (2.6 + 1.2 * u), rgb(EMBER), (float) (0.5 * u));
            Vec3 c0 = f.rel(HeliarchArena.CENTRE.add(0, 0.07, 0));
            float yaw = HeliarchArena.yawOf(start);
            TelegraphDraw.sector(add(GLOW), c0, 2.0, reach(h, start), yaw, -3, 3, 2, rgb(VIOLET), (float) (0.2 + 0.4 * u));
            mark("beam tell");
            return;
        }
        double t = a - HeliarchMoves.BEAM_TELL;
        if (t > HeliarchMoves.BEAM_SWEEP + 6) {
            return;
        }
        double centre = EclipseCover.beamAngle(start, dir, Math.min(t, HeliarchMoves.BEAM_SWEEP));
        float fade = t > HeliarchMoves.BEAM_SWEEP ? (float) (1.0 - (t - HeliarchMoves.BEAM_SWEEP) / 6.0) : 1f;
        double half = HeliarchMoves.BEAM_WIDTH / 2.0;
        int strips = 16;
        Vec3 origin = new Vec3(HeliarchArena.CX, HeliarchArena.FLOOR, HeliarchArena.CZ);
        for (int k = 0; k < strips; k++) {
            double a0 = centre - half + HeliarchMoves.BEAM_WIDTH * k / strips;
            double a1 = centre - half + HeliarchMoves.BEAM_WIDTH * (k + 1) / strips;
            double mid = (a0 + a1) / 2;
            double r = reach(h, mid);
            Vec3 b0 = origin.add(HeliarchArena.dir(a0).scale(1.5));
            Vec3 b1 = origin.add(HeliarchArena.dir(a1).scale(1.5));
            Vec3 e0 = origin.add(HeliarchArena.dir(a0).scale(r));
            Vec3 e1 = origin.add(HeliarchArena.dir(a1).scale(r));
            // a wall of eclipse-light from the floor to over the head, and the floor under it scorched
            double lead = dir > 0 ? (k + 0.5) / strips : 1.0 - (k + 0.5) / strips;
            float[] col = lead > 0.8 ? rgb(PALE_GOLD) : lead > 0.55 ? rgb(0xD8B0FF) : rgb(VIOLET);
            float al = (float) Math.min(1.0, (0.5 + 0.6 * lead) * fade);
            wall(shade(GLOW), f, b0, b1, e0, e1, 4.5, rgb(VOID), 0.4f * fade);
            wall(add(GLOW), f, b0, b1, e0, e1, 4.5, col, al);
            floorQuad(add(GLOW), f, b0, b1, e0, e1, rgb(VIOLET), 0.5f * fade);
            // the eclipse's gaze: a fan of dark light from it, high over the pillars, down onto the wedge
            Vec3 cr = f.rel(core);
            Vec3 f0 = f.rel(e0.add(0, 1.0, 0));
            Vec3 f1 = f.rel(e1.add(0, 1.0, 0));
            ShardDraw.vertex(add(GLOW), IDENTITY, cr, 0.5f, 0.5f, col[0], col[1], col[2], 0.05f * fade);
            ShardDraw.vertex(add(GLOW), IDENTITY, cr, 0.5f, 0.5f, col[0], col[1], col[2], 0.05f * fade);
            ShardDraw.vertex(add(GLOW), IDENTITY, f1, 0.5f, 0.5f, col[0], col[1], col[2], 0.22f * fade);
            ShardDraw.vertex(add(GLOW), IDENTITY, f0, 0.5f, 0.5f, col[0], col[1], col[2], 0.22f * fade);
        }
        // a faint penumbra either side, so the wedge's edge reads against the floor
        Vec3 c0 = f.rel(HeliarchArena.CENTRE.add(0, 0.07, 0));
        TelegraphDraw.sector(add(GLOW), c0, 1.5, 30.0, HeliarchArena.yawOf(centre), -half - 10, -half, 6, rgb(VIOLET), 0.15f * fade);
        TelegraphDraw.sector(add(GLOW), c0, 1.5, 30.0, HeliarchArena.yawOf(centre), half, half + 10, 6, rgb(VIOLET), 0.15f * fade);
        // the leading edge: a white-hot ray
        double edge = centre + dir * half;
        Vec3 l0 = f.rel(core);
        Vec3 l1 = f.rel(origin.add(HeliarchArena.dir(edge).scale(reach(h, edge))).add(0, 1.2, 0));
        TelegraphDraw.ribbon(add(BEAM), f.camera(), l0, l1, 1.4, rgb(WHITE), 0.95f * fade);
        // its leading edge also stands as a wall of white light, so it reads at eye level
        double r = reach(h, edge);
        Vec3 e0 = origin.add(HeliarchArena.dir(edge).scale(1.5));
        Vec3 e1 = origin.add(HeliarchArena.dir(edge).scale(r));
        wall(add(GLOW), f, e0, e0, e1, e1, 4.5, rgb(PALE_GOLD), 0.8f * fade);
        mark("beam");
    }

    /** How far the beam reaches along a compass angle: to the first monolith or pillar in the way, else past the rim. */
    private static double reach(HollowHeliarch h, double angle) {
        List<EclipseCover.Blocker> blockers = EclipseCover.blockers(h.pips(), h.pillarsDown());
        Vec3 d = HeliarchArena.dir(angle);
        double lo = 0;
        double hi = 31;
        boolean blocked = false;
        for (EclipseCover.Blocker b : blockers) {
            if (b.crosses(HeliarchArena.CX, HeliarchArena.CZ, HeliarchArena.CX + d.x * hi, HeliarchArena.CZ + d.z * hi)) {
                blocked = true;
                break;
            }
        }
        if (!blocked) {
            return hi;
        }
        // bisect to the first blocker
        for (int it = 0; it < 12; it++) {
            double m = (lo + hi) / 2;
            boolean any = false;
            for (EclipseCover.Blocker b : blockers) {
                if (b.crosses(HeliarchArena.CX, HeliarchArena.CZ, HeliarchArena.CX + d.x * m, HeliarchArena.CZ + d.z * m)) {
                    any = true;
                    break;
                }
            }
            if (any) {
                hi = m;
            } else {
                lo = m;
            }
        }
        return hi;
    }

    private static void wall(VertexConsumer out, Frame f, Vec3 b0, Vec3 b1, Vec3 e0, Vec3 e1, double height, float[] col, float alpha) {
        Vec3 up = new Vec3(0, height, 0);
        Vec3 m0 = b0.add(e0).scale(0.5);
        Vec3 m1 = b1.add(e1).scale(0.5);
        // one standing quad along each edge of the strip
        for (Vec3[] pair : new Vec3[][] {{b0, e0}, {b1, e1}, {m0, m1}}) {
            Vec3 p0 = f.rel(pair[0]);
            Vec3 p1 = f.rel(pair[1]);
            ShardDraw.vertex(out, IDENTITY, p0, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p1, 0.5f, 0.5f, col[0], col[1], col[2], alpha);
            ShardDraw.vertex(out, IDENTITY, p1.add(up), 0.5f, 0.05f, col[0], col[1], col[2], alpha * 0.2f);
            ShardDraw.vertex(out, IDENTITY, p0.add(up), 0.5f, 0.05f, col[0], col[1], col[2], alpha * 0.2f);
        }
    }

    private static void floorQuad(VertexConsumer out, Frame f, Vec3 b0, Vec3 b1, Vec3 e0, Vec3 e1, float[] col, float alpha) {
        Vec3 lift = new Vec3(0, 0.07, 0);
        ShardDraw.vertex(out, IDENTITY, f.rel(b0).add(lift), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, f.rel(e0).add(lift), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, f.rel(e1).add(lift), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
        ShardDraw.vertex(out, IDENTITY, f.rel(b1).add(lift), 0.5f, 0.5f, col[0], col[1], col[2], alpha);
    }

    /**
     * Nova's channel: the Corona Shield, a shell of gold light round the eclipse as bright as the shield is whole; a
     * gold ring every second closing in from the rim on the throne, the channel's clock.
     */
    private static void drawNova(Frame f, double a) {
        HollowHeliarch h = f.h();
        Vec3 core = f.rel(h.core(f.time()));
        float shield = h.shield();
        double u = HeliarchPose.clamp(a / HeliarchMoves.NOVA_CHANNEL);
        double r = 2.2 + 0.8 * u;
        TelegraphDraw.glow(add(RING), f.camera(), core, (float) (r * 1.7 + 0.4 * Math.sin(a * 0.3)), rgb(GOLD), 0.4f + 0.6f * shield);
        TelegraphDraw.glow(add(GLOW), f.camera(), core, (float) (r * 2.6), rgb(PALE_GOLD), (0.25f + 0.35f * (float) u) * shield);

        // the clock: one ring a second, closing from the rim to the throne; faster and redder near the end
        double sec = (a % 20) / 20.0;
        double ringR = 26.0 * (1.0 - sec);
        Vec3 c0 = f.rel(HeliarchArena.CENTRE.add(0, 0.07, 0));
        int col = u > 0.75 ? RED : GOLD;
        TelegraphDraw.sector(add(GLOW), c0, Math.max(0, ringR - 0.5), ringR + 0.5, 0f, 0, 360, 64, rgb(col), 0.6f);
        TelegraphDraw.sector(solid(), c0, Math.max(0, ringR - 0.15), ringR + 0.15, 0f, 0, 360, 64, rgb(col), 0.9f);
        mark("nova");
    }

    // ------------------------------------------------------------------ the Collapse

    private static void drawCollapse(Frame f) {
        HollowHeliarch h = f.h();
        if (h.state() != State.COLLAPSE || h.collapseStart() == Long.MIN_VALUE) {
            return;
        }
        CollapseSchedule c = new CollapseSchedule(h.side());
        double t = f.time() - h.collapseStart();
        for (CollapseSchedule.Crack k : c.cracks()) {
            if (t < k.crack() || t >= k.fall()) {
                continue;
            }
            double u = (t - k.crack()) / (k.fall() - k.crack());
            drawCrackedSegment(f, k.ring(), k.segment(), u);
        }
    }

    /**
     * A segment about to fall (100 ticks): bright jagged cracks run across it, its seams and edges burn, a low wall of
     * heat stands on its outer edge, and it all flickers, faster and redder as the fall comes; dust rises from it (tick).
     */
    private static void drawCrackedSegment(Frame f, SanctumArena.Ring ring, int seg, double u) {
        Vec3 c0 = f.rel(HeliarchArena.CENTRE.add(0, 0.08, 0));
        double rate = 0.35 + 1.4 * u;
        float flick = (float) (0.55 + 0.45 * Math.abs(Math.sin(f.time() * rate)));
        if (u > 0.7 && Math.floorMod((long) (f.time() * 1.5), 3) == 0) {
            flick *= 0.4f; // the last seconds stutter
        }
        float yaw = HeliarchArena.yawOf(45.0 * seg);
        float[] hot = rgb(u > 0.7 ? RED : EMBER);
        float[] white = rgb(u > 0.7 ? 0xFFB08A : 0xFFE3A0);
        TelegraphDraw.sector(add(GLOW), c0, ring.inner + 0.2, ring.outer, yaw, 0, 45, 12, hot, (float) (0.08 + 0.17 * u) * flick);
        TelegraphDraw.sector(solid(), c0, ring.outer - 0.3, ring.outer + 0.05, yaw, 0, 45, 12, hot, 0.95f * flick);
        TelegraphDraw.sector(solid(), c0, ring.inner - 0.05, ring.inner + 0.3, yaw, 0, 45, 12, hot, 0.95f * flick);
        TelegraphDraw.sector(solid(), c0, ring.inner, ring.outer, yaw, -0.8, 0.8, 1, hot, 0.95f * flick);
        TelegraphDraw.sector(solid(), c0, ring.inner, ring.outer, yaw, 44.2, 45.8, 1, hot, 0.95f * flick);
        TelegraphDraw.curtain(add(GLOW), c0, ring.outer, yaw, 0, 45, 12, 1.4 + 2.0 * u, hot, 0.45f * flick);
        // the cracks: two long ones running the segment's length from seam to seam, jagged, and short ones branching
        // off them toward its edges; the same for a segment every time
        RandomSource r = RandomSource.create(ring.ordinal() * 131L + seg * 17L + 7L);
        double depth = ring.outer - ring.inner;
        for (int k = 0; k < 2; k++) {
            double rad = ring.inner + depth * (k == 0 ? 0.33 : 0.66) + (r.nextDouble() - 0.5) * 0.4;
            double ang = 45.0 * seg + 1.0;
            Vec3 p = HeliarchArena.at(ang, rad).add(0, 0.09, 0);
            while (ang < 45.0 * seg + 44.0) {
                ang += 1.8 + 1.6 * r.nextDouble();
                rad = Math.max(ring.inner + 0.35, Math.min(ring.outer - 0.35, rad + (r.nextDouble() - 0.5) * 0.9));
                Vec3 q = HeliarchArena.at(Math.min(ang, 45.0 * seg + 44.0), rad).add(0, 0.09, 0);
                crackLine(f, p, q, hot, white, flick, 1.0);
                if (r.nextInt(3) == 0) {
                    // a branch toward the nearer edge
                    double toward = rad - ring.inner < ring.outer - rad ? ring.inner + 0.2 : ring.outer - 0.2;
                    Vec3 bq = HeliarchArena.at(ang + (r.nextDouble() - 0.5) * 3.0, toward).add(0, 0.09, 0);
                    crackLine(f, q, bq, hot, white, flick, 0.7);
                }
                p = q;
            }
        }
        mark("collapse crack");
    }

    /** One stroke of a crack on the floor (world points): a white-hot line in a hot glow, 3 px or more. */
    private static void crackLine(Frame f, Vec3 p, Vec3 q, float[] hot, float[] white, float flick, double weight) {
        Vec3 a = f.rel(p);
        Vec3 b = f.rel(q);
        TelegraphDraw.line(add(GLOW), f.camera(), a, b, TelegraphDraw.screenWidth(a, 14 * weight, 0.25), TelegraphDraw.screenWidth(b, 14 * weight, 0.25),
                hot, 0.75f * flick);
        TelegraphDraw.line(solid(), f.camera(), a, b, TelegraphDraw.screenWidth(a, 4.5 * weight, 0.07), TelegraphDraw.screenWidth(b, 4.5 * weight, 0.07),
                white, flick);
    }

    /** The falling segments: their blocks as they were, dropping and turning away into the Breach for three seconds. */
    private static void drawFalls(Camera camera, double time, PoseStack stack) {
        Iterator<Falling> it = FALLS.iterator();
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        while (it.hasNext()) {
            Falling fl = it.next();
            double s = time - fl.start();
            if (s > 70 || s < 0) {
                it.remove();
                continue;
            }
            double drop = 0.5 * 0.045 * s * s;
            double tilt = Math.toRadians(Math.min(35.0, s * 0.6));
            Vec3 out = new Vec3(fl.middle().x - HeliarchArena.CX, 0, fl.middle().z - HeliarchArena.CZ).normalize();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            PoseStack ps = new PoseStack();
            ps.mulPose(stack.last().pose());
            for (int i = 0; i < fl.blocks().size(); i++) {
                BlockPos p = fl.blocks().get(i);
                BlockState st = fl.states().get(i);
                if (st.isAir()) {
                    continue;
                }
                ps.pushPose();
                // turn the wedge away from the middle round its outer edge as it drops
                Vec3 rel = Vec3.atLowerCornerOf(p).subtract(cam);
                Vec3 pivot = fl.middle().subtract(cam);
                ps.translate(pivot.x, pivot.y - drop, pivot.z);
                ps.mulPose(new org.joml.Quaternionf().rotationAxis((float) tilt, (float) -out.z, 0f, (float) out.x));
                ps.translate(rel.x - pivot.x, rel.y - pivot.y, rel.z - pivot.z);
                mc.getBlockRenderer().renderSingleBlock(st, ps, buffers, LightTexture.pack(8, 12), OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            buffers.endBatch();
            mark("falling segment");
        }
    }

    /** Solar Rain's gold circles filling for 20 ticks, a column of light coming down in the last few, the impacts. */
    private static void drawRain(Camera camera, double time) {
        Iterator<Rain> it = RAIN.iterator();
        Vec3 cam = camera.getPosition();
        while (it.hasNext()) {
            Rain r = it.next();
            double left = r.land() - time;
            if (left < -8) {
                it.remove();
                continue;
            }
            for (Vec3 c : r.circles()) {
                Vec3 at = c.add(0, 0.07, 0).subtract(cam);
                if (left >= 0) {
                    double u = 1.0 - HeliarchPose.clamp(left / HeliarchMoves.RAIN_TELL);
                    TelegraphDraw.sector(BUFFERS.getBuffer(ShardDraw.solid()), at, HeliarchMoves.RAIN_RADIUS - 0.15, HeliarchMoves.RAIN_RADIUS + 0.05,
                            0f, 0, 360, 32, rgb(GOLD), 0.95f);
                    TelegraphDraw.sector(BUFFERS.getBuffer(ShardDraw.solid()), at, 0, HeliarchMoves.RAIN_RADIUS * u, 0f, 0, 360, 32, rgb(GOLD), 0.3f);
                    TelegraphDraw.curtain(add(GLOW), at, HeliarchMoves.RAIN_RADIUS, 0f, 0, 360, 32, 1.4, rgb(GOLD), 0.35f);
                    if (left < 8) {
                        double drop = 20.0 * (left / 8.0);
                        Vec3 top = at.add(0, drop + 1.5, 0);
                        TelegraphDraw.ribbon(add(BEAM), camera, top, at.add(0, drop, 0), 1.0, rgb(PALE_GOLD), 0.9f);
                    }
                    mark("solar rain");
                } else {
                    float a = (float) (1.0 + left / 8.0);
                    TelegraphDraw.sector(add(GLOW), at, 0, HeliarchMoves.RAIN_RADIUS + 1.0, 0f, 0, 360, 32, rgb(PALE_GOLD), 0.8f * a);
                    TelegraphDraw.glow(add(GLOW), camera, at.add(0, 1.0, 0), 3.0f, rgb(WHITE), 0.7f * a);
                }
            }
        }
    }
}
