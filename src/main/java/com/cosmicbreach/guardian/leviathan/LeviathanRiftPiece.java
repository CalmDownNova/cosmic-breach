package com.cosmicbreach.guardian.leviathan;

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

/** The Leviathan Rift as one structure piece: each chunk builds its own slice of the {@link RiftLayout}. */
public class LeviathanRiftPiece extends StructurePiece {
    private final RiftLayout layout;

    public LeviathanRiftPiece(RiftLayout layout) {
        super(LeviathanRegistry.LEVIATHAN_RIFT_PIECE.get(), 0, box(layout));
        this.layout = layout;
    }

    public LeviathanRiftPiece(CompoundTag tag) {
        super(LeviathanRegistry.LEVIATHAN_RIFT_PIECE.get(), tag);
        this.layout = new RiftLayout(tag.getInt("CX"), tag.getInt("CY"), tag.getInt("CZ"), tag.getLong("Seed"));
    }

    private static BoundingBox box(RiftLayout layout) {
        int[] b = layout.bounds();
        return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public RiftLayout layout() {
        return layout;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", layout.x());
        tag.putInt("CY", layout.y());
        tag.putInt("CZ", layout.z());
        tag.putLong("Seed", layout.seed());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        RiftBuilder.build(level, layout, box);
    }
}
