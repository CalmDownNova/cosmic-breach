package com.cosmicbreach.onboarding;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A Fallen Rift (GDD 1.3): a meteor crater with a half-buried Breach Ring and a chest, placed by random
 * spread (spacing 28 chunks, separation 10: {@code data/cosmicbreach/worldgen/structure_set/fallen_rift.json})
 * in the land biomes of {@code #cosmicbreach:has_structure/fallen_rift}. It only starts on dry ground that is
 * not too steep across the crater: water anywhere on its rim, or more than {@link #STEEP} blocks of rise, and
 * that region has none. The crater itself is {@link FallenRiftPiece}.
 */
public class FallenRiftStructure extends Structure {
    public static final MapCodec<FallenRiftStructure> CODEC = simpleCodec(FallenRiftStructure::new);
    public static final int STEEP = 7;

    public FallenRiftStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        long seed = context.random().nextLong();
        int radius = FallenRiftLayout.radius(seed);
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState random = context.randomState();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int sum = 0;
        for (int k = -1; k < 8; k++) {
            double a = k * Math.PI / 4.0;
            int sx = k < 0 ? x : x + (int) Math.round(Math.cos(a) * radius);
            int sz = k < 0 ? z : z + (int) Math.round(Math.sin(a) * radius);
            int floor = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, height, random);
            int surface = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, height, random);
            if (surface != floor || floor < generator.getSeaLevel() - 1) {
                return Optional.empty();
            }
            min = Math.min(min, floor);
            max = Math.max(max, floor);
            sum += floor;
        }
        if (max - min > STEEP) {
            return Optional.empty();
        }
        BlockPos centre = new BlockPos(x, Math.round(sum / 9.0f), z);
        return Optional.of(new GenerationStub(centre, pieces -> pieces.addPiece(new FallenRiftPiece(centre, radius, seed))));
    }

    @Override
    public StructureType<?> type() {
        return OnboardingRegistry.FALLEN_RIFT.get();
    }
}
