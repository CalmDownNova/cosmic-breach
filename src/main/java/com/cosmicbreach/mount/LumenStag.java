package com.cosmicbreach.mount;

import com.cosmicbreach.registry.ModBlocks;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Lumen Stag (GDD 8.1): a tall white stag of the Sunfield Terraces with crystal antlers that hold light. Health
 * 30, speed 0.30. Herds are skittish; a stag is tamed by trust, hand-fed Starbloom from a sneaking approach
 * ({@link StagRules}); its antlers are the only meter. Ridden, it has a triple jump (the second and third in mid-air,
 * a fourth with the Comet Bridle) and glides while jump is held on the way down (sinking 30% slower with the Halo
 * Reins). The dash key makes its next jump.
 *
 * <p>The rider's client moves it, as vanilla has the rider move a horse: the jumps and the glide run there
 * ({@link #travel}); the server keeps a glide or a jump from counting toward fall damage ({@link #move}).
 */
public class LumenStag extends CelestialMount {
    private static final EntityDataAccessor<Integer> DATA_TRUST = SynchedEntityData.defineId(LumenStag.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BOLTING = SynchedEntityData.defineId(LumenStag.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation LEAP = RawAnimation.begin().thenLoop("leap");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("glide");
    private static final RawAnimation EAT = RawAnimation.begin().thenLoop("eat");

    // server
    private @Nullable UUID herd;
    private long boltUntil = Long.MIN_VALUE;
    private @Nullable Vec3 boltFrom;
    private long lastSprintLoss = Long.MIN_VALUE / 2;
    private long chewUntil = Long.MIN_VALUE;
    private int stepCount;
    // the rider's client: the jumps and the glide
    private int jumpsUsed;
    private boolean jumpWasHeld;
    private boolean dashJump;
    private boolean gliding;
    private Vec3 glideVelocity = Vec3.ZERO;
    /** Measurements for tests, on the side that moves it: {jump index, start y} of the last jump. */
    private double lastJumpStartY;
    private int lastJumpIndex = -1;

    public LumenStag(EntityType<? extends LumenStag> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, StagRules.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, StagRules.SPEED)
                .add(Attributes.JUMP_STRENGTH, 0.5)
                .add(Attributes.SAFE_FALL_DISTANCE, 12.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public MountGear.Kind kind() {
        return MountGear.Kind.STAG;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TRUST, 0);
        builder.define(DATA_BOLTING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BoltGoal());
        goalSelector.addGoal(2, new WaryGoal());
        goalSelector.addGoal(4, new HerdGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.45));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ trust

    public int trust() {
        return entityData.get(DATA_TRUST);
    }

    public void setTrust(int trust) {
        entityData.set(DATA_TRUST, Math.max(0, Math.min(StagRules.TAME_AT, trust)));
    }

    /** How bright the antlers are, 0 to 1 (both sides). */
    public float antlerGlow() {
        return StagRules.antlerGlow(trust(), isTamed());
    }

    public boolean bolting() {
        return entityData.get(DATA_BOLTING);
    }

    public @Nullable UUID herd() {
        return herd;
    }

    public void setHerd(@Nullable UUID herd) {
        this.herd = herd;
    }

    /** Every untamed stag of this one's herd nearby, itself included. */
    public List<LumenStag> herdMembers() {
        List<LumenStag> out = new ArrayList<>();
        if (herd == null) {
            if (!isTamed()) {
                out.add(this);
            }
            return out;
        }
        for (LumenStag s : level().getEntitiesOfClass(LumenStag.class, getBoundingBox().inflate(StagRules.HERD_RADIUS))) {
            if (herd.equals(s.herd) && !s.isTamed()) {
                out.add(s);
            }
        }
        return out;
    }

    /** A scare: every untamed stag of the herd loses {@value StagRules#LOSS} trust. Server side. */
    public void herdLosesTrust() {
        for (LumenStag s : herdMembers()) {
            s.setTrust(StagRules.lose(s.trust()));
            s.level().broadcastEntityEvent(s, (byte) 6); // a puff: trust lost
        }
    }

    /** The herd runs from {@code from}. Server side. */
    public void herdBolts(Vec3 from) {
        long now = level().getGameTime();
        for (LumenStag s : herdMembers()) {
            s.boltUntil = now + StagRules.BOLT_TICKS;
            s.boltFrom = from;
            s.entityData.set(DATA_BOLTING, true);
            s.setEating(false);
            s.chewUntil = Long.MIN_VALUE;
        }
        playSound(Mounts.STAG_CALL.get(), 1.0f, 1.25f);
    }

    @Override
    protected InteractionResult wildInteract(Player player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(ModBlocks.STARBLOOM.get().asItem())) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.CONSUME;
        }
        long now = level().getGameTime();
        if (bolting() || now < chewUntil) {
            return InteractionResult.PASS;
        }
        stack.consume(1, player);
        int before = trust();
        setTrust(StagRules.feed(before, random.nextBoolean()));
        chewUntil = now + StagRules.CHEW_TICKS;
        setEating(true);
        playSound(SoundEvents.HORSE_EAT, 0.7f, 1.3f);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.3, getZ(), 4 + 3 * (trust() - before), 0.4, 0.3, 0.4, 0.01);
        }
        if (StagRules.tames(trust())) {
            tameTo(player);
            playSound(Mounts.STAG_CALL.get(), 1.0f, 1.0f);
            playSound(Mounts.TAMED.get(), 1.0f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ server tick

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) {
            return;
        }
        long now = level().getGameTime();
        if (isEating() && now >= chewUntil) {
            setEating(false);
        }
        if (bolting() && now >= boltUntil) {
            entityData.set(DATA_BOLTING, false);
        }
        if (!isTamed() && tickCount % 4 == 0) {
            watchForSprinters(now);
        }
    }

    private void watchForSprinters(long now) {
        for (Player p : level().players()) {
            if (p.isSpectator() || !p.isAlive() || !p.isSprinting()) {
                continue;
            }
            double d = distanceTo(p);
            if (StagRules.spooks(d, true) && StagRules.sprintLossReady(now, lastSprintLoss)) {
                for (LumenStag s : herdMembers()) {
                    s.lastSprintLoss = now;
                }
                herdLosesTrust();
            }
            if (StagRules.bolts(d, true) && !bolting()) {
                herdBolts(p.position());
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && !isTamed() && source.getEntity() instanceof Player player) {
            herdLosesTrust();
            herdBolts(player.position());
        }
        return hurt;
    }

    // ------------------------------------------------------------------ riding: the jumps and the glide

    /** The dash key: the next jump (the rider's client). */
    public void requestDashJump() {
        dashJump = true;
    }

    public boolean gliding() {
        return gliding;
    }

    public int jumpsUsed() {
        return jumpsUsed;
    }

    public int lastJumpIndex() {
        return lastJumpIndex;
    }

    public double lastJumpStartY() {
        return lastJumpStartY;
    }

    @Override
    public void travel(Vec3 input) {
        if (isAlive() && isControlledByLocalInstance() && getControllingPassenger() instanceof Player) {
            boolean held = riderJumping();
            boolean press = held && !jumpWasHeld || dashJump;
            jumpWasHeld = held;
            dashJump = false;
            if (onGround()) {
                jumpsUsed = 0;
            }
            boolean jumped = false;
            if (press && !isInWater() && !isInLava()) {
                int k = StagRules.nextJump(onGround(), jumpsUsed, wears(MountGear.COMET_BRIDLE));
                if (k >= 0) {
                    Vec3 v = getDeltaMovement();
                    setDeltaMovement(v.x, StagRules.jumpVelocity(k), v.z);
                    jumpsUsed = k + 1;
                    lastJumpIndex = k;
                    lastJumpStartY = getY();
                    hasImpulse = true;
                    jumped = true;
                    playSound(Mounts.STAG_HOOF.get(), 0.6f, 1.2f + 0.1f * k);
                    if (k > 0) {
                        spawnJumpGlints();
                    }
                }
            }
            Vec3 v = getDeltaMovement();
            if (!jumped && StagRules.glides(held, onGround(), v.y, isInWater() || isInLava())) {
                // the glide steers from its own speed of last tick, not the one vanilla has scaled since
                Vec3 from = gliding ? glideVelocity : v;
                double[] g = StagRules.glide(from.x, from.z, getYRot(), wears(MountGear.HALO_REINS));
                setDeltaMovement(g[0], g[1], g[2]);
                move(MoverType.SELF, getDeltaMovement());
                glideVelocity = getDeltaMovement(); // after collisions
                calculateEntityAnimation(false);
                gliding = true;
                resetFallDistance();
                return;
            }
            gliding = false;
        } else {
            gliding = false;
        }
        super.travel(input);
    }

    private void spawnJumpGlints() {
        for (int i = 0; i < 6; i++) {
            level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 0.2,
                    getZ() + (random.nextDouble() - 0.5) * 1.2, 0.0, -0.05, 0.0);
        }
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        if (type == MoverType.SELF && !level().isClientSide && !isVehicle() && onGround() && (delta.x != 0.0 || delta.z != 0.0)
                && overCliff(getX() + delta.x, getZ() + delta.z)) {
            // on its own it never steps (or is jostled by its herd) off an island's rim: the islands float over the sky
            delta = new Vec3(0.0, delta.y, 0.0);
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        }
        super.move(type, delta);
        if (isVehicle() && !onGround() && StagRules.fallHarmless(delta.y)) {
            resetFallDistance(); // a jump or a glide adds nothing to a fall
        }
    }

    /** True if nothing solid is under {@code (x, z)} within {@value StagRules#CLIFF} blocks of the stag's feet. */
    private boolean overCliff(double x, double z) {
        BlockPos p = BlockPos.containing(x, getY() - 0.01, z);
        for (int i = 0; i <= StagRules.CLIFF; i++) {
            BlockPos q = p.below(i);
            if (!level().getBlockState(q).getCollisionShape(level(), q).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ spawning and saving

    /** A herd: the stags spawned together share its id. */
    public static final class HerdData extends AgeableMob.AgeableMobGroupData {
        public final UUID id;

        public HerdData(UUID id) {
            super(false);
            this.id = id;
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                                  @Nullable SpawnGroupData data) {
        HerdData herdData = data instanceof HerdData h ? h : new HerdData(UUID.randomUUID());
        herd = herdData.id;
        super.finalizeSpawn(level, difficulty, type, herdData);
        return herdData;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Trust", trust());
        if (herd != null) {
            tag.putUUID("Herd", herd);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTrust(tag.getInt("Trust"));
        herd = tag.hasUUID("Herd") ? tag.getUUID("Herd") : null;
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return Mounts.STAG_CALL.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 480;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return Mounts.STAG_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return Mounts.STAG_HURT.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (state.liquid()) {
            return;
        }
        stepCount++;
        boolean fast = isVehicle() || bolting();
        if (!fast || stepCount % 2 == 0) {
            playSound(Mounts.STAG_HOOF.get(), fast ? 0.45f : 0.3f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    @Override
    protected void playJumpSound() {
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, this::pose));
    }

    private PlayState pose(AnimationState<LumenStag> state) {
        if (isEating()) {
            return state.setAndContinue(EAT);
        }
        double dy = getY() - yo;
        if (!onGround() && !isInWater()) {
            return state.setAndContinue(dy > 0.02 ? LEAP : dy > -0.16 ? GLIDE : LEAP);
        }
        double speed = Math.hypot(getX() - xo, getZ() - zo);
        if (speed > 0.22) {
            return state.setAndContinue(RUN);
        }
        if (speed > 0.02 || state.isMoving()) {
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    // ------------------------------------------------------------------ goals

    /** Runs from what scared the herd while it is bolting. */
    private final class BoltGoal extends Goal {
        private @Nullable Vec3 target;

        BoltGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isTamed() || isVehicle() || !bolting() || boltFrom == null) {
                return false;
            }
            target = DefaultRandomPos.getPosAway(LumenStag.this, 18, 6, boltFrom);
            return target != null;
        }

        @Override
        public void start() {
            navigation.moveTo(target.x, target.y, target.z, 1.9);
        }

        @Override
        public boolean canContinueToUse() {
            return bolting() && !navigation.isDone();
        }

        @Override
        public void stop() {
            target = null;
        }
    }

    /** A wild stag steps away from a player walking up standing; one approaching sneaking may come close. */
    private final class WaryGoal extends Goal {
        private @Nullable Vec3 target;

        WaryGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isTamed() || isVehicle() || bolting() || isEating() || tickCount % 5 != 0) {
                return false;
            }
            Player near = null;
            for (Player p : level().players()) {
                if (!p.isSpectator() && p.isAlive() && StagRules.wary(distanceTo(p), p.isShiftKeyDown())) {
                    near = p;
                    break;
                }
            }
            if (near == null) {
                return false;
            }
            target = DefaultRandomPos.getPosAway(LumenStag.this, 7, 3, near.position());
            return target != null;
        }

        @Override
        public void start() {
            navigation.moveTo(target.x, target.y, target.z, 0.8);
        }

        @Override
        public boolean canContinueToUse() {
            return !navigation.isDone() && !bolting();
        }
    }

    /** Keeps a wild stag near its herd. */
    private final class HerdGoal extends Goal {
        private @Nullable LumenStag toward;

        HerdGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isTamed() || isVehicle() || herd == null || tickCount % 20 != 0) {
                return false;
            }
            LumenStag closest = null;
            double nearest = Double.MAX_VALUE;
            for (LumenStag s : herdMembers()) {
                double d = distanceToSqr(s);
                if (s != LumenStag.this && d < nearest) {
                    nearest = d;
                    closest = s;
                }
            }
            toward = nearest > 10.0 * 10.0 ? closest : null; // strayed: back toward the nearest of the herd
            return toward != null;
        }

        @Override
        public void start() {
            navigation.moveTo(toward, 0.7);
        }

        @Override
        public boolean canContinueToUse() {
            return toward != null && toward.isAlive() && distanceToSqr(toward) > 16.0 && !navigation.isDone();
        }

        @Override
        public void stop() {
            toward = null;
        }
    }

}
