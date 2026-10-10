package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.structure.sanctum.SanctumSite;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.BreachShape;
import com.cosmicbreach.world.gen.DeepSpans;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Silent Nave as a structure ({@code cosmicbreach:silent_nave}): rare, in the Rift Abyss, placed by a random spread
 * of 48 chunks with 18 of separation (its structure set). From the start chunk it looks for a basalt pillar whose
 * platform is high enough for the keel and low enough that the dome stays under the Shear band, and sets the nave's
 * door on the platform, the nave and the apse running out over the void (a keel of basalt under them) toward whichever
 * side has the most room from the neighbouring pillars and from the Breach. Reads the terrain model
 * ({@link AetheriaTerrain}), not the world. Everything it builds stays within 7 chunks of the start, so every chunk it
 * touches references it.
 */
public class SilentNaveStructure extends Structure {
    public static final MapCodec<SilentNaveStructure> CODEC = simpleCodec(SilentNaveStructure::new);
    public static final int MIN_FLOOR = 86;
    /** The dome's top must stay under the Shear band (Y 145). */
    public static final int MAX_FLOOR = 145 - NaveLayout.ROOF_MAX - 3;
    public static final double MIN_PLATFORM = 8.5;
    /** The door stands this far along the nave from the apse's centre: at the pillar's middle. */
    public static final double DOOR_BACK = NaveLayout.DOOR_WALL - 0.5;
    /** How far from the start chunk anything may be built (7 chunks). */
    public static final int REACH = 7 * 16;
    public static final double MIN_GAP = 3.0;
    private static final long TAG = 0x511E47L;

    public SilentNaveStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        AetheriaTerrain terrain = AetheriaTerrain.of(context.randomState());
        return site(terrain, context.seed(), context.chunkPos())
                .map(layout -> new GenerationStub(new BlockPos(layout.cx(), layout.floorY(), layout.cz()),
                        builder -> builder.addPiece(new NavePiece(layout))));
    }

    @Override
    public StructureType<?> type() {
        return UnsungRegistry.SILENT_NAVE_TYPE.get();
    }

    /** The nave a start in {@code chunk} would build, if a pillar near it has room for one. */
    public static Optional<NaveLayout> site(AetheriaTerrain t, long seed, ChunkPos chunk) {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int ci = (int) Math.floor(x / DeepSpans.CELL);
        int cj = (int) Math.floor(z / DeepSpans.CELL);
        List<DeepSpans.Pillar> near = new ArrayList<>();
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                DeepSpans.Pillar p = t.deep.pillar(ci + di, cj + dj);
                if (p.exists) {
                    near.add(p);
                }
            }
        }
        near.sort(Comparator.comparingDouble(p -> Math.hypot(p.cx - x, p.cz - z)));
        for (DeepSpans.Pillar p : near) {
            if (p.platformR < MIN_PLATFORM || Math.hypot(p.cx - x, p.cz - z) > 80.0 || SanctumSite.occupies(t, p)) {
                continue;
            }
            int gx = (int) Math.floor(p.cx);
            int gz = (int) Math.floor(p.cz);
            int floorY = surface(t, gx, gz, (int) Math.ceil(p.top) + 3);
            if (floorY < MIN_FLOOR || floorY > MAX_FLOOR || floorY < p.top - 3) {
                continue;
            }
            int best = -1;
            double bestGap = MIN_GAP;
            for (int f = 0; f < 4; f++) {
                int[] a = axis(f);
                NaveLayout l = new NaveLayout((int) Math.round(p.cx - a[0] * DOOR_BACK), (int) Math.round(p.cz - a[1] * DOOR_BACK), floorY, f, 0L);
                if (!withinReach(l, chunk)) {
                    continue;
                }
                double gap = clearance(t, l, p, ci, cj);
                if (gap > bestGap) {
                    bestGap = gap;
                    best = f;
                }
            }
            if (best >= 0) {
                int[] a = axis(best);
                long h = seed ^ (chunk.x * 341873128712L) ^ (chunk.z * 132897987541L) ^ TAG;
                return Optional.of(new NaveLayout((int) Math.round(p.cx - a[0] * DOOR_BACK), (int) Math.round(p.cz - a[1] * DOOR_BACK),
                        floorY, best, h));
            }
        }
        return Optional.empty();
    }

    static int[] axis(int facing) {
        return new NaveLayout(0, 0, 0, facing, 0L).axis();
    }

    private static boolean withinReach(NaveLayout l, ChunkPos chunk) {
        int[] b = l.bounds();
        int ox = chunk.getMinBlockX();
        int oz = chunk.getMinBlockZ();
        return b[0] >= ox - REACH && b[3] <= ox + 15 + REACH && b[2] >= oz - REACH && b[5] <= oz + 15 + REACH;
    }

    /** The smallest gap between the nave (its apse, and the middle of its nave) and any other pillar, or the Breach. */
    private static double clearance(AetheriaTerrain t, NaveLayout l, DeepSpans.Pillar home, int ci, int cj) {
        int[] a = l.axis();
        double apseX = l.cx();
        double apseZ = l.cz();
        double midX = l.cx() + a[0] * 30.0;
        double midZ = l.cz() + a[1] * 30.0;
        double gap = Math.min(Math.hypot(apseX, apseZ) - BreachShape.DEEP_RADIUS - NaveLayout.BUTTRESS_OUT - 10.0,
                Math.hypot(midX, midZ) - BreachShape.DEEP_RADIUS - NaveLayout.NAVE_WALL - 10.0);
        for (int di = -3; di <= 3; di++) {
            for (int dj = -3; dj <= 3; dj++) {
                DeepSpans.Pillar q = t.deep.pillar(ci + di, cj + dj);
                // every piece of rock of the cell (in the Shattered Field and the Hanging Wood its chunks, roots and
                // ledges too); the home pillar's own body is the nave's floor
                boolean self = q.ci == home.ci && q.cj == home.cj;
                gap = Math.min(gap, q.gapTo(apseX, apseZ, self) - NaveLayout.BUTTRESS_OUT);
                gap = Math.min(gap, q.gapTo(midX, midZ, self) - NaveLayout.PIER_OUT);
            }
        }
        return gap;
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

    /** The arena centre of the nave starting in {@code chunk} (for the lair locate API). */
    public static Optional<BlockPos> siteFor(ServerLevel level, ChunkPos chunk) {
        return site(AetheriaTerrain.of(level.getChunkSource().randomState()), level.getSeed(), chunk)
                .map(layout -> layout.arena().centreBlock());
    }
}
