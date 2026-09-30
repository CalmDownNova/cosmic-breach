package com.cosmicbreach.entity.shardling;

import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One of Shard Spit's three needles: flies nearly straight at {@value #SPEED} blocks a tick and deals
 * {@value #DAMAGE} to whatever it hits first (never another Shardling), then breaks. Not parryable:
 * the red telegraph means dodge. It breaks on its own after {@value #MAX_AGE} ticks, which also keeps
 * it from ever landing while its Shardling's next lunge is active (see {@code ShardlingMovesTest}).
 */
public class ShardNeedle extends ThrowableProjectile {
    public static final float DAMAGE = 3.0f;
    public static final float SPEED = 1.3f;
    public static final int MAX_AGE = 20;
    private static final byte IMPACT = 3;

    public ShardNeedle(EntityType<? extends ShardNeedle> type, Level level) {
        super(type, level);
    }

    public ShardNeedle(Level level, LivingEntity shooter, Vec3 from) {
        super(ModEntities.SHARD_NEEDLE.get(), from.x, from.y, from.z, level);
        setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.01;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive()) {
                ShardlingEffects.handler().needleTick(this);
            }
        } else if (tickCount >= MAX_AGE && !isRemoved()) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof Shardling) && !(target instanceof ShardNeedle);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide()) {
            LivingEntity shooter = getOwner() instanceof LivingEntity living ? living : null;
            result.getEntity().hurt(damageSources().mobProjectile(this, shooter), DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide() && !isRemoved()) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.SHARDLING_NEEDLE_HIT.get(), SoundSource.HOSTILE,
                    0.9f, 0.9f + random.nextFloat() * 0.2f);
            level().broadcastEntityEvent(this, IMPACT);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == IMPACT) {
            ShardlingEffects.handler().needleImpact(this);
        } else {
            super.handleEntityEvent(id);
        }
    }
}
