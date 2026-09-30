package com.cosmicbreach.client.relic;

import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.fx.ClientMoveEffect;
import com.cosmicbreach.client.fx.ClientMoveEffects;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.relic.Relics;
import com.cosmicbreach.relic.lastlight.LastLight;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What Last Light looks like beyond its blade (GDD 7.3): the golden sunrise that flares on every parry (a burst, a sunburst
 * of rays and a horizon streak, the light rising off it as it fades), Daybreak's counter-slash (a 270 degree crescent of
 * dawn light sweeping round the bearer out to 5 blocks), Dawnguard's stance (a slow ring of light at the feet), the
 * Sunspear's lance of light (brighter for every charge it spent), the Sunfall's sunburst on the ground, and the Sunlight
 * charges themselves: a small sun per charge circling the bearer. The glaive's gems show them too (its item property).
 */
final class LastLightFx {
    private static final RandomSource RANDOM = RandomSource.create();
    /** The Sunlight charges each player holds, by entity id (the server tells). */
    static final Map<Integer, Integer> CHARGES = new HashMap<>();
    /** The charges each player's last release spent, by entity id, for its lance. */
    private static final Map<Integer, Integer> SPENT = new HashMap<>();
    private static final Map<Integer, Suns> SUNS = new HashMap<>();

    private LastLightFx() {
    }

    static void register() {
        ClientMoveEffects.register(LastLight.DAWNGUARD, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                moment(player, m);
            }
        });
        ClientMoveEffects.register(LastLight.SUNLIGHT, new ClientMoveEffect() {
            @Override
            public void active(Player player, MoveDef def, MoveEffect effect) {
                int spent = SPENT.getOrDefault(player.getId(), 0);
                SPENT.remove(player.getId());
                WorldFx.add(new Lance(player, effect.param("length", 6.0), spent));
            }
        });
        ClientMoveEffects.register(LastLight.SUNFALL, new ClientMoveEffect() {
            @Override
            public boolean landed(Player player, MoveDef def, MoveEffect effect, double fallBlocks) {
                sunfall(player.position(), effect.param("radius", 3.0), fallBlocks);
                return true;
            }
        });
    }

    static void clear() {
        CHARGES.clear();
        SPENT.clear();
        SUNS.clear();
    }

    private static @Nullable ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    /** The server's word about a player's Dawnguard, parries and Sunlight. */
    private static void moment(@Nullable Player player, MoveEffectPayload m) {
        switch (m.stage()) {
            case LastLight.STANCE -> {
                if (player != null) {
                    WorldFx.add(new StanceRing(player, m.ticks()));
                }
            }
            case LastLight.FLARE -> {
                WorldFx.add(new Flare(m.at()));
                sparks(m.at(), 16, 0.22);
                Player me = Minecraft.getInstance().player;
                if (me != null && player == me) {
                    ClientCombat.feelSlam(0.12, 0.6);
                }
            }
            case LastLight.DAYBREAK -> {
                WorldFx.add(new Daybreak(m.at(), m.value()));
                Player me = Minecraft.getInstance().player;
                if (me != null && player == me) {
                    ClientCombat.feelSlam(0.2, 1.2);
                }
            }
            case LastLight.CHARGES -> {
                int now = Math.round(m.value());
                if (now > 0) {
                    CHARGES.put(m.entityId(), now);
                    if (player != null && !SUNS.containsKey(player.getId())) {
                        Suns suns = new Suns(player);
                        SUNS.put(player.getId(), suns);
                        WorldFx.add(suns);
                    }
                } else {
                    CHARGES.remove(m.entityId());
                }
            }
            case LastLight.SPEND -> {
                SPENT.put(m.entityId(), Math.round(m.value()));
                WorldFx.add(new Flare(m.at()));
            }
            default -> {
            }
        }
    }

    /** Gold sparks thrown from {@code at}. */
    static void sparks(Vec3 at, int wanted, double speed) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        int n = FxBudget.count(wanted, at, true);
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6 + 0.5, RANDOM.nextGaussian()).normalize()
                    .scale(speed * (0.5 + RANDOM.nextDouble()));
            FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(RelicDraw.mix(RelicDraw.SUN_WHITE, RelicDraw.SUN_GOLD,
                    RANDOM.nextFloat())).size(0.035f, 0.01f).life(8 + RANDOM.nextInt(8)).gravity(0.25f).drag(0.86f).streak(1.6f, 0.08f));
        }
    }

    /** The Sunfall: a sunburst on the ground where it lands, its ring running out to the burst's edge. */
    private static void sunfall(Vec3 feet, double radius, double fall) {
        WorldFx.add(new GroundBurst(feet.add(0, 0.06, 0), (float) (radius * (0.8 + Math.min(0.4, fall * 0.04)))));
        sparks(feet.add(0, 0.3, 0), 24, 0.35);
        Player me = Minecraft.getInstance().player;
        if (me != null && me.position().distanceTo(feet) < 12) {
            ClientCombat.feelSlam(0.3 * (1.0 - me.position().distanceTo(feet) / 12.0), 1.4);
        }
    }

    // ------------------------------------------------------------------ the sunrise flare

    /** A parry's golden sunrise: a burst, turning rays and a horizon streak; the light lifts off the streak as it fades. */
    static final class Flare implements WorldFx.Effect {
        private static final int LIFE = 16;
        private final Vec3 at;
        private final double start = FxClock.ticks();

        Flare(Vec3 at) {
            this.at = at;
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < LIFE;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            double t = age / LIFE;
            if (t < 0 || t >= 1) {
                return;
            }
            float in = (float) Math.min(1.0, age / 1.2);
            float fade = (float) ((1 - t) * (1 - t));
            Vec3 centre = f.relative(at);
            float cap = (float) Math.max(0.15, centre.length() * 0.45); // a flash at the eyes stays a flash
            Vec3 risen = centre.add(0, 0.35 * (1 - Math.pow(1 - t, 2)), 0);
            float grow = (float) (1 - Math.pow(1 - t, 2.2));
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
            RelicDraw.billboard(glow, f.camera(), risen, Math.min(cap, 0.8f + 1.4f * grow), 0f, RelicDraw.SUN_GOLD, 0.75f * in * fade);
            RelicDraw.billboard(glow, f.camera(), risen, Math.min(cap, 0.35f + 0.4f * grow), 0f, RelicDraw.SUN_WHITE, in * fade);
            // the horizon: a thin streak across, where the sun rises from
            org.joml.Vector3f left = f.camera().getLeftVector();
            Vec3 l = new Vec3(left.x(), left.y(), left.z()).scale(Math.min(cap * 2.2, 1.4 + 1.6 * grow));
            Vec3 up = new Vec3(0, 0.07, 0);
            float[] c = WorldFx.rgb(RelicDraw.DAWN_ROSE);
            float a = 0.85f * in * fade;
            WorldFx.vertex(glow, centre.subtract(l).subtract(up), 0f, 1f, c[0], c[1], c[2], a);
            WorldFx.vertex(glow, centre.subtract(l).add(up), 0f, 0f, c[0], c[1], c[2], a);
            WorldFx.vertex(glow, centre.add(l).add(up), 1f, 0f, c[0], c[1], c[2], a);
            WorldFx.vertex(glow, centre.add(l).subtract(up), 1f, 1f, c[0], c[1], c[2], a);
            // each buffer is filled before the next is asked for: the frame's buffers draw one at a time
            VertexConsumer rays = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RAYS));
            RelicDraw.billboard(rays, f.camera(), risen, Math.min(cap * 1.4f, 1.0f + 1.8f * grow), (float) (age * 0.04),
                    RelicDraw.SUN_GOLD, 0.9f * in * fade);
            RelicDraw.billboard(rays, f.camera(), risen, Math.min(cap, 0.6f + 1.0f * grow), (float) (-age * 0.06 + 0.26),
                    RelicDraw.SUN_WHITE, 0.6f * in * fade);
        }
    }

    // ------------------------------------------------------------------ Daybreak

    /** The counter-slash: a crescent of dawn light sweeping 270 degrees round the bearer, out to 5 blocks, then fading. */
    static final class Daybreak implements WorldFx.Effect {
        private static final int SWEEP = 3;
        private static final int LIFE = 11;
        private final Vec3 at;
        private final double yaw;
        private final double start = FxClock.ticks();

        Daybreak(Vec3 at, float yawDeg) {
            this.at = at;
            this.yaw = Math.toRadians(yawDeg);
            ClientLevel level = level();
            if (level != null && FxParticles.ready()) {
                int n = FxBudget.count(18, at, true);
                for (int i = 0; i < n; i++) {
                    double a = yaw - Math.toRadians(135) + Math.toRadians(270) * RANDOM.nextDouble();
                    Vec3 dir = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    Vec3 p = at.add(dir.scale(3.2 + RANDOM.nextDouble() * 1.8)).add(0, RANDOM.nextGaussian() * 0.25, 0);
                    FxBudget.spawn(FxParticles.spark(level, p).velocity(dir.scale(0.12).add(0, 0.05, 0))
                            .color(RelicDraw.mix(RelicDraw.SUN_WHITE, RelicDraw.SUN_DEEP, RANDOM.nextFloat()))
                            .size(0.03f, 0.01f).life(7 + RANDOM.nextInt(6)).drag(0.85f).streak(1.4f, 0.08f));
                }
            }
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < LIFE;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            if (age < 0 || age >= LIFE) {
                return;
            }
            double swept = Math.min(1.0, age / SWEEP);
            swept = 1 - (1 - swept) * (1 - swept);
            double fade = age <= SWEEP ? 1.0 : Math.max(0.0, 1.0 - (age - SWEEP) / (LIFE - SWEEP));
            float a = (float) (fade * fade);
            double from = yaw - Math.toRadians(135);
            double to = from + Math.toRadians(270) * swept;
            Vec3 centre = f.relative(at);
            double reach = 3.6 + 1.4 * swept;
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
            RelicDraw.sector(glow, centre, reach - 0.9, reach + 0.15, from, to, 36, RelicDraw.SUN_GOLD, 0.35f * a, 0.9f * a);
            RelicDraw.sector(glow, centre, reach - 0.3, reach + 0.05, from, to, 36, RelicDraw.SUN_WHITE, 0.4f * a, a);
            RelicDraw.sector(glow, centre.add(0, -0.25, 0), 0.8, reach, from, to, 24, RelicDraw.DAWN_ROSE, 0.05f * a, 0.16f * a);
        }
    }

    // ------------------------------------------------------------------ Dawnguard's stance

    /** The stance: a slow ring of light at the bearer's feet and motes rising round it while it holds. */
    static final class StanceRing implements WorldFx.Effect {
        private final Player player;
        private final int ticks;
        private final double start = FxClock.ticks();

        StanceRing(Player player, int ticks) {
            this.player = player;
            this.ticks = Math.max(1, ticks);
        }

        @Override
        public boolean tick() {
            double age = FxClock.ticks() - start;
            if (player.isRemoved() || age > ticks + 6) {
                return false;
            }
            ClientLevel level = level();
            if (level != null && FxParticles.ready() && age < ticks && ((long) age) % 2 == 0 && !WorldFx.firstPersonOf(player)) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                Vec3 p = player.position().add(Math.cos(a) * 1.1, 0.1, Math.sin(a) * 1.1);
                FxBudget.spawn(FxParticles.glint(level, p).velocity(new Vec3(0, 0.06, 0)).color(RelicDraw.SUN_GOLD)
                        .size(0.05f, 0.02f).life(14).fade(0.2f, 1.2f));
            }
            return true;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            float in = (float) Math.min(1.0, age / 3.0);
            float out = (float) Math.max(0.0, Math.min(1.0, (ticks + 6 - age) / 6.0));
            float a = in * out * (0.8f + 0.2f * (float) Math.sin(age * 0.5));
            Vec3 feet = f.relative(player.getPosition(f.partialTick())).add(0, 0.05, 0);
            VertexConsumer ring = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RING));
            RelicDraw.flat(ring, feet, 1.35f, (float) (age * 0.05), RelicDraw.SUN_GOLD, 0.8f * a);
            VertexConsumer rays = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RAYS));
            RelicDraw.flat(rays, feet, 1.7f, (float) (-age * 0.03), RelicDraw.SUN_DEEP, 0.35f * a);
            if (!WorldFx.firstPersonOf(player)) {
                VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
                RelicDraw.billboard(glow, f.camera(), feet.add(0, 1.2, 0), 1.1f, 0f, RelicDraw.SUN_GOLD, 0.18f * a);
            }
        }
    }

    // ------------------------------------------------------------------ the Sunspear's lance

    /** The charged thrust's lance of light, along the aim, {@code length} blocks; brighter and wider per charge spent. */
    static final class Lance implements WorldFx.Effect {
        private static final int LIFE = 9;
        private final Vec3 from;
        private final Vec3 to;
        private final int spent;
        private final double start = FxClock.ticks();

        Lance(Player player, double length, int spent) {
            Vec3 forward = HitShape.forward(player.getYRot());
            this.from = player.position().add(0, player.getBbHeight() * 0.62, 0).add(forward.scale(0.8));
            this.to = from.add(forward.scale(length));
            this.spent = spent;
            if (spent > 0) {
                sparks(to, 8 + 6 * spent, 0.3);
            }
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < LIFE;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            double t = age / LIFE;
            if (t < 0 || t >= 1) {
                return;
            }
            double grown = Math.min(1.0, age / 1.5);
            float a = (float) ((1 - t) * (1 - t));
            Vec3 a0 = f.relative(from);
            Vec3 a1 = f.relative(from.add(to.subtract(from).scale(grown)));
            float wide = 0.32f + 0.14f * spent;
            VertexConsumer beam = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
            RelicDraw.ribbon(beam, f.camera(), a0, a1, wide, RelicDraw.SUN_GOLD, 0.45f * a, RelicDraw.SUN_GOLD, a);
            RelicDraw.ribbon(beam, f.camera(), a0, a1, wide * 0.4f, RelicDraw.SUN_WHITE, 0.6f * a, RelicDraw.SUN_WHITE, a);
            RelicDraw.billboard(beam, f.camera(), a1, 0.35f + 0.25f * spent, 0f, RelicDraw.SUN_WHITE, a);
            VertexConsumer rays = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RAYS));
            RelicDraw.billboard(rays, f.camera(), a1, 0.45f + 0.4f * spent, (float) (age * 0.1), RelicDraw.SUN_GOLD, a);
        }
    }

    // ------------------------------------------------------------------ the Sunfall's landing

    /** A sunburst on the ground and a ring running out to its edge. */
    static final class GroundBurst implements WorldFx.Effect {
        private static final int LIFE = 14;
        private final Vec3 at;
        private final float radius;
        private final double start = FxClock.ticks();

        GroundBurst(Vec3 at, float radius) {
            this.at = at;
            this.radius = radius;
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < LIFE;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            double t = age / LIFE;
            if (t < 0 || t >= 1) {
                return;
            }
            float a = (float) ((1 - t) * (1 - t));
            float grow = (float) (1 - Math.pow(1 - t, 2.5));
            Vec3 c = f.relative(at);
            VertexConsumer rays = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RAYS));
            RelicDraw.flat(rays, c, radius * (0.6f + 0.5f * grow), (float) (age * 0.03), RelicDraw.SUN_GOLD, a);
            VertexConsumer ring = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RING));
            RelicDraw.flat(ring, c.add(0, 0.01, 0), 0.4f + radius * grow, 0f, RelicDraw.SUN_WHITE, 0.9f * a);
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
            RelicDraw.billboard(glow, f.camera(), c.add(0, 0.6, 0), 1.0f + grow, 0f, RelicDraw.SUN_WHITE, 0.6f * a);
        }
    }

    // ------------------------------------------------------------------ the charges

    /** A small sun per Sunlight charge, circling the bearer at shoulder height (not in its own first-person view). */
    static final class Suns implements WorldFx.Effect {
        private final Player player;
        private final double start = FxClock.ticks();

        Suns(Player player) {
            this.player = player;
        }

        @Override
        public boolean tick() {
            boolean on = !player.isRemoved() && CHARGES.getOrDefault(player.getId(), 0) > 0;
            if (!on) {
                SUNS.remove(player.getId());
            }
            return on;
        }

        @Override
        public void render(WorldFx.Frame f) {
            int n = CHARGES.getOrDefault(player.getId(), 0);
            if (n <= 0 || WorldFx.firstPersonOf(player)) {
                return;
            }
            double age = f.now() - start;
            Vec3 centre = f.relative(player.getPosition(f.partialTick())).add(0, player.getBbHeight() * 0.85, 0);
            Vec3[] at = new Vec3[n];
            for (int i = 0; i < n; i++) {
                double a = age * 0.09 + i * Math.PI * 2 / 3;
                at[i] = centre.add(Math.cos(a) * 0.62, 0.08 * Math.sin(age * 0.2 + i), Math.sin(a) * 0.62);
            }
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
            for (Vec3 p : at) {
                RelicDraw.billboard(glow, f.camera(), p, 0.16f, 0f, RelicDraw.SUN_GOLD, 0.8f);
            }
            VertexConsumer rays = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RAYS));
            for (Vec3 p : at) {
                RelicDraw.billboard(rays, f.camera(), p, 0.13f, (float) (age * 0.1), RelicDraw.SUN_WHITE, 0.9f);
            }
        }
    }

    /** Entities round which a Sunlight sun could be drawn: kept tidy when players leave. */
    static void forget(Entity entity) {
        CHARGES.remove(entity.getId());
        SUNS.remove(entity.getId());
        SPENT.remove(entity.getId());
    }

    /** The Sunlight charges {@code entity} holds, as the client knows them. */
    static int charges(Entity entity) {
        return CHARGES.getOrDefault(entity.getId(), 0);
    }

    static boolean holdsLastLight(Player player) {
        return player.getMainHandItem().is(Relics.LAST_LIGHT.get());
    }
}
