package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Breach Sanctum as a structure ({@code cosmicbreach:breach_sanctum}): one per world, at the Breach's axis in the
 * Deep. Its placement ({@link SanctumPlacement}) names a single start chunk, the middle of the layout's bounds, so every
 * chunk it touches lies within 8 chunks of the start and references it. The layout comes from the terrain model
 * ({@link SanctumSite}) and the world's seed (the two puzzles).
 */
public class SanctumStructure extends Structure {
    public static final MapCodec<SanctumStructure> CODEC = simpleCodec(SanctumStructure::new);

    public SanctumStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        SanctumLayout layout = SanctumSite.of(AetheriaTerrain.of(context.randomState())).layout(context.seed());
        int[] start = layout.startChunk();
        if (context.chunkPos().x != start[0] || context.chunkPos().z != start[1]) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(0, SanctumLayout.ARENA_Y, 0), builder -> builder.addPiece(new SanctumPiece(layout))));
    }

    @Override
    public StructureType<?> type() {
        return SanctumRegistry.SANCTUM_TYPE.get();
    }
}
