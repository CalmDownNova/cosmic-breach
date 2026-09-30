package com.cosmicbreach.structure.crypt.trap;

import com.cosmicbreach.structure.crypt.CryptRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * How any structure lays the crypt's traps (GDD 6.4; the Breach Sanctum's Void Rift tiles, for one), a chunk at a
 * time like {@code LensRoom} and {@code ChoirRoom}: each writes only the part inside {@code chunk} and configures the
 * block entity that runs the trap when it is inside. The room round them is the caller's.
 *
 * <pre>{@code
 * TrapPlacer.riftCluster(level, chunk, middleOfTheFloorPatch);        // 3 by 3, into a Void Pocket you build below
 * TrapPlacer.pocket(level, chunk, pocketFloorMiddle, insideFrom, insideTo, sealBlocks);
 * TrapPlacer.gravityPlate(level, chunk, sigilMiddle, 6);              // the piston 6 blocks over the sigil's surface
 * TrapPlacer.chuteStrip(level, chunk, ceilingMiddle, Direction.Axis.X, ChuteRules.Pattern.WAVE, 0, 6);
 * }</pre>
 */
public final class TrapPlacer {
    private TrapPlacer() {
    }

    /** A Void Rift cluster: the nine floor tiles round {@code middle} (a floor block). Open air must lie under it to fall into. */
    public static void riftCluster(WorldGenLevel level, BoundingBox chunk, BlockPos middle) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, chunk, middle.offset(dx, 0, dz), CryptRegistry.VOID_RIFT_TILE.get().part(dx, dz));
            }
        }
    }

    /**
     * A Void Pocket's sigil at {@code floorMiddle} (a floor block), the room's inside from {@code from} to {@code to} and the
     * seal's blocks, all relative to it; the seal blocks themselves are placed here too.
     */
    public static void pocket(WorldGenLevel level, BoundingBox chunk, BlockPos floorMiddle, BlockPos from, BlockPos to, List<BlockPos> seal) {
        set(level, chunk, floorMiddle, CryptRegistry.VOID_POCKET.get().defaultBlockState());
        for (BlockPos rel : seal) {
            set(level, chunk, floorMiddle.offset(rel), CryptRegistry.POCKET_SEAL.get().defaultBlockState());
        }
        if (chunk.isInside(floorMiddle) && level.getBlockEntity(floorMiddle) instanceof VoidPocketBlockEntity be) {
            be.configure(from, to, seal);
        }
    }

    /** A Crushing Gravity Plate: the 5 by 5 sigil round {@code sigilMiddle} (floor blocks) and its piston {@code drop} + 1 blocks over it. */
    public static void gravityPlate(WorldGenLevel level, BoundingBox chunk, BlockPos sigilMiddle, int drop) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(level, chunk, sigilMiddle.offset(dx, 0, dz), CryptRegistry.GRAVITY_SIGIL.get().part(dx, dz));
            }
        }
        BlockPos piston = sigilMiddle.above(drop + 1);
        set(level, chunk, piston, CryptRegistry.GRAVITY_PISTON_BLOCK.get().defaultBlockState());
        if (chunk.isInside(piston) && level.getBlockEntity(piston) instanceof GravityPistonBlockEntity be) {
            be.configure(drop);
        }
    }

    /**
     * A Starfall Chute: three apertures in the ceiling along {@code axis} round {@code middle}, dropping on
     * {@code pattern}'s beat for chute number {@code index}, onto a floor {@code drop} blocks below their undersides.
     */
    public static void chuteStrip(WorldGenLevel level, BoundingBox chunk, BlockPos middle, Direction.Axis axis, ChuteRules.Pattern pattern,
            int index, int drop) {
        for (int k = -1; k <= 1; k++) {
            BlockPos p = axis == Direction.Axis.X ? middle.offset(k, 0, 0) : middle.offset(0, 0, k);
            set(level, chunk, p, CryptRegistry.STARFALL_CHUTE.get().defaultBlockState().setValue(StarfallChuteBlock.AXIS, axis)
                    .setValue(StarfallChuteBlock.MIDDLE, k == 0));
        }
        if (chunk.isInside(middle) && level.getBlockEntity(middle) instanceof StarfallChuteBlockEntity be) {
            be.configure(pattern.period, pattern.phase(index), drop);
        }
    }

    private static void set(WorldGenLevel level, BoundingBox chunk, BlockPos pos, BlockState state) {
        if (chunk.isInside(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
