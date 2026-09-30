package com.cosmicbreach.structure.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The Breach Sanctum as one structure piece: each chunk builds its own slice of the {@link SanctumLayout}. */
public class SanctumPiece extends StructurePiece {
    private final SanctumLayout layout;

    public SanctumPiece(SanctumLayout layout) {
        super(SanctumRegistry.SANCTUM_PIECE.get(), 0, box(layout));
        this.layout = layout;
    }

    public SanctumPiece(CompoundTag tag) {
        super(SanctumRegistry.SANCTUM_PIECE.get(), tag);
        this.layout = new SanctumLayout(tag.getInt("Side"), tag.getInt("EX"), tag.getInt("EY"), tag.getInt("EZ"), tag.getLong("Seed"));
    }

    static BoundingBox box(SanctumLayout layout) {
        int[] b = layout.bounds();
        return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public SanctumLayout layout() {
        return layout;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        int[] e = layout.end();
        tag.putInt("Side", layout.side());
        tag.putInt("EX", e[0]);
        tag.putInt("EY", e[1]);
        tag.putInt("EZ", e[2]);
        tag.putLong("Seed", layout.seed());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        SanctumBuilder.build(level, layout, box);
    }
}
