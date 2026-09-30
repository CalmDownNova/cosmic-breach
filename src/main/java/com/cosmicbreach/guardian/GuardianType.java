package com.cosmicbreach.guardian;

import com.cosmicbreach.voice.EchoLine;
import com.cosmicbreach.world.Layer;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * One of the biome guardians (GDD 7.2): what its lair needs to know about it. Each guardian registers one
 * ({@link GuardianTypes}); its altar, the lair registry ({@link GuardianLairs}), the Echo and the commands work from
 * it.
 *
 * @param id          the guardian's id, also its lair's name in commands ({@code colossus})
 * @param attunes     the layer its first kill attunes to ({@link com.cosmicbreach.world.LayerAttunement}), or null for
 *                    none (the Unsung attunes to the Breach Sanctum, which is not a layer: its own kill grants it)
 * @param advancement the visible first-kill advancement ("Refracted"); having it makes later kills repeats
 * @param structure   its lair's structure, for locating lairs not generated yet
 * @param spawner     makes a dormant guardian at a lair
 * @param site        where the lair's arena centre is for a structure start chunk (exact locating), or null
 * @param echo        what the Starfall's voice says to each participant after the rewards of their first kill
 *                    ({@link com.cosmicbreach.voice.Echo}), or null for nothing
 */
public record GuardianType(ResourceLocation id, String name, @Nullable Layer attunes, ResourceLocation advancement,
                           ResourceKey<Structure> structure, Spawner spawner, @Nullable SiteFinder site,
                           @Nullable EchoLine echo) {
    /** A guardian the Starfall's voice says nothing about. */
    public GuardianType(ResourceLocation id, String name, Layer attunes, ResourceLocation advancement,
                        ResourceKey<Structure> structure, Spawner spawner, @Nullable SiteFinder site) {
        this(id, name, attunes, advancement, structure, spawner, site, null);
    }

    /** Makes a dormant guardian standing in the arena. */
    @FunctionalInterface
    public interface Spawner {
        @Nullable LairGuardian spawnDormant(ServerLevel level, BlockPos arenaCentre, BlockPos altar);
    }

    /** The arena centre of the lair that starts in {@code chunk}, if one does. */
    @FunctionalInterface
    public interface SiteFinder {
        Optional<BlockPos> arenaCentre(ServerLevel level, ChunkPos chunk);
    }

    private static final Map<String, GuardianType> BY_NAME = new LinkedHashMap<>();

    public static synchronized GuardianType register(GuardianType type) {
        if (BY_NAME.putIfAbsent(type.name(), type) != null) {
            throw new IllegalStateException("duplicate guardian " + type.name());
        }
        return type;
    }

    /** By its short name ({@code colossus}). */
    public static synchronized Optional<GuardianType> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    public static synchronized Collection<GuardianType> all() {
        return Collections.unmodifiableCollection(BY_NAME.values());
    }
}
