package com.cosmicbreach.client.relic;

import com.cosmicbreach.client.fx.ClientMoveEffect;
import com.cosmicbreach.client.fx.ClientMoveEffects;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.relic.cantor.Cantor;
import com.cosmicbreach.relic.cantor.UmbraArrow;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the Umbra Cantor looks like beyond its own entities (GDD 7.3): violet sparks where an arrow lands (a note arrow's
 * bigger), a ring when a note is struck, a flash and a ring running out over the ground when a chord forms, and a ring
 * for every pulse. The notes, the chord's triangle and the arrows are drawn by their renderers.
 */
final class CantorFx {
    private static final RandomSource RANDOM = RandomSource.create();

    private CantorFx() {
    }

    static void register() {
        ClientMoveEffects.register(Cantor.UMBRA_SHOT, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                boolean note = Math.round(m.value()) == UmbraArrow.NOTE;
                if (m.stage() == Cantor.LAND) {
                    sparks(m.at(), note ? 14 : 7, note ? 0.2 : 0.14);
                    WorldFx.add(new Burst(m.at(), note ? 0.9f : 0.45f, note ? RelicDraw.PALE_VIOLET : RelicDraw.VIOLET, 7));
                } else if (m.stage() == Cantor.FADE) {
                    sparks(m.at(), 3, 0.05);
                }
            }
        });
        ClientMoveEffects.register(Cantor.NOTES, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                switch (m.stage()) {
                    case Cantor.NOTE -> {
                        WorldFx.add(new Ring(m.at(), 0.2f, 1.1f, NoteColors.of(Math.round(m.value())), 10, false));
                        sparks(m.at(), 6, 0.08);
                    }
                    case Cantor.CHORD -> {
                        WorldFx.add(new Burst(m.at(), 1.8f, RelicDraw.PALE_VIOLET, 12));
                        WorldFx.add(new Ring(m.at().add(0, -0.4, 0), 0.5f, 5.0f, RelicDraw.VIOLET, 16, true));
                        sparks(m.at(), 20, 0.25);
                    }
                    case Cantor.PULSE -> WorldFx.add(new Ring(m.at().add(0, -0.4, 0), 0.4f, 3.2f, RelicDraw.DEEP_VIOLET, 12, true));
                    default -> {
                    }
                }
            }
        });
    }

    /** Violet sparks thrown from {@code at}. */
    static void sparks(Vec3 at, int wanted, double speed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FxParticles.ready()) {
            return;
        }
        int n = FxBudget.count(wanted, at, false);
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize()
                    .scale(speed * (0.4 + RANDOM.nextDouble()));
            FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(RelicDraw.mix(RelicDraw.PALE_VIOLET, RelicDraw.VIOLET,
                    RANDOM.nextFloat())).size(0.03f, 0.01f).life(6 + RANDOM.nextInt(6)).drag(0.85f).streak(1.4f, 0.06f));
        }
    }

    /** A soft flash facing the camera. */
    static final class Burst implements WorldFx.Effect {
        private final Vec3 at;
        private final float size;
        private final int color;
        private final int life;
        private final double start = FxClock.ticks();

        Burst(Vec3 at, float size, int color, int life) {
            this.at = at;
            this.size = size;
            this.color = color;
            this.life = life;
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double t = (f.now() - start) / life;
            if (t < 0 || t >= 1) {
                return;
            }
            Vec3 c = f.relative(at);
            float s = (float) Math.min(size * (0.6 + 0.4 * t), c.length() * 0.45);
            VertexConsumer glow = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.GLOW));
            RelicDraw.billboard(glow, f.camera(), c, s, 0f, color, (float) ((1 - t) * (1 - t)));
        }
    }

    /** A ring spreading from {@code from} to {@code to} blocks: facing the camera, or lying on the ground. */
    static final class Ring implements WorldFx.Effect {
        private final Vec3 at;
        private final float from;
        private final float to;
        private final int color;
        private final int life;
        private final boolean flat;
        private final double start = FxClock.ticks();

        Ring(Vec3 at, float from, float to, int color, int life, boolean flat) {
            this.at = at;
            this.from = from;
            this.to = to;
            this.color = color;
            this.life = life;
            this.flat = flat;
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start < life;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double t = (f.now() - start) / life;
            if (t < 0 || t >= 1) {
                return;
            }
            float size = (float) (from + (to - from) * (1 - Math.pow(1 - t, 2.2)));
            float a = (float) ((1 - t) * (1 - t));
            VertexConsumer ring = f.buffers().getBuffer(FxRenderTypes.additive(RelicDraw.RING));
            if (flat) {
                RelicDraw.flat(ring, f.relative(at), size, 0f, color, a);
            } else {
                RelicDraw.billboard(ring, f.camera(), f.relative(at), size, 0f, color, a);
            }
        }
    }

    /** Each note's colour by its tone: the scale runs from deep violet (D) to pale lilac (B). */
    static final class NoteColors {
        private static final int[] BY_STEP = {0xB07CFF, 0xA38CFF, 0xC38BFF, 0xCFB2FF, 0xE8DCFF};

        static int of(int semitones) {
            int s = Math.floorMod(semitones, 12);
            int step = switch (s) {
                case 0 -> 0;
                case 2 -> 1;
                case 4 -> 2;
                case 7 -> 3;
                default -> 4;
            };
            return BY_STEP[step];
        }
    }
}
