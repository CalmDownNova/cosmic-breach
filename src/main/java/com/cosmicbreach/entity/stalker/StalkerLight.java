package com.cosmicbreach.entity.stalker;

import com.cosmicbreach.world.light.DeepLight;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * The light a Hollow Stalker judges a cell by, read from the level ({@link StalkerRules#effectiveLight}). Reads are
 * cached for the rest of the game tick (GDD 9.4: Stalkers read cached light rather than asking the light engine at
 * every step): a path search asks about the same cells again and again, and several Stalkers share a room; a new
 * tick starts afresh, so a torch or an ability's light counts from the tick it appears.
 */
public final class StalkerLight {
    private static final Map<Level, Cache> CACHES = new WeakHashMap<>();

    private StalkerLight() {
    }

    private static final class Cache {
        long tick = Long.MIN_VALUE;
        final Long2IntOpenHashMap raw = new Long2IntOpenHashMap();

        Cache() {
            raw.defaultReturnValue(-1);
        }
    }

    /** Block light and sky light of the cell, packed ({@code block | sky << 4}), from this tick's cache. */
    private static int raw(Level level, BlockPos pos) {
        Cache cache;
        synchronized (CACHES) {
            cache = CACHES.computeIfAbsent(level, l -> new Cache());
        }
        long now = level.getGameTime();
        if (cache.tick != now) {
            cache.tick = now;
            cache.raw.clear();
        }
        long key = pos.asLong();
        int v = cache.raw.get(key);
        if (v < 0) {
            v = level.getBrightness(LightLayer.BLOCK, pos) | level.getBrightness(LightLayer.SKY, pos) << 4;
            cache.raw.put(key, v);
        }
        return v;
    }

    /** Block light in the cell. */
    public static int block(Level level, BlockPos pos) {
        return raw(level, pos) & 15;
    }

    /** The brighter of block light and the sky light the time of day leaves (shaded in the Deep, {@link DeepLight}). */
    public static int effective(Level level, BlockPos pos) {
        int v = raw(level, pos);
        return StalkerRules.effectiveLight(v & 15, v >> 4 & 15, level.getSkyDarken(), DeepLight.skyScale(level, pos.getY()));
    }

    /** True if the cell is dark to a Stalker (not lit, not too bright to enter). */
    public static boolean dark(Level level, BlockPos pos) {
        int v = raw(level, pos);
        int block = v & 15;
        return StalkerRules.dark(StalkerRules.effectiveLight(block, v >> 4 & 15, level.getSkyDarken(), DeepLight.skyScale(level, pos.getY())), block);
    }
}
