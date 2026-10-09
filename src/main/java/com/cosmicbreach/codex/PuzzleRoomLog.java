package com.cosmicbreach.codex;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which puzzle-room vaults exist and who has opened each, so the Codex can skip the rooms a player has done without
 * loading their chunks. A vault reports itself when its chunk loads (which also brings in vaults opened before this
 * log existed) and again each time a player opens it. Saved per level as {@code cosmicbreach_puzzle_rooms.dat}.
 */
public final class PuzzleRoomLog extends SavedData {
    private static final String NAME = "cosmicbreach_puzzle_rooms";

    private record Entry(PuzzleRooms.Kind kind, Set<UUID> opened) {
    }

    private final Map<BlockPos, Entry> vaults = new LinkedHashMap<>();

    public static PuzzleRoomLog get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PuzzleRoomLog::new, PuzzleRoomLog::load), NAME);
    }

    /** The kind of puzzle room a vault block belongs to, or empty (the Sanctum's vaults, or not a vault). */
    public static Optional<PuzzleRooms.Kind> kindOf(BlockState state) {
        if (state.is(StructureRegistry.RELIQUARY_VAULT.get())) {
            return Optional.of(PuzzleRooms.Kind.RELIQUARY);
        }
        if (state.is(StructureRegistry.OBSERVATORY_VAULT.get())) {
            return Optional.of(PuzzleRooms.Kind.OBSERVATORY);
        }
        if (state.is(CryptRegistry.CRYPT_VAULT.get())) {
            return Optional.of(PuzzleRooms.Kind.CRYPT);
        }
        return Optional.empty();
    }

    /** A vault of {@code kind} at {@code pos} has loaded, opened by {@code opened} so far. */
    public void seen(BlockPos pos, PuzzleRooms.Kind kind, Collection<UUID> opened) {
        Entry e = vaults.get(pos);
        if (e == null || e.kind() != kind) {
            e = new Entry(kind, new HashSet<>());
            vaults.put(pos.immutable(), e);
            setDirty();
        }
        if (e.opened().addAll(opened)) {
            setDirty();
        }
    }

    /** {@code player} has opened the vault of {@code kind} at {@code pos}. */
    public void opened(BlockPos pos, PuzzleRooms.Kind kind, UUID player) {
        seen(pos, kind, List.of(player));
    }

    /** The vaults {@code player} has opened ({@code true}) or not ({@code false}). */
    public List<PuzzleRooms.Room> vaults(UUID player, boolean opened) {
        List<PuzzleRooms.Room> out = new ArrayList<>();
        vaults.forEach((pos, e) -> {
            if (e.opened().contains(player) == opened) {
                out.add(new PuzzleRooms.Room(e.kind(), pos));
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ saving

    /** Reads a saved log (for tests). */
    static PuzzleRoomLog fromTag(CompoundTag tag) {
        return load(tag, null);
    }

    private static PuzzleRoomLog load(CompoundTag tag, HolderLookup.Provider registries) {
        PuzzleRoomLog data = new PuzzleRoomLog();
        ListTag list = tag.getList("Vaults", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Optional<BlockPos> pos = NbtUtils.readBlockPos(entry, "Pos");
            Optional<PuzzleRooms.Kind> kind = PuzzleRooms.Kind.byKey(entry.getString("Kind"));
            if (pos.isEmpty() || kind.isEmpty()) {
                continue;
            }
            Set<UUID> opened = new HashSet<>();
            for (Tag t : entry.getList("Opened", Tag.TAG_INT_ARRAY)) {
                opened.add(NbtUtils.loadUUID(t));
            }
            data.vaults.put(pos.get(), new Entry(kind.get(), opened));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        vaults.forEach((pos, e) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Pos", NbtUtils.writeBlockPos(pos));
            entry.putString("Kind", e.kind().key());
            ListTag opened = new ListTag();
            e.opened().forEach(id -> opened.add(NbtUtils.createUUID(id)));
            entry.put("Opened", opened);
            list.add(entry);
        });
        tag.put("Vaults", list);
        return tag;
    }
}
