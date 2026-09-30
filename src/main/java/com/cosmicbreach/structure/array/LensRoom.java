package com.cosmicbreach.structure.array;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.lens.Lens;
import com.cosmicbreach.structure.lens.LensDifficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * How any structure builds a Lens Array room (the Reliquary and the Observatory do; the Sanctum's west wing can):
 *
 * <pre>{@code
 * // in a StructurePiece's postProcess, for the chunk being generated:
 * LensRoom.place(level, chunkBox, core, LensDifficulty.HARD_7, seed, 3, 8, vaultPos);
 * // and elsewhere, once:
 * LensArrays.onSolved((level, core) -> { if (core.getBlockPos().equals(westWingCore)) lightEclipseLock(level); });
 * }</pre>
 *
 * The room around it is the caller's: a floor under the pedestals, walkway to at least {@link #reach} blocks from
 * the core on each side plus one, headroom of {@code ceiling} blocks over the pedestal row, and a ceiling there
 * (the aperture replaces the block over the focus). Geometry: the core is a floor block under the middle pedestal;
 * pedestal (x, z) stands at core + (2x - 2c, 1, 2z - 2c) with c = (n - 1) / 2; the wall sockets one cell step (2
 * blocks) outside the grid. The puzzle itself (pieces, receptors, the Eye, the aperture) is made from {@code seed}
 * when a player first comes within 24 blocks.
 */
public final class LensRoom {
    private LensRoom() {
    }

    /** How far the grid and its sockets reach from the core, in blocks (6 for 5 by 5, 8 for 7 by 7). */
    public static int reach(LensDifficulty difficulty) {
        return LensCoreBlockEntity.SPACING * ((difficulty.size() - 1) / 2 + 1);
    }

    /**
     * Writes the part of the room inside {@code chunk}: the core (configured with the seed, difficulty, layer tier 1
     * to 3 for its XP, the ceiling height and the vault it opens), the empty pedestals and the bare sockets.
     */
    public static void place(WorldGenLevel level, BoundingBox chunk, BlockPos core, LensDifficulty difficulty, long seed, int tier,
            int ceiling, @Nullable BlockPos vault) {
        int n = difficulty.size();
        int c = (n - 1) / 2;
        if (chunk.isInside(core)) {
            level.setBlock(core, StructureRegistry.LENS_CORE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(core) instanceof LensCoreBlockEntity be) {
                be.configure(seed, difficulty, tier, ceiling, vault);
            }
        }
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                BlockPos p = core.offset(2 * (x - c), 1, 2 * (z - c));
                if (chunk.isInside(p)) {
                    level.setBlock(p, StructureRegistry.LENS_PEDESTAL.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        for (int port = 0; port < 4 * n; port++) {
            BlockPos p = core.offset(2 * (Lens.portX(n, port) - c), 1, 2 * (Lens.portZ(n, port) - c));
            if (chunk.isInside(p)) {
                level.setBlock(p, StructureRegistry.LENS_SOCKET.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
