package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.combat.ImpactSink;
import com.cosmicbreach.combat.ParryableAttacker;
import java.util.Collections;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * One of the Unsung's three porcelain masks (Unsung design v1): its own health pool (250, scaled with the players),
 * armor 10 and toughness 4, moved every tick by its {@link Unsung}, which decides whether a hit lands: only the
 * singing mask can be hurt, except in a Break when all the living masks can, at x1.5; the others ring off with a
 * porcelain tink. At zero health it breaks (it doesn't die): its line leaves the song and it lies cracked on the floor
 * until the fight ends. It hands its Impact to the choir's shared Break gauge and says when its gold Bass Drop can be
 * parried. Never saved: the Unsung makes its masks again.
 */
public class UnsungMask extends LivingEntity implements GeoEntity, ParryableAttacker, ImpactSink, Enemy {
    /** What the mask is doing, for its animation. */
    public enum Mode { REST, RISE, FLOAT, DROP, FALLEN, BROKEN, GONE }

    public static final int FLAG_SINGING = 1;
    public static final int FLAG_GLINT = 2;
    public static final int FLAG_INHALE = 4;
    public static final int FLAG_HUM = 8;

    public static final byte EVENT_TINK = 100;
    public static final byte EVENT_SHATTER = 101;
    public static final byte EVENT_FIRST_NOTE = 102;
    public static final byte EVENT_LAND = 103;
    public static final byte EVENT_PARRIED = 104;

    private static final EntityDataAccessor<Byte> DATA_VOICE = SynchedEntityData.defineId(UnsungMask.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(UnsungMask.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_MODE_START = SynchedEntityData.defineId(UnsungMask.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(UnsungMask.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_CHOIR = SynchedEntityData.defineId(UnsungMask.class, EntityDataSerializers.INT);

    private static final RawAnimation REST = RawAnimation.begin().thenLoop("rest");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("rise").thenLoop("float");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("float");
    private static final RawAnimation SING = RawAnimation.begin().thenLoop("sing");
    private static final RawAnimation INHALE = RawAnimation.begin().thenPlayAndHold("inhale");
    private static final RawAnimation DROP = RawAnimation.begin().thenPlayAndHold("drop");
    private static final RawAnimation FALLEN = RawAnimation.begin().thenPlay("fall").thenLoop("fallen");
    private static final RawAnimation BROKEN = RawAnimation.begin().thenPlay("shatter").thenLoop("broken");
    private static final RawAnimation SHROUD = RawAnimation.begin().thenLoop("shroud");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private @Nullable Unsung owner;
    /** Set by the choir around the hits of its attacks. */
    boolean parryableNow;
    double currentImpact;
    private long lastTink = Long.MIN_VALUE;

    public UnsungMask(EntityType<? extends UnsungMask> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, UnsungMoves.MASK_HEALTH)
                .add(Attributes.ARMOR, UnsungMoves.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, UnsungMoves.TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** A mask of {@code voice} for {@code owner}, its face at {@code face}. */
    static UnsungMask make(Level level, Unsung owner, Voice voice, Vec3 face) {
        UnsungMask m = new UnsungMask(UnsungRegistry.UNSUNG_MASK.get(), level);
        m.owner = owner;
        m.entityData.set(DATA_VOICE, (byte) voice.ordinal());
        m.entityData.set(DATA_CHOIR, owner.getId());
        Vec3 feet = ChoirArena.feetForFace(face);
        m.moveTo(feet.x, feet.y, feet.z, 0f, 0f);
        return m;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VOICE, (byte) 0);
        builder.define(DATA_MODE, (byte) Mode.REST.ordinal());
        builder.define(DATA_MODE_START, 0L);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_CHOIR, -1);
    }

    // ------------------------------------------------------------------ state

    public Voice voice() {
        return Voice.values()[Math.floorMod(entityData.get(DATA_VOICE), 3)];
    }

    public Mode mode() {
        return Mode.values()[Math.floorMod(entityData.get(DATA_MODE), Mode.values().length)];
    }

    public long modeStart() {
        return entityData.get(DATA_MODE_START);
    }

    void setMode(Mode mode, long now) {
        if (mode() != mode) {
            entityData.set(DATA_MODE, (byte) mode.ordinal());
            entityData.set(DATA_MODE_START, now);
        }
    }

    public boolean hasFlag(int flag) {
        return (entityData.get(DATA_FLAGS) & flag) != 0;
    }

    void setFlag(int flag, boolean on) {
        byte f = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? f | flag : f & ~flag);
        if (next != f) {
            entityData.set(DATA_FLAGS, next);
        }
    }

    public boolean singing() {
        return hasFlag(FLAG_SINGING);
    }

    /** Broken for good (its health is gone). */
    public boolean shattered() {
        Mode m = mode();
        return m == Mode.BROKEN || m == Mode.GONE;
    }

    /** The choir this mask belongs to (the server's, or on the client the synced one), or null. */
    public @Nullable Unsung choir() {
        if (owner != null) {
            return owner;
        }
        Entity e = level().getEntity(entityData.get(DATA_CHOIR));
        return e instanceof Unsung u ? u : null;
    }

    /** Where the face's middle is. */
    public Vec3 face() {
        return position().add(0, UnsungMoves.FACE_UP, 0);
    }

    /** Where the mouth is: a little under the face's middle, toward where the mask looks. */
    public Vec3 mouth() {
        Vec3 ahead = com.cosmicbreach.guardian.Telegraphs.forward(getYRot());
        return face().add(ahead.scale(0.95)).add(0, -0.35, 0);
    }

    /** Puts the face at {@code face}, looking along {@code yaw}. */
    void placeFace(Vec3 face, float yaw) {
        Vec3 feet = ChoirArena.feetForFace(face);
        setPos(feet.x, feet.y, feet.z);
        setDeltaMovement(Vec3.ZERO);
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    /** Full health at {@code max}, whole again. */
    void restore(double max) {
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(max);
        }
        super.setHealth((float) max);
        setFlag(FLAG_SINGING, false);
        setFlag(FLAG_GLINT, false);
        setFlag(FLAG_INHALE, false);
        setFlag(FLAG_HUM, false);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        hurtTime = 0; // no red flash: the choir shows its hits
        setDeltaMovement(Vec3.ZERO);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (level().isClientSide()) {
            UnsungEffects.handler().maskTick(this);
            return;
        }
        if (owner == null || owner.isRemoved()) {
            discard();
        }
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        yBodyRot = getYRot();
        return animStep;
    }

    // ------------------------------------------------------------------ hits

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            if (owner != null) {
                owner.maskLost(this);
            }
            discard();
            return false;
        }
        if (owner == null || shattered()) {
            return false;
        }
        double mult = owner.hitMultiplier(this, source);
        if (mult <= 0.0) {
            return false;
        }
        float before = getHealth();
        boolean hurt = super.hurt(source, (float) (amount * mult));
        if (hurt) {
            owner.hitLanded(this, source, Math.max(0f, before - getHealth()));
        }
        return hurt;
    }

    /** A porcelain tink for a hit that glanced off (at most every few ticks). */
    void tink(long now) {
        if (now - lastTink >= 4) {
            lastTink = now;
            level().broadcastEntityEvent(this, EVENT_TINK);
            level().playSound(null, getX(), getY() + UnsungMoves.FACE_UP, getZ(), UnsungRegistry.TINK.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.9f + 0.2f * random.nextFloat());
        }
    }

    /** Health can't pass zero: at zero the mask breaks and stays. */
    @Override
    public void setHealth(float health) {
        if (!level().isClientSide() && owner != null && health <= 0.0f && !shattered()) {
            super.setHealth(0.001f);
            owner.maskBroken(this);
            return;
        }
        super.setHealth(health);
    }

    /** No healing mid-fight: only the choir restores its masks. */
    @Override
    public void heal(float amount) {
    }

    @Override
    public void takeImpact(double impact) {
        if (owner != null && !level().isClientSide()) {
            owner.maskImpact(this, impact);
        }
    }

    @Override
    public boolean isParryableAttackActive() {
        return parryableNow;
    }

    @Override
    public double attackImpact() {
        return currentImpact;
    }

    @Override
    public void onParried(Player player) {
        if (owner != null) {
            owner.dropParried(this, player);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EVENT_TINK && id <= EVENT_PARRIED && level().isClientSide()) {
            UnsungEffects.handler().maskEvent(this, id);
            return;
        }
        super.handleEntityEvent(id);
    }

    // ------------------------------------------------------------------ what it is not

    @Override
    public boolean isAttackable() {
        return !shattered();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved() && !shattered();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.5, 2.0, 1.5);
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return UnsungRegistry.HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return UnsungRegistry.CRACK.get();
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pose", 3, this::pose));
        controllers.add(new AnimationController<>(this, "shroud", 0, s -> s.setAndContinue(SHROUD)));
    }

    private PlayState pose(AnimationState<UnsungMask> s) {
        RawAnimation anim = switch (mode()) {
            case REST -> REST;
            case RISE -> RISE;
            case DROP -> DROP;
            case FALLEN -> FALLEN;
            case BROKEN, GONE -> BROKEN;
            case FLOAT -> hasFlag(FLAG_INHALE) ? INHALE : singing() ? SING : FLOAT;
        };
        return s.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
