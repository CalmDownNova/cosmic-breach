package com.cosmicbreach.client.fx;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.client.anim.PlayerAnimations;
import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.client.combat.ClientCombat;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.combat.server.effect.Crater;
import com.cosmicbreach.combat.server.effect.GravityWell;
import com.cosmicbreach.net.MoveEffectPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The Comet Maul's visuals (GDD 4.2), as move effect handlers:
 * <ul>
 *   <li>{@code cosmicbreach:slam}: where the head strikes the ground ({@code ahead} blocks in front, or the
 *       feet of a landing): cracks that fade over 3 s, chunks of the block underfoot kicked up, a shockwave
 *       ring, a flash and embers, and through the attacker's own eyes a camera dip. A landing grows with the
 *       fall.</li>
 *   <li>{@code cosmicbreach:embers}: ember sparks shed off the drawn head through the swing, beside the
 *       orange-white trail ({@link SlashTrail} with the move's colour).</li>
 *   <li>{@code cosmicbreach:comet}: the Meteorfall's comet: a streaming tail and embers off the head while
 *       diving, a hot glow on the head.</li>
 *   <li>{@code cosmicbreach:gravity_well}: the well's dust sphere ({@link GravityWellEffect}) and its
 *       Collapse (a flash, a ring, sparks flung out, a camera kick), from the server's moments.</li>
 *   <li>{@code cosmicbreach:crater}: the Cratered ground's own cracks, for as long as the crater lasts.</li>
 * </ul>
 * No smoke anywhere: the dust is the well's, and debris is the ground's own blocks.
 */
public final class MaulVisuals {
    public static final ResourceLocation SLAM = CosmicBreach.id("slam");
    public static final ResourceLocation EMBERS = CosmicBreach.id("embers");
    public static final ResourceLocation COMET = CosmicBreach.id("comet");

    public static final int EMBER = 0xFF8A2E;
    public static final int COMET_WHITE = 0xFFF1DA;
    public static final int COMET_ORANGE = 0xFFA650;
    private static final int CRACK_GLOW = 0xFF7A26;
    private static final int COLLAPSE_VIOLET = 0xB9A8FF;
    private static final RandomSource RANDOM = RandomSource.create();
    /** Cracks fade over this (the GDD's 3 s). */
    public static final int CRACK_TICKS = 60;

    /** The wells running, by the player whose they are. */
    private static final Map<Integer, GravityWellEffect> WELLS = new HashMap<>();

    private MaulVisuals() {
    }

    public static void register() {
        ClientMoveEffects.register(SLAM, new ClientMoveEffect() {
            @Override
            public void active(Player player, MoveDef def, MoveEffect effect) {
                Vec3 feet = player.position();
                Vec3 at = feet.add(BodyMotion.forward(player.getYRot()).scale(effect.param("ahead", 1.9)));
                slam(player, at, effect, 1.0);
            }

            @Override
            public boolean landed(Player player, MoveDef def, MoveEffect effect, double fallBlocks) {
                double grow = 1.0 + Math.min(fallBlocks, 15.0) * effect.param("per_block", 0.05);
                Vec3 at = player.position().add(BodyMotion.forward(player.getYRot()).scale(effect.param("ahead", 0.0)));
                slam(player, at, effect, grow);
                return true;
            }
        });
        ClientMoveEffects.register(EMBERS, new ClientMoveEffect() {
            @Override
            public void started(Player player, MoveDef def, MoveEffect effect) {
                int ticks = def.timing().startup() + def.timing().active() + effect.intParam("after", 3);
                int from = Math.max(0, def.timing().startup() - effect.intParam("before", 3));
                int perTick = effect.intParam("per_tick", 3);
                int[] tick = {0};
                WorldFx.follow(player, ticks, p -> {
                    if (tick[0]++ >= from && p instanceof Player swinging) {
                        headEmbers(swinging, perTick);
                    }
                });
            }
        });
        ClientMoveEffects.register(COMET, new ClientMoveEffect() {
            @Override
            public void started(Player player, MoveDef def, MoveEffect effect) {
                WorldFx.add(new Comet(player, def.animation()));
            }
        });
        ClientMoveEffects.register(GravityWell.ID, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload moment) {
                gravityWell(player, moment);
            }
        });
        ClientMoveEffects.register(Crater.ID, new ClientMoveEffect() {
            @Override
            public void serverMoment(@Nullable Player player, MoveEffectPayload moment) {
                if (moment.stage() == Crater.OPENED) {
                    Vec3 at = moment.at();
                    WorldFx.add(new CrackDecal(at, moment.value() * 0.95f, RANDOM.nextFloat() * Mth.TWO_PI, CRACK_GLOW, 0.75f,
                            Math.max(CRACK_TICKS, moment.ticks())));
                    GlowEffect.ground(at, moment.value() * 0.9f, EMBER, 0.35f, Math.max(20, moment.ticks() / 2));
                }
            }
        });
    }

    // ------------------------------------------------------------------ the slam

    /**
     * A slam at {@code at} (feet height; the ground is found under it), scaled by {@code grow}: cracks, debris,
     * a shockwave ring, a flash, embers, and the camera for the attacker's own view.
     */
    static void slam(Player player, Vec3 at, MoveEffect effect, double grow) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FxParticles.ready()) {
            return;
        }
        Vec3 ground = groundAt(level, at);
        float crack = (float) (effect.param("crack", 1.6) * grow);
        float ring = (float) (effect.param("ring", 3.0) * grow);
        WorldFx.add(new CrackDecal(ground, crack, RANDOM.nextFloat() * Mth.TWO_PI, CRACK_GLOW, 0.8f, CRACK_TICKS));
        GlowEffect.groundRing(ground, 0.3f, ring, COMET_ORANGE, 1.0f, 10);
        GlowEffect.groundRing(ground.add(0, 0.01, 0), 0.2f, ring * 0.55f, COMET_WHITE, 0.8f, 7);
        GlowEffect.flash(ground.add(0, 0.35, 0), (float) (0.8 * Math.sqrt(grow)), COMET_WHITE, 0.75f, 5);
        debris(level, ground, (int) Math.round(effect.param("debris", 14) * Math.sqrt(grow)), grow);
        embersBurst(level, ground, (int) Math.round(effect.param("embers", 10) * Math.sqrt(grow)), grow);
        if (player == Minecraft.getInstance().player) {
            ClientCombat.feelSlam(effect.param("shake", 0.25) * Math.min(1.6, grow), effect.param("kick", 2.5) * Math.min(1.5, grow));
        }
    }

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

    /** Chunks of the block under {@code ground} kicked up and out: the block's own particles. */
    private static void debris(ClientLevel level, Vec3 ground, int count, double grow) {
        BlockPos under = BlockPos.containing(ground.x, ground.y - 0.5, ground.z);
        BlockState state = level.getBlockState(under);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, state);
        int n = FxBudget.count(count, ground, true);
        for (int i = 0; i < n; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double out = 0.15 + RANDOM.nextDouble() * 0.25 * grow;
            double r = RANDOM.nextDouble() * 0.9 * Math.sqrt(grow);
            level.addParticle(chunk, ground.x + Math.cos(angle) * r, ground.y + 0.1, ground.z + Math.sin(angle) * r,
                    Math.cos(angle) * out, 0.3 + RANDOM.nextDouble() * 0.35, Math.sin(angle) * out);
        }
    }

    /** Hot sparks thrown up and out from a strike. */
    private static void embersBurst(ClientLevel level, Vec3 ground, int count, double grow) {
        int n = FxBudget.count(count, ground, true);
        for (int i = 0; i < n; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            Vec3 v = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(0.12 + RANDOM.nextDouble() * 0.22 * grow)
                    .add(0, 0.2 + RANDOM.nextDouble() * 0.3, 0);
            FxBudget.spawn(FxParticles.spark(level, ground.add(0, 0.15, 0)).velocity(v)
                    .color(CombatEffects.mix(EMBER, COMET_WHITE, RANDOM.nextFloat() * 0.6f)).size(0.035f, 0.012f)
                    .life(9 + RANDOM.nextInt(8)).gravity(0.7f).drag(0.9f).physics().streak(1.5f, 0.08f));
        }
    }

    // ------------------------------------------------------------------ embers off the head

    /** A few embers shed off the drawn head, drifting up and cooling. */
    static void headEmbers(Player player, int count) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FxParticles.ready()) {
            return;
        }
        boolean own = WorldFx.firstPersonOf(player);
        float half = BladeTracker.halfWidth(player);
        Vec3 probe = CombatEffects.bladePoint(player, 0.6);
        int n = FxBudget.count(count, probe, false);
        for (int i = 0; i < n; i++) {
            double edge = (RANDOM.nextDouble() * 2.0 - 1.0) * half;
            Vec3 at = CombatEffects.bladePoint(player, 0.15 + RANDOM.nextDouble() * 0.85, edge);
            Vec3 v = new Vec3((RANDOM.nextDouble() - 0.5) * 0.05, 0.02 + RANDOM.nextDouble() * 0.05, (RANDOM.nextDouble() - 0.5) * 0.05);
            FxBudget.spawn(FxParticles.spark(level, at).velocity(v).drag(0.92f).gravity(-0.02f)
                    .color(CombatEffects.mix(EMBER, COMET_WHITE, RANDOM.nextFloat() * 0.7f))
                    .size(own ? 0.02f : 0.035f, 0.008f).life(8 + RANDOM.nextInt(7)).fade(0.1f, 1.2f).streak(1.2f, 0.05f));
        }
    }

    // ------------------------------------------------------------------ the Meteorfall's comet

    /** While the dive plays: a comet's tail streaming up off the head, embers, and a hot glow on the head. */
    private static final class Comet implements WorldFx.Effect {
        private static final int MAX_TICKS = 220;
        private final Player player;
        private final ResourceLocation animation;
        private int ticks;

        Comet(Player player, ResourceLocation animation) {
            this.player = player;
            this.animation = animation;
        }

        private boolean diving() {
            if (player.isRemoved() || !(player instanceof AbstractClientPlayer client)) {
                return false;
            }
            return animation.equals(PlayerAnimations.state(client).animation());
        }

        @Override
        public boolean tick() {
            if (++ticks > MAX_TICKS || !diving()) {
                return false;
            }
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || ticks < 2) {
                return level != null;
            }
            Vec3 head = CombatEffects.bladePoint(player, 0.6);
            Vec3 fall = player.getDeltaMovement();
            Vec3 back = fall.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : fall.normalize().scale(-1);
            boolean own = WorldFx.firstPersonOf(player);
            int n = FxBudget.count(own ? 2 : 4, head, true);
            for (int i = 0; i < n; i++) {
                Vec3 at = head.add((RANDOM.nextDouble() - 0.5) * 0.3, (RANDOM.nextDouble() - 0.5) * 0.3, (RANDOM.nextDouble() - 0.5) * 0.3);
                FxBudget.spawn(FxParticles.spark(level, at).streakAlong(back, (float) (0.8 + RANDOM.nextDouble() * 0.9))
                        .velocity(back.scale(0.05)).size(own ? 0.05f : 0.09f, 0.02f).life(5 + RANDOM.nextInt(4))
                        .color(CombatEffects.mix(COMET_ORANGE, COMET_WHITE, RANDOM.nextFloat())));
            }
            headEmbers(player, 2);
            return true;
        }

        @Override
        public void render(WorldFx.Frame f) {
            if (ticks < 2) {
                return;
            }
            Vec3 head = CombatEffects.bladePoint(player, 0.6);
            VertexConsumer out = f.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            float[] c = WorldFx.rgb(COMET_WHITE);
            float size = WorldFx.firstPersonOf(player) ? 0.25f : 0.55f;
            float pulse = 0.85f + 0.15f * (float) Math.sin(f.now() * 2.1);
            WorldFx.billboard(out, f.camera(), f.relative(head), size * pulse, c[0], c[1], c[2], 0.8f);
            float[] o = WorldFx.rgb(COMET_ORANGE);
            WorldFx.billboard(out, f.camera(), f.relative(head), size * 1.8f, o[0], o[1], o[2], 0.35f);
        }
    }

    // ------------------------------------------------------------------ the Gravity Well

    private static void gravityWell(@Nullable Player player, MoveEffectPayload moment) {
        GravityWellEffect well = WELLS.get(moment.entityId());
        switch (moment.stage()) {
            case GravityWell.PLANTED -> {
                if (well != null) {
                    well.fade();
                }
                GravityWellEffect next = new GravityWellEffect(moment.at(), moment.value(), moment.ticks());
                WELLS.put(moment.entityId(), next);
                WorldFx.add(next);
                GlowEffect.groundRing(moment.at(), moment.value() * 1.15f, 0.4f, COLLAPSE_VIOLET, 0.6f, 10);
            }
            case GravityWell.COLLAPSE -> {
                if (well != null) {
                    well.collapse();
                    WELLS.remove(moment.entityId());
                }
                collapse(player, moment.at(), moment.value(), moment.ticks() / 100.0);
            }
            case GravityWell.FADED -> {
                if (well != null) {
                    well.fade();
                    WELLS.remove(moment.entityId());
                }
            }
            default -> {
            }
        }
    }

    /** The Collapse at {@code centre}: a flash, a ring racing out, sparks flung wide, the camera kicked. */
    private static void collapse(@Nullable Player player, Vec3 centre, float radius, double strength) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 core = centre.add(0, 0.9, 0);
        float s = (float) Math.max(0.4, strength);
        GlowEffect.flash(core, 1.4f * s, COMET_WHITE, 0.85f, 5);
        GlowEffect.flash(core, 2.3f * s, COLLAPSE_VIOLET, 0.4f, 8);
        GlowEffect.ring(core, 0.4f, radius * 1.4f * s, COMET_WHITE, 0.9f, 9);
        GlowEffect.groundRing(centre, 0.5f, radius * 1.6f * s, COMET_ORANGE, 1.0f, 12);
        WorldFx.add(new CrackDecal(groundAt(level, centre), radius * 0.8f * s, RANDOM.nextFloat() * Mth.TWO_PI, CRACK_GLOW, 0.8f,
                CRACK_TICKS));
        int n = FxBudget.count((int) (18 * s), core, true);
        for (int i = 0; i < n; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), Math.abs(RANDOM.nextGaussian()) * 0.6, RANDOM.nextGaussian()).normalize();
            FxBudget.spawn(FxParticles.spark(level, core.add(dir.scale(0.4))).velocity(dir.scale(0.22 + RANDOM.nextDouble() * 0.25))
                    .color(CombatEffects.mix(COMET_ORANGE, COMET_WHITE, RANDOM.nextFloat() * 0.6f)).size(0.04f, 0.012f)
                    .life(7 + RANDOM.nextInt(6)).gravity(0.5f).drag(0.84f).streak(1.2f, 0.08f));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double distance = mc.player.position().distanceTo(centre);
            if (player == mc.player) {
                ClientCombat.feelSlam(0.45 * s, 4.0 * s);
            } else if (distance < 14.0) {
                ClientCombat.feelSlam(0.3 * s * (1.0 - distance / 14.0), 0.0);
            }
        }
    }
}
