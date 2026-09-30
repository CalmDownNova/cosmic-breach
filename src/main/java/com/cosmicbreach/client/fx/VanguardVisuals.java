package com.cosmicbreach.client.fx;

import com.cosmicbreach.client.combat.ClientCombat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Starfall Vanguard's effects, drawn for everyone the server told: Heavy Landing's shockwave, Meteor
 * Call's burning circle and the meteor falling onto it, and the impact with its burning crater.
 */
public final class VanguardVisuals {
    public static final int EMBER = 0xFF8A2E;
    public static final int WHITE_HOT = 0xFFF1DA;
    public static final int ORANGE = 0xFFA650;
    private static final int CRACK_GLOW = 0xFF7A26;
    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * The meteor starts this high above the mark and this far beyond it (seen from its caster), and falls for the
     * last ticks of the warning: a long slant a caster looking at the mark watches come in.
     */
    private static final double FALL_HEIGHT = 12.0;
    private static final double FALL_SIDE = 20.0;
    private static final int FALL_TICKS = 18;
    private static final int TRAIL = 7;

    private VanguardVisuals() {
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    // ------------------------------------------------------------------ Heavy Landing

    /** A landing from {@code fallBlocks} at {@code at} (the feet). */
    public static void shockwave(Vec3 at, double fallBlocks, boolean own) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        float grow = (float) Mth.clamp(0.7 + fallBlocks * 0.05, 0.8, 1.4);
        Vec3 ground = groundAt(level, at);
        WorldFx.add(new CrackDecal(ground, 1.5f * grow, RANDOM.nextFloat() * Mth.TWO_PI, CRACK_GLOW, 0.7f, 40));
        GlowEffect.groundRing(ground, 0.4f, 3.2f, ORANGE, 1.0f, 9);
        GlowEffect.groundRing(ground.add(0, 0.01, 0), 0.3f, 2.2f, WHITE_HOT, 0.7f, 6);
        GlowEffect.ground(ground, 1.6f * grow, EMBER, 0.6f, 8);
        debris(level, ground, (int) (14 * grow), 1.8);
        embers(level, ground, (int) (10 * grow), 1.0);
        if (own) {
            ClientCombat.feelSlam(0.2 * grow, 2.0 * grow);
        }
    }

    // ------------------------------------------------------------------ Meteor Call

    /**
     * The mark: a burning circle for {@code ticks}, and the meteor falling into it at the end, from beyond the mark
     * as seen from {@code caster} (anywhere, if the caster isn't known here).
     */
    public static void meteorMark(Vec3 at, float radius, int ticks, @Nullable Vec3 caster) {
        ClientLevel level = level();
        if (level == null) {
            return;
        }
        Vec3 ground = groundAt(level, at);
        Vec3 away = caster == null ? Vec3.ZERO : new Vec3(ground.x - caster.x, 0, ground.z - caster.z);
        if (away.lengthSqr() < 1e-4) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            away = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        }
        WorldFx.add(new Mark(ground, radius, ticks, away.normalize()));
    }

    /** The meteor landed: a flash, a blast ring, debris and a crater that burns for {@code ticks}. */
    public static void meteorImpact(Vec3 at, float radius, int ticks) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        Vec3 ground = groundAt(level, at);
        WorldFx.add(new CrackDecal(ground, radius * 1.15f, RANDOM.nextFloat() * Mth.TWO_PI, CRACK_GLOW, 0.85f, ticks + 30));
        GlowEffect.flash(ground.add(0, 1.0, 0), 3.2f, WHITE_HOT, 1.0f, 7);
        GlowEffect.groundRing(ground, 0.5f, radius * 1.7f, ORANGE, 1.0f, 12);
        GlowEffect.groundRing(ground.add(0, 0.01, 0), 0.4f, radius * 1.1f, WHITE_HOT, 0.9f, 8);
        debris(level, ground, 34, 3.0);
        embers(level, ground, 26, 2.2);
        WorldFx.add(new Crater(ground, radius, ticks));
        Player me = Minecraft.getInstance().player;
        if (me != null) {
            double d = me.position().distanceTo(ground);
            if (d < 16) {
                double near = 1.0 - d / 16;
                ClientCombat.feelSlam(0.45 * near, 3.5 * near);
            }
        }
    }

    /** The burning circle and the falling meteor. */
    private static final class Mark implements WorldFx.Effect {
        private final Vec3 ground;
        private final float radius;
        private final int ticks;
        private final Vec3 side;
        private final double start;

        Mark(Vec3 ground, float radius, int ticks, Vec3 side) {
            this.ground = ground;
            this.radius = radius;
            this.ticks = ticks;
            this.side = side;
            this.start = FxClock.ticks();
        }

        private double age(double now) {
            return now - start;
        }

        @Override
        public boolean tick() {
            double age = age(FxClock.ticks());
            if (age >= ticks) {
                return false;
            }
            ClientLevel level = level();
            if (level == null) {
                return false;
            }
            // flames rising all round the circle, thicker as the meteor nears
            double urgency = age / ticks;
            int flames = 3 + (int) (urgency * 6);
            for (int i = 0; i < flames; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double r = radius * (0.92 + RANDOM.nextDouble() * 0.12);
                level.addParticle(ParticleTypes.FLAME, ground.x + Math.cos(a) * r, ground.y + 0.05, ground.z + Math.sin(a) * r,
                        0, 0.02 + RANDOM.nextDouble() * 0.04, 0);
            }
            double fall = age - (ticks - FALL_TICKS);
            if (fall >= 0 && FxParticles.ready()) {
                Vec3 rock = meteorAt(fall / FALL_TICKS);
                Vec3 back = meteorAt(Math.max(0, fall - 1) / FALL_TICKS).subtract(rock);
                Vec3 dir = back.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : back.normalize();
                for (int i = 0; i < 3; i++) {
                    Vec3 p = rock.add(dir.scale(RANDOM.nextDouble() * 1.2)).add((RANDOM.nextDouble() - 0.5) * 0.7,
                            (RANDOM.nextDouble() - 0.5) * 0.7, (RANDOM.nextDouble() - 0.5) * 0.7);
                    level.addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.01, 0);
                }
                int n = FxBudget.count(6, rock, true);
                for (int i = 0; i < n; i++) {
                    Vec3 p = rock.add((RANDOM.nextDouble() - 0.5) * 0.8, (RANDOM.nextDouble() - 0.5) * 0.8, (RANDOM.nextDouble() - 0.5) * 0.8);
                    FxBudget.spawn(FxParticles.spark(level, p).streakAlong(dir, (float) (1.5 + RANDOM.nextDouble() * 2.0))
                            .velocity(dir.scale(0.1)).size(0.14f, 0.05f).life(6 + RANDOM.nextInt(5))
                            .color(CombatEffects.mix(ORANGE, WHITE_HOT, RANDOM.nextFloat())));
                }
            }
            return true;
        }

        /** Where the meteor is at {@code t} (0 high in the sky, 1 on the mark), falling ever faster. */
        private Vec3 meteorAt(double t) {
            double e = Math.min(1.0, Math.max(0.0, t));
            double left = 1.0 - e * e;
            return ground.add(side.scale(FALL_SIDE * left)).add(0, 0.6 + FALL_HEIGHT * left, 0);
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = age(f.now());
            if (age < 0 || age >= ticks) {
                return;
            }
            double urgency = age / ticks;
            float pulse = 0.75f + 0.25f * (float) Math.sin(age * (0.6 + urgency * 1.2));
            Vec3 c = f.relative(ground).add(0, 0.06, 0);
            float[] o = WorldFx.rgb(ORANGE);
            float[] w = WorldFx.rgb(WHITE_HOT);
            VertexConsumer ring = f.buffers().getBuffer(FxRenderTypes.additive(GlowEffect.RING));
            WorldFx.flat(ring, c, radius * 1.08f, o[0], o[1], o[2], 0.95f * pulse);
            WorldFx.flat(ring, c.add(0, 0.005, 0), radius * 0.55f, w[0], w[1], w[2], (float) (0.35 + 0.5 * urgency) * pulse);
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            WorldFx.flat(glow, c.add(0, 0.01, 0), radius * 1.1f, o[0], o[1], o[2], (float) (0.25 + 0.45 * urgency));
            double fall = age - (ticks - FALL_TICKS);
            if (fall >= 0) {
                Vec3 rock = meteorAt(fall / FALL_TICKS);
                Vec3 rel = f.relative(rock);
                for (int i = TRAIL; i >= 1; i--) {
                    double back = Math.max(0, fall - i * 0.8);
                    Vec3 tail = f.relative(meteorAt(back / FALL_TICKS));
                    float k = 1f - i / (float) (TRAIL + 1);
                    WorldFx.billboard(glow, f.camera(), tail, 0.6f + 1.4f * k, o[0], o[1], o[2], 0.55f * k);
                }
                WorldFx.billboard(glow, f.camera(), rel, 2.8f, o[0], o[1], o[2], 0.75f);
                WorldFx.billboard(glow, f.camera(), rel, 1.5f, w[0], w[1], w[2], 0.95f);
                drawRock(f, rel, age);
            }
        }

        private void drawRock(WorldFx.Frame f, Vec3 rel, double age) {
            // molten, not the cooled Meteorite block it leaves behind: the dark block read as a hole in the sky
            BlockState state = Blocks.MAGMA_BLOCK.defaultBlockState();
            PoseStack pose = new PoseStack();
            pose.translate(rel.x, rel.y, rel.z);
            pose.mulPose(Axis.YP.rotationDegrees((float) (age * 23)));
            pose.mulPose(Axis.XP.rotationDegrees((float) (age * 31)));
            pose.scale(1.6f, 1.6f, 1.6f);
            pose.translate(-0.5, -0.5, -0.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, f.buffers(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    /** The crater burning after the impact: flames and ember pops, fading at the end. */
    private static final class Crater implements WorldFx.Effect {
        private final Vec3 ground;
        private final float radius;
        private final int ticks;
        private final double start;

        Crater(Vec3 ground, float radius, int ticks) {
            this.ground = ground;
            this.radius = radius;
            this.ticks = ticks;
            this.start = FxClock.ticks();
        }

        @Override
        public boolean tick() {
            double age = FxClock.ticks() - start;
            ClientLevel level = level();
            if (age >= ticks || level == null) {
                return false;
            }
            double left = 1.0 - age / ticks;
            int flames = (int) Math.round(7 * left) + 1;
            for (int i = 0; i < flames; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double r = radius * Math.sqrt(RANDOM.nextDouble()) * 0.95;
                level.addParticle(ParticleTypes.FLAME, ground.x + Math.cos(a) * r, ground.y + 0.05, ground.z + Math.sin(a) * r,
                        0, 0.03 + RANDOM.nextDouble() * 0.05, 0);
            }
            if (RANDOM.nextFloat() < 0.35f * left) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double r = radius * Math.sqrt(RANDOM.nextDouble()) * 0.8;
                level.addParticle(ParticleTypes.LAVA, ground.x + Math.cos(a) * r, ground.y + 0.1, ground.z + Math.sin(a) * r, 0, 0, 0);
            }
            return true;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            if (age < 0 || age >= ticks) {
                return;
            }
            float left = (float) (1.0 - age / ticks);
            float[] e = WorldFx.rgb(EMBER);
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            WorldFx.flat(glow, f.relative(ground).add(0, 0.03, 0), radius * 1.2f, e[0], e[1], e[2], 0.55f * left);
        }
    }

    // ------------------------------------------------------------------ shared

    /** The top of the ground under {@code at} (within 3 blocks down), a hair above it. */
    static Vec3 groundAt(ClientLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y + 0.3, at.z);
        for (int i = 0; i < 4; i++, pos = pos.below()) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(at.x, pos.getY() + 0.01, at.z);
            }
        }
        return at;
    }

    /** Chunks of the ground's own block thrown up and out, {@code spread} blocks across. */
    private static void debris(ClientLevel level, Vec3 ground, int count, double spread) {
        BlockPos under = BlockPos.containing(ground.x, ground.y - 0.5, ground.z);
        BlockState state = level.getBlockState(under);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, state);
        int n = FxBudget.count(count, ground, true);
        for (int i = 0; i < n; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double out = 0.15 + RANDOM.nextDouble() * 0.2 * spread;
            double r = RANDOM.nextDouble() * 0.6 * spread;
            level.addParticle(chunk, ground.x + Math.cos(angle) * r, ground.y + 0.1, ground.z + Math.sin(angle) * r,
                    Math.cos(angle) * out, 0.3 + RANDOM.nextDouble() * 0.35, Math.sin(angle) * out);
        }
    }

    /** Hot sparks thrown up and out. */
    private static void embers(ClientLevel level, Vec3 ground, int count, double grow) {
        int n = FxBudget.count(count, ground, true);
        for (int i = 0; i < n; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            Vec3 v = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(0.12 + RANDOM.nextDouble() * 0.22 * grow)
                    .add(0, 0.2 + RANDOM.nextDouble() * 0.3, 0);
            FxBudget.spawn(FxParticles.spark(level, ground.add(0, 0.15, 0)).velocity(v)
                    .color(CombatEffects.mix(EMBER, WHITE_HOT, RANDOM.nextFloat() * 0.6f)).size(0.04f, 0.012f)
                    .life(9 + RANDOM.nextInt(8)).gravity(0.7f).drag(0.9f).physics().streak(1.5f, 0.08f));
        }
    }
}
