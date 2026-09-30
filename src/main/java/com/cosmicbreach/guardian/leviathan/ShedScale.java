package com.cosmicbreach.guardian.leviathan;

import java.util.Collections;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A glowing scale peeled off the Leviathan's flank in phase 2 (Scale Shed, Thalassine Leviathan design v1): it drifts
 * toward its player slowly ({@value LeviathanMoves#SCALE_SPEED} blocks a tick), visible the whole way, and bursts on
 * the first player it touches for {@value LeviathanMoves#SCALE_DAMAGE}. Dodge it or break it (4 health: any hit).
 * A living thing so the combat engine's swings find it; never saved.
 */
public class ShedScale extends LivingEntity {
    public static final byte EVENT_POP = 60;
    /** How hard it steers toward its player each tick (it drifts, it doesn't chase). */
    private static final double STEER = 0.06;

    private @Nullable UUID owner;
    private @Nullable UUID target;
    private int life;

    public ShedScale(EntityType<? extends ShedScale> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, LeviathanMoves.SCALE_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Peels a scale off {@code leviathan} at {@code at}, drifting toward {@code target}. */
    public static ShedScale shed(ServerLevel level, ThalassineLeviathan leviathan, Vec3 at, ServerPlayer target) {
        ShedScale s = new ShedScale(LeviathanRegistry.SHED_SCALE.get(), level);
        s.owner = leviathan.getUUID();
        s.target = target.getUUID();
        s.moveTo(at.x, at.y - 0.45, at.z, level.random.nextFloat() * 360f, 0f);
        Vec3 away = at.subtract(leviathan.point(2));
        away = away.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : away.normalize();
        s.setDeltaMovement(away.scale(0.08).add(0, 0.03, 0));
        level.addFreshEntity(s);
        return s;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            LeviathanEffects.handler().scaleTick(this);
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (++life > LeviathanMoves.SCALE_LIFE) {
            pop(server);
            return;
        }
        Vec3 v = getDeltaMovement();
        ServerPlayer p = target == null ? null : server.getPlayerByUUID(target) instanceof ServerPlayer sp ? sp : null;
        if (p != null && p.isAlive() && !p.isSpectator()) {
            Vec3 want = p.getBoundingBox().getCenter().subtract(position().add(0, getBbHeight() / 2.0, 0));
            if (want.lengthSqr() > 1e-6) {
                want = want.normalize().scale(LeviathanMoves.SCALE_SPEED);
                v = v.add(want.subtract(v).scale(STEER));
            }
        } else {
            v = v.scale(0.95);
        }
        setDeltaMovement(v);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        for (Player hit : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.2),
                q -> q.isAlive() && !q.isSpectator() && !q.isCreative())) {
            Entity o = owner == null ? null : server.getEntity(owner);
            DamageSource source = o instanceof LivingEntity living ? damageSources().mobProjectile(this, living) : damageSources().magic();
            hit.hurt(source, (float) LeviathanMoves.SCALE_DAMAGE);
            pop(server);
            return;
        }
    }

    private void pop(ServerLevel server) {
        server.broadcastEntityEvent(this, EVENT_POP);
        server.playSound(null, getX(), getY(), getZ(), LeviathanRegistry.SCALE_POP.get(), SoundSource.HOSTILE, 1.4f,
                0.9f + random.nextFloat() * 0.25f);
        discard();
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide() && level() instanceof ServerLevel server && !isRemoved()) {
            pop(server);
            return;
        }
        super.die(source);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_POP && level().isClientSide()) {
            LeviathanEffects.handler().scalePopped(this);
            return;
        }
        super.handleEntityEvent(id);
    }

    /** How long it has drifted (server). */
    public int life() {
        return life;
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
