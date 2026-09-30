package com.cosmicbreach.client.fx;

import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.gear.net.SetFxPayload;
import com.cosmicbreach.gear.regalia.HymnRings;
import com.cosmicbreach.world.VesperClock;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Driftweave's and the Choir Regalia's effects, drawn for everyone the server told ({@link SetFxPayload}):
 * Drift's star dust; the Afterimage (a {@link GhostBodies} copy in star dust) appearing, repeating its wearer's
 * moves with their hit shapes traced in glints, striking and fading; Harmonics' echoes (an ivory-gold copy
 * replaying the ability where it was cast, its hit shapes, a ghost Gravity Well, a ghost blade); the Hymn of
 * Alignment's ring on the ground, turning and pulsing on Vesper's beat, with the Aligned glyph over every enemy
 * inside it.
 */
public final class SetVisuals {
    public static final int DUST_VIOLET = 0x6A5CFF;
    public static final int DUST_CYAN = 0x8FE6FF;
    public static final int DUST_WHITE = 0xE8F8FF;
    public static final int HYMN_GOLD = 0xFFD27A;
    public static final int HYMN_WHITE = 0xFFF4D6;
    private static final ResourceLocation RING = com.cosmicbreach.CosmicBreach.id("textures/fx/hymn_ring.png");
    private static final ResourceLocation GLYPH = com.cosmicbreach.CosmicBreach.id("textures/fx/aligned_glyph.png");
    private static final RandomSource RANDOM = RandomSource.create();

    /** The Afterimage standing for each wearer (by entity id). */
    private static final Map<Integer, GhostBodies.Ghost> AFTERIMAGES = new HashMap<>();
    /** The ghost wells of echoes, by wearer. */
    private static final Map<Integer, GravityWellEffect> WELLS = new HashMap<>();

    private SetVisuals() {
    }

    private static @Nullable ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static @Nullable AbstractClientPlayer owner(int id) {
        ClientLevel level = level();
        Entity e = level == null ? null : level.getEntity(id);
        return e instanceof AbstractClientPlayer p ? p : null;
    }

    public static void handle(SetFxPayload p) {
        ClientLevel level = level();
        if (level == null) {
            return;
        }
        double now = FxClock.ticks();
        AFTERIMAGES.values().removeIf(ghost -> ghost.over(now));
        switch (p.kind()) {
            case SetFxPayload.Kind.DRIFT_START -> driftStart(p.ownerId(), p.at(), p.ticks());
            case SetFxPayload.Kind.DRIFT_END -> driftEnd(p.at());
            case SetFxPayload.Kind.AFTERIMAGE -> afterimage(p.ownerId(), p.at(), p.yaw(), p.ticks());
            case SetFxPayload.Kind.AFTERIMAGE_REPEAT -> repeat(p.ownerId(), p.at(), p.yaw(), p.move(), p.ticks());
            case SetFxPayload.Kind.AFTERIMAGE_STRIKE -> strike(p.at(), p.dir(), p.value(), DUST_CYAN);
            case SetFxPayload.Kind.AFTERIMAGE_END -> {
                GhostBodies.Ghost ghost = AFTERIMAGES.remove(p.ownerId());
                if (ghost != null) {
                    ghost.fade();
                    burst(p.at().add(0, 1.0, 0), 8, DUST_CYAN, 0.08);
                }
            }
            case SetFxPayload.Kind.ECHO -> echo(p.ownerId(), p.at(), p.yaw(), p.move(), p.ticks());
            case SetFxPayload.Kind.ECHO_WELL -> {
                GravityWellEffect well = new GravityWellEffect(p.at(), p.value(), p.ticks());
                WELLS.put(p.ownerId(), well);
                WorldFx.add(well);
                GlowEffect.groundRing(p.at(), 0.4f, p.value(), HYMN_GOLD, 0.8f, 10);
            }
            case SetFxPayload.Kind.ECHO_COLLAPSE -> {
                GravityWellEffect well = WELLS.remove(p.ownerId());
                if (well != null) {
                    well.collapse();
                }
                GlowEffect.flash(p.at().add(0, 1.0, 0), 1.6f, HYMN_WHITE, 0.9f, 6);
                GlowEffect.groundRing(p.at(), 0.3f, p.value() * 1.3f, HYMN_GOLD, 1.0f, 9);
            }
            case SetFxPayload.Kind.ECHO_THROW -> WorldFx.add(new GhostBlade(p.at(), p.dir(), p.value(), p.ticks()));
            case SetFxPayload.Kind.ECHO_STRIKE -> strike(p.at(), p.dir(), p.value(), HYMN_GOLD);
            case SetFxPayload.Kind.HYMN -> hymn(level, p.ownerId(), p.at(), p.value(), p.ticks());
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Drift

    private static void driftStart(int ownerId, Vec3 feet, int ticks) {
        ClientLevel level = level();
        GlowEffect.groundRing(feet, 0.3f, 2.6f, DUST_CYAN, 0.9f, 12);
        GlowEffect.flash(feet.add(0, 1.0, 0), 1.1f, DUST_WHITE, 0.6f, 6);
        rising(feet, 18, 1.2);
        Entity owner = level == null ? null : level.getEntity(ownerId);
        if (owner != null) {
            WorldFx.follow(owner, ticks, e -> {
                if (e.tickCount % 2 == 0) {
                    Vec3 at = e.position().add((RANDOM.nextDouble() - 0.5) * 0.7, RANDOM.nextDouble() * 1.6, (RANDOM.nextDouble() - 0.5) * 0.7);
                    dust(at, new Vec3(0, -0.01 - RANDOM.nextDouble() * 0.02, 0), 0.07f, 14);
                }
            });
        }
    }

    private static void driftEnd(Vec3 feet) {
        GlowEffect.groundRing(feet, 1.8f, 0.4f, DUST_VIOLET, 0.6f, 8);
        for (int i = 0; i < 10; i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            dust(feet.add(out.scale(0.5)).add(0, 0.2 + RANDOM.nextDouble(), 0), out.scale(0.03).add(0, -0.04, 0), 0.08f, 12);
        }
    }

    // ------------------------------------------------------------------ the Afterimage

    private static void afterimage(int ownerId, Vec3 feet, float yaw, int ticks) {
        AbstractClientPlayer owner = owner(ownerId);
        GhostBodies.Ghost old = AFTERIMAGES.remove(ownerId);
        if (old != null) {
            old.fade();
        }
        burst(feet.add(0, 1.0, 0), 14, DUST_WHITE, 0.12);
        GlowEffect.groundRing(feet, 0.3f, 1.6f, DUST_CYAN, 0.8f, 9);
        if (owner == null) {
            return;
        }
        GhostBodies.Ghost ghost = GhostBodies.spawn(owner, feet, yaw, GhostBodies.Style.STARDUST, ticks);
        if (ghost == null) {
            return;
        }
        PlayerAnimations.State state = PlayerAnimations.state(owner);
        if (state.animation() != null) {
            ghost.hold(state.animation(), Math.max(0f, state.time()), state.mirrored());
        }
        AFTERIMAGES.put(ownerId, ghost);
    }

    private static void repeat(int ownerId, Vec3 feet, float yaw, ResourceLocation moveId, int ticks) {
        GhostBodies.Ghost ghost = AFTERIMAGES.get(ownerId);
        MoveDef def = CombatData.client().move(moveId);
        if (ghost == null || def == null) {
            return;
        }
        ghost.face(yaw).play(def.animation(), ticks, false).lastFor(ticks + 2);
        int startup = def.timing().startup();
        WorldFx.after(startup, () -> sweep(ghost.body(), def, DUST_CYAN, DUST_WHITE));
    }

    // ------------------------------------------------------------------ echoes

    private static void echo(int ownerId, Vec3 feet, float yaw, ResourceLocation moveId, int ticks) {
        AbstractClientPlayer owner = owner(ownerId);
        MoveDef def = CombatData.client().move(moveId);
        GlowEffect.groundRing(feet, 0.2f, 1.8f, HYMN_GOLD, 0.9f, 10);
        burst(feet.add(0, 1.1, 0), 10, HYMN_WHITE, 0.1);
        if (owner == null || def == null) {
            return;
        }
        GhostBodies.Ghost ghost = GhostBodies.spawn(owner, feet, yaw, GhostBodies.Style.CHOIR, ticks);
        if (ghost == null) {
            return;
        }
        ghost.play(def.animation(), ticks, false);
        WorldFx.after(def.timing().startup(), () -> {
            CombatEffects.moveActive(ghost.body(), def);
            sweep(ghost.body(), def, HYMN_GOLD, HYMN_WHITE);
        });
    }

    // ------------------------------------------------------------------ hit shapes, strikes, dust

    /** A move's hitbox traced in glints from where {@code body} stands and faces: its arc, line or sphere. */
    static void sweep(Entity body, MoveDef def, int color, int bright) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        Vec3 chest = body.position().add(0, 0.55 * body.getBbHeight(), 0);
        float yaw = body.getYRot();
        HitShape shape = def.hitbox();
        int n = FxBudget.count(18, chest, false);
        for (int i = 0; i < n; i++) {
            double t = (i + RANDOM.nextDouble() * 0.5) / Math.max(1, n);
            Vec3 at;
            if (shape instanceof HitShape.Arc arc) {
                double half = Math.toRadians(Math.min(360.0, arc.angle()) / 2.0);
                double a = Math.toRadians(-yaw) + (-half + 2 * half * t);
                double r = arc.radius() * (0.85 + RANDOM.nextDouble() * 0.15);
                at = chest.add(Math.sin(a) * r, (RANDOM.nextDouble() - 0.5) * 0.3, Math.cos(a) * r);
            } else if (shape instanceof HitShape.Line line) {
                Vec3 f = HitShape.forward(yaw);
                Vec3 side = new Vec3(-f.z, 0, f.x);
                at = chest.add(f.scale(line.length() * t)).add(side.scale((RANDOM.nextDouble() - 0.5) * line.width()));
            } else if (shape instanceof HitShape.Sphere sphere) {
                double a = t * Mth.TWO_PI;
                double r = Math.max(0.6, sphere.radius());
                at = body.position().add(0, 0.3, 0).add(Math.cos(a) * r, 0, Math.sin(a) * r);
            } else {
                at = chest.add(HitShape.forward(yaw).scale(1.5 * t));
            }
            FxBudget.spawn(FxParticles.glint(level, at).velocity(new Vec3(0, 0.02, 0)).drag(0.9f)
                    .size(0.14f, 0.02f).life(8 + RANDOM.nextInt(4)).color(i % 3 == 0 ? bright : color).fade(0.1f, 1.2f).spin(0.15f));
        }
    }

    private static void strike(Vec3 at, Vec3 dir, float impact, int color) {
        CombatEffects.hit(at, dir, Math.max(2.0, impact), false);
        burst(at, 6, color, 0.1);
    }

    private static void burst(Vec3 at, int count, int color, double speed) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        int n = FxBudget.count(count, at, false);
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6, RANDOM.nextGaussian()).normalize().scale(speed * (0.5 + RANDOM.nextDouble()));
            FxBudget.spawn(FxParticles.glint(level, at).velocity(v).drag(0.82f).size(0.16f, 0.02f).life(8 + RANDOM.nextInt(5))
                    .color(color).fade(0.05f, 1.2f).spin(0.2f));
        }
    }

    private static void rising(Vec3 feet, int count, double radius) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        int n = FxBudget.count(count, feet, false);
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI;
            double r = radius * Math.sqrt(RANDOM.nextDouble());
            Vec3 at = feet.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            dust(at, new Vec3(0, 0.05 + RANDOM.nextDouble() * 0.06, 0), 0.1f, 18 + RANDOM.nextInt(8));
        }
    }

    private static void dust(Vec3 at, Vec3 velocity, float size, int life) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        int color = switch (RANDOM.nextInt(3)) {
            case 0 -> DUST_VIOLET;
            case 1 -> DUST_CYAN;
            default -> DUST_WHITE;
        };
        FxBudget.spawn(FxParticles.glint(level, at).velocity(velocity).drag(0.95f).size(size, 0.01f).life(life).color(color)
                .fade(0.15f, 1.0f).spin(0.1f));
    }

    // ------------------------------------------------------------------ the Hymn of Alignment

    private static void hymn(ClientLevel level, int ownerId, Vec3 centre, float radius, int ticks) {
        HymnRings.add(level, new HymnRings.Ring(level.dimension(), centre, radius, level.getGameTime() + ticks, ownerId));
        WorldFx.add(new HymnRing(level, centre, radius, ticks));
        GlowEffect.flash(centre.add(0, 1.2, 0), 1.6f, HYMN_WHITE, 0.7f, 8);
    }

    /**
     * The ring on the ground: the ring texture lying flat, turning slowly, gold, flaring on every beat of Vesper; a
     * fainter second ring turning the other way; motes rising at its edge; the Aligned glyph over each enemy inside.
     */
    static final class HymnRing implements WorldFx.Effect {
        private final ClientLevel level;
        private final Vec3 centre;
        private final float radius;
        private final int life;
        private final double born = FxClock.ticks();

        HymnRing(ClientLevel level, Vec3 centre, float radius, int life) {
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.life = Math.max(1, life);
        }

        @Override
        public boolean tick() {
            double age = FxClock.ticks() - born;
            if (age % 3 == 0 && FxParticles.ready()) {
                double a = RANDOM.nextDouble() * Mth.TWO_PI;
                Vec3 at = centre.add(Math.cos(a) * radius * 0.93, 0.1, Math.sin(a) * radius * 0.93);
                FxBudget.spawn(FxParticles.glint(level, at).velocity(new Vec3(0, 0.06, 0)).drag(0.96f).size(0.12f, 0.01f)
                        .life(16).color(RANDOM.nextBoolean() ? HYMN_GOLD : HYMN_WHITE).fade(0.2f, 1.0f).spin(0.1f));
            }
            return age < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - born;
            if (age < 0 || age >= life) {
                return;
            }
            float grow = (float) Math.min(1.0, age / 5.0);
            float fade = (float) Math.min(1.0, (life - age) / 10.0);
            float beat = VesperClock.pulse(level.getGameTime(), f.partialTick());
            float strength = (0.55f + 0.45f * beat) * fade;
            float[] gold = WorldFx.rgb(HYMN_GOLD);
            float[] white = WorldFx.rgb(HYMN_WHITE);
            Vec3 c = f.relative(centre).add(0, 0.06, 0);
            VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(RING));
            double spin = age * 0.012;
            flatQuad(out, c, radius * (0.85f + 0.15f * grow), spin, gold, 0.95f * strength);
            flatQuad(out, c.add(0, 0.01, 0), radius * 0.72f * grow, -spin * 1.7, white, 0.45f * strength);
            // over every enemy inside: the Aligned glyph, turning to the camera, bobbing on the beat
            VertexConsumer glyph = f.buffers().getBuffer(FxRenderTypes.additive(GLYPH));
            HymnRings.Ring ring = new HymnRings.Ring(level.dimension(), centre, radius, Long.MAX_VALUE, -1);
            for (LivingEntity enemy : HymnRings.enemiesIn(level, ring)) {
                Vec3 top = enemy.getPosition(f.partialTick()).add(0, enemy.getBbHeight() + 0.45 + 0.06 * beat, 0);
                WorldFx.billboard(glyph, f.camera(), f.relative(top), 0.28f, gold[0], gold[1], gold[2], (0.7f + 0.3f * beat) * fade);
            }
        }

        private static void flatQuad(VertexConsumer out, Vec3 c, float half, double angle, float[] rgb, float a) {
            double cos = Math.cos(angle) * half;
            double sin = Math.sin(angle) * half;
            Vec3 u = new Vec3(cos, 0, sin);
            Vec3 v = new Vec3(-sin, 0, cos);
            WorldFx.vertex(out, c.subtract(u).subtract(v), 0f, 0f, rgb[0], rgb[1], rgb[2], a);
            WorldFx.vertex(out, c.subtract(u).add(v), 0f, 1f, rgb[0], rgb[1], rgb[2], a);
            WorldFx.vertex(out, c.add(u).add(v), 1f, 1f, rgb[0], rgb[1], rgb[2], a);
            WorldFx.vertex(out, c.add(u).subtract(v), 1f, 0f, rgb[0], rgb[1], rgb[2], a);
        }
    }

    // ------------------------------------------------------------------ the echo's thrown blade

    /** A ghost sickle flying from {@code from} along {@code dir}: a spinning gold glint with a fading streak. */
    static final class GhostBlade implements WorldFx.Effect {
        private final Vec3 from;
        private final Vec3 dir;
        private final double speed;
        private final int life;
        private final double born = FxClock.ticks();

        GhostBlade(Vec3 from, Vec3 dir, float range, int ticks) {
            this.from = from;
            this.dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
            this.life = Math.max(1, ticks);
            this.speed = range / (double) this.life;
        }

        @Override
        public boolean tick() {
            double age = FxClock.ticks() - born;
            ClientLevel level = level();
            if (level != null && FxParticles.ready()) {
                Vec3 at = from.add(dir.scale(Math.min(age, life) * speed));
                FxBudget.spawn(FxParticles.glint(level, at).size(0.3f, 0.04f).life(5).color(HYMN_WHITE).fade(0f, 1.2f).spin(0.6f));
                FxBudget.spawn(FxParticles.spark(level, at).streakAlong(dir, 1.2f).size(0.05f, 0.01f).life(6).color(HYMN_GOLD));
            }
            return age < life;
        }
    }

    /** Forget everything (leaving a world). */
    public static void clear() {
        AFTERIMAGES.clear();
        WELLS.clear();
        HymnRings.clearClient();
        GhostBodies.clear();
    }
}
