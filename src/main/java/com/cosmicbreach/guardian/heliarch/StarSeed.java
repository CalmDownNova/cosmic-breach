package com.cosmicbreach.guardian.heliarch;

import java.util.Collections;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Star Seed (GDD 7.3), fired through the Gravity Inversion: a slow homing orb of the Hollow's light, visible the whole
 * way. It turns slowly toward its mark, so a sidestep or a dash beats it; touching a player it bursts
 * ({@value HeliarchMoves#SEED_DAMAGE}); {@value HeliarchMoves#SEED_HEALTH} damage from a weapon or a projectile breaks
 * it first. A living entity only so the combat engine's swings find it; never saved.
 */
public class StarSeed extends LivingEntity {
    public static final byte EVENT_BURST = 100;
    public static final byte EVENT_BREAK = 101;
    /** Degrees a tick it may turn toward its mark. */
    public static final double TURN = 3.5;

    private @Nullable HollowHeliarch owner;
    private @Nullable UUID target;
    private Vec3 heading = Vec3.ZERO;
    private double health = HeliarchMoves.SEED_HEALTH;
    private int age;

    public StarSeed(EntityType<? extends StarSeed> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, HeliarchMoves.SEED_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** A seed leaving {@code from} toward {@code mark}. */
    static StarSeed fire(ServerLevel level, HollowHeliarch owner, Vec3 from, ServerPlayer mark) {
        StarSeed s = new StarSeed(HeliarchRegistry.STAR_SEED.get(), level);
        s.owner = owner;
        s.target = mark.getUUID();
        Vec3 d = mark.position().add(0, 1.0, 0).subtract(from);
        s.heading = d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
        // it leaves upward and outward first, then bends toward its mark: the arc shows where it came from
        s.heading = s.heading.add(0, 0.9, 0).normalize();
        s.moveTo(from.x, from.y - s.getBbHeight() / 2.0, from.z, 0f, 0f);
        level.addFreshEntity(s);
        level.playSound(null, from.x, from.y, from.z, HeliarchRegistry.SEED.get(), SoundSource.HOSTILE, 1.2f, 0.9f + level.random.nextFloat() * 0.2f);
        return s;
    }

    public Vec3 centre() {
        return position().add(0, getBbHeight() / 2.0, 0);
    }

    @Override
    public void tick() {
        super.tick();
        hurtTime = 0;
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            return;
        }
        if (owner == null || owner.isRemoved() || !owner.fighting()) {
            discard();
            return;
        }
        age++;
        if (age > HeliarchMoves.SEED_LIFE) {
            burst((ServerLevel) level(), null);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        ServerPlayer mark = target == null ? null : level.getServer().getPlayerList().getPlayer(target);
        Vec3 here = centre();
        if (mark != null && mark.isAlive() && mark.level() == level && age > 8) {
            Vec3 want = mark.position().add(0, 1.0, 0).subtract(here);
            if (want.lengthSqr() > 1e-6) {
                heading = turn(heading, want.normalize(), Math.toRadians(TURN));
            }
        }
        Vec3 next = here.add(heading.scale(HeliarchMoves.SEED_SPEED));
        setPos(next.x, next.y - getBbHeight() / 2.0, next.z);
        AABB box = getBoundingBox().inflate(0.15);
        for (ServerPlayer p : owner.fighters()) {
            if (p.getBoundingBox().intersects(box)) {
                burst(level, p);
                return;
            }
        }
    }

    /** {@code from} turned toward {@code to} by at most {@code max} radians (both unit vectors). */
    static Vec3 turn(Vec3 from, Vec3 to, double max) {
        double cos = Math.max(-1.0, Math.min(1.0, from.dot(to)));
        double angle = Math.acos(cos);
        if (angle <= max || angle < 1e-6) {
            return to;
        }
        double t = max / angle;
        Vec3 mixed = from.scale(1.0 - t).add(to.scale(t));
        return mixed.lengthSqr() < 1e-9 ? to : mixed.normalize();
    }

    /** It bursts on {@code on} (or into nothing when its time runs out). */
    private void burst(ServerLevel level, @Nullable ServerPlayer on) {
        if (on != null && owner != null) {
            DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(HeliarchRegistry.VOID), this, owner);
            owner.strikePlayer(on, src, HeliarchMoves.SEED_DAMAGE, 6.0, false);
            owner.seedLanded();
        }
        Vec3 c = centre();
        level.broadcastEntityEvent(this, EVENT_BURST);
        level.playSound(null, c.x, c.y, c.z, HeliarchRegistry.SEED_BURST.get(), SoundSource.HOSTILE, on == null ? 0.6f : 1.2f, 1.0f);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            discard();
            return false;
        }
        boolean byPlayer = source.getEntity() instanceof Player;
        boolean byProjectile = source.getDirectEntity() instanceof Projectile;
        if (!byPlayer && !byProjectile || owner == null || owner.burnsUp(source)) {
            return false;
        }
        health -= amount;
        if (health > 1e-6) {
            return true;
        }
        Vec3 c = centre();
        level().broadcastEntityEvent(this, EVENT_BREAK);
        level().playSound(null, c.x, c.y, c.z, HeliarchRegistry.SEED_BURST.get(), SoundSource.HOSTILE, 0.9f, 1.5f);
        owner.seedBroken();
        discard();
        return true;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_BURST || id == EVENT_BREAK) {
            HeliarchEffects.handler().seedEvent(this, id);
            return;
        }
        super.handleEntityEvent(id);
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
