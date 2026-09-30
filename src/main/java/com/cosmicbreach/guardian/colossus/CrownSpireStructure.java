package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.Hashing;
import com.cosmicbreach.world.gen.ReachIslands;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Crown Spire as a structure ({@code cosmicbreach:crown_spire}): rare, on Shattered Spires islands, placed by
 * a random spread of 40 chunks with 16 of separation (its structure set). From the start chunk it looks round
 * (up to 80 blocks) for the island column deepest inside a Shattered Spires island and stands the spire there,
 * its crown floor at Y 441 to 460 and its root 110 to 140 blocks below. Placement reads the terrain model
 * ({@link AetheriaTerrain#of}), since the surface heightmap only sees the Reach. Built in the last decoration
 * step, after the spire field and the plants, so nothing grows through it.
 */
public class CrownSpireStructure extends Structure {
    public static final MapCodec<CrownSpireStructure> CODEC = simpleCodec(CrownSpireStructure::new);
    /** The spire's foot must be at least this far inside its island's rim. */
    public static final double MIN_EDGE = 18.0;
    public static final int SEARCH_STEPS = 5;
    public static final int SEARCH_STEP = 16;
    public static final int MIN_FLOOR_Y = 441;
    public static final int FLOOR_SPREAD = 20;
    public static final int MIN_HEIGHT = 110;
    public static final int HEIGHT_SPREAD = 31;
    private static final int TAG = 0x5C0105;

    public CrownSpireStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return site(AetheriaTerrain.of(context.randomState()), context.seed(), context.chunkPos())
                .map(layout -> new GenerationStub(new BlockPos(layout.x(), layout.floorY(), layout.z()),
                        builder -> builder.addPiece(new CrownSpirePiece(layout))));
    }

    @Override
    public StructureType<?> type() {
        return GuardianRegistry.CROWN_SPIRE_TYPE.get();
    }

    /** The lair a start in {@code chunk} would build, if the island there has room for one. */
    public static Optional<CrownSpireLayout> site(AetheriaTerrain terrain, long seed, ChunkPos chunk) {
        return site(terrain, seed, chunk, 0, 0, -1.0);
    }

    /** {@link #site} keeping the spire's centre at least {@code avoidRadius} from {@code (avoidX, avoidZ)} (commands). */
    public static Optional<CrownSpireLayout> site(AetheriaTerrain terrain, long seed, ChunkPos chunk, int avoidX, int avoidZ,
                                                  double avoidRadius) {
        ReachIslands.Column col = new ReachIslands.Column();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        double bestEdge = MIN_EDGE;
        int bx = 0;
        int bz = 0;
        double top = 0;
        boolean found = false;
        for (int i = -SEARCH_STEPS; i <= SEARCH_STEPS; i++) {
            for (int j = -SEARCH_STEPS; j <= SEARCH_STEPS; j++) {
                int x = cx + i * SEARCH_STEP;
                int z = cz + j * SEARCH_STEP;
                terrain.reach.sample(x + 0.5, z + 0.5, col);
                if (!col.island || col.isle == null || col.isle.sunfield || col.neck
                        || (avoidRadius > 0 && Math.hypot(x - avoidX, z - avoidZ) < avoidRadius)) {
                    continue;
                }

                if (col.edge > bestEdge) {
                    bestEdge = col.edge;
                    bx = x;
                    bz = z;
                    top = col.top;
                    found = true;
                }
            }
        }
        if (!found) {
            return Optional.empty();
        }
        long h = Hashing.hash(seed, TAG, chunk.x, chunk.z, 0);
        int floorY = MIN_FLOOR_Y + (int) Math.floorMod(h, (long) FLOOR_SPREAD);
        int height = MIN_HEIGHT + (int) Math.floorMod(h >>> 16, (long) HEIGHT_SPREAD);
        int baseY = (int) Math.floor(top) + 1;
        int rootY = Math.max(Layer.REACH.rockMinY + 1, floorY - height);
        return Optional.of(new CrownSpireLayout(bx, bz, floorY, baseY, Math.min(rootY, baseY - 6)));
    }

    /** The arena centre of the lair starting in {@code chunk} (for the lair locate API). */
    public static Optional<BlockPos> siteFor(ServerLevel level, ChunkPos chunk) {
        return site(AetheriaTerrain.of(level.getChunkSource().randomState()), level.getSeed(), chunk)
                .map(layout -> new BlockPos(layout.x(), layout.floorY(), layout.z()));
    }
}
