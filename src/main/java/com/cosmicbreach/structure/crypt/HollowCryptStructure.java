package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.DeepSpans;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Hollow Crypt (GDD 6.1): a labyrinth sunk into the top of a Rift Abyss pillar, placed by random spread (spacing
 * 40 chunks, separation 14: {@code data/cosmicbreach/worldgen/structure_set/hollow_crypt.json}). It takes the
 * nearest pillar to the chunk's middle whose platform is broad enough for the gatehouse and high enough for three
 * levels below it, and reads the platform's surface from the terrain model ({@link AetheriaTerrain}); a Rift Scar
 * through the platform's middle rules a pillar out.
 */
public class HollowCryptStructure extends Structure {
    public static final MapCodec<HollowCryptStructure> CODEC = simpleCodec(HollowCryptStructure::new);
    public static final double MIN_PLATFORM = 11.0;
    public static final int MIN_TOP = 84;
    public static final int MAX_TOP = 134;

    public HollowCryptStructure(StructureSettings settings) {
        super(settings);
    }

    /** A place: the grid's north-west corner at level 0's floor (the piece's origin), and the gatehouse's column. */
    public record Site(BlockPos origin, BlockPos gate) {}

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        AetheriaTerrain terrain = AetheriaTerrain.of(context.randomState());
        long seed = context.random().nextLong();
        return site(terrain, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())
                .map(s -> new GenerationStub(s.gate(), pieces -> pieces.addPiece(new CryptPiece(s.origin(), seed))));
    }

    /** The crypt's place near column (x, z): the nearest suitable pillar within 64 blocks, or empty. */
    public static Optional<Site> site(AetheriaTerrain t, int x, int z) {
        int ci = (int) Math.floor(x / DeepSpans.CELL);
        int cj = (int) Math.floor(z / DeepSpans.CELL);
        DeepSpans.Pillar best = null;
        double bestDist = 64.0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                DeepSpans.Pillar p = t.deep.pillar(ci + di, cj + dj);
                if (!p.exists || p.platformR < MIN_PLATFORM || p.top < MIN_TOP || p.top > MAX_TOP) {
                    continue;
                }
                double d = Math.hypot(p.cx - x, p.cz - z);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        int gx = (int) Math.floor(best.cx);
        int gz = (int) Math.floor(best.cz);
        int surface = surface(t, gx, gz, (int) Math.ceil(best.top) + 3);
        if (surface < 0 || surface < best.top - 3) {
            return Optional.empty(); // a scar through the middle, or no platform where the model says
        }
        int f0 = surface - 9;
        BlockPos origin = new BlockPos(gx - CryptLayout.SIZE / 2, f0, gz - CryptLayout.SIZE / 2);
        return Optional.of(new Site(origin, new BlockPos(gx, surface, gz)));
    }

    /** The first air block over rock at column (x, z), searching down from {@code fromY} over 12 blocks, or -1. */
    static int surface(AetheriaTerrain t, int x, int z, int fromY) {
        AetheriaTerrain.Column col = new AetheriaTerrain.Column();
        t.sampleColumn(x, z, col);
        for (int y = fromY; y > fromY - 12; y--) {
            if (t.blocks(col, x, y, z) > 0) {
                return y + 1;
            }
        }
        return -1;
    }

    @Override
    public StructureType<?> type() {
        return CryptRegistry.HOLLOW_CRYPT.get();
    }
}
