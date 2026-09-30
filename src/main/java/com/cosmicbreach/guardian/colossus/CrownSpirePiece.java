package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.GuardianRegistry;
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

/** The Crown Spire as one structure piece: each chunk builds its own slice of the {@link CrownSpireLayout}. */
public class CrownSpirePiece extends StructurePiece {
    private final CrownSpireLayout layout;

    public CrownSpirePiece(CrownSpireLayout layout) {
        super(GuardianRegistry.CROWN_SPIRE_PIECE.get(), 0, box(layout));
        this.layout = layout;
    }

    public CrownSpirePiece(CompoundTag tag) {
        super(GuardianRegistry.CROWN_SPIRE_PIECE.get(), tag);
        this.layout = new CrownSpireLayout(tag.getInt("CX"), tag.getInt("CZ"), tag.getInt("FloorY"), tag.getInt("BaseY"), tag.getInt("RootY"));
    }

    private static BoundingBox box(CrownSpireLayout layout) {
        int[] b = layout.bounds();
        return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public CrownSpireLayout layout() {
        return layout;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", layout.x());
        tag.putInt("CZ", layout.z());
        tag.putInt("FloorY", layout.floorY());
        tag.putInt("BaseY", layout.baseY());
        tag.putInt("RootY", layout.rootY());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        CrownSpireBuilder.build(level, layout, box);
    }
}
