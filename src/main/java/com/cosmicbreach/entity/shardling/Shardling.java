package com.cosmicbreach.entity.shardling;

import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.combat.PoiseSource;
import com.cosmicbreach.combat.Staggerable;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.entity.shardling.ShardlingMoves.Phase;
import com.cosmicbreach.entity.shardling.ShardlingPack.Attack;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The Shardling (GDD section 7.1): a crystal hunter a little bigger than a fox that fights in packs. Health 14,
 * armor 4, poise 10, speed 0.32, 25 experience, Starshards when it dies.
 *
 * <p>Its pack ({@link ShardlingPack}) decides where it stands and when it may attack; it carries the
 * attacks out on its own {@link ShardlingMoves} timeline:
 * <ul>
 *   <li><b>Splinter Lunge</b>: a 12 tick crouch with the spines flashing gold and a rising ting, a
 *       leap of about 5 blocks at the target for 5 damage (the only parryable moment, Impact 10),
 *       then 16 ticks of recovery taking 25% more damage.</li>
 *   <li><b>Shard Spit</b>, at a target beyond 6 blocks: 10 ticks rearing back with the throat
 *       glowing red, then three needles 10 degrees apart for 3 damage each, then a 12 tick punish
 *       window. Not parryable.</li>
 * </ul>
 * Breaking its poise (a parry, or 10 Impact of hits within 3 s) staggers it for 20 ticks, breaking off
 * its attack and giving back its attack token. When it dies it shatters into three
 * {@link ShardFragment}s that burst 30 ticks later.
 *
 * <p>The phase is synced to clients as a byte, with a counter so the same phase twice restarts its
 * animation; the client's effects read it through {@link ShardlingEffects}.
 */
public class Shardling extends Monster implements GeoEntity, ParryableAttacker, Staggerable, PoiseSource {
    /**
     * The model is drawn this much bigger than it was built, and the hitbox matches: at fox size a
     * Shardling was a speck beyond 9 blocks. Everything tied to its body scales with this (hitbox,
     * eye height, shadow, where the needles leave the mouth); ranges measured to the player don't.
     */
    public static final float SCALE = 1.25f;
    /** The hitbox as built (fox size) before {@link #SCALE}: 0.75 wide and 0.875 high in game. */
    public static final float BASE_WIDTH = 0.6f;
    public static final float BASE_HEIGHT = 0.7f;
    public static final float BASE_EYE_HEIGHT = 0.5f;
    public static final double HEALTH = 14.0;
    public static final double ARMOR = 4.0;
    public static final double SPEED = 0.32;
    public static final double ATTACK = 5.0;
    public static final double FOLLOW_RANGE = 32.0;
    public static final int EXPERIENCE = 25;
    public static final double POISE = 10.0;
    /** The Splinter Lunge's Impact: a parry deals twice this to the Shardling's poise, so it always staggers. */
    public static final double LUNGE_IMPACT = 10.0;
    /** Horizontal speed through the lunge's active ticks; with the skid afterwards it carries about 5 blocks. */
    public static final double LUNGE_SPEED = 1.1;
    public static final double LUNGE_HOP = 0.2;
    /**
     * How far beyond its own box the leap catches a player. With the scaled box it connects at 1.125
     * blocks centre to centre (0.3 + 0.375 + 0.45), so 4 active ticks of flight reach a player 5.5
     * away, the pack brain's longest lunge.
     */
    public static final double LUNGE_REACH = 0.45;
    /** Speed kept as the leap's active ticks end (it lands in a short skid): 4.4 blocks flown, about 0.6 skidded. */
    public static final double SKID = 0.2;
    /** Speed kept when the leap connects: it stops at the player it hit. */
    public static final double HIT_STOP = 0.2;
    public static final double RUN = 1.3;
    public static final double WALK = 0.9;
    public static final double DART = 1.5;
    /** Needles leave from about the tip of the snout (0.56 ahead and 0.5 up as built, then scaled). */
    public static final double MOUTH_AHEAD = 0.56 * SCALE;
    public static final double MOUTH_HEIGHT = 0.5 * SCALE;
    public static final float NEEDLE_SPREAD = 10.0f;

    private static final String PACE_TAG = "Pace";
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(Shardling.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_PHASE_COUNT = SynchedEntityData.defineId(Shardling.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation CROUCH_TELL = RawAnimation.begin().thenPlayAndHold("crouch_tell");
    private static final RawAnimation LUNGE_ANIM = RawAnimation.begin().thenPlayAndHold("lunge");
    private static final RawAnimation RECOVER_ANIM = RawAnimation.begin().thenPlay("recover").thenLoop("idle");
    private static final RawAnimation SPIT_TELL = RawAnimation.begin().thenPlayAndHold("spit_tell");
    private static final RawAnimation SPIT_ANIM = RawAnimation.begin().thenPlay("spit").thenLoop("idle");
    private static final RawAnimation STAGGER_ANIM = RawAnimation.begin().thenPlay("stagger").thenLoop("idle");
    /** Blocks a tick: below is standing, above {@value #RUN_ABOVE} is the bounding run. */
    private static final double WALK_ABOVE = 0.015;
    private static final double RUN_ABOVE = 0.17;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ShardlingMoves moves = new ShardlingMoves();
    private @Nullable ShardlingPack pack;
    private Attack pendingAttack = Attack.NONE;
    private int attackTarget = -1;
    private int rest;
    /** Rests are this many times as long (see {@link ShardlingMoves#restAfter}). Saved. */
    private float pace = 1.0f;
    private Vec3 lungeDirection = Vec3.ZERO;
    private boolean lungeConnected;
    private int phaseCount;
    private Phase syncedPhase = Phase.NONE;
    // client: the animation last started, to restart one that comes round twice in a row
    private int animatedCount = -1;

    public Shardling(EntityType<? extends Shardling> type, Level level) {
        super(type, level);
        this.xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ATTACK_DAMAGE, ATTACK)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PackHuntGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, (byte) 0);
        builder.define(DATA_PHASE_COUNT, 0);
    }

    // ------------------------------------------------------------------ the pack

    /** Joins {@code joined} (packs spawned together share one); false if it is full. */
    public boolean joinPack(ShardlingPack joined) {
        if (!joined.join(getId())) {
            return false;
        }
        leavePack();
        pack = joined;
        return true;
    }

    public @Nullable ShardlingPack pack() {
        return pack;
    }

    /** This Shardling's role in its pack (Flanker when it has none yet). */
    public ShardlingPack.Role role() {
        return pack == null ? ShardlingPack.Role.FLANKER : pack.role(getId());
    }

    private void leavePack() {
        if (pack != null) {
            pack.leave(getId(), Shardlings.tokens());
            pack = null;
        }
        Shardlings.tokens().release(getId());
    }

    /**
     * A Shardling on its own (summoned, loaded from a save, or dropped from a pack) joins a pack within
     * 16 blocks that has room, or starts its own.
     */
    private void ensurePack() {
        if (pack != null && (pack.contains(getId()) || pack.join(getId()))) {
            return;
        }
        pack = null;
        for (Shardling other : level().getEntitiesOfClass(Shardling.class, getBoundingBox().inflate(16.0),
                s -> s != this && s.isAlive() && s.pack != null)) {
            if (other.pack.join(getId())) {
                pack = other.pack;
                return;
            }
        }
        pack = new ShardlingPack();
        pack.join(getId());
    }

    /** The leader's plan: find the pack, pick or keep the target, and let the brain hand out orders. */
    private void planPack(long now) {
        AttackTokens tokens = Shardlings.tokens();
        List<Shardling> found = new ArrayList<>();
        for (int id : pack.members()) {
            if (level().getEntity(id) instanceof Shardling member && member.isAlive() && member.pack == pack) {
                found.add(member);
            } else {
                pack.leave(id, tokens);
            }
        }
        Player target = chooseTarget(found);
        ShardlingPack.Target hunted = target == null ? null
                : new ShardlingPack.Target(target.getId(), target.getX(), target.getZ(), target.getYRot());
        List<ShardlingPack.Member> views = new ArrayList<>(found.size());
        for (Shardling member : found) {
            boolean sees = target != null && member.getSensing().hasLineOfSight(target);
            views.add(new ShardlingPack.Member(member.getId(), member.getX(), member.getZ(), member.free(), member.readyToAttack(), sees));
        }
        pack.plan(now, hunted, views, tokens);
        for (Shardling member : found) {
            if (member.getTarget() != target) {
                member.setTarget(target);
            }
        }
    }

    /**
     * The pack's current target while it is still fair game within reach of the pack, else the nearest
     * player any member can see: the pack hunts with all its eyes (a pillar hiding the player from the
     * leader hides it from nobody else).
     */
    private @Nullable Player chooseTarget(List<Shardling> members) {
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        if (pack.target() != -1 && level().getEntity(pack.target()) instanceof Player current && fairGame(current)
                && nearestMember(members, current) <= (range + 8.0) * (range + 8.0)) {
            return current;
        }
        Player best = null;
        double bestDistance = range * range;
        for (Player player : level().players()) {
            double d = nearestMember(members, player);
            if (d < bestDistance && fairGame(player) && members.stream().anyMatch(m -> m.getSensing().hasLineOfSight(player))) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Squared distance from {@code player} to the nearest member. */
    private static double nearestMember(List<Shardling> members, Player player) {
        double nearest = Double.MAX_VALUE;
        for (Shardling member : members) {
            nearest = Math.min(nearest, member.distanceToSqr(player));
        }
        return nearest;
    }

    /** Players it may hunt: alive, in survival or adventure, and not on peaceful. */
    public boolean fairGame(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative()
                && level().getDifficulty() != Difficulty.PEACEFUL && canAttack(player)
                && !com.cosmicbreach.onboarding.ArrivalGrace.shelters(player);
    }

    /** The player the pack hunts, if it is still fair game. */
    public @Nullable Player huntTarget() {
        if (pack == null || pack.target() == -1) {
            return null;
        }
        return level().getEntity(pack.target()) instanceof Player player && fairGame(player) ? player : null;
    }

    /** Not in an attack or a stagger: it can move, and feint. */
    public boolean free() {
        return moves.idle() && pendingAttack == Attack.NONE;
    }

    /** Rests after its attacks last {@code pace} times as long: 1.5 for the sandbox's warm-up pair. */
    public void setPace(float pace) {
        this.pace = Math.max(0.1f, pace);
    }

    public float pace() {
        return pace;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (pace != 1.0f) {
            tag.putFloat(PACE_TAG, pace);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(PACE_TAG)) {
            setPace(tag.getFloat(PACE_TAG));
        }
    }

    /** Free and rested: the pack may pick it for an attack. */
    public boolean readyToAttack() {
        return free() && rest == 0 && !Shardlings.tokens().holds(getId());
    }

    /** Called by {@link PackHuntGoal} when the plan picked this Shardling to attack; it starts this tick. */
    void attackWhenFree(Attack attack) {
        pendingAttack = attack;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        long now = level().getGameTime();
        ensurePack();
        if (pack.planDue(getId(), now)) {
            planPack(now);
        }
        Phase before = moves.phase();
        switch (moves.tick()) {
            case LAUNCHED -> launch();
            case RECOVERING -> {
                if (!lungeConnected) {
                    setDeltaMovement(getDeltaMovement().multiply(SKID, 1.0, SKID));
                }
            }
            case FIRED -> fireNeedles();
            case ENDED -> {
                Shardlings.tokens().release(getId());
                attackTarget = -1;
                rest = ShardlingMoves.restAfter(before == Phase.STAGGER, pace, random.nextInt(ShardlingMoves.REST_SPREAD + 1));
            }
            case NONE -> {
            }
        }
        startPendingAttack();
        steer();
        if (rest > 0 && moves.idle()) {
            rest--;
        }
        syncPhase();
    }

    /** The attack the pack gave this Shardling, if it can still make it. */
    private void startPendingAttack() {
        Attack attack = pendingAttack;
        pendingAttack = Attack.NONE;
        if (attack == Attack.NONE) {
            return;
        }
        Player target = huntTarget();
        if (target == null || !moves.idle() || Shardlings.tokens().targetOf(getId()) != target.getId()) {
            Shardlings.tokens().release(getId());
            return;
        }
        attackTarget = target.getId();
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.3, 1.0, 0.3));
        if (attack == Attack.LUNGE) {
            moves.startLunge();
            playSound(ModSounds.SHARDLING_TELL.get(), 1.0f, 1.0f);
        } else {
            moves.startSpit();
        }
    }

    /** What the body does in each phase of an attack: stand and face the target, or fly. */
    private void steer() {
        switch (moves.phase()) {
            case TELL, SPIT_TELL -> {
                getNavigation().stop();
                Entity target = attackTarget();
                if (target != null) {
                    face(target.getX() - getX(), target.getZ() - getZ(), 40.0f);
                    getLookControl().setLookAt(target, 40.0f, 40.0f);
                }
            }
            case LUNGE -> {
                getNavigation().stop();
                if (!lungeConnected) {
                    Vec3 v = getDeltaMovement();
                    setDeltaMovement(lungeDirection.x * LUNGE_SPEED, v.y, lungeDirection.z * LUNGE_SPEED);
                }
                face(lungeDirection.x, lungeDirection.z, 90.0f);
            }
            case RECOVER, SPIT, STAGGER -> getNavigation().stop();
            case NONE -> {
            }
        }
    }

    private void face(double dx, double dz, float maxTurn) {
        if (dx * dx + dz * dz < 1e-6) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxTurn));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private @Nullable Entity attackTarget() {
        return attackTarget == -1 ? null : level().getEntity(attackTarget);
    }

    /** The leap: locked on where the target stands now, so a dash aside makes it miss. */
    private void launch() {
        Entity target = attackTarget();
        Vec3 toward = target == null ? Vec3.directionFromRotation(0.0f, getYRot())
                : new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ());
        lungeDirection = toward.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0.0f, getYRot()) : toward.normalize();
        lungeDirection = new Vec3(lungeDirection.x, 0.0, lungeDirection.z).normalize();
        lungeConnected = false;
        setDeltaMovement(lungeDirection.x * LUNGE_SPEED, LUNGE_HOP, lungeDirection.z * LUNGE_SPEED);
        hasImpulse = true;
        playSound(ModSounds.SHARDLING_LUNGE.get(), 1.0f, 0.95f + random.nextFloat() * 0.1f);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && moves.phase() == Phase.LUNGE && !lungeConnected && isAlive()) {
            checkLungeContact();
        }
    }

    /** After the leap moved this tick: the first player it touches takes the hit (or parries it). */
    private void checkLungeContact() {
        AABB reach = getBoundingBox().inflate(LUNGE_REACH, 0.2, LUNGE_REACH);
        for (Player player : level().getEntitiesOfClass(Player.class, reach, this::fairGame)) {
            lungeConnected = true;
            if (doHurtTarget(player)) {
                setDeltaMovement(getDeltaMovement().multiply(HIT_STOP, 1.0, HIT_STOP));
                if (pack != null) {
                    pack.onHitLanded(getId());
                }
            }
            return;
        }
    }

    /** Three needles, 10 degrees apart, at the target's chest. */
    private void fireNeedles() {
        Vec3 forward = Vec3.directionFromRotation(0.0f, getYRot());
        Vec3 mouth = position().add(forward.scale(MOUTH_AHEAD)).add(0.0, MOUTH_HEIGHT, 0.0);
        Entity target = attackTarget();
        Vec3 aim = target == null ? forward : target.position().add(0.0, target.getBbHeight() * 0.55, 0.0).subtract(mouth);
        if (aim.lengthSqr() < 1e-6) {
            aim = forward;
        }
        aim = aim.normalize();
        for (int i = -1; i <= 1; i++) {
            Vec3 dir = aim.yRot(i * NEEDLE_SPREAD * Mth.DEG_TO_RAD);
            ShardNeedle needle = new ShardNeedle(level(), this, mouth);
            needle.shoot(dir.x, dir.y, dir.z, ShardNeedle.SPEED, 0.0f);
            level().addFreshEntity(needle);
        }
        playSound(ModSounds.SHARDLING_SPIT.get(), 1.0f, 0.95f + random.nextFloat() * 0.1f);
    }

    private void syncPhase() {
        Phase phase = moves.phase();
        if (phase != syncedPhase || entityData.get(DATA_PHASE) != (byte) phase.ordinal()) {
            syncedPhase = phase;
            entityData.set(DATA_PHASE, (byte) phase.ordinal());
            entityData.set(DATA_PHASE_COUNT, ++phaseCount);
        }
    }

    /** Syncs a stagger at once, counting it even if it lands on a stagger, so the reel plays again. */
    private void resyncPhase() {
        entityData.set(DATA_PHASE, (byte) moves.phase().ordinal());
        entityData.set(DATA_PHASE_COUNT, ++phaseCount);
        syncedPhase = moves.phase();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive()) {
            ShardlingEffects.handler().shardlingTick(this);
        }
    }

    // ------------------------------------------------------------------ combat hooks

    @Override
    public boolean isParryableAttackActive() {
        return moves.parryable();
    }

    @Override
    public double attackImpact() {
        return LUNGE_IMPACT;
    }

    @Override
    public double poise() {
        return POISE;
    }

    /** Its poise broke: whatever it was doing stops, it gives back its attack token and reels. */
    @Override
    public void onStagger(int ticks) {
        if (level().isClientSide()) {
            return;
        }
        moves.stagger(ticks);
        pendingAttack = Attack.NONE;
        attackTarget = -1;
        Shardlings.tokens().release(getId());
        getNavigation().stop();
        resyncPhase();
    }

    /** Takes 25% more in the lunge's recovery (the punish window). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount * moves.damageTakenMultiplier());
    }

    // ------------------------------------------------------------------ death

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && dead) {
            leavePack();
            shatter();
        }
    }

    /** Three shards fly apart from the body, 120 degrees apart give or take. */
    private void shatter() {
        Vec3 centre = position().add(0.0, getBbHeight() * 0.5, 0.0);
        float start = random.nextFloat() * 360.0f;
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(start + i * 120.0f + (random.nextFloat() - 0.5f) * 40.0f);
            double speed = 0.2 + random.nextDouble() * 0.1;
            // a low arc: the shards stay under eye height instead of flying into the killer's face
            Vec3 velocity = new Vec3(Math.cos(angle) * speed, 0.18 + random.nextDouble() * 0.06, Math.sin(angle) * speed);
            level().addFreshEntity(new ShardFragment(level(), centre, velocity));
        }
    }

    /** It shatters at once: no body lies there for a second, and no smoke puff when it goes. */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!level().isClientSide() && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.DEATH && level().isClientSide()) {
            ShardlingEffects.handler().shardlingDied(this);
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide()) {
            leavePack();
        }
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SHARDLING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHARDLING_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.SHARDLING_STEP.get(), 0.35f, 0.9f + random.nextFloat() * 0.3f);
    }

    // ------------------------------------------------------------------ what the client and tests read

    /** The phase as the server last synced it (both sides). */
    public Phase phase() {
        int ordinal = entityData.get(DATA_PHASE);
        Phase[] phases = Phase.values();
        return ordinal >= 0 && ordinal < phases.length ? phases[ordinal] : Phase.NONE;
    }

    /** Counts phase changes; a new value with the same phase means the phase began again. */
    public int phaseCount() {
        return entityData.get(DATA_PHASE_COUNT);
    }

    /** Server side: the timeline itself. */
    public ShardlingMoves moves() {
        return moves;
    }

    /**
     * For tests and screenshots (server side): starts a telegraph or a stagger now. A Shardling with
     * NoAI never ticks its timeline, so it holds the pose.
     */
    public void showPhase(Phase phase) {
        switch (phase) {
            case TELL -> moves.startLunge();
            case SPIT_TELL -> moves.startSpit();
            case STAGGER -> moves.stagger(PoiseTracker.STAGGER_TICKS);
            default -> {
            }
        }
        syncPhase();
    }

    /** Server side: the target of the attack under way, or -1. */
    public int attackTargetId() {
        return attackTarget;
    }

    /** Server side: whether the last leap touched a player (and stopped there). */
    public boolean lungeConnected() {
        return lungeConnected;
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animate));
    }

    private PlayState animate(AnimationState<Shardling> state) {
        Phase phase = phase();
        int count = phaseCount();
        RawAnimation animation = switch (phase) {
            case TELL -> CROUCH_TELL;
            case LUNGE -> LUNGE_ANIM;
            case RECOVER -> RECOVER_ANIM;
            case SPIT_TELL -> SPIT_TELL;
            case SPIT -> SPIT_ANIM;
            case STAGGER -> STAGGER_ANIM;
            case NONE -> {
                double moved = Math.hypot(getX() - xo, getZ() - zo);
                yield moved > RUN_ABOVE ? RUN_ANIM : moved > WALK_ABOVE ? WALK_ANIM : IDLE;
            }
        };
        if (phase != Phase.NONE && count != animatedCount && state.isCurrentAnimation(animation)) {
            state.getController().forceAnimationReset(); // the same phase again: from the top
        }
        if (phase != Phase.NONE) {
            animatedCount = count;
        }
        return state.setAndContinue(animation);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
