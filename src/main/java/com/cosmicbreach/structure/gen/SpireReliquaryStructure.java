package com.cosmicbreach.structure.gen;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.ReachIslands;
import com.cosmicbreach.world.gen.SpireField;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Spire Reliquary (GDD 6.1): a hollow spire on a Shattered Spires island, placed by random spread (spacing
 * 24 chunks, separation 8: {@code data/cosmicbreach/worldgen/structure_set/spire_reliquary.json}). It stands
 * only well inside an island (the plaza clear of the rim), on ground that is level across its foot, and never on
 * a natural spire. The surface heightmap only sees the Reach's islands through {@link AetheriaTerrain}, so the site
 * is read from the terrain model, not the chunk.
 */
public class SpireReliquaryStructure extends Structure {
    public static final MapCodec<SpireReliquaryStructure> CODEC = simpleCodec(SpireReliquaryStructure::new);
    /** How far the ground may rise or fall across the spire's foot. */
    public static final double LEVEL = 3.0;

    public SpireReliquaryStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        AetheriaTerrain terrain = AetheriaTerrain.of(context.randomState());
        long seed = context.random().nextLong();
        return site(terrain, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())
                .map(pos -> new GenerationStub(pos, pieces -> pieces.addPiece(new ReliquaryPiece(pos, seed))));
    }

    /**
     * The entrance floor's centre for a Reliquary near column (x, z): the first spot, nearest first on a 16-block
     * grid out to {@link #SEARCH} blocks, where one can stand ({@link #standsAt}); empty if there is none.
     */
    public static Optional<BlockPos> site(AetheriaTerrain t, int x, int z) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -SEARCH; dx <= SEARCH; dx += 16) {
            for (int dz = -SEARCH; dz <= SEARCH; dz += 16) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        for (int[] o : offsets) {
            Optional<BlockPos> at = standsAt(t, x + o[0], z + o[1]);
            if (at.isPresent()) {
                return at;
            }
        }
        return Optional.empty();
    }

    /** How far from its chunk's middle a Reliquary may stand, in blocks. */
    public static final int SEARCH = 48;

    /**
     * The entrance floor's centre for a Reliquary exactly at column (x, z), or empty: well inside a Shattered Spires
     * island, level across its foot, and no natural spire (or fallen spire top) reaching into its plaza, which
     * would be cut off and left floating.
     */
    public static Optional<BlockPos> standsAt(AetheriaTerrain t, int x, int z) {
        ReachIslands.Column col = new ReachIslands.Column();
        t.reach.sample(x + 0.5, z + 0.5, col);
        if (!col.island || col.isle == null || col.isle.sunfield || col.edge < ReliquaryLayout.PLAZA + 4 || col.neckness > 0.05) {
            return Optional.empty();
        }
        double top = col.top;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            t.reach.sample(x + 0.5 + Math.cos(a) * ReliquaryLayout.OUTER, z + 0.5 + Math.sin(a) * ReliquaryLayout.OUTER, col);
            if (!col.island || Math.abs(col.top - top) > LEVEL) {
                return Optional.empty();
            }
        }
        List<SpireField.Spire> spires = new ArrayList<>();
        double clear = ReliquaryLayout.PLAZA + 1;
        int r = (int) Math.ceil(clear);
        t.spires.spiresTouching(x - r, z - r, x + r, z + r, spires);
        for (SpireField.Spire sp : spires) {
            double nx = Math.max(sp.minX, Math.min(sp.maxX, x));
            double nz = Math.max(sp.minZ, Math.min(sp.maxZ, z));
            if (Math.hypot(nx - x, nz - z) <= clear) {
                return Optional.empty();
            }
        }
        return Optional.of(new BlockPos(x, (int) Math.floor(top - 0.5) + 1, z));
    }

    @Override
    public StructureType<?> type() {
        return StructureRegistry.SPIRE_RELIQUARY.get();
    }
}
