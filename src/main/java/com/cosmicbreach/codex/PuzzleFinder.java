package com.cosmicbreach.codex;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.crypt.HollowCryptStructure;
import com.cosmicbreach.structure.gen.GyreObservatoryStructure;
import com.cosmicbreach.structure.gen.SpireReliquaryStructure;
import com.cosmicbreach.world.AetheriaSpots;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Finds the nearest puzzle room for the Codex (the rules are {@link PuzzleRooms#choose}). Like
 * {@code /cosmicbreach debug locate}, it walks the structure set's placement regions outward and asks each candidate
 * chunk what worldgen would put there (the same site test on the terrain model, and the biome there), so nothing is
 * generated or loaded and the server thread never waits on a chunk. Each region's answer depends only on the world
 * seed, so it is cached for the life of the server: a second throw over the same ground costs next to nothing.
 */
public final class PuzzleFinder {
    /** How far out the search looks, in blocks (about 6 regions of Reliquaries, 4 of Observatories or Crypts). */
    public static final int SEARCH_BLOCKS = 2400;
    /** The cache is dropped whole past this many regions (each entry is a few dozen bytes). */
    private static final int CACHE_LIMIT = 20_000;

    private record RegionKey(PuzzleRooms.Kind kind, int rx, int rz) {
    }

    private static final Map<RegionKey, Optional<BlockPos>> SITES = new ConcurrentHashMap<>();

    private PuzzleFinder() {
    }

    /** The puzzle room the book points {@code player} at, or empty when none is left on their layer. */
    public static Optional<PuzzleRooms.Room> nearest(ServerLevel level, ServerPlayer player) {
        Layer here = Layer.at(player.getY());
        BlockPos from = player.blockPosition();
        PuzzleRoomLog log = PuzzleRoomLog.get(level);
        List<PuzzleRooms.Room> open = log.vaults(player.getUUID(), false);
        List<PuzzleRooms.Room> done = log.vaults(player.getUUID(), true);
        List<PuzzleRooms.Room> sites = new ArrayList<>();
        if (level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            for (PuzzleRooms.Kind kind : PuzzleRooms.Kind.values()) {
                if (kind.layer == here) {
                    sites.addAll(sitesNear(level, kind, here, from, done));
                }
            }
        }
        return PuzzleRooms.choose(here, from, sites, open, done);
    }

    /**
     * The sites of {@code kind} out from {@code from}, ring by ring of placement regions: it stops one ring after the
     * first ring with a room the player has not done (rings are only roughly sorted by distance), or at
     * {@link #SEARCH_BLOCKS}.
     */
    static List<PuzzleRooms.Room> sitesNear(ServerLevel level, PuzzleRooms.Kind kind, Layer here, BlockPos from, List<PuzzleRooms.Room> done) {
        List<PuzzleRooms.Room> out = new ArrayList<>();
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id(kind.structure));
        StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceKey.create(Registries.STRUCTURE_SET, key.location()));
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (set == null || structure == null || !(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
            return out;
        }
        if (SITES.size() > CACHE_LIMIT) {
            SITES.clear();
        }
        long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        AetheriaTerrain terrain = AetheriaSpots.terrain(level);
        var biomes = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        int rx = Math.floorDiv(from.getX() >> 4, spread.spacing());
        int rz = Math.floorDiv(from.getZ() >> 4, spread.spacing());
        int rings = rings(spread.spacing());
        int stopAfter = Integer.MAX_VALUE;
        for (int ring = 0; ring <= rings && ring <= stopAfter; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = rx + dx;
                    int z = rz + dz;
                    Optional<BlockPos> site = SITES.computeIfAbsent(new RegionKey(kind, x, z), k -> {
                        ChunkPos c = spread.getPotentialStructureChunk(seed, k.rx(), k.rz());
                        Optional<BlockPos> stub = site(kind, terrain, c.getMiddleBlockX(), c.getMiddleBlockZ());
                        return stub.filter(s -> structure.biomes().contains(biomes.getNoiseBiome(QuartPos.fromBlock(s.getX()),
                                QuartPos.fromBlock(s.getY()), QuartPos.fromBlock(s.getZ()), sampler)));
                    });
                    site.ifPresent(s -> out.add(new PuzzleRooms.Room(kind, s)));
                }
            }
            if (stopAfter == Integer.MAX_VALUE && PuzzleRooms.choose(here, from, out, List.of(), done).isPresent()) {
                stopAfter = ring + 1;
            }
        }
        return out;
    }

    /** How many rings of regions {@link #SEARCH_BLOCKS} takes at a set's spacing (in chunks). */
    static int rings(int spacing) {
        return Math.max(1, (int) Math.ceil(SEARCH_BLOCKS / (spacing * 16.0)));
    }

    /** Where worldgen stands a room of {@code kind} started in the chunk whose middle is (x, z), the same test it uses. */
    static Optional<BlockPos> site(PuzzleRooms.Kind kind, AetheriaTerrain terrain, int x, int z) {
        return switch (kind) {
            case RELIQUARY -> SpireReliquaryStructure.site(terrain, x, z);
            case OBSERVATORY -> GyreObservatoryStructure.site(terrain, x, z).map(GyreObservatoryStructure.Site::centre);
            case CRYPT -> HollowCryptStructure.site(terrain, x, z).map(HollowCryptStructure.Site::gate);
        };
    }

    /** Forgets every cached region (the server stopped; another world may load next). */
    public static void reset() {
        SITES.clear();
    }
}
