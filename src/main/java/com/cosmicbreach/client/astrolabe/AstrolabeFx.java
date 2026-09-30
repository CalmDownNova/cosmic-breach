package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.astrolabe.AstrolabeEffects;
import com.cosmicbreach.astrolabe.AstrolabeTunes;
import com.cosmicbreach.astrolabe.Astrolabes;
import com.cosmicbreach.astrolabe.PocketStar;
import com.cosmicbreach.astrolabe.StarBolt;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.client.fx.Afterimages;
import com.cosmicbreach.client.fx.ClientMoveEffect;
import com.cosmicbreach.client.fx.ClientMoveEffects;
import com.cosmicbreach.client.fx.FxBudget;
import com.cosmicbreach.client.fx.FxClock;
import com.cosmicbreach.client.fx.FxParticles;
import com.cosmicbreach.client.fx.FxRenderTypes;
import com.cosmicbreach.client.fx.WorldFx;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.net.MoveEffectPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the Choir Astrolabe looks and sounds like beyond its own entities (GDD 4.2): sparks where bolts land, the
 * Starfall's ring, the Constellation's small stars over marked enemies and its beam through them playing an arpeggio,
 * the Parallax's afterimage, the Pocket Star's pulses, swallowed projectiles and Singularity, the Supernova's expanding
 * white sphere, ring and flash; and the chimes of the local player's own bolts (every bolt a note of D major
 * pentatonic, {@link AstrolabeTunes}), played at once from prediction. Client thread.
 */
final class AstrolabeFx {
    private static final RandomSource RANDOM = RandomSource.create();
    /** The local player's bar of the phrase (the server counts its own for everyone else's ears). */
    private static int bar;
    /** Marks shown, by marking player: the marked entities in order. */
    private static final Map<Integer, List<Integer>> MARKS = new HashMap<>();
    private static boolean marksEffect;

    private AstrolabeFx() {
    }

    static void register() {
        ClientMoveEffects.register(AstrolabeEffects.STAR_BOLT, new ClientMoveEffect() {
            @Override
            public void active(Player player, MoveDef def, MoveEffect effect) {
                handFlash(player, effect.intParam("hand", 1));
            }

            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                boltLanded(m);
            }
        });
        ClientMoveEffects.register(AstrolabeEffects.CONSTELLATION, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                constellation(player, m);
            }
        });
        ClientMoveEffects.register(AstrolabeEffects.PARALLAX, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                if (player != null) {
                    Afterimages.leave(player, m.at().subtract(0, player.getBbHeight() * 0.62, 0), AstroDraw.VIOLET, 0.55f, 14);
                }
                sparkle(m.at(), 8, AstroDraw.VIOLET, 0.08);
            }
        });
        ClientMoveEffects.register(AstrolabeEffects.POCKET_STAR, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                pocketStar(m);
            }
        });
        ClientMoveEffects.register(AstrolabeEffects.SUPERNOVA, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload m) {
                boolean singular = m.ticks() >= 100000;
                WorldFx.add(new Nova(m.at(), m.value(), singular));
                sparkle(m.at(), 40, singular ? AstroDraw.VIOLET : AstroDraw.WHITE_GOLD, 0.35);
                LocalPlayer me = Minecraft.getInstance().player;
                if (me != null && me.position().distanceTo(m.at()) < 16) {
                    ClientCombat.feelSlam(0.35 * (1.0 - me.position().distanceTo(m.at()) / 16.0), 1.0);
                }
            }
        });
        ClientCombat.addEventListener(AstrolabeFx::ownChimes);
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    // ------------------------------------------------------------------ chimes

    /** The local player's bolt moves: their notes, now. */
    private static void ownChimes(CombatEvent event) {
        if (!(event instanceof CombatEvent.ActiveTick active) || active.activeTick() != 0) {
            return;
        }
        MoveDef def = active.move().def();
        if (def.traits().effect(AstrolabeEffects.STAR_BOLT).isEmpty()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        WeaponDef weapon = player == null ? null : PlayerCombat.of(player).machine().weapon();
        if (weapon == null) {
            return;
        }
        int slot = weapon.combo().indexOf(active.move().id());
        int[] notes;
        if (slot == 0) {
            bar++;
            notes = new int[] {AstrolabeTunes.first(bar)};
        } else if (slot == 1) {
            notes = new int[] {AstrolabeTunes.second(bar)};
        } else if (slot == 2) {
            notes = AstrolabeTunes.chord(bar);
        } else {
            notes = new int[] {AstrolabeTunes.starfall(bar)};
        }
        for (int n : notes) {
            player.level().playLocalSound(player, Astrolabes.CHIME.get(), SoundSource.PLAYERS, notes.length > 1 ? 0.6f : 0.8f,
                    AstrolabeTunes.pitch(n));
        }
    }

    private static void note(Vec3 at, int semitones, float volume) {
        ClientLevel level = level();
        if (level != null) {
            level.playLocalSound(at.x, at.y, at.z, Astrolabes.CHIME.get(), SoundSource.PLAYERS, volume, AstrolabeTunes.pitch(semitones), false);
        }
    }

    // ------------------------------------------------------------------ bolts

    /** The free hand's flick: a small burst at the hand as the bolt leaves. */
    private static void handFlash(Player player, int hand) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(right.scale(0.32 * hand)).add(0, -0.3, 0);
        if (WorldFx.firstPersonOf(player)) {
            at = at.add(player.getLookAngle().scale(0.4));
        }
        sparkle(at, 3, AstroDraw.GOLD, 0.03);
    }

    private static void boltLanded(MoveEffectPayload m) {
        int color = StarBoltRenderer.color((byte) m.value());
        switch (m.stage()) {
            case StarBolt.BURST -> {
                sparkle(m.at(), 7, color, 0.09);
                FxBudget.spawn(FxParticles.glint(level(), m.at()).color(0xFFFFFF).size(0.45f, 0.05f).life(6).spin(0.3f));
            }
            case StarBolt.SPLASH -> {
                sparkle(m.at(), 16, color, 0.18);
                FxBudget.spawn(FxParticles.ring(level(), m.at().add(0, 0.1, 0)).flat().color(color).size(0.4f, 4.2f).sizeEase(2f).life(10));
                FxBudget.spawn(FxParticles.glint(level(), m.at()).color(0xFFFFFF).size(0.9f, 0.1f).life(7));
            }
            default -> sparkle(m.at(), 3, color, 0.03);
        }
    }

    static void sparkle(Vec3 at, int wanted, int color, double speed) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        int n = FxBudget.count(wanted, at, true);
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize()
                    .scale(speed * (0.4 + RANDOM.nextDouble() * 0.8));
            if (RANDOM.nextBoolean()) {
                FxBudget.spawn(FxParticles.spark(level, at).velocity(v).color(color).size(0.035f, 0.01f).life(8 + RANDOM.nextInt(5)).drag(0.86f));
            } else {
                FxBudget.spawn(FxParticles.glint(level, at).velocity(v.scale(0.6)).color(color).size(0.14f, 0.02f).life(10 + RANDOM.nextInt(6))
                        .drag(0.9f).spin(0.2f));
            }
        }
    }

    // ------------------------------------------------------------------ the Constellation

    private static void constellation(@Nullable Player player, MoveEffectPayload m) {
        int owner = m.entityId();
        switch (m.stage()) {
            case AstrolabeEffects.MARK -> {
                MARKS.computeIfAbsent(owner, k -> new ArrayList<>()).add(m.ticks());
                if (!marksEffect) {
                    marksEffect = true;
                    WorldFx.add(new Marks());
                }
                sparkle(m.at(), 5, AstroDraw.WHITE_GOLD, 0.05);
            }
            case AstrolabeEffects.CLEAR -> MARKS.remove(owner);
            case AstrolabeEffects.LINK -> {
                Beam beam = Beam.of(owner);
                int index = (int) m.value();
                if (index == 0 || beam == null) {
                    beam = new Beam(owner);
                    Beam.put(owner, beam);
                    WorldFx.add(beam);
                }
                if (index >= 0) {
                    beam.add(m.ticks(), m.at());
                    Vec3 at = m.at();
                    int note = AstrolabeTunes.arpeggio(index);
                    WorldFx.after(index, () -> {
                        note(at, note, 0.8f);
                        sparkle(at, 8, AstroDraw.PALE_BLUE, 0.12);
                    });
                } else {
                    beam.add(-1, m.at());
                }
                MARKS.remove(owner);
            }
            default -> {
            }
        }
    }

    /** Small turning stars over every marked enemy, until the marks clear or the beam takes them. */
    private static final class Marks implements WorldFx.Effect {
        @Override
        public boolean tick() {
            if (MARKS.isEmpty()) {
                marksEffect = false;
                return false;
            }
            return true;
        }

        @Override
        public void render(WorldFx.Frame f) {
            ClientLevel level = level();
            if (level == null) {
                return;
            }
            VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(AstroDraw.STAR));
            float[] c = WorldFx.rgb(AstroDraw.WHITE_GOLD);
            for (List<Integer> ids : MARKS.values()) {
                for (int i = 0; i < ids.size(); i++) {
                    Entity e = level.getEntity(ids.get(i));
                    if (e == null) {
                        continue;
                    }
                    Vec3 top = e.getPosition(f.partialTick()).add(0, e.getBbHeight() + 0.45, 0);
                    float pulse = 0.85f + 0.15f * (float) Math.sin(f.now() * 0.4 + i);
                    WorldFx.billboard(out, f.camera(), f.relative(top), 0.28f * pulse, c[0], c[1], c[2], 0.95f);
                }
            }
        }
    }

    /** The beam: from the caster's hand through each mark in turn, one link a tick, then fading. */
    private static final class Beam implements WorldFx.Effect {
        private static final Map<Integer, Beam> BEAMS = new HashMap<>();
        private static final int LIFE = 14;
        private final int owner;
        private final List<Integer> ids = new ArrayList<>();
        private final List<Vec3> points = new ArrayList<>();
        private final double start = FxClock.ticks();

        Beam(int owner) {
            this.owner = owner;
        }

        static @Nullable Beam of(int owner) {
            return BEAMS.get(owner);
        }

        static void put(int owner, Beam beam) {
            BEAMS.put(owner, beam);
        }

        void add(int id, Vec3 at) {
            ids.add(id);
            points.add(at);
        }

        @Override
        public boolean tick() {
            if (FxClock.ticks() - start > LIFE) {
                BEAMS.remove(owner, this);
                return false;
            }
            return true;
        }

        @Override
        public void render(WorldFx.Frame f) {
            ClientLevel level = level();
            if (level == null || points.isEmpty()) {
                return;
            }
            Entity caster = level.getEntity(owner);
            double age = f.now() - start;
            float fade = (float) Mth.clamp(1.0 - (age - 6) / (LIFE - 6), 0.0, 1.0);
            Vec3 from = caster == null ? points.get(0) : caster.getPosition(f.partialTick()).add(0, caster.getBbHeight() * 0.7, 0)
                    .add(caster.getViewVector(f.partialTick()).scale(0.7));
            VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(AstroDraw.BEAM));
            float[] core = WorldFx.rgb(0xFFFFFF);
            float[] edge = WorldFx.rgb(AstroDraw.PALE_BLUE);
            Vec3 prev = from;
            for (int i = 0; i < points.size(); i++) {
                if (age < i) {
                    break; // the arpeggio: a link a tick
                }
                int id = ids.get(i);
                Entity e = id < 0 ? null : level.getEntity(id);
                Vec3 p = e == null ? points.get(i) : e.getPosition(f.partialTick()).add(0, e.getBbHeight() * 0.55, 0);
                Vec3 a = f.relative(prev);
                Vec3 b = f.relative(p);
                AstroDraw.ribbon(out, f.camera(), a, b, 0.22f, edge, 0.45f * fade, edge, 0.45f * fade);
                AstroDraw.ribbon(out, f.camera(), a, b, 0.07f, core, 0.95f * fade, core, 0.95f * fade);
                prev = p;
            }
        }
    }

    // ------------------------------------------------------------------ the Pocket Star and the Supernova

    private static void pocketStar(MoveEffectPayload m) {
        ClientLevel level = level();
        if (level == null || !FxParticles.ready()) {
            return;
        }
        switch (m.stage()) {
            case PocketStar.PLACED -> {
                FxBudget.spawn(FxParticles.ring(level, m.at()).color(AstroDraw.SUN).size(0.3f, 1.4f).sizeEase(2f).life(8));
                sparkle(m.at(), 14, AstroDraw.SUN, 0.14);
            }
            case PocketStar.PULSE -> {
                // a ring of light running out across the ground to the pulse's reach
                FxBudget.spawn(FxParticles.ring(level, groundUnder(level, m.at())).flat().color(AstroDraw.SUN).size(0.4f, m.value() * 2.0f)
                        .sizeEase(2f).life(9));
                sparkle(m.at().add(0, 0.25, 0), 6, AstroDraw.GOLD, 0.12);
            }
            case PocketStar.SWALLOWED -> sparkle(m.at(), 10, AstroDraw.WHITE_GOLD, 0.1);
            case PocketStar.SINGULARITY -> {
                FxBudget.spawn(FxParticles.ring(level, m.at().add(0, 0.25, 0)).color(AstroDraw.DEEP_VIOLET).size(3.5f, 0.4f).life(12));
                sparkle(m.at(), 16, AstroDraw.VIOLET, 0.1);
            }
            case PocketStar.FADED -> sparkle(m.at().add(0, 0.25, 0), 10, AstroDraw.SUN, 0.05);
            default -> {
            }
        }
    }

    /** Just above the ground under {@code at} (within 4 blocks), or {@code at} itself. */
    private static Vec3 groundUnder(ClientLevel level, Vec3 at) {
        net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(at);
        for (int d = 0; d <= 4; d++) {
            net.minecraft.core.BlockPos below = p.below(d + 1);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(at.x, below.getY() + 1.06, at.z);
            }
        }
        return at;
    }

    /** The Supernova: a white sphere swelling to its radius, a ring racing out across the ground, and a flash. */
    private static final class Nova implements WorldFx.Effect {
        private static final int LIFE = 16;
        private final Vec3 at;
        private final float radius;
        private final boolean singular;
        private final double start = FxClock.ticks();

        Nova(Vec3 at, float radius, boolean singular) {
            this.at = at;
            this.radius = radius;
            this.singular = singular;
        }

        @Override
        public boolean tick() {
            return FxClock.ticks() - start <= LIFE;
        }

        @Override
        public void render(WorldFx.Frame f) {
            double age = f.now() - start;
            double grow = Mth.clamp(age / 5.0, 0.0, 1.0);
            double r = radius * (1.0 - Math.pow(1.0 - grow, 3.0));
            float fade = (float) Mth.clamp(1.0 - (age - 4) / (LIFE - 4), 0.0, 1.0);
            float[] white = WorldFx.rgb(singular ? 0xF2E6FF : 0xFFFBF0);
            Vec3 c = f.relative(at.add(0, 0.25, 0));
            VertexConsumer shell = f.buffers().getBuffer(FxRenderTypes.additive(AstroDraw.GLOW));
            AstroDraw.shell(shell, c, Math.max(0.2, r), 10, 20, white, 0.8f * fade);
            float flash = (float) Mth.clamp(1.0 - age / 4.0, 0.0, 1.0);
            WorldFx.billboard(shell, f.camera(), c, (float) (radius * 1.6), white[0], white[1], white[2], 0.9f * flash);
            VertexConsumer ring = f.buffers().getBuffer(FxRenderTypes.additive(AstroDraw.RING));
            double rr = radius * (0.3 + 1.2 * Mth.clamp(age / 8.0, 0.0, 1.0));
            float[] gold = WorldFx.rgb(singular ? AstroDraw.VIOLET : AstroDraw.GOLD);
            WorldFx.flat(ring, f.relative(at.add(0, 0.08, 0)).add(0, -0.2, 0), (float) rr, gold[0], gold[1], gold[2], 0.8f * fade);
            WorldFx.billboard(ring, f.camera(), c, (float) (rr * 0.9), gold[0], gold[1], gold[2], 0.5f * fade);
        }
    }
}
