package com.cosmicbreach.jelly;

import com.cosmicbreach.jelly.JellyRules.BloomPhase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The drift jelly (1.2 design section 7), a big passive creature of the layer 3 air: a bell 3 blocks wide, a glowing
 * core, tendrils hanging four to five blocks. It has no path finding and never falls: it sets its own velocity each tick
 * ({@link #steer}), drifting on a slow heading that changes every few seconds, holding itself 2 to 5 blocks over the
 * ground it probes twice a second ({@link JellyRules#hoverVelocity}), bobbing a little, and when hurt fleeing slowly
 * away for a few seconds. Over a void it just keeps its height.
 *
 * <ul>
 *   <li>The bell is its hitbox and is solid to players, who land on it: {@link JellyBounce} sends them high.</li>
 *   <li>The tendrils sting, below the bell, every ten ticks ({@link #sting}): 2 damage a second to each player, no knockback.</li>
 *   <li>A bloom jelly ({@link JellyBloom}) follows a bloom's plan instead: rising from under the layer, drifting, sinking
 *       away, and removing itself at its end, whatever loads or unloads.</li>
 * </ul>
 */
public class DriftJelly extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_FLEEING = SynchedEntityData.defineId(DriftJelly.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");
    private static final RawAnimation SQUISH = RawAnimation.begin().thenPlay("squish");
    /** Ticks a dying jelly sinks and deflates before it is gone (vanilla's is 20). */
    private static final int DEATH_TICKS = 26;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** A player within this many blocks is what the sting and the bell top look for. */
    private static final double NEAR = 9.0;

    // server state
    private boolean playerNear;
    private Vec3 vel = Vec3.ZERO;
    private double heading;
    private int headingTicks;
    private double wanderScale = 1.0;
    private double bobPhase;
    private int probeIn;
    private int groundDepth = JellyRules.UNKNOWN_GROUND;
    private boolean overGround;
    private double lastGroundX;
    private double lastGroundZ;
    private int fleeTicks;
    private Vec3 fleeDir = Vec3.ZERO;
    private int turnedAt = -100;
    private final Map<UUID, Integer> stung = new HashMap<>();
    private final Map<UUID, Integer> bounced = new HashMap<>();
    /** How far the open-air scan looks past the bell's box, and how far the jelly may then drift before it looks again. */
    private static final double OPEN_MARGIN = 2.0;
    /** Blocks the jelly may still drift on the last open-air scan (negative: move the vanilla way). */
    private double openBudget = -1.0;
    // a bloom's plan (zero for none)
    private long bloomStart;
    private int bloomLife;
    private double cruiseY = 90.0;
    private double riseScale = 1.0;
    // for tests and tools
    private int stings;
    private int bounces;
    private double stepNanos;
    private long steps;
    // client
    private float tendrilScale = 1.0f;
    private int clientProbe;

    public DriftJelly(EntityType<? extends DriftJelly> type, Level level) {
        super(type, level);
        this.xpReward = JellyRules.XP;
        this.setNoGravity(true);
        this.heading = random.nextDouble() * Math.PI * 2.0;
        this.headingTicks = 40 + random.nextInt(120);
        this.wanderScale = 0.7 + random.nextDouble() * 0.6;
        this.bobPhase = random.nextDouble() * Math.PI * 2.0;
        this.probeIn = random.nextInt(20);
        this.clientProbe = random.nextInt(10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, JellyRules.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, JellyRules.SPEED)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLEEING, false);
    }

    @Override
    protected void registerGoals() {
        // none: it steers itself
    }

    // ------------------------------------------------------------------ collision

    /** The bell is solid to whoever lands on it (players do not fall through; see {@link JellyBounce}). */
    @Override
    public boolean canBeCollidedWith() {
        return isAlive();
    }

    /** Jellies pass through each other: a bloom would otherwise jam. */
    @Override
    public boolean canCollideWith(Entity other) {
        return !(other instanceof DriftJelly) && super.canCollideWith(other);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Nobody shoves a jelly and it shoves nobody: no per-tick search for neighbours (a bloom of 25 would pay for it 25 times). */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    // ------------------------------------------------------------------ movement

    /**
     * It moves by its own steering ({@link #wanted}), whatever the vanilla path and move controls say. Its speed is kept here, not
     * in the entity's motion: vanilla zeroes any motion under 0.003 a tick before this runs, which a slow drift easing up from
     * rest never gets over.
     */
    @Override
    public void travel(Vec3 input) {
        if (level().isClientSide || isNoAi()) {
            return;                    // (vanilla calls this even for a mob with no AI)
        }
        Vec3 dm = getDeltaMovement();
        if (dm.lengthSqr() > vel.lengthSqr() + 4.0e-4) {
            vel = dm;                  // a shove (a blow's knockback) is taken up, then eased away like any other speed
        }
        Vec3 want = wanted();
        if (isRemoved()) {
            return;
        }
        vel = vel.add(want.subtract(vel).scale(JellyRules.EASE));
        setDeltaMovement(vel);
        if (!driftInOpenAir(vel)) {
            move(MoverType.SELF, vel);
        }
        vel = getDeltaMovement();
        // face where it drifts
        if (vel.x * vel.x + vel.z * vel.z > 1.0e-5) {
            float target = (float) (Mth.atan2(vel.z, vel.x) * Mth.RAD_TO_DEG) - 90.0f;
            setYRot(Mth.rotLerp(0.08f, getYRot(), target));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    /**
     * Most of a jelly's life is spent in open air, where a full {@link #move} (the block collision sweep, the fire and lava scan
     * and the inside-blocks check over the bell's box) finds nothing, every tick, for every jelly of a bloom. Instead the air
     * around the bell is scanned once, {@link #OPEN_MARGIN} blocks out, and while the jelly has drifted less than that margin
     * since, it simply moves: nothing solid, fluid or burning can be within reach of its box. Anywhere else (near blocks, in
     * unloaded chunks, a fast shove) it takes the vanilla move. Returns true if it moved here.
     */
    private boolean driftInOpenAir(Vec3 step) {
        double reach = Math.max(Math.abs(step.x), Math.max(Math.abs(step.y), Math.abs(step.z)));
        if (openBudget < reach + 0.05) {
            openBudget = scanOpenAir() ? OPEN_MARGIN - 0.1 : -1.0;
            if (openBudget < reach + 0.05) {
                return false;
            }
        }
        openBudget -= reach;
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        horizontalCollision = false;
        verticalCollision = false;
        minorHorizontalCollision = false;
        setOnGround(false);
        if (getRemainingFireTicks() <= 0) {
            setRemainingFireTicks(-getFireImmuneTicks());   // what move does when it finds no fire or lava under the box
        }
        return true;
    }

    /** True if every block within {@link #OPEN_MARGIN} of the bell's box is loaded and air. */
    private boolean scanOpenAir() {
        AABB box = getBoundingBox().inflate(OPEN_MARGIN);
        int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
        int x1 = Mth.floor(box.maxX), y1 = Mth.floor(box.maxY), z1 = Mth.floor(box.maxZ);
        Level level = level();
        if (y0 < level.getMinBuildHeight() || y1 >= level.getMaxBuildHeight() || !level.hasChunksAt(x0, z0, x1, z1)) {
            return false;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    if (!level.getBlockState(p.set(x, y, z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide) {
            long t0 = System.nanoTime();
            super.aiStep();
            if ((tickCount + getId()) % 10 == 0) {
                playerNear = level().getNearestPlayer(this, NEAR) != null;
            }
            if (playerNear) {
                sting();
                standing();
            }
            stepNanos += System.nanoTime() - t0;
            steps++;
            return;
        }
        super.aiStep();
        clientTendrils();
    }

    /** The velocity (blocks a tick) it wants this tick: a bloom's plan, a flight, or a drift at its height. */
    private Vec3 wanted() {
        double want_x = 0.0;
        double want_y = 0.0;
        double want_z = 0.0;
        long now = level().getGameTime();
        if (isDeadOrDying()) {
            want_y = -0.04;
        } else {
            bobPhase += 0.07;
            double bob = 0.006 * Math.sin(bobPhase);
            boolean fleeing = fleeTicks > 0;
            if (fleeing) {
                fleeTicks--;
                if (fleeTicks == 0) {
                    entityData.set(DATA_FLEEING, false);
                }
            }
            if (bloomLife > 0) {
                BloomPhase phase = JellyRules.bloomPhase(now - bloomStart, bloomLife);
                if (phase == BloomPhase.GONE || (phase == BloomPhase.SINK && getY() < 6.0)) {
                    finishBloom();
                    return Vec3.ZERO;
                }
                double v = JellyRules.bloomVertical(phase) * (phase == BloomPhase.RISE ? riseScale : 1.0);
                if (phase == BloomPhase.RISE && getY() >= cruiseY) {
                    v = 0.0;
                }
                want_y = v + bob;
                double speed = 0.012 * wanderScale;      // a bloom stays within a hundred blocks or so of where it began, in its few minutes
                want_x = Math.cos(heading) * speed;
                want_z = Math.sin(heading) * speed;
                if (horizontalCollision || (verticalCollision && v != 0.0)) {
                    turn();
                }
            } else {
                // ground, probed twice a second
                if (--probeIn <= 0) {
                    probeIn = 10 + (getId() & 7);
                    groundDepth = groundDepth(level(), blockPosition());
                    if (groundDepth >= 0) {
                        overGround = true;
                        lastGroundX = getX();
                        lastGroundZ = getZ();
                    } else if (overGround && fleeTicks == 0) {
                        // drifted off the edge of its ground: back to where it last had some (one that began over a void roams freely)
                        overGround = false;
                        heading = Math.atan2(lastGroundZ - getZ(), lastGroundX - getX()) + (random.nextDouble() - 0.5) * 0.5;
                        headingTicks = 60 + random.nextInt(60);
                        wanderScale = 0.8 + random.nextDouble() * 0.4;
                    }
                }
                double height = groundDepth < 0 ? -1.0 : groundDepth + (getY() - Math.floor(getY()));
                want_y = JellyRules.hoverVelocity(height) + bob;
                if (getY() < 8.0) {
                    want_y = Math.max(want_y, 0.03);
                } else if (getY() > 152.0) {
                    want_y = Math.min(want_y, -0.03);
                }
                if (--headingTicks <= 0) {
                    heading = random.nextDouble() * Math.PI * 2.0;
                    headingTicks = 60 + random.nextInt(160);
                    wanderScale = random.nextInt(5) == 0 ? 0.0 : 0.6 + random.nextDouble() * 0.7;   // now and then it just hangs
                }
                if (horizontalCollision) {
                    turn();
                }
                double speed = JellyRules.WANDER_SPEED * wanderScale;
                want_x = Math.cos(heading) * speed;
                want_z = Math.sin(heading) * speed;
            }
            if (fleeing) {
                want_x = fleeDir.x * JellyRules.FLEE_SPEED;
                want_z = fleeDir.z * JellyRules.FLEE_SPEED;
                want_y += fleeDir.y * JellyRules.FLEE_SPEED;
            }
        }
        return new Vec3(want_x, want_y, want_z);
    }

    /** Blocked: swing round to a new heading (at most once in ten ticks, so a wall is not a spin). */
    private void turn() {
        if (tickCount - turnedAt < 10) {
            return;
        }
        turnedAt = tickCount;
        heading += Math.PI * (0.6 + random.nextDouble() * 0.8);
        headingTicks = 80 + random.nextInt(100);
    }

    /** The number of air blocks between {@code pos} and the first block with a collision shape below it, or {@link JellyRules#UNKNOWN_GROUND}. */
    public static int groundDepth(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= JellyRules.PROBE_DEPTH + 1; i++) {
            p.setY(pos.getY() - i);
            if (p.getY() < level.getMinBuildHeight()) {
                break;
            }
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return i - 1;
            }
        }
        return JellyRules.UNKNOWN_GROUND;
    }

    // ------------------------------------------------------------------ the sting

    private static DamageSource stingSource(Entity by) {
        Holder<DamageType> type = by.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(Jellies.STING_DAMAGE);
        return new DamageSource(type, by);
    }

    /** Every ten ticks the tendrils sting what hangs in them: a player (not creative or spectating), once a second, no knockback. */
    private void sting() {
        if (isDeadOrDying() || (tickCount + getId()) % JellyRules.STING_PERIOD != 0) {
            return;
        }
        AABB volume = new AABB(getX() - JellyRules.STING_HALF_WIDTH, getY() - JellyRules.TENDRIL_HANG, getZ() - JellyRules.STING_HALF_WIDTH,
                getX() + JellyRules.STING_HALF_WIDTH, getY(), getZ() + JellyRules.STING_HALF_WIDTH);
        for (Player p : level().getEntitiesOfClass(Player.class, volume, EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
            Integer last = stung.get(p.getUUID());
            if (JellyRules.stingReady(tickCount, last == null ? Integer.MIN_VALUE / 2 : last) && p.hurt(stingSource(this), JellyRules.STING_DAMAGE)) {
                stung.put(p.getUUID(), tickCount);
                stings++;
            }
        }
        if (stung.size() > 8) {
            stung.values().removeIf(t -> tickCount - t > 200);
        }
    }

    // ------------------------------------------------------------------ the bell top

    /** A player standing on the bell, not sneaking, is bounced at the least launch (a landing is {@link JellyBounce}'s). */
    private void standing() {
        if (isDeadOrDying() || (tickCount + getId()) % 2 != 0) {
            return;
        }
        AABB top = new AABB(getX() - 1.8, getY() + JellyRules.HEIGHT - 0.3, getZ() - 1.8, getX() + 1.8, getY() + JellyRules.HEIGHT + 0.7, getZ() + 1.8);
        for (Player p : level().getEntitiesOfClass(Player.class, top, EntitySelector.NO_SPECTATORS)) {
            if (p.onGround() && !p.isShiftKeyDown() && JellyRules.landsOnBell(p.getX() - getX(), p.getZ() - getZ(), p.getY(), getY())) {
                JellyBounce.bounce(this, p, JellyRules.BOUNCE_MIN);
            }
        }
    }

    boolean bounceReady(Player p) {
        Integer last = bounced.get(p.getUUID());
        return last == null || tickCount - last >= JellyRules.BOUNCE_COOLDOWN;
    }

    void bounced(Player p) {
        bounced.put(p.getUUID(), tickCount);
        bounces++;
        if (bounced.size() > 8) {
            bounced.values().removeIf(t -> tickCount - t > 100);
        }
        triggerAnim("action", "squish");
    }

    // ------------------------------------------------------------------ hurt, death, sounds

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) {
            Entity from = source.getEntity();
            Vec3 away = from == null ? new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5) : position().subtract(from.position());
            away = new Vec3(away.x, 0.0, away.z);
            fleeDir = (away.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize()).add(0.0, 0.15, 0.0);
            fleeTicks = JellyRules.FLEE_TICKS;
            entityData.set(DATA_FLEEING, true);
            triggerAnim("action", "hurt");
        }
        return hurt;
    }

    public boolean isFleeing() {
        return entityData.get(DATA_FLEEING);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8f;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f + random.nextFloat() * 0.15f;
    }

    /** A bloom jelly is not left to vanilla's despawning (a far one would go within a minute); its plan ends it. */
    @Override
    public boolean requiresCustomPersistence() {
        return bloomLife > 0;
    }

    // ------------------------------------------------------------------ the bloom

    /** Puts this jelly in a bloom that began at game time {@code start} and lasts {@code life} ticks. */
    void joinBloom(long start, int life, double cruise, double rise, double heading) {
        this.bloomStart = start;
        this.bloomLife = life;
        this.cruiseY = cruise;
        this.riseScale = rise;
        this.heading = heading;
    }

    public boolean inBloom() {
        return bloomLife > 0;
    }

    private void finishBloom() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.GLOW, getX(), getY() + 1.0, getZ(), 12, 0.8, 0.8, 0.8, 0.02);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (bloomLife > 0) {
            tag.putLong("BloomStart", bloomStart);
            tag.putInt("BloomLife", bloomLife);
            tag.putDouble("CruiseY", cruiseY);
            tag.putDouble("RiseScale", riseScale);
            tag.putDouble("Heading", heading);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BloomLife")) {
            bloomStart = tag.getLong("BloomStart");
            bloomLife = tag.getInt("BloomLife");
            cruiseY = tag.getDouble("CruiseY");
            riseScale = tag.getDouble("RiseScale");
            heading = tag.getDouble("Heading");
        }
    }

    // ------------------------------------------------------------------ client

    /** The tendrils shorten to fit the room under the bell (a probe five times a second), so they never hang into the ground. */
    private void clientTendrils() {
        if (--clientProbe <= 0) {
            clientProbe = 10;
            int depth = groundDepth(level(), blockPosition());
            float target = depth < 0 ? 1.0f : Mth.clamp((depth + (float) (getY() - Math.floor(getY())) + 0.3f) / 5.0f, 0.3f, 1.0f);
            clientTarget = target;
        }
        tendrilScale += (clientTarget - tendrilScale) * 0.12f;
    }

    private float clientTarget = 1.0f;

    /** How much of its length the tendrils are drawn at, 0.3 to 1. */
    public float tendrilScale() {
        return tendrilScale;
    }

    // ------------------------------------------------------------------ for tests and tools

    public int stings() {
        return stings;
    }

    public int bounces() {
        return bounces;
    }

    /** Mean nanoseconds a server tick of this jelly took (its whole aiStep), and how many it has had. */
    public double meanStepNanos() {
        return steps == 0 ? 0.0 : stepNanos / steps;
    }

    public long stepCount() {
        return steps;
    }

    /** Starts the timing again (after a warm up). */
    public void resetTiming() {
        stepNanos = 0.0;
        steps = 0;
    }

    public int groundDepthNow() {
        return groundDepth;
    }

    public int fleeTicksLeft() {
        return fleeTicks;
    }

    @Nullable
    public Vec3 fleeDirection() {
        return fleeTicks > 0 ? fleeDir : null;
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(isDeadOrDying() ? DEATH : IDLE))
                .setAnimationSpeedHandler(jelly -> jelly.isFleeing() ? 1.7 : 1.0));
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP)
                .triggerableAnim("hurt", HURT)
                .triggerableAnim("squish", SQUISH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
