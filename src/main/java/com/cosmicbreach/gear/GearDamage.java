package com.cosmicbreach.gear;

import com.cosmicbreach.combat.server.HitResolver;
import com.cosmicbreach.combat.server.PoiseTracker;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Blasts that armor sets set off (Heavy Landing's shockwave, the meteor): who they reach and how they hit.
 * They use their own damage types, so they never spend the Vanguard's Heat (that goes to the next real hit),
 * and they push outward from where they went off.
 */
public final class GearDamage {
    private GearDamage() {
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity attacker, Vec3 at) {
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
        return new DamageSource(holder, null, attacker, at);
    }

    /**
     * Everything {@code owner} may hit whose feet are within {@code radius} of {@code centre} across the ground
     * (measured to the nearest point of its box) and between {@code below} and {@code above} blocks of its height,
     * with no block between the blast and the target's middle.
     */
    public static List<LivingEntity> inRadius(ServerPlayer owner, ServerLevel level, Vec3 centre, double radius, double below,
                                              double above) {
        AABB area = new AABB(centre.x - radius, centre.y - below, centre.z - radius,
                centre.x + radius, centre.y + above, centre.z + radius).inflate(1.0);
        Vec3 eye = centre.add(0, 0.6, 0);
        return level.getEntitiesOfClass(LivingEntity.class, area, target -> {
            if (!HitResolver.isValidTarget(owner, target)) {
                return false;
            }
            AABB box = target.getBoundingBox();
            double dx = Math.max(0, Math.max(box.minX - centre.x, centre.x - box.maxX));
            double dz = Math.max(0, Math.max(box.minZ - centre.z, centre.z - box.maxZ));
            if (dx * dx + dz * dz > radius * radius || box.maxY < centre.y - below || box.minY > centre.y + above) {
                return false;
            }
            Vec3 middle = target.position().add(0, target.getBbHeight() * 0.5, 0);
            return level.clip(new ClipContext(eye, middle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner))
                    .getType() == HitResult.Type.MISS;
        });
    }

    /**
     * One blast hit: {@code amount} damage, a push away from {@code centre} of the engine's knockback for
     * {@code impact} (at most {@code maxPush}), and the Impact on the target's poise. True if it took damage.
     */
    public static boolean blast(LivingEntity target, DamageSource source, float amount, Vec3 centre, double impact, double maxPush) {
        Vec3 before = target.getDeltaMovement();
        target.invulnerableTime = 0;
        if (!target.hurt(source, amount)) {
            return false;
        }
        target.setDeltaMovement(before);
        Vec3 away = new Vec3(target.getX() - centre.x, 0, target.getZ() - centre.z);
        Vec3 direction = away.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : away.normalize();
        target.knockback(Math.min(maxPush, HitResolver.knockback(impact)), -direction.x, -direction.z);
        target.hurtMarked = true;
        if (target.isAlive()) {
            PoiseTracker.addImpact(target, impact);
        }
        return true;
    }
}
