package com.cosmicbreach.guardian.heliarch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What a Heliarch fight changed in the arena, saved with the Aetheria level ({@code data/cosmicbreach_heliarch.dat}):
 * every block the Collapse dropped (to put back) and every monolith block the Hollowing stood up (to take away), and who set
 * the Heart that began the fight. A fight that ends puts it all right at once; one cut short by the server stopping or
 * its chunks unloading is put right when the level next ticks. A fight that ends without a kill gives the Heart back to
 * whoever set it ({@link Heliarchs#returnHeart}), so a failed attempt never strands anyone.
 */
public final class HeliarchData extends SavedData {
    public static final String NAME = "cosmicbreach_heliarch";
    private static final SavedData.Factory<HeliarchData> FACTORY = new SavedData.Factory<>(HeliarchData::new, HeliarchData::load);

    private final Map<BlockPos, BlockState> removed = new LinkedHashMap<>();
    private final List<BlockPos> placed = new ArrayList<>();
    /** Who set the Heart for the fight under way (or cut short), until it ends in a kill or the Heart goes back. */
    private @org.jetbrains.annotations.Nullable java.util.UUID summoner;

    public static HeliarchData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The Collapse took the block at {@code pos} (keeps the first state seen there). */
    public void removed(BlockPos pos, BlockState state) {
        removed.putIfAbsent(pos.immutable(), state);
        setDirty();
    }

    /** A monolith block stands at {@code pos}. */
    public void placed(BlockPos pos) {
        placed.add(pos.immutable());
        setDirty();
    }

    public boolean dirty() {
        return !removed.isEmpty() || !placed.isEmpty();
    }

    /** A fight began: {@code player} set the Heart. */
    public void summoned(java.util.UUID player) {
        summoner = player;
        setDirty();
    }

    /** Who set the Heart for a fight not yet settled, or null. */
    public @org.jetbrains.annotations.Nullable java.util.UUID summoner() {
        return summoner;
    }

    /** The fight is settled: the Heart was spent on a kill, or went back. */
    public void clearSummoner() {
        if (summoner != null) {
            summoner = null;
            setDirty();
        }
    }

    public int removedCount() {
        return removed.size();
    }

    /** Takes the monoliths away and puts every dropped block back. Returns the blocks put back. */
    public int restore(ServerLevel level) {
        for (BlockPos p : placed) {
            if (level.getBlockState(p).is(HeliarchRegistry.MONOLITH.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        placed.clear();
        int n = 0;
        for (Map.Entry<BlockPos, BlockState> e : removed.entrySet()) {
            if (level.getBlockState(e.getKey()).isAir()) {
                level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
                n++;
            }
        }
        removed.clear();
        setDirty();
        return n;
    }

    /** Puts back only the blocks of {@code blocks} (a segment rising again). */
    public int restore(ServerLevel level, List<BlockPos> blocks) {
        int n = 0;
        for (BlockPos p : blocks) {
            BlockState s = removed.remove(p);
            if (s != null && level.getBlockState(p).isAir()) {
                level.setBlock(p, s, Block.UPDATE_CLIENTS);
                n++;
            }
        }
        setDirty();
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, BlockState> e : removed.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", e.getKey().getX());
            entry.putInt("y", e.getKey().getY());
            entry.putInt("z", e.getKey().getZ());
            entry.put("state", NbtUtils.writeBlockState(e.getValue()));
            list.add(entry);
        }
        tag.put("removed", list);
        ListTag monoliths = new ListTag();
        for (BlockPos p : placed) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", p.getX());
            entry.putInt("y", p.getY());
            entry.putInt("z", p.getZ());
            monoliths.add(entry);
        }
        tag.put("placed", monoliths);
        if (summoner != null) {
            tag.putUUID("summoner", summoner);
        }
        return tag;
    }

    private static HeliarchData load(CompoundTag tag, HolderLookup.Provider registries) {
        HeliarchData d = new HeliarchData();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag t : tag.getList("removed", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            d.removed.put(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")), NbtUtils.readBlockState(blocks, e.getCompound("state")));
        }
        for (Tag t : tag.getList("placed", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            d.placed.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
        }
        if (tag.hasUUID("summoner")) {
            d.summoner = tag.getUUID("summoner");
        }
        return d;
    }
}
