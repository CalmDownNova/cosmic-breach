package com.cosmicbreach.shrine;

import com.cosmicbreach.lift.AscentCurrent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Every shrine placed in Aetheria, by lair (guardian and altar), saved as {@code cosmicbreach_shrines.dat}: each is placed once. */
public final class ShrineData extends SavedData {
    private static final String NAME = "cosmicbreach_shrines";
    /** The Sanctum's shrine (it has no lair entry). */
    public static final String SANCTUM_KEY = "heliarch@sanctum";

    /** A placed shrine. */
    public record Placed(ShrineKind kind, BlockPos pos, Direction facing) {
    }

    private final Map<String, Placed> placed = new LinkedHashMap<>();
    /** The rising current beside each shrine that has one (1.1 design section 5), by the shrine's key: found once, then kept. */
    private final Map<String, AscentCurrent.Current> currents = new LinkedHashMap<>();
    private List<AscentCurrent.Current> currentList = List.of();

    public static ShrineData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShrineData::new, ShrineData::load), NAME);
    }

    /** The key of the lair whose altar is at {@code altar}. */
    public static String key(String guardian, BlockPos altar) {
        return guardian + "@" + altar.getX() + "," + altar.getY() + "," + altar.getZ();
    }

    public boolean has(String key) {
        return placed.containsKey(key);
    }

    public void put(String key, Placed p) {
        placed.put(key, p);
        setDirty();
    }

    public Map<String, Placed> all() {
        return Collections.unmodifiableMap(placed);
    }

    public Optional<AscentCurrent.Current> current(String key) {
        return Optional.ofNullable(currents.get(key));
    }

    public void putCurrent(String key, AscentCurrent.Current c) {
        currents.put(key, c);
        currentList = List.copyOf(currents.values());
        setDirty();
    }

    /** Every current, as a list kept ready (the lifts read it every tick for every player). */
    public List<AscentCurrent.Current> currentList() {
        return currentList;
    }

    /** Forgets every saved current, as a world saved before the currents existed has none (debug). */
    public void clearCurrents() {
        currents.clear();
        currentList = List.of();
        setDirty();
    }

    public Map<String, AscentCurrent.Current> currents() {
        return Collections.unmodifiableMap(currents);
    }

    static ShrineData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShrineData data = new ShrineData();
        ListTag list = tag.getList("Shrines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            ShrineKind kind = ShrineKind.ofGuardian(e.getString("Kind"));
            Optional<BlockPos> pos = NbtUtils.readBlockPos(e, "Pos");
            Direction facing = Direction.byName(e.getString("Facing"));
            if (kind != null && pos.isPresent() && facing != null) {
                data.placed.put(e.getString("Key"), new Placed(kind, pos.get(), facing));
            }
        }
        ListTag cl = tag.getList("Currents", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag e = cl.getCompound(i);
            data.currents.put(e.getString("Key"), new AscentCurrent.Current(e.getDouble("X"), e.getDouble("Z"), e.getDouble("Bottom"),
                    e.getDouble("Top"), e.getDouble("LandX"), e.getDouble("LandZ")));
        }
        data.currentList = List.copyOf(data.currents.values());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        placed.forEach((key, p) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Key", key);
            e.putString("Kind", p.kind().id());
            e.put("Pos", NbtUtils.writeBlockPos(p.pos()));
            e.putString("Facing", p.facing().getName());
            list.add(e);
        });
        tag.put("Shrines", list);
        ListTag cl = new ListTag();
        currents.forEach((key, c) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Key", key);
            e.putDouble("X", c.x());
            e.putDouble("Z", c.z());
            e.putDouble("Bottom", c.bottom());
            e.putDouble("Top", c.top());
            e.putDouble("LandX", c.landX());
            e.putDouble("LandZ", c.landZ());
            cl.add(e);
        });
        tag.put("Currents", cl);
        return tag;
    }
}
