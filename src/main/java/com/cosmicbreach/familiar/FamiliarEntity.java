package com.cosmicbreach.familiar;

import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.progression.ProgressionStats;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A combat familiar (GDD 8.2), alive only while summoned from its Familiar Lantern ({@link FamiliarSessions}): never
 * saved, never despawned, gone the moment its owner logs out, dies or puts the lantern away. It flies (no gravity, no
 * collisions, no fall damage), so it never presses a plate or crosses a tripwire, and it teleports back to its owner
 * from beyond {@value FamiliarRules#TELEPORT_RANGE} blocks. Its numbers come from its owner every second: health
 * 10 + 0.5 x Resilience, damage 2 + 0.1 x Arcane.
 *
 * <p>Fighting ({@link FamiliarMode}): it picks its owner's recent target, else what hurt its owner (or it) lately, and
 * in Attack also the nearest monster near its owner; it darts in for a hit every {@value FamiliarRules#ATTACK_INTERVAL}
 * ticks with the damage type {@code cosmicbreach:familiar}, which credits its owner. Each kind moves its own way and
 * adds its specials ({@link #steer}, {@link #special}).
 */
public abstract class FamiliarEntity extends Mob implements GeoEntity, OwnableEntity {
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_ACT = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ACT_AT = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);

    /** One-off moves the clients animate ({@link #act}): its hit, the Gravikin's hop and taunt, the wisp's flare, the moth's glint. */
    public static final int ACT_STRIKE = 1;
    public static final int ACT_HOP = 2;
    public static final int ACT_TAUNT = 3;
    public static final int ACT_FLARE = 4;
    public static final int ACT_GLINT = 5;

    protected static final String CONTROLLER = "main";
    /** Ticks before its next hit it sets off toward its target (so the hit lands as it arrives). */
    protected static final int DART_LEAD = 8;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final Cadence attack = new Cadence(FamiliarRules.ATTACK_INTERVAL);
    private @Nullable UUID owner;
    private @Nullable UUID bond;
    private @Nullable LivingEntity target;
    private long targetCheckedAt = -1_000_000L;
    private long lastHurtAt = -1_000_000L;
    private double hitDamage = FamiliarRules.DAMAGE_BASE;
    /** Its own phase, so two familiars never move in step. */
    protected final double phase;

    protected FamiliarEntity(EntityType<? extends FamiliarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 0;
        this.phase = level.random.nextDouble() * 400.0;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, FamiliarRules.HEALTH_BASE)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, FamiliarRules.DAMAGE_BASE)
                .add(Attributes.FOLLOW_RANGE, FamiliarRules.TARGET_RANGE);
    }

    public abstract FamiliarKind kind();

    /** Where it rests beside its owner at game time {@code time} (also where it lands after a teleport). */
    protected abstract Vec3 home(Player owner, double time);

    /** Its movement this tick: toward {@code target} when a hit is near, else about its owner. Server. */
    protected abstract void steer(ServerPlayer owner, @Nullable LivingEntity target, long now);

    /** Its specials this tick (Scorch, the taunt and the slow, Refract and the cleanse). Server. */
    protected abstract void special(ServerPlayer owner, @Nullable LivingEntity target, FamiliarMode mode, long now);

    /** The sound of its hit. */
    protected abstract SoundEvent strikeSound();

    /** Its idle loop (GeckoLib). */
    protected abstract RawAnimation idle();

    /** The animation of a one-off move ({@link #ACT_STRIKE} and the rest), or null if it has none. */
    protected abstract @Nullable RawAnimation action(int act);

    /** How long a one-off move's animation lasts, in ticks. */
    protected abstract int actionTicks(int act);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, (byte) FamiliarMode.GUARD.ordinal());
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_ACT, (byte) 0);
        builder.define(DATA_ACT_AT, 0);
    }

    /**
     * A one-off move every client animates (server side). Synced state rather than GeckoLib's triggered animations, which
     * left the controller with no animation at all once a triggered one had played.
     */
    protected void act(int act) {
        entityData.set(DATA_ACT, (byte) act);
        entityData.set(DATA_ACT_AT, (int) level().getGameTime());
    }

    // ------------------------------------------------------------------ the bond

    /** Ties it to {@code player} and {@code bond}'s numbers as it is summoned at {@code now}. Server. */
    void bind(ServerPlayer player, FamiliarBond bondData, long now) {
        this.owner = player.getUUID();
        this.bond = bondData.id();
        this.entityData.set(DATA_OWNER, player.getId());
        setMode(bondData.mode());
        refreshStats(player);
        setHealth(Math.max(0.05f, bondData.healthAt(now)) * getMaxHealth());
    }

    public @Nullable UUID bondId() {
        return bond;
    }

    @Override
    public @Nullable UUID getOwnerUUID() {
        return owner;
    }

    /** The owner's entity id (either side; -1 before it is bound). */
    public int ownerEntityId() {
        return entityData.get(DATA_OWNER);
    }

    /** The owner as the client sees it, or null. */
    public @Nullable Player ownerOnClient() {
        return level().getEntity(ownerEntityId()) instanceof Player p ? p : null;
    }

    /** The owner on the server, or null if they are gone. */
    public @Nullable ServerPlayer ownerPlayer() {
        return owner != null && level() instanceof ServerLevel server && server.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
    }

    public FamiliarMode mode() {
        return FamiliarMode.byOrdinal(entityData.get(DATA_MODE));
    }

    public void setMode(FamiliarMode mode) {
        entityData.set(DATA_MODE, (byte) mode.ordinal());
        target = null;
        targetCheckedAt = -1_000_000L;
    }

    /** The entity id of what it fights now (-1 for nothing); synced. */
    public int targetId() {
        return entityData.get(DATA_TARGET);
    }

    /** One hit's damage now (from its owner's Arcane). */
    public double hitDamage() {
        return hitDamage;
    }

    /** Health and damage from its owner's stats (every second, and when summoned). */
    void refreshStats(ServerPlayer player) {
        double max = FamiliarRules.maxHealth(kind(), ProgressionStats.points(player, Stat.RESILIENCE));
        AttributeInstance mh = getAttribute(Attributes.MAX_HEALTH);
        if (mh != null && Math.abs(mh.getBaseValue() - max) > 1e-6) {
            float share = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1f;
            mh.setBaseValue(max);
            setHealth((float) (share * max));
        }
        hitDamage = FamiliarRules.damage(ProgressionStats.points(player, Stat.ARCANE));
        AttributeInstance ad = getAttribute(Attributes.ATTACK_DAMAGE);
        if (ad != null) {
            ad.setBaseValue(hitDamage);
        }
    }

    // ------------------------------------------------------------------ the brain (server)

    @Override
    protected void customServerAiStep() {
        ServerPlayer player = ownerPlayer();
        if (player == null || player.level() != level() || !player.isAlive() || player.isSpectator() || !FamiliarSessions.owns(player, this)) {
            discard();
            return;
        }
        long now = level().getGameTime();
        if (tickCount % 20 == 0) {
            refreshStats(player);
        }
        if (distanceToSqr(player) > FamiliarRules.TELEPORT_RANGE * FamiliarRules.TELEPORT_RANGE) {
            teleportHome(player, now);
        }
        FamiliarMode mode = mode();
        LivingEntity t = mode.fights() ? target(player, mode, now) : null;
        entityData.set(DATA_TARGET, t == null ? -1 : t.getId());
        steer(player, t, now);
        if (t != null) {
            strikeIfClose(player, t, now);
        }
        special(player, t, mode, now);
        if (now - lastHurtAt > FamiliarRules.REGEN_AFTER && tickCount % FamiliarRules.REGEN_EVERY == 0 && getHealth() < getMaxHealth()) {
            heal(FamiliarRules.REGEN);
        }
    }

    /** Back to its owner's side (a burst where it left and where it lands). */
    protected void teleportHome(ServerPlayer player, long now) {
        Vec3 from = position();
        Vec3 to = home(player, now + phase);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        onTeleported(from, to);
        FamiliarNet.fx(this, FamiliarFxPayload.TELEPORT, getId(), -1, from, 0);
    }

    /** Told after a teleport home (the Gravikin forgets its perch). */
    protected void onTeleported(Vec3 from, Vec3 to) {
    }

    /** What it fights now: re-picked every 10 ticks, or at once when the old one stops counting. */
    private @Nullable LivingEntity target(ServerPlayer player, FamiliarMode mode, long now) {
        if (target != null && (now - targetCheckedAt >= 10 || !valid(player, target, mode))) {
            target = null;
        }
        if (target == null && now - targetCheckedAt >= 10) {
            targetCheckedAt = now;
            target = FamiliarSessions.pickTarget(player, this, mode, now);
        }
        return target;
    }

    /** True if it may fight {@code e} for {@code player} in {@code mode}. */
    boolean valid(ServerPlayer player, @Nullable Entity e, FamiliarMode mode) {
        if (!(e instanceof LivingEntity living) || e instanceof FamiliarEntity || !living.isAlive() || !HitResolver.isValidTarget(player, living)) {
            return false;
        }
        double range = mode == FamiliarMode.GUARD ? FamiliarRules.GUARD_RADIUS : FamiliarRules.TARGET_RANGE;
        return living.level() == level() && living.distanceToSqr(player) <= range * range;
    }

    /** Where it aims on {@code t}: a little over its middle. */
    protected static Vec3 aim(LivingEntity t) {
        return t.position().add(0, t.getBbHeight() * 0.55, 0);
    }

    /** Where it flies to strike {@code t}: just inside its reach, on its own side. */
    protected Vec3 strikePoint(LivingEntity t) {
        Vec3 c = aim(t);
        Vec3 away = position().subtract(c);
        double len = away.length();
        double keep = Math.max(0.3, FamiliarRules.ATTACK_REACH * 0.6 + t.getBbWidth() * 0.25);
        return len < 1e-4 ? c.add(0, 0.5, 0) : c.add(away.scale(keep / len));
    }

    /** True while the next hit is near enough that it should be on its way. */
    protected boolean darting(@Nullable LivingEntity t, long now) {
        return t != null && attack.left(now) <= DART_LEAD;
    }

    /** Hits {@code t} if its turn has come and it is in reach. */
    protected boolean strikeIfClose(ServerPlayer player, LivingEntity t, long now) {
        if (!attack.ready(now)) {
            return false;
        }
        double reach = FamiliarRules.ATTACK_REACH + t.getBbWidth() * 0.5;
        if (position().add(0, getBbHeight() * 0.5, 0).distanceToSqr(aim(t)) > reach * reach) {
            return false;
        }
        attack.fire(now);
        t.invulnerableTime = 0; // like the engine's hits: a familiar paces its own
        boolean hit = t.hurt(FamiliarDamage.of(this, player), (float) hitDamage);
        act(ACT_STRIKE);
        playSound(strikeSound(), 0.8f, 0.9f + random.nextFloat() * 0.2f);
        if (hit) {
            onStruck(player, t, now);
        }
        return hit;
    }

    /** After one of its hits landed. */
    protected void onStruck(ServerPlayer player, LivingEntity t, long now) {
    }

    // ------------------------------------------------------------------ moving

    /** Flies toward {@code goal} this tick ({@code gain} of the gap, at most {@link FamiliarPaths#MAX_SPEED}). */
    protected void flyTo(Vec3 goal, double gain) {
        setDeltaMovement(FamiliarPaths.step(position(), goal, gain, FamiliarPaths.MAX_SPEED));
    }

    /** Turns toward {@code point} (on the ground plane), a share of the way a tick. */
    protected void faceToward(Vec3 point, float share) {
        double dx = point.x - getX();
        double dz = point.z - getZ();
        if (dx * dx + dz * dz < 1e-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float y = Mth.rotLerp(share, getYRot(), yaw);
        setYRot(y);
        yBodyRot = y;
        yHeadRot = y;
    }

    /** Moves by its velocity, through anything: it flies, and the server alone moves it. */
    @Override
    public void travel(Vec3 input) {
        if (!level().isClientSide()) {
            move(MoverType.SELF, getDeltaMovement());
        }
    }

    // ------------------------------------------------------------------ taking hits and dying

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (friendly(source.getEntity()) || friendly(source.getDirectEntity())) {
            return false;
        }
        if (!level().isClientSide() && source.getEntity() instanceof Player other && ownerPlayer() instanceof ServerPlayer mine
                && !other.canHarmPlayer(mine)) {
            return false; // another player's familiar is as safe as its owner where there is no PvP
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            lastHurtAt = level().getGameTime();
        }
        return hurt;
    }

    /** Its owner and its owner's other familiars never hurt it. */
    private boolean friendly(@Nullable Entity e) {
        if (e == null || owner == null) {
            return false;
        }
        return e.getUUID().equals(owner) || (e instanceof FamiliarEntity f && Objects.equals(f.owner, owner));
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL) || super.isInvulnerableTo(source);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide() && !dead && !isRemoved()) {
            FamiliarSessions.died(this);
        }
        super.die(source);
    }

    /** Gone in a few ticks, in a burst of its own light (the client draws it), not vanilla's slow topple. */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= 3 && !level().isClientSide() && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return FamiliarRegistry.HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return FamiliarRegistry.DEATH.get();
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7f;
    }

    // ------------------------------------------------------------------ a companion, not a creature

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return friendly(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean shouldShowName() {
        return hasCustomName() && super.shouldShowName();
    }

    // ------------------------------------------------------------------ GeckoLib

    /** The start of the one-off move the client animates now (to restart it when a new one comes). */
    private int seenActAt = Integer.MIN_VALUE;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 2, state -> {
            int act = entityData.get(DATA_ACT);
            int at = entityData.get(DATA_ACT_AT);
            long age = level().getGameTime() - at;
            RawAnimation move = act != 0 && age >= 0 && age < actionTicks(act) ? action(act) : null;
            if (move != null) {
                if (at != seenActAt) {
                    seenActAt = at;
                    state.getController().forceAnimationReset();
                }
                return state.setAndContinue(move);
            }
            return state.setAndContinue(idle());
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
