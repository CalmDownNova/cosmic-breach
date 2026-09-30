package com.cosmicbreach.relic.cantor;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.ServerCombatSounds;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.relic.Relics;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A chord (GDD 7.3): three of a player's notes within 8 blocks of each other, rung together. For
 * {@value CantorRules#CHORD_LIFE} ticks the triangle between its corners ({@link CantorRules#inside}) silences every
 * enemy in it that isn't a boss ({@link Silence}: no abilities while it lasts and a moment after) and pulses
 * {@value CantorRules#PULSE_DAMAGE} damage every {@value CantorRules#PULSE_TICKS} ticks to every enemy in it (the
 * Cantor's grades and tier scale it; no crits, no knockback: damage type {@code cosmicbreach:chord}). The corners are
 * synced so the client draws the triangle and its three notes; it sits at their middle. Never saved.
 */
public class ChordField extends Entity {
    private static final EntityDataAccessor<Vector3f> A = SynchedEntityData.defineId(ChordField.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> B = SynchedEntityData.defineId(ChordField.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> C = SynchedEntityData.defineId(ChordField.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ChordField.class, EntityDataSerializers.INT);
    /** How long a touch of the chord keeps an enemy silenced after it leaves (or the chord ends). */
    public static final int SILENCE_LINGER = 30;

    private @Nullable UUID owner;
    private double tierMultiplier = 1.0;
    private int age;

    public ChordField(EntityType<? extends ChordField> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public ChordField(Level level, ServerPlayer owner, Vec3 a, Vec3 b, Vec3 c, double tierMultiplier) {
        this(Relics.CHORD.get(), level);
        this.owner = owner.getUUID();
        this.tierMultiplier = tierMultiplier;
        Vec3 mid = a.add(b).add(c).scale(1.0 / 3.0);
        setPos(mid.x, mid.y, mid.z);
        entityData.set(A, a.toVector3f());
        entityData.set(B, b.toVector3f());
        entityData.set(C, c.toVector3f());
        entityData.set(OWNER, owner.getId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(A, new Vector3f());
        builder.define(B, new Vector3f());
        builder.define(C, new Vector3f());
        builder.define(OWNER, -1);
    }

    /** The three corners, in the order the notes were struck (both sides). */
    public Vec3[] corners() {
        return new Vec3[] {new Vec3(entityData.get(A)), new Vec3(entityData.get(B)), new Vec3(entityData.get(C))};
    }

    public int ownerId() {
        return entityData.get(OWNER);
    }

    /** Ticks since it formed (server). */
    public int age() {
        return age;
    }

    /** True if the point is in the chord's ground (both sides). */
    public boolean contains(Vec3 p) {
        Vec3[] c = corners();
        return CantorRules.inside(new double[] {c[0].x, c[1].x, c[2].x}, new double[] {c[0].y, c[1].y, c[2].y},
                new double[] {c[0].z, c[1].z, c[2].z}, p.x, p.y, p.z);
    }

    /** The box every point of the chord's ground lies in. */
    public AABB reach() {
        Vec3[] c = corners();
        double m = CantorRules.EDGE_MARGIN + 0.5;
        return new AABB(Math.min(c[0].x, Math.min(c[1].x, c[2].x)) - m,
                Math.min(c[0].y, Math.min(c[1].y, c[2].y)) - CantorRules.BAND_BELOW - 1.0,
                Math.min(c[0].z, Math.min(c[1].z, c[2].z)) - m,
                Math.max(c[0].x, Math.max(c[1].x, c[2].x)) + m,
                Math.max(c[0].y, Math.max(c[1].y, c[2].y)) + CantorRules.BAND_ABOVE,
                Math.max(c[0].z, Math.max(c[1].z, c[2].z)) + m);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        age++;
        ServerPlayer player = owner == null ? null : ((ServerLevel) level()).getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != level() || age > CantorRules.CHORD_LIFE) {
            discard();
            return;
        }
        boolean pulse = CantorRules.pulses(age);
        double damage = pulse ? pulseDamage(player) : 0.0;
        int struck = 0;
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, reach(), e -> HitResolver.isValidTarget(player, e));
        for (LivingEntity target : near) {
            if (!contains(target.position())) {
                continue;
            }
            if (!Silence.isBoss(target)) {
                Silence.silence(target, SILENCE_LINGER);
            }
            if (pulse && damage > 0) {
                target.invulnerableTime = 0; // the chord keeps its own time
                if (target.hurt(Cantor.chordDamage(level(), player, this), (float) damage)) {
                    struck++;
                    Cantor.markStruck(player, target);
                }
            }
        }
        if (pulse) {
            Cantor.chordMoment(player, this, Cantor.PULSE, struck);
            ServerCombatSounds.forEveryone(level(), position(), Relics.UC_PULSE, 0.8f, 1.0f);
            if (struck > 0) {
                PlayerCombat.of(player).machine().onDamaged(); // the fight goes on
            }
        }
        if (age >= CantorRules.CHORD_LIFE) {
            discard();
        }
    }

    /** One pulse's damage for {@code player}: 4 times the Cantor's grade scaling and the tier it was struck with. */
    private double pulseDamage(ServerPlayer player) {
        WeaponDef cantor = CombatData.server().weapon(Relics.UMBRA_CANTOR.getId());
        double scaling = cantor == null ? 1.0 : CombatMath.scaling(cantor.grades(), PlayerCombat.of(player).machine().stats());
        return CantorRules.pulseDamage(scaling, tierMultiplier);
    }

    /** The tier multiplier for a chord struck with {@code stack} (x1.15 a reforge; the Cantor unlocks at the top tier). */
    static double tierMultiplier(net.minecraft.world.item.ItemStack stack, int unlockTier) {
        return GearTier.multiplier(GearTier.stepsAbove(stack, unlockTier));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return reach();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
