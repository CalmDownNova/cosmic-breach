package com.cosmicbreach.entity.shardling;

import com.cosmicbreach.registry.ModEntities;
import com.cosmicbreach.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One of the three glowing shards a Shardling breaks into (GDD section 7.1). It flies apart from the
 * body, bounces once and settles, chimes faster and higher as its fuse runs, and {@value #FUSE}
 * ticks after the death it bursts: {@value #DAMAGE} damage to every player within {@value #RADIUS}
 * blocks. Its ground ring is the timer (the telegraph language's decal): it fills to the edge as the
 * burst comes. A blast, so a dash's i-frames dodge it and a parry can't. Never saved.
 */
public class ShardFragment extends Entity {
    public static final int FUSE = 30;
    public static final double RADIUS = 1.5;
    public static final float DAMAGE = 3.0f;
    /** The ticks of its life on which it chimes: closer together as the burst nears. */
    public static final int[] CHIMES = {3, 10, 16, 21, 24, 27, 29};
    private static final byte BURST = 71;
    private static final double GRAVITY = 0.04;

    public ShardFragment(EntityType<? extends ShardFragment> type, Level level) {
        super(type, level);
    }

    public ShardFragment(Level level, Vec3 at, Vec3 velocity) {
        this(ModEntities.SHARD_FRAGMENT.get(), level);
        setPos(at.x, at.y, at.z);
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement().add(0.0, -GRAVITY, 0.0);
        double falling = v.y;
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        v = getDeltaMovement();
        if (onGround()) {
            // one small bounce off the first landing, then it lies still
            double up = falling < -0.15 ? -falling * 0.3 : 0.0;
            setDeltaMovement(v.x * 0.5, up, v.z * 0.5);
        } else {
            setDeltaMovement(v.scale(0.98));
        }
        if (!level().isClientSide()) {
            for (int i = 0; i < CHIMES.length; i++) {
                if (tickCount == CHIMES[i]) {
                    level().playSound(null, getX(), getY(), getZ(), ModSounds.SHARDLING_SHARD_TICK.get(), SoundSource.HOSTILE,
                            0.55f, 1.0f + 0.08f * i + random.nextFloat() * 0.04f);
                }
            }
            if (tickCount >= FUSE) {
                burst();
            }
        }
    }

    /** How far the fuse has burned, 0 to 1 (both sides; the client counts from when it saw the shard). */
    public float fuse(float partialTick) {
        return Math.min(1.0f, (tickCount + partialTick) / FUSE);
    }

    private void burst() {
        Vec3 centre = position().add(0.0, getBbHeight() * 0.5, 0.0);
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(RADIUS + 1.0),
                p -> p.isAlive() && !p.isSpectator())) {
            if (player.getBoundingBox().distanceToSqr(centre) <= RADIUS * RADIUS) {
                player.hurt(damageSources().explosion(this, null), DAMAGE);
            }
        }
        level().playSound(null, centre.x, centre.y, centre.z, ModSounds.SHARDLING_SHARD_BURST.get(), SoundSource.HOSTILE,
                1.0f, 0.95f + random.nextFloat() * 0.1f);
        level().broadcastEntityEvent(this, BURST);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == BURST) {
            ShardlingEffects.handler().fragmentBurst(this);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
