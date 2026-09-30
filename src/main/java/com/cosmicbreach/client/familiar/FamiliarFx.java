package com.cosmicbreach.client.familiar;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.familiar.FamiliarEntity;
import com.cosmicbreach.familiar.FamiliarFxPayload;
import com.cosmicbreach.familiar.FamiliarKind;
import com.cosmicbreach.familiar.FamiliarRules;
import com.cosmicbreach.familiar.Gravikin;
import com.cosmicbreach.familiar.RefractStacks;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

/**
 * The familiars' moments as the clients see them (the server says when, {@link FamiliarFxPayload}):
 * <ul>
 *   <li>summoned, dismissed, dead, teleported: a burst of the familiar's own light (sun gold, violet-blue, pearl);</li>
 *   <li>the Emberwisp's Scorch: an ember streak to the target and a flare on it; Kindled: a ring of embers round the
 *       owner;</li>
 *   <li>the Gravikin's taunt: a violet ring sweeping out along the ground to 8 blocks, and its glow flaring for the 80
 *       ticks; its landings kick up dust;</li>
 *   <li>the Prism Moth's glint: a thin rainbow beam to the target; Refract: one small prism per stack turning over the
 *       target's head; consumed: the prisms burst into colours; the cleanse: rainbow motes rising round the owner.</li>
 * </ul>
 * Also the familiars' ambient light: the wisp sheds embers, the moth an occasional rainbow glint.
 */
public final class FamiliarFx {
    static final int SUN = 0xFFB040;
    static final int SUN_WHITE = 0xFFF1C4;
    static final int VIOLET = 0x8C9CFF;
    static final int PEARL = 0xE6F6FF;
    static final ResourceLocation RING = CosmicBreach.id("textures/particle/ring.png");
    static final ResourceLocation GLINT = CosmicBreach.id("textures/particle/star_glint.png");
    private static final RandomSource RANDOM = RandomSource.create();
    /** The rainbow the moth throws, in order. */
    private static final int[] RAINBOW = {0xFF6B6B, 0xFFB35C, 0xFFF27A, 0x7CFF9B, 0x6BD8FF, 0x9C8CFF, 0xF08CFF};

    /** Refract stacks on each target the client knows of: entity id to (stacks, until when). */
    private static final Map<Integer, long[]> REFRACT = new HashMap<>();
    /** When each Gravikin last taunted (client ticks). */
    private static final Map<Integer, Long> TAUNTS = new HashMap<>();
    private static boolean marksAdded;

    private FamiliarFx() {
    }

    public static void handle(FamiliarFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> play(payload));
    }

    static int color(int kind) {
        return switch (FamiliarKind.values()[Math.floorMod(kind, FamiliarKind.values().length)]) {
            case EMBERWISP -> SUN;
            case GRAVIKIN -> VIOLET;
            case PRISM_MOTH -> PEARL;
        };
    }

    static void play(FamiliarFxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 at = p.at();
        Entity a = p.a() >= 0 ? level.getEntity(p.a()) : null;
        Entity b = p.b() >= 0 ? level.getEntity(p.b()) : null;
        switch (p.kind()) {
            case FamiliarFxPayload.SUMMON -> {
                Vec3 c = a != null ? middle(a) : at;
                int col = color(p.value());
                Glow.flash(c, 1.2f, col, 0.9f, 8);
                Glow.ring(c, 0.2f, 1.4f, col, 0.8f, 9);
                burst(level, c, 14, 0.2, col, 12);
            }
            case FamiliarFxPayload.DISMISS -> {
                int col = color(p.value());
                Glow.flash(at, 0.9f, col, 0.8f, 7);
                implode(level, at, 12, col);
            }
            case FamiliarFxPayload.DEATH -> {
                int col = color(p.value());
                Glow.flash(at, 1.8f, col, 1.0f, 10);
                Glow.ring(at, 0.3f, 2.2f, col, 0.9f, 12);
                burst(level, at, 28, 0.32, col, 16);
            }
            case FamiliarFxPayload.TELEPORT -> {
                int col = a instanceof FamiliarEntity f ? color(f.kind().ordinal()) : PEARL;
                Glow.flash(at, 0.8f, col, 0.7f, 6);
                if (a != null) {
                    Glow.flash(middle(a), 1.0f, col, 0.8f, 7);
                }
            }
            case FamiliarFxPayload.SCORCH -> {
                Vec3 from = a != null ? middle(a) : at;
                Beam.add(from, at, SUN, SUN, 0.09f, 7);
                Glow.flash(at, 0.9f, SUN, 0.9f, 8);
                burst(level, at, 10, 0.12, SUN, 12);
            }
            case FamiliarFxPayload.KINDLED -> {
                Vec3 feet = a != null ? a.position() : at;
                Glow.groundRing(feet, 0.3f, 1.6f, SUN, 1.0f, 12);
                Glow.flash(feet.add(0, 1.0, 0), 1.3f, SUN_WHITE, 0.6f, 8);
                for (int i = 0; FxParticles.ready() && i < FxBudget.count(16, feet, true); i++) {
                    double ang = i * Mth.TWO_PI / 16.0;
                    Vec3 s = feet.add(Math.cos(ang) * 0.7, 0.2 + RANDOM.nextDouble() * 0.6, Math.sin(ang) * 0.7);
                    FxBudget.spawn(FxParticles.spark(level, s).velocity(new Vec3(0, 0.06 + RANDOM.nextDouble() * 0.05, 0)).color(SUN)
                            .size(0.06f, 0.02f).life(14 + RANDOM.nextInt(6)).drag(0.92f));
                }
            }
            case FamiliarFxPayload.TAUNT -> {
                TAUNTS.put(p.a(), FxClock.ticks());
                Vec3 feet = a != null ? a.position() : at;
                Glow.groundRing(feet, 0.4f, (float) FamiliarRules.TAUNT_RADIUS, VIOLET, 1.0f, 16);
                Glow.groundRing(feet, 0.2f, (float) FamiliarRules.TAUNT_RADIUS * 0.6f, 0xC6CEFF, 0.7f, 12);
                Glow.flash(feet.add(0, 0.4, 0), 1.6f, VIOLET, 0.8f, 10);
                dust(level, feet, 18, 0.25);
            }
            case FamiliarFxPayload.GLINT -> {
                Vec3 from = a != null ? middle(a) : at;
                int stacks = Math.max(1, p.value());
                Beam.add(from, at, RAINBOW[(stacks * 2) % RAINBOW.length], RAINBOW[(stacks * 2 + 3) % RAINBOW.length], 0.07f, 8);
                for (int i = 0; FxParticles.ready() && i < FxBudget.count(7, at, true); i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(0.08);
                    FxBudget.spawn(FxParticles.glint(level, at).velocity(v).color(RAINBOW[i % RAINBOW.length]).size(0.14f, 0.03f).life(10));
                }
            }
            case FamiliarFxPayload.REFRACT -> {
                if (p.value() <= 0) {
                    REFRACT.remove(p.a());
                } else {
                    REFRACT.put(p.a(), new long[] {p.value(), FxClock.ticks() + RefractStacks.TICKS});
                    ensureMarks();
                }
            }
            case FamiliarFxPayload.REFRACT_BREAK -> {
                REFRACT.remove(p.a());
                Glow.flash(at, 1.6f, PEARL, 1.0f, 8);
                Glow.ring(at, 0.3f, 2.0f, 0xFFF27A, 0.8f, 10);
                for (int i = 0; FxParticles.ready() && i < FxBudget.count(21, at, true); i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6, RANDOM.nextGaussian()).normalize().scale(0.22);
                    FxBudget.spawn(FxParticles.shard(level, at).velocity(v).color(RAINBOW[i % RAINBOW.length]).size(0.09f, 0.03f)
                            .life(14 + RANDOM.nextInt(6)).drag(0.9f));
                }
            }
            case FamiliarFxPayload.CLEANSE -> {
                Vec3 feet = a != null ? a.position() : at;
                Glow.groundRing(feet, 0.2f, 1.3f, PEARL, 0.9f, 12);
                for (int i = 0; FxParticles.ready() && i < FxBudget.count(21, feet, true); i++) {
                    double ang = i * Mth.TWO_PI / 7.0;
                    Vec3 s = feet.add(Math.cos(ang) * 0.6, 0.1 + (i / 7) * 0.5, Math.sin(ang) * 0.6);
                    FxBudget.spawn(FxParticles.glint(level, s).velocity(new Vec3(0, 0.07, 0)).color(RAINBOW[i % RAINBOW.length])
                            .size(0.13f, 0.03f).life(16));
                }
            }
            case FamiliarFxPayload.HATCH -> {
                int col = color(p.value());
                Glow.flash(at, 2.2f, SUN_WHITE, 1.0f, 12);
                Glow.flash(at, 1.4f, col, 0.9f, 16);
                Glow.ring(at, 0.3f, 2.6f, col, 0.9f, 14);
                burst(level, at, 30, 0.25, col, 18);
            }
            case FamiliarFxPayload.LAND -> dust(level, at, 5, 0.08);
            case FamiliarFxPayload.STRIKE -> burst(level, at, 5, 0.12, a instanceof FamiliarEntity f ? color(f.kind().ordinal()) : SUN, 6);
            default -> {
            }
        }
    }

    /** 0 to 1: how much a Gravikin's taunt flare is still on it. */
    static double taunting(Entity gravikin, float partial) {
        Long at = TAUNTS.get(gravikin.getId());
        if (at == null) {
            return 0.0;
        }
        double t = FxClock.ticks() + partial - at;
        if (t >= FamiliarRules.TAUNT_TICKS) {
            return 0.0;
        }
        return t < 4 ? t / 4.0 : 1.0 - Math.pow(t / FamiliarRules.TAUNT_TICKS, 3);
    }

    /** The stacks of Refract the client shows on {@code target} now. */
    public static int refract(Entity target) {
        long[] r = REFRACT.get(target.getId());
        return r == null || FxClock.ticks() >= r[1] ? 0 : (int) r[0];
    }

    /** Once a tick: the familiars' own sparks, and forgetting old marks. */
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            REFRACT.clear();
            TAUNTS.clear();
            return;
        }
        long now = FxClock.ticks();
        REFRACT.values().removeIf(r -> now >= r[1]);
        TAUNTS.values().removeIf(t -> now - t > FamiliarRules.TAUNT_TICKS);
        if (!FxParticles.ready() || mc.isPaused()) {
            return;
        }
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof FamiliarEntity f) || f.isRemoved() || !f.isAlive()) {
                continue;
            }
            Vec3 c = middle(f);
            switch (f.kind()) {
                case EMBERWISP -> {
                    if (RANDOM.nextInt(2) == 0 && FxBudget.count(1, c, false) > 0) {
                        Vec3 v = f.getDeltaMovement().scale(-0.3).add(0, 0.015, 0);
                        FxBudget.spawn(FxParticles.spark(level, c.add(RANDOM.nextGaussian() * 0.08, RANDOM.nextGaussian() * 0.08,
                                RANDOM.nextGaussian() * 0.08)).velocity(v).color(RANDOM.nextInt(3) == 0 ? SUN_WHITE : SUN).size(0.045f, 0.01f)
                                .life(8 + RANDOM.nextInt(6)).drag(0.9f));
                    }
                }
                case PRISM_MOTH -> {
                    if (RANDOM.nextInt(7) == 0 && FxBudget.count(1, c, false) > 0) {
                        FxBudget.spawn(FxParticles.glint(level, c.add(RANDOM.nextGaussian() * 0.2, RANDOM.nextGaussian() * 0.1, RANDOM.nextGaussian() * 0.2))
                                .velocity(new Vec3(0, -0.01, 0)).color(RAINBOW[RANDOM.nextInt(RAINBOW.length)]).size(0.1f, 0.02f).life(12));
                    }
                }
                case GRAVIKIN -> {
                    if (f instanceof Gravikin && RANDOM.nextInt(12) == 0 && FxBudget.count(1, c, false) > 0) {
                        FxBudget.spawn(FxParticles.glint(level, c.add(RANDOM.nextGaussian() * 0.15, 0.1, RANDOM.nextGaussian() * 0.15))
                                .velocity(new Vec3(0, 0.012, 0)).color(VIOLET).size(0.07f, 0.02f).life(14));
                    }
                }
            }
        }
    }

    static Vec3 middle(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private static void burst(ClientLevel level, Vec3 at, int wanted, double speed, int color, int life) {
        if (!FxParticles.ready()) {
            return;
        }
        for (int i = 0; i < FxBudget.count(wanted, at, true); i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.7, RANDOM.nextGaussian()).normalize()
                    .scale(speed * (0.6 + RANDOM.nextDouble() * 0.6));
            FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(color).size(0.05f, 0.015f).life(life + RANDOM.nextInt(5)).drag(0.88f));
        }
    }

    private static void implode(ClientLevel level, Vec3 at, int wanted, int color) {
        if (!FxParticles.ready()) {
            return;
        }
        for (int i = 0; i < FxBudget.count(wanted, at, true); i++) {
            Vec3 from = at.add(new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(0.8));
            FxBudget.spawn(FxParticles.glint(level, from).velocity(at.subtract(from).scale(1.0 / 8.0)).color(color).size(0.1f, 0.02f).life(8));
        }
    }

    private static void dust(ClientLevel level, Vec3 feet, int n, double speed) {
        BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
        BlockState state = level.getBlockState(below);
        if (state.isAir()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            double ang = RANDOM.nextDouble() * Mth.TWO_PI;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), feet.x + Math.cos(ang) * 0.3, feet.y + 0.05,
                    feet.z + Math.sin(ang) * 0.3, Math.cos(ang) * speed, 0.05, Math.sin(ang) * speed);
        }
    }

    private static void ensureMarks() {
        if (!marksAdded) {
            marksAdded = true;
            WorldFx.add(new Marks());
        }
    }

    /** The Refract prisms over every marked target: one per stack, turning, each its own colour. */
    private static final class Marks implements WorldFx.Effect {
        @Override
        public boolean tick() {
            if (REFRACT.isEmpty()) {
                marksAdded = false;
                return false;
            }
            return true;
        }

        @Override
        public void render(WorldFx.Frame frame) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            VertexConsumer out = frame.buffers().getBuffer(FxRenderTypes.additive(GLINT));
            for (Iterator<Map.Entry<Integer, long[]>> it = REFRACT.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, long[]> e = it.next();
                Entity target = level.getEntity(e.getKey());
                if (target == null || !target.isAlive()) {
                    continue;
                }
                int stacks = (int) e.getValue()[0];
                double left = e.getValue()[1] - frame.now();
                float fade = (float) Math.max(0.0, Math.min(1.0, left / 20.0));
                Vec3 top = target.getPosition(frame.partialTick()).add(0, target.getBbHeight() + 0.35, 0);
                for (int i = 0; i < stacks; i++) {
                    double ang = frame.now() * 0.09 + i * Mth.TWO_PI / 3.0;
                    Vec3 p = top.add(Math.cos(ang) * 0.42, 0.08 * Math.sin(frame.now() * 0.2 + i), Math.sin(ang) * 0.42);
                    float[] c = WorldFx.rgb(RAINBOW[(i * 2 + 1) % RAINBOW.length]);
                    float size = stacks >= RefractStacks.MAX ? 0.2f : 0.15f;
                    WorldFx.billboard(out, frame.camera(), frame.relative(p), size, c[0], c[1], c[2], 0.95f * fade);
                }
                if (stacks >= RefractStacks.MAX) {
                    float pulse = (float) (0.55 + 0.25 * Math.sin(frame.now() * 0.4));
                    WorldFx.billboard(out, frame.camera(), frame.relative(top), 0.3f, 1f, 1f, 1f, pulse * fade);
                }
            }
        }
    }

    /** A thin beam of light between two points, fading over its life, its colour running from one end to the other. */
    private static final class Beam implements WorldFx.Effect {
        private final Vec3 from;
        private final Vec3 to;
        private final float[] c0;
        private final float[] c1;
        private final float width;
        private final long start = FxClock.ticks();
        private final int life;

        private Beam(Vec3 from, Vec3 to, int color0, int color1, float width, int life) {
            this.from = from;
            this.to = to;
            this.c0 = WorldFx.rgb(color0);
            this.c1 = WorldFx.rgb(color1);
            this.width = width;
            this.life = life;
        }

        static void add(Vec3 from, Vec3 to, int color0, int color1, float width, int life) {
            WorldFx.add(new Beam(from, to, color0, color1, width, life));
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < life;
        }

        @Override
        public void render(WorldFx.Frame frame) {
            double t = (frame.now() - start) / life;
            if (t < 0 || t >= 1) {
                return;
            }
            float alpha = (float) Math.pow(1.0 - t, 1.5);
            Vec3 a = frame.relative(from);
            Vec3 b = frame.relative(to);
            Vec3 along = b.subtract(a);
            Vector3f look = frame.camera().getLookVector();
            Vec3 side = along.cross(new Vec3(look.x(), look.y(), look.z()));
            double len = side.length();
            if (len < 1e-6) {
                return;
            }
            side = side.scale(width / len);
            VertexConsumer out = frame.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            WorldFx.vertex(out, a.subtract(side), 0f, 0f, c0[0], c0[1], c0[2], alpha);
            WorldFx.vertex(out, a.add(side), 1f, 0f, c0[0], c0[1], c0[2], alpha);
            WorldFx.vertex(out, b.add(side), 1f, 1f, c1[0], c1[1], c1[2], alpha);
            WorldFx.vertex(out, b.subtract(side), 0f, 1f, c1[0], c1[1], c1[2], alpha);
        }
    }

    /** A round shape of light: a soft glow or a thin ring, facing the camera or lying flat, growing and fading. */
    private static final class Glow implements WorldFx.Effect {
        private final Vec3 at;
        private final boolean flat;
        private final ResourceLocation texture;
        private final float from;
        private final float to;
        private final float[] color;
        private final float strength;
        private final long start = FxClock.ticks();
        private final int life;

        private Glow(Vec3 at, boolean flat, ResourceLocation texture, float from, float to, int color, float strength, int life) {
            this.at = at;
            this.flat = flat;
            this.texture = texture;
            this.from = from;
            this.to = to;
            this.color = WorldFx.rgb(color);
            this.strength = strength;
            this.life = Math.max(1, life);
        }

        static void flash(Vec3 at, float size, int color, float strength, int life) {
            WorldFx.add(new Glow(at, false, FxRenderTypes.GLOW, size * 0.6f, size, color, strength, life));
        }

        static void ring(Vec3 at, float from, float to, int color, float strength, int life) {
            WorldFx.add(new Glow(at, false, RING, from, to, color, strength, life));
        }

        static void groundRing(Vec3 at, float from, float to, int color, float strength, int life) {
            WorldFx.add(new Glow(at.add(0, 0.06, 0), true, RING, from, to, color, strength, life));
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < life;
        }

        @Override
        public void render(WorldFx.Frame frame) {
            double t = (frame.now() - start) / life;
            if (t < 0 || t >= 1) {
                return;
            }
            float grow = (float) (1.0 - Math.pow(1.0 - t, 3));
            float size = Mth.lerp(grow, from, to);
            float alpha = strength * Math.min(1f, (float) t * 10f) * (float) Math.pow(1.0 - t, 1.5);
            if (alpha <= 0.005f || size <= 0.01f) {
                return;
            }
            VertexConsumer out = frame.buffers().getBuffer(FxRenderTypes.additive(texture));
            Vec3 rel = frame.relative(at);
            if (flat) {
                WorldFx.flat(out, rel, size, color[0], color[1], color[2], alpha);
            } else {
                WorldFx.billboard(out, frame.camera(), rel, size, color[0], color[1], color[2], alpha);
            }
        }
    }
}
