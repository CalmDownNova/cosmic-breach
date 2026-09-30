package com.cosmicbreach.guardian;

import com.cosmicbreach.combat.ImpactSink;
import java.util.Collections;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A piece of a guardian that can be struck on its own: the Colossus's fists (a stuck fist is the parry's free hits),
 * later the Leviathan's song glands and the Heliarch's hands. A living entity so the combat engine's swings, hit
 * sparks and hit-stop find it like any creature; it takes no damage itself but hands every hit and its Impact to
 * its owner ({@link Owner}), which applies them with the part's multiplier. The owner moves it every tick and
 * draws it (the part itself draws nothing). Never saved: the owner makes its parts again.
 */
public class GuardianPart extends LivingEntity implements ImpactSink {
    /** What owns parts: a hit on one, to apply as the owner sees fit. */
    public interface Owner {
        /** A hit on {@code part}; true if it did damage. */
        boolean hurtByPart(GuardianPart part, DamageSource source, float amount);

        /** Impact landed on {@code part}. */
        default void impactOnPart(GuardianPart part, double impact) {
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(GuardianPart.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(GuardianPart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(GuardianPart.class, EntityDataSerializers.FLOAT);

    private @Nullable Entity owner;

    public GuardianPart(EntityType<? extends GuardianPart> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Makes a part of {@code owner} with a role (the owner's own numbering), {@code width} by {@code height}, at {@code at}. */
    public static GuardianPart spawn(ServerLevel level, Entity owner, int role, float width, float height, Vec3 at) {
        GuardianPart part = new GuardianPart(GuardianRegistry.GUARDIAN_PART.get(), level);
        part.owner = owner;
        part.entityData.set(DATA_ROLE, (byte) role);
        part.entityData.set(DATA_WIDTH, width);
        part.entityData.set(DATA_HEIGHT, height);
        part.refreshDimensions();
        part.moveTo(at.x, at.y - height / 2.0, at.z, 0f, 0f);
        level.addFreshEntity(part);
        return part;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, (byte) 0);
        builder.define(DATA_WIDTH, 1.0f);
        builder.define(DATA_HEIGHT, 1.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.scalable(entityData.get(DATA_WIDTH), entityData.get(DATA_HEIGHT));
    }

    public int role() {
        return entityData.get(DATA_ROLE);
    }

    public @Nullable Entity owner() {
        return owner;
    }

    /** Puts the part's middle at {@code centre}. */
    public void placeCentre(Vec3 centre) {
        double h = getBbHeight() / 2.0;
        setPos(centre.x, centre.y - h, centre.z);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        if (!level().isClientSide() && (owner == null || owner.isRemoved())) {
            discard();
            return;
        }
        super.tick();
        hurtTime = 0; // no red flash: the owner shows its hits
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || !(owner instanceof Owner o) || !owner.isAlive() || isInvulnerableTo(source)) {
            return false;
        }
        return o.hurtByPart(this, source, amount);
    }

    @Override
    public void takeImpact(double impact) {
        if (owner instanceof Owner o) {
            o.impactOnPart(this, impact);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
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
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
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
}
