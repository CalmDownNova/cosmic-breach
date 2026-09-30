package com.cosmicbreach.structure.choir;

import com.cosmicbreach.structure.crypt.CryptRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * How any structure builds a Choir Floor (the Hollow Crypt does; the Breach Sanctum's east wing can, at its hardest):
 *
 * <pre>{@code
 * // in a StructurePiece's postProcess, for the chunk being generated:
 * ChoirRoom.place(level, chunkBox, centre, ChoirDifficulty.SANCTUM, seed, 3, vaultPos);
 * // and elsewhere, once:
 * ChoirFloors.onSolved((level, conductor) -> { ... });
 * }</pre>
 *
 * {@code centre} is where the Conductor stands: on the floor, whose blocks are at {@code centre.y - 1}. This writes
 * the ring's floor (the resonance floor out to {@link #FLOOR} blocks from the axis, the eight pads drawn on it by the
 * client) and the two-block statue, and configures the Conductor with the seed, difficulty, layer tier (1 to 3, its
 * XP) and the vault it opens. The room around it is the caller's: floor and walls clear out to at least
 * {@link #CLEAR} blocks from the axis, headroom of 4 or more, and the vault block itself (a
 * {@code VaultBlockEntity}, sealed until the floor is solved). The song is drawn from {@code seed} when a player
 * first steps onto the ring.
 */
public final class ChoirRoom {
    /** The resonance floor's radius. */
    public static final int FLOOR = 7;
    /** How far the room must be clear of walls. */
    public static final int CLEAR = 8;

    private ChoirRoom() {
    }

    public static void place(WorldGenLevel level, BoundingBox chunk, BlockPos centre, ChoirDifficulty difficulty, long seed, int tier,
            @Nullable BlockPos vault) {
        for (int dx = -FLOOR; dx <= FLOOR; dx++) {
            for (int dz = -FLOOR; dz <= FLOOR; dz++) {
                if (dx * dx + dz * dz > (FLOOR + 0.5) * (FLOOR + 0.5)) {
                    continue;
                }
                BlockPos p = centre.offset(dx, -1, dz);
                if (chunk.isInside(p)) {
                    level.setBlock(p, CryptRegistry.RESONANCE_FLOOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (chunk.isInside(centre)) {
            level.setBlock(centre, CryptRegistry.CONDUCTOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(centre) instanceof ConductorBlockEntity be) {
                be.configure(seed, difficulty, tier, vault);
            }
        }
        if (chunk.isInside(centre.above())) {
            level.setBlock(centre.above(), CryptRegistry.CONDUCTOR_TOP.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
