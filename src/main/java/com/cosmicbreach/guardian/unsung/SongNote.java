package com.cosmicbreach.guardian.unsung;

import java.util.Collections;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Homing Note (Unsung design v1): a pale-gold note that forms at the Alto's mouth for a beat, flies for two beats
 * bending slowly toward its target, and bursts on the beat it lands: {@value UnsungMoves#NOTE_DAMAGE} to a player
 * within {@value UnsungMoves#NOTE_BURST} of it. It homes too slowly to follow a sidestep, a dash's i-frames carry a
 * player through it, and any hit or projectile breaks it first. A living entity only so the combat engine's swings
 * find it; it has no health to speak of and is never saved.
 */
public class SongNote extends LivingEntity {
    public static final byte EVENT_BURST = 100;
    public static final byte EVENT_BREAK = 101;

    private static final EntityDataAccessor<Byte> DATA_VOICE = SynchedEntityData.defineId(SongNote.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_FORMED = SynchedEntityData.defineId(SongNote.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_MASK = SynchedEntityData.defineId(SongNote.class, EntityDataSerializers.INT);

    private @Nullable Unsung owner;
    private @Nullable UnsungMask mask;
    private @Nullable UUID target;
    private Vec3 heading = Vec3.ZERO;

    public SongNote(EntityType<? extends SongNote> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** A note forming now at {@code mask}'s mouth, for {@code target}. */
    static SongNote form(ServerLevel level, Unsung owner, UnsungMask mask, Voice voice, @Nullable ServerPlayer target, long now) {
        SongNote n = new SongNote(UnsungRegistry.SONG_NOTE.get(), level);
        n.owner = owner;
        n.mask = mask;
        n.target = target == null ? null : target.getUUID();
        n.entityData.set(DATA_VOICE, (byte) voice.ordinal());
        n.entityData.set(DATA_FORMED, now);
        n.entityData.set(DATA_MASK, mask.getId());
        Vec3 at = mask.mouth();
        n.moveTo(at.x, at.y - n.getBbHeight() / 2.0, at.z, 0f, 0f);
        level.addFreshEntity(n);
        return n;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VOICE, (byte) 0);
        builder.define(DATA_FORMED, 0L);
        builder.define(DATA_MASK, -1);
    }

    public Voice voice() {
        return Voice.values()[Math.floorMod(entityData.get(DATA_VOICE), 3)];
    }

    /** The tick it began to form. */
    public long formed() {
        return entityData.get(DATA_FORMED);
    }

    /** The tick it leaves the mask. */
    public long flies() {
        return formed() + UnsungMoves.NOTE_FORM;
    }

    /** The tick it bursts. */
    public long lands() {
        return flies() + UnsungMoves.NOTE_FLIGHT;
    }

    /** The mask it formed at (the client's copy too), or null. */
    public @Nullable UnsungMask mask() {
        if (mask != null) {
            return mask;
        }
        Entity e = level().getEntity(entityData.get(DATA_MASK));
        return e instanceof UnsungMask m ? m : null;
    }

    public Vec3 centre() {
        return position().add(0, getBbHeight() / 2.0, 0);
    }

    private void placeCentre(Vec3 c) {
        setPos(c.x, c.y - getBbHeight() / 2.0, c.z);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        hurtTime = 0;
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            return;
        }
        if (owner == null || owner.isRemoved() || !owner.fighting() || mask == null || mask.isRemoved()) {
            discard();
            return;
        }
        long now = level().getGameTime();
        if (now < flies()) {
            placeCentre(mask.mouth());
            return;
        }
        if (now >= lands()) {
            burst((ServerLevel) level());
            return;
        }
        fly(now);
    }

    /** Bends toward the target at most {@value UnsungMoves#NOTE_TURN_DEG} degrees a tick, paced to arrive on the beat. */
    private void fly(long now) {
        Vec3 here = centre();
        ServerPlayer p = target == null ? null : ((ServerLevel) level()).getServer().getPlayerList().getPlayer(target);
        Vec3 aim = p != null && p.isAlive() && p.level() == level() ? p.position().add(0, UnsungMoves.NOTE_HEIGHT, 0) : here.add(heading);
        Vec3 want = aim.subtract(here);
        double dist = want.length();
        if (dist < 1e-4) {
            return;
        }
        Vec3 dir = want.scale(1.0 / dist);
        if (heading.lengthSqr() < 1e-6) {
            heading = dir;
        } else {
            heading = turn(heading, dir, Math.toRadians(UnsungMoves.NOTE_TURN_DEG));
        }
        long left = Math.max(1, lands() - now);
        double speed = Math.max(0.25, Math.min(1.4, dist / left));
        placeCentre(here.add(heading.scale(speed)));
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

    /** On its beat: it bursts where it is, hurting a player it touches. */
    private void burst(ServerLevel level) {
        Vec3 c = centre();
        if (owner != null) {
            owner.landed(0, level.getGameTime());
            for (ServerPlayer p : owner.fightersOnFloor()) {
                AABB box = p.getBoundingBox();
                double nx = Math.max(box.minX, Math.min(c.x, box.maxX));
                double ny = Math.max(box.minY, Math.min(c.y, box.maxY));
                double nz = Math.max(box.minZ, Math.min(c.z, box.maxZ));
                if (c.distanceToSqr(nx, ny, nz) <= UnsungMoves.NOTE_BURST * UnsungMoves.NOTE_BURST) {
                    // the note itself strikes: dodgeable (i-frames) but not parryable (no living attacker)
                    DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(UnsungRegistry.NOTE_DAMAGE), this, null);
                    if (p.hurt(src, UnsungMoves.NOTE_DAMAGE)) {
                        owner.noteHit(p);
                    }
                }
            }
        }
        level.broadcastEntityEvent(this, EVENT_BURST);
        level.playSound(null, c.x, c.y, c.z, UnsungRegistry.NOTE_BURST.get(), SoundSource.MUSIC, 1.2f, 1.0f);
        discard();
    }

    /** Any hit or projectile breaks it (a projectile from outside the apse burns up first). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved() || owner == null) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            discard();
            return false;
        }
        boolean byPlayer = source.getEntity() instanceof net.minecraft.world.entity.player.Player;
        boolean byProjectile = source.getDirectEntity() instanceof Projectile;
        if (!byPlayer && !byProjectile) {
            return false;
        }
        if (owner.projectileBurnsUp(source)) {
            return false;
        }
        Vec3 c = centre();
        level().broadcastEntityEvent(this, EVENT_BREAK);
        level().playSound(null, c.x, c.y, c.z, UnsungRegistry.NOTE_BREAK.get(), SoundSource.MUSIC, 1.0f, 1.0f);
        owner.noteBroken();
        discard();
        return true;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if ((id == EVENT_BURST || id == EVENT_BREAK) && level().isClientSide()) {
            UnsungEffects.handler().noteEvent(this, id);
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
