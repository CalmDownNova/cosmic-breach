package com.cosmicbreach.structure.gen;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.DriftBelts;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Gyre Observatory (GDD 6.1): a tower grown through a Drift Belt asteroid, placed by random spread (spacing 36
 * chunks, separation 12: {@code data/cosmicbreach/worldgen/structure_set/gyre_observatory.json}). It takes the
 * nearest big round asteroid to the chunk's middle (from the terrain model, {@link AetheriaTerrain}) and needs room
 * for five floors above it and its root below, all inside the Drift's band.
 */
public class GyreObservatoryStructure extends Structure {
    public static final MapCodec<GyreObservatoryStructure> CODEC = simpleCodec(GyreObservatoryStructure::new);
    /** Highest the dome may reach: under the upper Shear band. */
    public static final int CEILING_Y = 296;
    /** Lowest the root may reach: over the lower Shear band. */
    public static final int FLOOR_Y = 164;

    public GyreObservatoryStructure(StructureSettings settings) {
        super(settings);
    }

    /** A place: the asteroid's centre, its radius, and the headroom over it. */
    public record Site(BlockPos centre, int radius, int headroom) {}

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        AetheriaTerrain terrain = AetheriaTerrain.of(context.randomState());
        long seed = context.random().nextLong();
        return site(terrain, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()).map(s -> new GenerationStub(s.centre(),
                pieces -> pieces.addPiece(new ObservatoryPiece(s.centre(), seed, s.radius(), s.headroom()))));
    }

    /** A place near column (x, z): the chunk's middle first, then four spots a belt cell out. */
    public static Optional<Site> site(AetheriaTerrain t, int x, int z) {
        int c = DriftBelts.CELL;
        int[][] offsets = {{0, 0}, {c, 0}, {-c, 0}, {0, c}, {0, -c}};
        for (int[] o : offsets) {
            Optional<Site> s = siteNear(t, x + o[0], z + o[1]);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }

    /** The nearest big round asteroid to (x, z), within 48 blocks, that a tower fits through. */
    static Optional<Site> siteNear(AetheriaTerrain t, int x, int z) {
        int ci = Math.floorDiv(x, DriftBelts.CELL);
        int cj = Math.floorDiv(z, DriftBelts.CELL);
        DriftBelts.Asteroid best = null;
        double bestDist = Double.MAX_VALUE;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                for (int k = DriftBelts.K_MIN; k <= DriftBelts.K_MAX; k++) {
                    DriftBelts.Asteroid a = t.drift.asteroid(ci + di, cj + dj, k);
                    if (a == null || a.shard || a.r < 9 || a.r > 16) {
                        continue;
                    }
                    double dist = Math.hypot(a.cx - x, a.cz - z);
                    if (dist < bestDist && dist < 48) {
                        bestDist = dist;
                        best = a;
                    }
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        int radius = (int) Math.round(best.r);
        int cy = (int) Math.round(best.cy);
        int headroom = CEILING_Y - cy;
        ObservatoryLayout probe = ObservatoryLayout.of(0L, radius, headroom);
        if (cy - probe.depth() < FLOOR_Y || cy + radius + 20 + 7 * 5 > CEILING_Y || Layer.at(cy) != Layer.DRIFT) {
            return Optional.empty();
        }
        return Optional.of(new Site(new BlockPos((int) Math.round(best.cx), cy, (int) Math.round(best.cz)), radius, headroom));
    }

    @Override
    public StructureType<?> type() {
        return StructureRegistry.GYRE_OBSERVATORY.get();
    }
}
