package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.combat.ParryableAttacker;
import com.cosmicbreach.combat.PoiseSource;
import com.cosmicbreach.combat.Staggerable;
import com.cosmicbreach.guardian.ArenaRules;
import com.cosmicbreach.guardian.GuardianRegistry;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 * A Prism Shard (Prism Colossus design v1, Shatter): one of the three the Colossus bursts into at zero, pure red,
 * green or blue, a quick knee-high crystal hound that chases the nearest fighter and lunges. The lunge: 10 ticks
 * crouched with a gold glint (parryable), a leap for 8 damage and Impact 10, 12 ticks of recovery. It reports its
 * death to the Colossus ({@link PrismColossus#onShardDied}); when the countdown runs out it flies home. Never saved.
 */
public class PrismShard extends Monster implements GeoEntity, ParryableAttacker, Staggerable, PoiseSource {
    public enum Phase { NONE, LAND, TELL, LUNGE, RECOVER, STAGGER, HOME }

    public static final int TELL_TICKS = 10;
    public static final int LUNGE_TICKS = 6;
    public static final int RECOVER_TICKS = 12;
    public static final int LAND_TICKS = 24;
    public static final int HOME_TICKS = 20;
    public static final double LUNGE_DAMAGE = 8.0;
    public static final double LUNGE_IMPACT = 10.0;
    public static final double LUNGE_SPEED = 0.95;
    public static final double LUNGE_RANGE = 4.5;
    public static final double POISE = 15.0;
    public static final double SPEED = 0.34;

    private static final EntityDataAccessor<Byte> DATA_COLOR = SynchedEntityData.defineId(PrismShard.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(PrismShard.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_PHASE_COUNT = SynchedEntityData.defineId(PrismShard.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation TELL = RawAnimation.begin().thenPlayAndHold("tell");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenPlayAndHold("lunge");
    private static final RawAnimation RECOVER = RawAnimation.begin().thenPlay("recover").thenLoop("idle");
    private static final RawAnimation STAGGER = RawAnimation.begin().thenPlay("stagger").thenLoop("idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private @Nullable UUID owner;
    private Phase phase = Phase.NONE;
    private int phaseTicks;
    private int cooldown;
    private int phaseCount;
    private @Nullable Player target;
    private Vec3 lungeDir = Vec3.ZERO;
    private boolean connected;
    private Vec3 home = Vec3.ZERO;
    private Vec3 homeFrom = Vec3.ZERO;
    private int animatedCount = -1;

    public PrismShard(EntityType<? extends PrismShard> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ColossusMoves.SHARD_HEALTH)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ATTACK_DAMAGE, LUNGE_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    /** Bursts out of the Colossus's chest, flying outward at {@code yaw}, with {@code health}. */
    public static PrismShard burstFrom(ServerLevel level, PrismColossus owner, int color, Vec3 chest, float yaw, double health) {
        PrismShard shard = new PrismShard(GuardianRegistry.PRISM_SHARD.get(), level);
        shard.owner = owner.getUUID();
        shard.entityData.set(DATA_COLOR, (byte) Math.floorMod(color, 3));
        shard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        shard.setHealth((float) health);
        shard.moveTo(chest.x, chest.y, chest.z, yaw, 0f);
        Vec3 out = CrownArena.forward(yaw);
        shard.setDeltaMovement(out.x * 0.62, 0.55, out.z * 0.62);
        shard.setPhase(Phase.LAND);
        level.addFreshEntity(shard);
        return shard;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COLOR, (byte) 0);
        builder.define(DATA_PHASE, (byte) 0);
        builder.define(DATA_PHASE_COUNT, 0);
    }

    /** 0 red, 1 green, 2 blue. */
    public int color() {
        return entityData.get(DATA_COLOR);
    }

    public Phase phase() {
        int o = entityData.get(DATA_PHASE);
        Phase[] all = Phase.values();
        return o >= 0 && o < all.length ? all[o] : Phase.NONE;
    }

    public int phaseCount() {
        return entityData.get(DATA_PHASE_COUNT);
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        entityData.set(DATA_PHASE, (byte) next.ordinal());
        entityData.set(DATA_PHASE_COUNT, ++phaseCount);
    }

    private @Nullable PrismColossus ownerEntity() {
        return owner != null && level() instanceof ServerLevel server && server.getEntity(owner) instanceof PrismColossus c ? c : null;
    }

    /** The re-merge: it flies to {@code to} and is gone. */
    public void flyHome(Vec3 to, long now) {
        home = to;
        homeFrom = position();
        noPhysics = true;
        setNoGravity(true);
        getNavigation().stop();
        setPhase(Phase.HOME);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive()) {
            ColossusEffects.handler().shardTick(this);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        phaseTicks++;
        if (cooldown > 0) {
            cooldown--;
        }
        PrismColossus colossus = ownerEntity();
        if (phase != Phase.HOME && (colossus == null || colossus.state() != PrismColossus.State.SHATTERED)) {
            discard(); // its Colossus is gone or reset
            return;
        }
        switch (phase) {
            case HOME -> {
                double u = Math.min(1.0, phaseTicks / (double) HOME_TICKS);
                Vec3 p = homeFrom.add(home.subtract(homeFrom).scale(u * u)).add(0, Math.sin(Math.PI * u) * 2.0, 0);
                setPos(p.x, p.y, p.z);
                setDeltaMovement(Vec3.ZERO);
                if (phaseTicks >= HOME_TICKS) {
                    discard();
                }
            }
            case LAND -> {
                if (phaseTicks >= LAND_TICKS || (onGround() && phaseTicks > 6)) {
                    setPhase(Phase.NONE);
                }
            }
            case NONE -> hunt(colossus);
            case TELL -> {
                getNavigation().stop();
                if (target != null) {
                    face(target.getX() - getX(), target.getZ() - getZ(), 30f);
                }
                if (phaseTicks >= TELL_TICKS) {
                    Vec3 d = target == null ? Vec3.directionFromRotation(0f, getYRot())
                            : new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
                    lungeDir = d.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0f, getYRot()) : new Vec3(d.x, 0, d.z).normalize();
                    connected = false;
                    setDeltaMovement(lungeDir.x * LUNGE_SPEED, 0.25, lungeDir.z * LUNGE_SPEED);
                    hasImpulse = true;
                    playSound(GuardianRegistry.SHARD_LUNGE.get(), 1.0f, 0.95f + random.nextFloat() * 0.1f);
                    setPhase(Phase.LUNGE);
                }
            }
            case LUNGE -> {
                if (!connected) {
                    Vec3 v = getDeltaMovement();
                    setDeltaMovement(lungeDir.x * LUNGE_SPEED, v.y, lungeDir.z * LUNGE_SPEED);
                }
                face(lungeDir.x, lungeDir.z, 90f);
                if (phaseTicks >= LUNGE_TICKS) {
                    setDeltaMovement(getDeltaMovement().multiply(0.25, 1.0, 0.25));
                    setPhase(Phase.RECOVER);
                }
            }
            case RECOVER -> {
                getNavigation().stop();
                if (phaseTicks >= RECOVER_TICKS) {
                    cooldown = 20 + random.nextInt(20);
                    setPhase(Phase.NONE);
                }
            }
            case STAGGER -> {
                getNavigation().stop();
                if (phaseTicks >= 20) {
                    cooldown = 16;
                    setPhase(Phase.NONE);
                }
            }
        }
        if (colossus != null && colossus.arena() != null && getY() < colossus.arena().floorY() - 4) {
            CrownArena a = colossus.arena();
            double angle = random.nextDouble() * Math.PI * 2;
            teleportTo(a.x() + Math.cos(angle) * 8, a.floorY() + 0.5, a.z() + Math.sin(angle) * 8);
            resetFallDistance();
        }
    }

    private void hunt(@Nullable PrismColossus colossus) {
        if (colossus == null) {
            return;
        }
        if (target == null || !colossus.fightersInside().contains(target) || tickCount % 20 == 0) {
            target = null;
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : colossus.fightersInside()) {
                double d = distanceToSqr(p);
                if (d < best) {
                    best = d;
                    target = p;
                }
            }
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        setTarget(target);
        double d = distanceTo(target);
        if (d <= LUNGE_RANGE && cooldown == 0 && hasLineOfSight(target)) {
            getNavigation().stop();
            setPhase(Phase.TELL);
            playSound(GuardianRegistry.SHARD_TELL.get(), 1.0f, 1.0f + 0.1f * color());
            return;
        }
        if (tickCount % 5 == 0) {
            getNavigation().moveTo(target, 1.3);
        }
        getLookControl().setLookAt(target, 30f, 30f);
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

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && phase == Phase.LUNGE && !connected && isAlive()) {
            AABB reach = getBoundingBox().inflate(0.4, 0.2, 0.4);
            PrismColossus colossus = ownerEntity();
            for (Player p : level().getEntitiesOfClass(Player.class, reach, p -> colossus != null && colossus.fairGame(p))) {
                connected = true;
                if (p.hurt(damageSources().mobAttack(this), (float) LUNGE_DAMAGE)) {
                    setDeltaMovement(getDeltaMovement().multiply(0.2, 1.0, 0.2));
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ combat hooks

    @Override
    public boolean isParryableAttackActive() {
        return phase == Phase.LUNGE && !connected;
    }

    @Override
    public double attackImpact() {
        return LUNGE_IMPACT;
    }

    @Override
    public double poise() {
        return POISE;
    }

    @Override
    public void onStagger(int ticks) {
        if (!level().isClientSide() && phase != Phase.HOME) {
            connected = true;
            setPhase(Phase.STAGGER);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase == Phase.HOME || phase == Phase.LAND && phaseTicks < 4) {
            return false;
        }
        PrismColossus colossus = level().isClientSide() ? null : ownerEntity();
        if (colossus != null && colossus.arena() != null
                && ArenaRules.burnsUp(source, colossus.arena().centre(), PrismColossus.PROJECTILE_RANGE)) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && colossus != null && source.getEntity() instanceof ServerPlayer p) {
            colossus.onShardHurtBy(p);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && dead) {
            PrismColossus colossus = ownerEntity();
            if (colossus != null) {
                colossus.onShardDied(this);
            }
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!level().isClientSide() && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == net.minecraft.world.entity.EntityEvent.DEATH && level().isClientSide()) {
            ColossusEffects.handler().shardDied(this);
        }
        super.handleEntityEvent(id);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return GuardianRegistry.SHARD_CHITTER.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 50;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GuardianRegistry.SHARD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GuardianRegistry.SHARD_DEATH.get();
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, this::animate));
    }

    private PlayState animate(AnimationState<PrismShard> s) {
        Phase p = phase();
        int count = phaseCount();
        RawAnimation anim = switch (p) {
            case TELL -> TELL;
            case LUNGE -> LUNGE;
            case RECOVER -> RECOVER;
            case STAGGER -> STAGGER;
            case HOME, LAND -> FLY;
            case NONE -> Math.hypot(getX() - xo, getZ() - zo) > 0.02 ? RUN : IDLE;
        };
        if (p != Phase.NONE && count != animatedCount && s.isCurrentAnimation(anim)) {
            s.getController().forceAnimationReset();
        }
        if (p != Phase.NONE) {
            animatedCount = count;
        }
        return s.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Unused: shards never persist. */
    @SuppressWarnings("unused")
    private static boolean alive(Entity e) {
        return e != null && e.isAlive();
    }
}
