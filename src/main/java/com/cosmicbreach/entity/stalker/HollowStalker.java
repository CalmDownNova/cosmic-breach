package com.cosmicbreach.entity.stalker;

import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.combat.PoiseSource;
import com.cosmicbreach.combat.Staggerable;
import com.cosmicbreach.entity.stalker.StalkerRules.Attack;
import com.cosmicbreach.entity.stalker.StalkerRules.StepCandidate;
import com.cosmicbreach.status.Rift;
import com.cosmicbreach.world.weather.EclipseSurge;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Hollow Stalker (GDD 7.1), the Rift Abyss's ambusher: a tall thin shape of void, 2.6 blocks high, nearly
 * invisible but for a porcelain mask with magenta eye slits. Health 40, armor 6, poise 20, 0.30 in the dark. Every rule
 * and number is {@link StalkerRules}; this carries them out.
 *
 * <ul>
 *   <li><b>Stalking</b>: it paths through the dark ({@link StalkerNavigation}) toward a point behind its target, never
 *       into block light of 12 or more, and leaves such light at once (a torch placed by it, an ability's light).</li>
 *   <li><b>Caught</b>: looked at directly within 6 blocks, it freezes for 10 ticks, its mask flaring with a shriek,
 *       then backs off into the dark; it can't be caught again for 30 ticks.</li>
 *   <li><b>Shadow Step</b>: when its target looks away and it is more than 4 blocks off, it teleports up to 10 blocks
 *       (20 in an Eclipse Surge) to the dark spot nearest behind the target; 100 ticks between steps.</li>
 *   <li><b>Rend</b>: a 20-tick tell (the mask flares magenta, a rising whisper, a gold glint at tick 16), then 12
 *       damage and a stack of Rift; parryable on the hit, which staggers it.</li>
 *   <li><b>Grasp</b>: only on a target in the dark facing away; a whisper behind it for 14 ticks, then a hold: 3
 *       damage every 10 ticks for 40 ticks through armor, the target unable to walk or jump, until it dashes or
 *       parries ({@link StalkerGrasp}). Once per 30 s.</li>
 * </ul>
 * Silent footsteps; whispers now and then (one at a time, rarely); dark flakes that fall upward.
 */
public class HollowStalker extends Monster implements GeoEntity, ParryableAttacker, PoiseSource, Staggerable {
    public static final int EXPERIENCE = 10;
    public static final byte EVENT_STEP_FROM = 80;
    public static final byte EVENT_STEP_TO = 81;
    public static final byte EVENT_GLINT = 82;
    public static final byte EVENT_REND = 83;
    public static final byte EVENT_GRASP = 84;

    public enum State { STALK, CAUGHT, REND_TELL, REND_RECOVER, GRASP_TELL, GRASPING, FLEE, STAGGER }

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(HollowStalker.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE_COUNT = SynchedEntityData.defineId(HollowStalker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HELD = SynchedEntityData.defineId(HollowStalker.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation CAUGHT_ANIM = RawAnimation.begin().thenPlayAndHold("caught");
    private static final RawAnimation REND_TELL_ANIM = RawAnimation.begin().thenPlayAndHold("rend_tell");
    private static final RawAnimation REND_ANIM = RawAnimation.begin().thenPlayAndHold("rend");
    private static final RawAnimation GRASP_TELL_ANIM = RawAnimation.begin().thenPlayAndHold("grasp_tell");
    private static final RawAnimation GRASP_ANIM = RawAnimation.begin().thenLoop("grasp");
    private static final RawAnimation STAGGER_ANIM = RawAnimation.begin().thenPlayAndHold("stagger");
    private static final double WALK_ABOVE = 0.012;
    /** Attack reach of the grasp hold: the target is held this far in front of it. */
    private static final double HOLD_DISTANCE = 0.75;
    /** No two whispers this close together within {@value #WHISPER_REACH} blocks of each other. */
    private static final int WHISPER_GAP = 160;
    private static final double WHISPER_REACH = 32.0;
    private static long lastWhisper = Long.MIN_VALUE / 2;
    private static @Nullable Vec3 lastWhisperAt;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // server state
    private State state = State.STALK;
    private int stateTicks;
    private int stateCount;
    private int stepCooldown = 40;
    private int graspCooldown;
    private int caughtImmune;
    private int waitedClose;
    private int fleeTicks;
    private int whisperCooldown = 200;
    private int staggerTicks;
    private boolean rendActive;
    private @Nullable UUID held;
    private int heldTicks;
    private int rethink;
    private @Nullable Vec3 fleeTo;
    // for tests and tools
    private int steps;
    private int caughtCount;
    private int rends;
    private int rendHits;
    private int grasps;
    private int lightEscapes;
    private boolean inBrightLight;
    private int maxBlockLightStood;
    // client
    private int animatedCount = -1;
    private int clientStateCount = -1;
    private int clientStateTicks;

    public HollowStalker(EntityType<? extends HollowStalker> type, Level level) {
        super(type, level);
        this.xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, StalkerRules.HEALTH)
                .add(Attributes.ARMOR, StalkerRules.ARMOR)
                .add(Attributes.MOVEMENT_SPEED, StalkerRules.SPEED)
                .add(Attributes.ATTACK_DAMAGE, StalkerRules.REND_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, StalkerRules.FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_STATE_COUNT, 0);
        builder.define(DATA_HELD, 0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new StalkerNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    /** Darker ground is better for wandering. */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level instanceof Level l ? 15.0f - StalkerLight.effective(l, pos) : 0.0f;
    }

    // ------------------------------------------------------------------ state

    public State state() {
        byte b = entityData.get(DATA_STATE);
        State[] all = State.values();
        return b >= 0 && b < all.length ? all[b] : State.STALK;
    }

    /** Counts state changes (a state entered twice in a row counts twice). */
    public int stateCount() {
        return entityData.get(DATA_STATE_COUNT);
    }

    /** True while the mask flares (caught, or telling a Rend). */
    public boolean maskFlaring() {
        State s = state();
        return s == State.CAUGHT || s == State.REND_TELL;
    }

    /** The player held by a Grasp, either side (null for none). */
    public @Nullable Player heldPlayer() {
        int id = entityData.get(DATA_HELD) - 1;
        return id < 0 ? null : level().getEntity(id) instanceof Player p ? p : null;
    }

    /** Client: ticks since the synced state last began. */
    public int clientStateTicks() {
        return clientStateTicks;
    }

    private void enter(State next) {
        state = next;
        stateTicks = 0;
        entityData.set(DATA_STATE, (byte) next.ordinal());
        entityData.set(DATA_STATE_COUNT, ++stateCount);
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        think();
    }

    /** Ticks since the stalking path was last found, and the goal it was found for. */
    private int sincePath = 20;
    private @Nullable Vec3 pathGoal;

    private void think() {
        stateTicks++;
        if (stepCooldown > 0) {
            stepCooldown--;
        }
        if (graspCooldown > 0) {
            graspCooldown--;
        }
        if (caughtImmune > 0) {
            caughtImmune--;
        }
        if (whisperCooldown > 0) {
            whisperCooldown--;
        }
        BlockPos feet = blockPosition();
        int blockLight = StalkerLight.block(level(), feet);
        maxBlockLightStood = Math.max(maxBlockLightStood, blockLight);
        Player target = getTarget() instanceof Player p && p.isAlive() && !p.isSpectator() ? p : null;

        if (state == State.STAGGER) {
            getNavigation().stop();
            if (stateTicks >= staggerTicks) {
                enter(State.STALK);
            }
            return;
        }
        boolean bright = StalkerRules.forbidden(blockLight);
        if (bright && !inBrightLight) {
            lightEscapes++; // found itself in light it won't stand in: it leaves as soon as it can move
        }
        inBrightLight = bright;
        if (bright && state != State.CAUGHT) {
            escapeLight(target);
            return;
        }
        if (state == State.GRASPING) {
            tickGrasp();
            return;
        }
        if (state == State.STALK || state == State.REND_TELL || state == State.GRASP_TELL) {
            if (checkCaught()) {
                return;
            }
        }
        switch (state) {
            case CAUGHT -> {
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (stateTicks >= StalkerRules.CAUGHT_TICKS) {
                    caughtImmune = StalkerRules.CAUGHT_IMMUNE;
                    backOff(target, 24);
                }
            }
            case FLEE -> {
                if (fleeTo != null && (rethink-- <= 0)) {
                    getNavigation().moveTo(fleeTo.x, fleeTo.y, fleeTo.z, 1.1);
                    rethink = 10;
                }
                if (--fleeTicks <= 0 || (fleeTo != null && position().distanceTo(fleeTo) < 1.0)) {
                    enter(State.STALK);
                }
            }
            case REND_TELL -> tickRendTell(target);
            case REND_RECOVER -> {
                getNavigation().stop();
                if (stateTicks >= StalkerRules.REND_RECOVERY) {
                    enter(State.STALK);
                }
            }
            case GRASP_TELL -> tickGraspTell(target);
            default -> stalk(target);
        }
        if (state == State.STALK && target != null) {
            maybeWhisper(target);
        }
    }

    /** Looked at directly by anyone near: frozen, the mask flaring. */
    private boolean checkCaught() {
        Vec3 mask = position().add(0, StalkerRules.MASK_Y, 0);
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(StalkerRules.CAUGHT_RANGE + 1),
                p -> p.isAlive() && !p.isSpectator())) {
            Vec3 eye = p.getEyePosition();
            double distance = eye.distanceTo(mask);
            double angle = StalkerRules.viewAngle(eye, p.getViewVector(1.0f), mask);
            if (StalkerRules.caught(distance, angle, p.hasLineOfSight(this), caughtImmune > 0)) {
                caughtCount++;
                getNavigation().stop();
                enter(State.CAUGHT);
                playSound(Stalkers.SHRIEK.get(), 0.9f, 0.95f + random.nextFloat() * 0.1f);
                return true;
            }
        }
        return false;
    }

    private void stalk(@Nullable Player target) {
        if (target == null) {
            wander();
            waitedClose = 0;
            return;
        }
        lookAt(target, 20f, 20f);
        Vec3 eye = target.getEyePosition();
        Vec3 look = target.getViewVector(1.0f);
        Vec3 mask = position().add(0, StalkerRules.MASK_Y, 0);
        boolean away = StalkerRules.turnedAway(eye, look, mask);
        double distance = horizontalTo(target);
        if (StalkerRules.wantsStep(stepCooldown, away, distance) && shadowStep(target)) {
            return;
        }
        boolean inDark = StalkerLight.dark(level(), target.blockPosition());
        waitedClose = distance <= StalkerRules.REND_START + 0.5 ? waitedClose + 1 : 0;
        Attack attack = StalkerRules.pickAttack(inDark, away, graspCooldown, distance, waitedClose);
        if (attack == Attack.GRASP && !com.cosmicbreach.relic.cantor.Silence.silenced(this)) { // a chord's Silence: no Grasp
            grasps++;
            enter(State.GRASP_TELL);
            whisper(Stalkers.GRASP_WHISPER.get(), 0.55f, true);
            return;
        }
        if (attack == Attack.REND) {
            rends++;
            enter(State.REND_TELL);
            playSound(Stalkers.REND_TELL.get(), 0.8f, 1.0f);
            return;
        }
        // GDD 9.4: a path is found again only when its goal has moved 2 blocks or more, when the last one is walked, or
        // every 20 ticks; never more often than every 5 (a light-priced search is the Stalker's dearest work)
        sincePath++;
        if (sincePath < 5) {
            return;
        }
        Vec3 goal = distance > 3.5 ? StalkerRules.behind(target.position(), look, 2.0) : target.position();
        if (distance > 3.5 && !StalkerLight.dark(level(), BlockPos.containing(goal))) {
            goal = target.position(); // the point behind is lit: come the dark way as near as it can
        }
        if (sincePath >= 20 || pathGoal == null || pathGoal.distanceToSqr(goal) >= 4.0 || getNavigation().isDone()) {
            sincePath = 0;
            pathGoal = goal;
            double speed = StalkerRules.lit(StalkerLight.effective(level(), blockPosition())) ? StalkerRules.LIT_SPEED_SCALE : 1.0;
            getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
        }
    }

    private void wander() {
        if (getNavigation().isDone() && random.nextInt(60) == 0) {
            for (int i = 0; i < 8; i++) {
                BlockPos p = blockPosition().offset(random.nextInt(17) - 8, random.nextInt(5) - 2, random.nextInt(17) - 8);
                if (StalkerLight.dark(level(), p)) {
                    getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.7);
                    return;
                }
            }
        }
    }

    /** Standing in bright light: out of it the quickest dark way (a step if it can, else a run). */
    private void escapeLight(@Nullable Player target) {
        if (state == State.GRASPING) {
            releaseGrasp();
        }
        if (state == State.FLEE && fleeTo != null && fleeTicks-- > 0) {
            if (rethink-- <= 0) {
                getNavigation().moveTo(fleeTo.x, fleeTo.y, fleeTo.z, 1.3);
                rethink = 5;
            }
            return;
        }
        Vec3 away = target == null ? Vec3.ZERO : position().subtract(target.position());
        Vec3 spot = nearestDark(10, away);
        if (spot != null) {
            fleeTo = spot;
            fleeTicks = 30;
            rethink = 0;
            enter(State.FLEE);
            getNavigation().moveTo(spot.x, spot.y, spot.z, 1.3);
        }
    }

    /** Backs off into the dark, away from the target, for {@code ticks}. */
    private void backOff(@Nullable Player target, int ticks) {
        Vec3 away = target == null ? Vec3.ZERO : position().subtract(target.position());
        Vec3 spot = nearestDark(7, away);
        fleeTo = spot;
        fleeTicks = ticks;
        rethink = 0;
        enter(State.FLEE);
    }

    /**
     * The best standing spot within {@code radius} it may stand in (block light under 12), darker and further toward
     * {@code away} being better, and nearer; null if none.
     */
    private @Nullable Vec3 nearestDark(int radius, Vec3 away) {
        Vec3 dir = away.lengthSqr() < 1e-6 ? Vec3.ZERO : new Vec3(away.x, 0, away.z).normalize();
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2.0 + random.nextDouble() * (radius - 2.0);
            Vec3 probe = position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockPos stand = standable(BlockPos.containing(probe), 3);
            if (stand == null || StalkerRules.forbidden(StalkerLight.block(level(), stand))) {
                continue;
            }
            Vec3 at = Vec3.atBottomCenterOf(stand);
            Vec3 off = at.subtract(position());
            double score = off.length() - 3.0 * (dir == Vec3.ZERO ? 0 : off.normalize().dot(dir))
                    + 0.6 * StalkerLight.effective(level(), stand);
            if (score < bestScore) {
                best = at;
                bestScore = score;
            }
        }
        return best;
    }

    /** The nearest cell at or within {@code dy} above or below {@code pos} it could stand in, or null. */
    private @Nullable BlockPos standable(BlockPos pos, int dy) {
        for (int d = 0; d <= dy; d++) {
            for (int sign : d == 0 ? new int[] {1} : new int[] {1, -1}) {
                BlockPos p = pos.above(d * sign);
                if (canStand(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private boolean canStand(BlockPos p) {
        BlockState below = level().getBlockState(p.below());
        if (!below.isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)) {
            return false;
        }
        AABB box = getType().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(p));
        return level().noCollision(this, box) && !level().containsAnyLiquid(box);
    }

    // ------------------------------------------------------------------ Shadow Step

    /** Teleports behind the target through the dark if a spot is found; true if it stepped. */
    private boolean shadowStep(Player target) {
        if (com.cosmicbreach.relic.cantor.Silence.silenced(this)) return false; // a chord's Silence: no Shadow Step
        double range = StalkerRules.stepRange(EclipseSurge.stalkerStepMultiplier(level()));
        Vec3 look = target.getViewVector(1.0f);
        Vec3 ideal = StalkerRules.behind(target.position(), look, StalkerRules.STEP_BEHIND);
        List<StepCandidate> spots = new ArrayList<>();
        for (int ring = 0; ring <= 3; ring++) {
            int samples = ring == 0 ? 1 : 8 * ring;
            for (int i = 0; i < samples; i++) {
                double a = 2 * Math.PI * i / samples;
                Vec3 probe = ideal.add(Math.cos(a) * ring, 0, Math.sin(a) * ring);
                BlockPos stand = standable(BlockPos.containing(probe), 3);
                if (stand != null) {
                    spots.add(new StepCandidate(Vec3.atBottomCenterOf(stand), StalkerLight.effective(level(), stand),
                            StalkerLight.block(level(), stand)));
                }
            }
        }
        Optional<Vec3> step = StalkerRules.chooseStep(spots, position(), target.position(), target.getEyePosition(), look, range);
        stepCooldown = StalkerRules.STEP_COOLDOWN;
        if (step.isEmpty()) {
            stepCooldown = 20; // nowhere dark behind: look again in a second
            return false;
        }
        Vec3 to = step.get();
        level().broadcastEntityEvent(this, EVENT_STEP_FROM);
        getNavigation().stop();
        teleportTo(to.x, to.y, to.z);
        float yaw = (float) (Mth.atan2(target.getZ() - to.z, target.getX() - to.x) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        level().broadcastEntityEvent(this, EVENT_STEP_TO);
        level().playSound(null, to.x, to.y + 1.2, to.z, Stalkers.STEP.get(), SoundSource.HOSTILE, 0.5f, 0.9f + random.nextFloat() * 0.2f);
        steps++;
        return true;
    }

    // ------------------------------------------------------------------ Rend

    private void tickRendTell(@Nullable Player target) {
        getNavigation().stop();
        if (target != null) {
            lookAt(target, 30f, 30f);
            getLookControl().setLookAt(target, 30f, 30f);
        }
        if (stateTicks == StalkerRules.REND_GLINT) {
            level().broadcastEntityEvent(this, EVENT_GLINT);
            playSound(Stalkers.GLINT.get(), 0.8f, 1.0f);
        }
        if (stateTicks >= StalkerRules.REND_TELL) {
            level().broadcastEntityEvent(this, EVENT_REND);
            playSound(Stalkers.REND.get(), 1.0f, 0.95f + random.nextFloat() * 0.1f);
            if (target != null) {
                double yawTo = Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG - 90.0;
                double angle = Mth.wrapDegrees(yawTo - getYRot());
                if (StalkerRules.rendHits(horizontalTo(target), angle)) {
                    rendActive = true;
                    boolean hurt;
                    try {
                        hurt = target.hurt(damageSources().mobAttack(this),
                                (float) (StalkerRules.REND_DAMAGE * EclipseSurge.hollowDamageMultiplier(level())));
                    } finally {
                        rendActive = false;
                    }
                    if (hurt) {
                        rendHits++;
                        Rift.addStack(target);
                        target.knockback(0.5, getX() - target.getX(), getZ() - target.getZ());
                    }
                }
            }
            if (state == State.REND_TELL) { // a parry staggers it on the spot
                enter(State.REND_RECOVER);
            }
        }
    }

    // ------------------------------------------------------------------ Grasp

    private void tickGraspTell(@Nullable Player target) {
        if (target == null) {
            enter(State.STALK);
            return;
        }
        Vec3 hold = holdPoint(target);
        getNavigation().moveTo(hold.x, hold.y, hold.z, 1.2);
        lookAt(target, 30f, 30f);
        if (stateTicks < StalkerRules.GRASP_TELL) {
            return;
        }
        Vec3 mask = position().add(0, StalkerRules.MASK_Y, 0);
        boolean away = StalkerRules.turnedAway(target.getEyePosition(), target.getViewVector(1.0f), mask);
        boolean inDark = StalkerLight.dark(level(), target.blockPosition());
        boolean near = horizontalTo(target) <= StalkerRules.GRASP_REACH;
        boolean dodging = target instanceof ServerPlayer sp
                && com.cosmicbreach.combat.PlayerCombat.of(sp).machine().isInvulnerable();
        graspCooldown = StalkerRules.GRASP_COOLDOWN;
        if (away && inDark && near && !dodging && target instanceof ServerPlayer sp2 && StalkerGrasp.hold(sp2, this)) {
            held = target.getUUID();
            heldTicks = 0;
            entityData.set(DATA_HELD, target.getId() + 1);
            enter(State.GRASPING);
            level().broadcastEntityEvent(this, EVENT_GRASP);
            playSound(Stalkers.GRASP.get(), 0.9f, 1.0f);
        } else {
            enter(State.STALK);
        }
    }

    private void tickGrasp() {
        Player player = held == null ? null : level().getPlayerByUUID(held);
        if (player == null || !player.isAlive() || player.isSpectator() || horizontalTo(player) > 4.0) {
            releaseGrasp();
            return;
        }
        heldTicks++;
        Vec3 hold = holdPoint(player);
        getNavigation().stop();
        setPos(hold.x, hold.y, hold.z);
        setDeltaMovement(Vec3.ZERO);
        lookAt(player, 90f, 90f);
        if (StalkerRules.graspHitsAt(heldTicks)) {
            player.hurt(Stalkers.graspDamage(this), (float) (StalkerRules.GRASP_DAMAGE * EclipseSurge.hollowDamageMultiplier(level())));
        }
        if (heldTicks >= StalkerRules.GRASP_TICKS) {
            releaseGrasp();
        }
    }

    /** Where it stands to hold {@code player}: just behind it. */
    private Vec3 holdPoint(Player player) {
        return StalkerRules.behind(player.position(), player.getViewVector(1.0f), HOLD_DISTANCE);
    }

    /** Lets go (a dash or parry broke it, the hold ran out, or it can't hold on). */
    public void releaseGrasp() {
        if (held != null && level().getPlayerByUUID(held) instanceof ServerPlayer p) {
            StalkerGrasp.let(p, this);
        }
        held = null;
        heldTicks = 0;
        entityData.set(DATA_HELD, 0);
        if (state == State.GRASPING) {
            backOff(null, 20);
        }
    }

    /** True while it stands in block light of 12 or more and is getting out: its paths may cross bright cells then. */
    public boolean escapingLight() {
        return inBrightLight;
    }

    /** Ticks the current Grasp has held (0 when none). */
    public int heldTicks() {
        return heldTicks;
    }

    // ------------------------------------------------------------------ whispers

    private void maybeWhisper(Player target) {
        if (whisperCooldown > 0 || distanceTo(target) > 16.0) {
            return;
        }
        whisperCooldown = 300 + random.nextInt(300);
        whisper(Stalkers.WHISPER.get(), 0.5f, false);
    }

    /** A whisper at its mask, heard in 3D; skipped if another played near here just now (one at a time), unless {@code always}. */
    private void whisper(SoundEvent sound, float volume, boolean always) {
        long now = level().getGameTime();
        Vec3 at = position().add(0, StalkerRules.MASK_Y, 0);
        if (!always && lastWhisperAt != null && now - lastWhisper < WHISPER_GAP && lastWhisperAt.distanceTo(at) < WHISPER_REACH) {
            return;
        }
        lastWhisper = now;
        lastWhisperAt = at;
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, 0.95f + random.nextFloat() * 0.1f);
    }

    // ------------------------------------------------------------------ combat hooks

    @Override
    public boolean isParryableAttackActive() {
        return rendActive;
    }

    @Override
    public double attackImpact() {
        return state == State.GRASPING ? 0.0 : StalkerRules.REND_IMPACT;
    }

    @Override
    public double poise() {
        return StalkerRules.POISE;
    }

    @Override
    public void onStagger(int ticks) {
        if (level().isClientSide()) {
            return;
        }
        if (state == State.GRASPING) {
            releaseGrasp();
        }
        rendActive = false;
        staggerTicks = ticks;
        getNavigation().stop();
        enter(State.STAGGER);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && state == State.STALK && source.getEntity() instanceof Player p && isAlive()) {
            setTarget(p);
            if (random.nextInt(3) == 0) {
                backOff(p, 20); // an ambusher: struck in the open, it melts back into the dark
            }
        }
        return hurt;
    }

    // ------------------------------------------------------------------ sounds and body

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // silent footsteps
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Stalkers.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Stalkers.DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.8f;
    }

    /** It dissolves quickly: no body lying there, and no smoke. */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!level().isClientSide() && deathTime >= 8 && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (state == State.GRASPING) {
            releaseGrasp();
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && held != null) {
            releaseGrasp();
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            int count = stateCount();
            if (count != clientStateCount) {
                clientStateCount = count;
                clientStateTicks = 0;
            } else {
                clientStateTicks++;
            }
            StalkerEffects.handler().tick(this);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (level().isClientSide()) {
            StalkerEffects.handler().event(this, id);
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GraspCooldown", graspCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        graspCooldown = tag.getInt("GraspCooldown");
    }

    private double horizontalTo(Entity e) {
        double dx = e.getX() - getX();
        double dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ for tests and tools (server)

    /** {steps, caught, rends, rend hits, grasps, light escapes, the brightest block light it ever stood in}. */
    public int[] counts() {
        return new int[] {steps, caughtCount, rends, rendHits, grasps, lightEscapes, maxBlockLightStood};
    }

    /** Server: the state its brain is in. */
    public State serverState() {
        return state;
    }

    /** Server: ticks into the current state. */
    public int serverStateTicks() {
        return stateTicks;
    }

    /** Tools: lets its Shadow Step and Grasp come round again now. */
    public void readyNow() {
        stepCooldown = 0;
        graspCooldown = 0;
        caughtImmune = 0;
    }

    /** Tools: no Shadow Step or Grasp for {@code ticks}. */
    public void holdBack(int ticks) {
        stepCooldown = Math.max(stepCooldown, ticks);
        graspCooldown = Math.max(graspCooldown, ticks);
    }

    /** Tools: a Rend now at whatever is in front of it (the tell first). */
    public void rendNow() {
        rends++;
        enter(State.REND_TELL);
        playSound(Stalkers.REND_TELL.get(), 0.8f, 1.0f);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animate));
    }

    private PlayState animate(AnimationState<HollowStalker> event) {
        State s = state();
        int count = stateCount();
        RawAnimation animation = switch (s) {
            case CAUGHT -> CAUGHT_ANIM;
            case REND_TELL -> REND_TELL_ANIM;
            case REND_RECOVER -> REND_ANIM;
            case GRASP_TELL -> GRASP_TELL_ANIM;
            case GRASPING -> GRASP_ANIM;
            case STAGGER -> STAGGER_ANIM;
            default -> Math.hypot(getX() - xo, getZ() - zo) > WALK_ABOVE ? WALK : IDLE;
        };
        boolean moving = animation == WALK || animation == IDLE;
        if (!moving && count != animatedCount && event.isCurrentAnimation(animation)) {
            event.getController().forceAnimationReset();
        }
        if (!moving) {
            animatedCount = count;
        }
        return event.setAndContinue(animation);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** The attribute modifier the Grasp puts on a held player's walking and jumping. */
    static AttributeModifier holdModifier() {
        return new AttributeModifier(com.cosmicbreach.CosmicBreach.id("stalker_grasp"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
