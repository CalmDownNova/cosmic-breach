package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Places the Breach Sanctum once per world: its start chunk only ({@link SanctumLayout#startChunk}, read from the
 * terrain model, so it needs nothing but the generator's random state).
 */
public class SanctumPlacement extends StructurePlacement {
    public static final MapCodec<SanctumPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i).apply(i, SanctumPlacement::new));

    public SanctumPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt, Optional<ExclusionZone> zone) {
        super(locateOffset, method, frequency, salt, zone);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        int[] start = SanctumSite.of(AetheriaTerrain.of(state.randomState())).layout(0L).startChunk();
        return x == start[0] && z == start[1];
    }

    @Override
    public StructurePlacementType<?> type() {
        return SanctumRegistry.SANCTUM_PLACEMENT.get();
    }
}
