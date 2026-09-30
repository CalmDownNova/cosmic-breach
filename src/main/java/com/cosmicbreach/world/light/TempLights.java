package com.cosmicbreach.world.light;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Light that lasts a while (GDD 7.1: "every weapon ability leaves light 12 behind for 40 ticks where it hits"; the
 * Pocket Star lights the ground to 15): vanilla's invisible light block, placed in an empty cell and taken away again
 * when its time is up. Real block light, so the ground shows it and a Hollow Stalker's light rules see it.
 *
 * <p>Only air is ever replaced, and only a light block this placed is ever removed (one a player put there, or a block
 * built over it since, is left alone). A light asked for where one of ours already stands keeps the brighter level
 * and the later end. The cells are saved with the level ({@code cosmicbreach_temp_lights.dat}), so a light left when
 * the game stopped or its chunk unloaded is cleared the next time its chunk is loaded and the level ticks.
 */
public final class TempLights extends SavedData {
    public static final String NAME = "cosmicbreach_temp_lights";
    private static final SavedData.Factory<TempLights> FACTORY = new SavedData.Factory<>(TempLights::new, TempLights::load);

    private record Entry(long until, int level) {
    }

    private final Map<Long, Entry> lights = new HashMap<>();

    public static void register(IEventBus game) {
        game.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                TempLights data = level.getDataStorage().get(FACTORY, NAME);
                if (data != null && !data.lights.isEmpty()) {
                    data.tick(level);
                }
            }
        });
    }

    public static TempLights of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /**
     * Light {@code brightness} (0 to 15) at {@code pos} for {@code ticks} ticks: in that cell if it is air (or one of
     * ours), else the cell above it. Returns the cell lit, or null if neither could be.
     */
    public static @Nullable BlockPos place(ServerLevel level, BlockPos pos, int brightness, int ticks) {
        TempLights data = of(level);
        for (BlockPos at : new BlockPos[] {pos, pos.above()}) {
            if (!level.isLoaded(at) || level.isOutsideBuildHeight(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            long key = at.asLong();
            Entry own = data.lights.get(key);
            boolean ours = own != null && state.is(Blocks.LIGHT);
            if (!ours && !state.isAir()) {
                continue;
            }
            int bright = Math.max(0, Math.min(LightBlock.MAX_LEVEL, brightness));
            long until = level.getGameTime() + Math.max(1, ticks);
            if (ours) {
                bright = Math.max(bright, own.level());
                until = Math.max(until, own.until());
            }
            if (!ours || state.getValue(LightBlock.LEVEL) != bright) {
                level.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, bright), 3);
            }
            data.lights.put(key, new Entry(until, bright));
            data.setDirty();
            return at;
        }
        return null;
    }

    /** Takes one of our lights away now (a Pocket Star gone early). Anything else there is left alone. */
    public static void remove(ServerLevel level, BlockPos pos) {
        TempLights data = level.getDataStorage().get(FACTORY, NAME);
        if (data != null && data.lights.remove(pos.asLong()) != null) {
            clear(level, pos);
            data.setDirty();
        }
    }

    /** True if one of our lights stands at {@code pos} (for tests). */
    public static boolean isOurs(ServerLevel level, BlockPos pos) {
        TempLights data = level.getDataStorage().get(FACTORY, NAME);
        return data != null && data.lights.containsKey(pos.asLong()) && level.getBlockState(pos).is(Blocks.LIGHT);
    }

    /** How many of our lights stand in this level now. */
    public static int count(ServerLevel level) {
        TempLights data = level.getDataStorage().get(FACTORY, NAME);
        return data == null ? 0 : data.lights.size();
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean changed = false;
        Iterator<Map.Entry<Long, Entry>> it = lights.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> e = it.next();
            if (e.getValue().until() > now) {
                continue;
            }
            BlockPos pos = BlockPos.of(e.getKey());
            if (!level.isLoaded(pos)) {
                continue; // cleared when its chunk is back
            }
            clear(level, pos);
            it.remove();
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> e : lights.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey());
            t.putLong("until", e.getValue().until());
            t.putInt("level", e.getValue().level());
            list.add(t);
        }
        tag.put("lights", list);
        return tag;
    }

    private static TempLights load(CompoundTag tag, HolderLookup.Provider registries) {
        TempLights data = new TempLights();
        for (Tag t : tag.getList("lights", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.lights.put(c.getLong("pos"), new Entry(c.getLong("until"), c.getInt("level")));
        }
        return data;
    }

    /** Every cell lit now, for tests and tools. */
    public List<BlockPos> cells() {
        List<BlockPos> out = new ArrayList<>();
        lights.keySet().forEach(k -> out.add(BlockPos.of(k)));
        return out;
    }
}
