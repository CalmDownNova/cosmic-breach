package com.cosmicbreach.relic.cantor;

import com.cosmicbreach.relic.Relics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A resonant note (GDD 7.3): where a charged shot of the Umbra Cantor landed, a note hangs for
 * {@value CantorRules#NOTE_LIFE} ticks, sounding its tone of D major pentatonic. It does nothing by itself; three of a
 * player's within 8 blocks of each other strike a chord ({@link Cantor}), which takes them as its corners. The server
 * ages it and lets it go; the client draws it from its tone and age. Never saved.
 */
public class ResonantNote extends Entity {
    private static final EntityDataAccessor<Integer> TONE = SynchedEntityData.defineId(ResonantNote.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ResonantNote.class, EntityDataSerializers.INT);

    private @Nullable UUID owner;
    private long placed;

    public ResonantNote(EntityType<? extends ResonantNote> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public ResonantNote(Level level, ServerPlayer owner, Vec3 at, int tone) {
        this(Relics.RESONANT_NOTE.get(), level);
        this.owner = owner.getUUID();
        this.placed = level.getGameTime();
        setPos(at.x, at.y, at.z);
        entityData.set(TONE, tone);
        entityData.set(OWNER, owner.getId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TONE, 0);
        builder.define(OWNER, -1);
    }

    /** Semitones from D5 (D major pentatonic). */
    public int tone() {
        return entityData.get(TONE);
    }

    /** The entity id of the player whose note it is (both sides). */
    public int ownerId() {
        return entityData.get(OWNER);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    /** Game time it was placed (server). */
    public long placed() {
        return placed;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && (owner == null || CantorRules.expired(placed, level().getGameTime()))) {
            discard();
        }
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
        return getBoundingBox().inflate(1.0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
