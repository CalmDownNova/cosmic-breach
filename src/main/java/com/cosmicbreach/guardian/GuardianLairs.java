package com.cosmicbreach.guardian;

import com.mojang.datafixers.util.Pair;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The lair locate API (Prism Colossus design v1, "Finding it"): every lair's altar registers itself here when it
 * loads, and {@link #nearest} finds the nearest lair of a guardian, built or not yet generated, for the Codex's
 * mote of light (like an Eye of Ender) and for {@code /cosmicbreach debug locate}. Saved per level as
 * {@code cosmicbreach_lairs.dat}.
 */
public final class GuardianLairs extends SavedData {
    private static final String NAME = "cosmicbreach_lairs";
    /**
     * How many rings of placement regions the search for lairs not generated yet walks out (each ring is the structure
     * set's spacing further: 6 rings of 40 chunks reach about 3,800 blocks). Vanilla's /locate walks 100, which in a
     * world without the structure takes minutes on the server thread.
     */
    public static final int SEARCH_RINGS = 6;
    /** About how far that reaches, in blocks. */
    public static final int SEARCH_BLOCKS = SEARCH_RINGS * 40 * 16;

    /** A known lair: its guardian's name and its arena's centre. */
    public record Lair(String guardian, BlockPos arenaCentre) {
    }

    private final Map<BlockPos, Lair> lairs = new LinkedHashMap<>();

    public static GuardianLairs get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(GuardianLairs::new, GuardianLairs::load), NAME);
    }

    /** Records (or updates) the lair whose altar is at {@code altar}. */
    public void register(BlockPos altar, GuardianType type, BlockPos arenaCentre) {
        Lair lair = new Lair(type.name(), arenaCentre.immutable());
        if (!lair.equals(lairs.get(altar))) {
            lairs.put(altar.immutable(), lair);
            setDirty();
        }
    }

    public Map<BlockPos, Lair> all() {
        return java.util.Collections.unmodifiableMap(lairs);
    }

    /** The nearest registered lair of {@code type} to {@code pos} (arena centre), built lairs only. */
    public Optional<BlockPos> nearestKnown(GuardianType type, BlockPos pos) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Lair lair : lairs.values()) {
            if (!lair.guardian().equals(type.name())) {
                continue;
            }
            double d = horizontalDistSqr(lair.arenaCentre(), pos);
            if (d < bestD) {
                bestD = d;
                best = lair.arenaCentre();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The nearest lair of {@code type} to {@code pos}: its arena's centre. Looks at the lairs already built and at
     * where the lair's structure will generate within {@value #SEARCH_RINGS} rings of placement regions (about
     * {@value #SEARCH_BLOCKS} blocks), and returns the nearer.
     */
    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, GuardianType type) {
        Optional<BlockPos> known = get(level).nearestKnown(type, pos);
        Optional<BlockPos> planned = planned(level, pos, type);
        if (known.isEmpty()) {
            return planned;
        }
        if (planned.isEmpty()) {
            return known;
        }
        return horizontalDistSqr(known.get(), pos) <= horizontalDistSqr(planned.get(), pos) ? known : planned;
    }

    /**
     * The nearest place the lair's structure starts (generated or not), refined to its arena centre when possible. None in a
     * world made without structures.
     */
    public static Optional<BlockPos> planned(ServerLevel level, BlockPos pos, GuardianType type) {
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return Optional.empty();
        }
        Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(type.structure());
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holder.get()), pos, SEARCH_RINGS, false);
        if (found == null) {
            return Optional.empty();
        }
        BlockPos at = found.getFirst();
        if (type.site() != null) {
            Optional<BlockPos> exact = type.site().arenaCentre(level, new ChunkPos(at));
            if (exact.isPresent()) {
                return exact;
            }
        }
        return Optional.of(at);
    }

    private static double horizontalDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    // ------------------------------------------------------------------ saving

    private static GuardianLairs load(CompoundTag tag, HolderLookup.Provider registries) {
        GuardianLairs data = new GuardianLairs();
        ListTag list = tag.getList("Lairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Optional<BlockPos> altar = NbtUtils.readBlockPos(entry, "Altar");
            Optional<BlockPos> centre = NbtUtils.readBlockPos(entry, "Arena");
            if (altar.isPresent() && centre.isPresent()) {
                data.lairs.put(altar.get(), new Lair(entry.getString("Guardian"), centre.get()));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        lairs.forEach((altar, lair) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Altar", NbtUtils.writeBlockPos(altar));
            entry.put("Arena", NbtUtils.writeBlockPos(lair.arenaCentre()));
            entry.putString("Guardian", lair.guardian());
            list.add(entry);
        });
        tag.put("Lairs", list);
        return tag;
    }
}
