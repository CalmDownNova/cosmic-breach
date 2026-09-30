package com.cosmicbreach.guardian;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * What players are owed and have not been handed yet, saved with the Overworld ({@code data/cosmicbreach_owed_rewards.dat})
 * so a restart keeps it: guardian kills they took part in while offline or dead at the moment of the kill (by the
 * guardian's short name), and item stacks to give back (saved stacks). {@link GuardianPayouts} fills it and pays it out
 * when the player is next online and alive. The ledger itself is plain NBT, so tests need no game.
 */
public final class OwedRewards extends SavedData {
    public static final String NAME = "cosmicbreach_owed_rewards";
    private static final SavedData.Factory<OwedRewards> FACTORY = new SavedData.Factory<>(OwedRewards::new, OwedRewards::load);

    private final Map<UUID, List<String>> kills = new LinkedHashMap<>();
    private final Map<UUID, List<CompoundTag>> items = new LinkedHashMap<>();

    public static OwedRewards get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** {@code player} took part in a kill of {@code guardian} (its short name) and has not been paid for it. */
    public void oweKill(UUID player, String guardian) {
        kills.computeIfAbsent(player, id -> new ArrayList<>()).add(guardian);
        setDirty();
    }

    /** {@code player} is to get the saved stack {@code stack} back. */
    public void oweItem(UUID player, CompoundTag stack) {
        items.computeIfAbsent(player, id -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    /** True if anything is owed to {@code player}. */
    public boolean owes(UUID player) {
        return kills.containsKey(player) || items.containsKey(player);
    }

    /** The kills owed to {@code player}, oldest first, now no longer owed. */
    public List<String> takeKills(UUID player) {
        List<String> taken = kills.remove(player);
        if (taken == null) {
            return List.of();
        }
        setDirty();
        return taken;
    }

    /** The stacks owed to {@code player}, oldest first, now no longer owed. */
    public List<CompoundTag> takeItems(UUID player) {
        List<CompoundTag> taken = items.remove(player);
        if (taken == null) {
            return List.of();
        }
        setDirty();
        return taken;
    }

    /** How many kills and stacks are owed in all (checks). */
    public int size() {
        int n = 0;
        for (List<String> k : kills.values()) {
            n += k.size();
        }
        for (List<CompoundTag> s : items.values()) {
            n += s.size();
        }
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        java.util.Set<UUID> everyone = new java.util.LinkedHashSet<>(kills.keySet());
        everyone.addAll(items.keySet());
        for (UUID id : everyone) {
            CompoundTag entry = new CompoundTag();
            entry.put("player", NbtUtils.createUUID(id));
            ListTag k = new ListTag();
            for (String name : kills.getOrDefault(id, List.of())) {
                k.add(StringTag.valueOf(name));
            }
            entry.put("kills", k);
            ListTag s = new ListTag();
            s.addAll(items.getOrDefault(id, List.of()));
            entry.put("items", s);
            players.add(entry);
        }
        tag.put("players", players);
        return tag;
    }

    public static OwedRewards load(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        OwedRewards owed = new OwedRewards();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            if (!entry.contains("player", Tag.TAG_INT_ARRAY)) {
                continue;
            }
            UUID id = NbtUtils.loadUUID(entry.get("player"));
            for (Tag k : entry.getList("kills", Tag.TAG_STRING)) {
                owed.kills.computeIfAbsent(id, x -> new ArrayList<>()).add(k.getAsString());
            }
            for (Tag s : entry.getList("items", Tag.TAG_COMPOUND)) {
                owed.items.computeIfAbsent(id, x -> new ArrayList<>()).add((CompoundTag) s);
            }
        }
        return owed;
    }
}
