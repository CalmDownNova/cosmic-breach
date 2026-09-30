package com.cosmicbreach.structure.sanctum;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The Sanctum's memory in a world ({@code cosmicbreach_sanctum.dat} in Aetheria's data): who the Gate has admitted,
 * who has already been paid for finding the place, the Eclipse Locks and the Throne Stair ({@link LockRule}).
 */
public final class SanctumData extends SavedData {
    public static final String NAME = "cosmicbreach_sanctum";
    private static final SavedData.Factory<SanctumData> FACTORY = new SavedData.Factory<>(SanctumData::new, SanctumData::load);

    private final Set<UUID> admitted = new LinkedHashSet<>();
    private final Set<UUID> entered = new HashSet<>();
    private LockRule locks = LockRule.SEALED;
    /** When the seal dissolves (the game time), or -1. */
    private long stairOpensAt = -1;

    public static SanctumData get(ServerLevel aetheria) {
        return aetheria.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean admitted(UUID id) {
        return admitted.contains(id);
    }

    public Set<UUID> admitted() {
        return Set.copyOf(admitted);
    }

    /** Admits these players; returns those not admitted before. */
    public List<UUID> admit(Collection<UUID> ids) {
        List<UUID> fresh = ids.stream().filter(admitted::add).toList();
        if (!fresh.isEmpty()) {
            setDirty();
        }
        return fresh;
    }

    /** True the first time a player is marked as having entered. */
    public boolean markEntered(UUID id) {
        boolean fresh = entered.add(id);
        if (fresh) {
            setDirty();
        }
        return fresh;
    }

    public LockRule locks() {
        return locks;
    }

    public void setLocks(LockRule locks) {
        this.locks = locks;
        setDirty();
    }

    public long stairOpensAt() {
        return stairOpensAt;
    }

    public void setStairOpensAt(long at) {
        this.stairOpensAt = at;
        setDirty();
    }

    /** Forgets everything (debug reset). */
    public void clear() {
        admitted.clear();
        entered.clear();
        locks = LockRule.SEALED;
        stairOpensAt = -1;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag a = new ListTag();
        admitted.forEach(id -> a.add(NbtUtils.createUUID(id)));
        tag.put("admitted", a);
        ListTag e = new ListTag();
        entered.forEach(id -> e.add(NbtUtils.createUUID(id)));
        tag.put("entered", e);
        tag.putBoolean("west", locks.west());
        tag.putBoolean("east", locks.east());
        tag.putBoolean("stair", locks.stair());
        tag.putLong("stair_opens_at", stairOpensAt);
        return tag;
    }

    private static SanctumData load(CompoundTag tag, HolderLookup.Provider registries) {
        SanctumData d = new SanctumData();
        for (Tag t : tag.getList("admitted", Tag.TAG_INT_ARRAY)) {
            d.admitted.add(NbtUtils.loadUUID(t));
        }
        for (Tag t : tag.getList("entered", Tag.TAG_INT_ARRAY)) {
            d.entered.add(NbtUtils.loadUUID(t));
        }
        d.locks = new LockRule(tag.getBoolean("west"), tag.getBoolean("east"), tag.getBoolean("stair"));
        d.stairOpensAt = tag.contains("stair_opens_at") ? tag.getLong("stair_opens_at") : -1;
        return d;
    }
}
