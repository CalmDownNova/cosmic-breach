package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.structure.crypt.CryptNaveLink;
import com.cosmicbreach.structure.crypt.CryptPiece;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The Silent Nave as one structure piece: each chunk builds its own slice of the {@link NaveLayout}. */
public class NavePiece extends StructurePiece {
    private final NaveLayout layout;

    public NavePiece(NaveLayout layout) {
        super(UnsungRegistry.SILENT_NAVE_PIECE.get(), 0, box(layout));
        this.layout = layout;
    }

    public NavePiece(CompoundTag tag) {
        super(UnsungRegistry.SILENT_NAVE_PIECE.get(), tag);
        this.layout = new NaveLayout(tag.getInt("CX"), tag.getInt("CZ"), tag.getInt("FloorY"), tag.getInt("Facing"), tag.getLong("Seed"));
    }

    static BoundingBox box(NaveLayout layout) {
        int[] b = layout.bounds();
        return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public NaveLayout layout() {
        return layout;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", layout.cx());
        tag.putInt("CZ", layout.cz());
        tag.putInt("FloorY", layout.floorY());
        tag.putInt("Facing", layout.facing());
        tag.putLong("Seed", layout.seed());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        NaveBuilder.build(level, layout, box, crypts(structureManager, chunkPos));
    }

    /**
     * The crypts sharing this nave's platform that reach into the chunk being built. A crypt's start is referenced by
     * every chunk its box meets, so the chunk's own references find it; no other chunk is read, because asking for one
     * outside the generating region during world generation crashes the server (1.2.1).
     */
    private List<CryptNaveLink> crypts(StructureManager structureManager, ChunkPos chunkPos) {
        List<CryptNaveLink> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (StructureStart start : structureManager.startsForStructure(chunkPos, s -> s.type() == CryptRegistry.HOLLOW_CRYPT.get())) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof CryptPiece crypt && CryptNaveLink.overlaps(layout, crypt.origin())
                        && seen.add(crypt.origin().asLong())) {
                    found.add(CryptNaveLink.of(layout, crypt.origin(), crypt.plan()));
                }
            }
        }
        return found;
    }
}
