package com.cosmicbreach.structure.gen;

import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.lens.LensDifficulty;
import com.cosmicbreach.structure.trap.GuardMarkerBlockEntity;
import com.cosmicbreach.structure.trap.KineticEmitterBlock;
import com.cosmicbreach.structure.trap.KineticEmitterBlockEntity;
import com.cosmicbreach.structure.trap.KineticThreadBlock;
import com.cosmicbreach.structure.trap.UpdraftBlock;
import com.cosmicbreach.structure.vault.VaultBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Writes a structure piece into one chunk at a time ({@link #chunk} is the part being generated), and the
 * working parts every structure shares: a Lens Array room's grid, a vault, guard posts, a Kinetic Tripwire, a
 * gravity lift. Block entities are configured as their blocks go in.
 */
final class Placer {
    private final WorldGenLevel level;
    private final BoundingBox chunk;

    Placer(WorldGenLevel level, BoundingBox chunk) {
        this.level = level;
        this.chunk = chunk;
    }

    boolean inside(BlockPos pos) {
        return chunk.isInside(pos);
    }

    void set(BlockPos pos, BlockState state) {
        if (chunk.isInside(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    BlockState get(BlockPos pos) {
        return level.getBlockState(pos);
    }

    /** A Lens Array's grid ({@link com.cosmicbreach.structure.array.LensRoom#place}). */
    void lensGrid(BlockPos core, LensDifficulty difficulty, long seed, int tier, int ceiling, @Nullable BlockPos vault) {
        com.cosmicbreach.structure.array.LensRoom.place(level, chunk, core, difficulty, seed, tier, ceiling, vault);
    }

    void vault(BlockPos pos, Block vault, Direction facing) {
        set(pos, vault.defaultBlockState().setValue(VaultBlock.FACING, facing));
    }

    void guard(BlockPos pos, int kind, int count) {
        set(pos, StructureRegistry.GUARD_MARKER.get().defaultBlockState());
        if (inside(pos) && level.getBlockEntity(pos) instanceof GuardMarkerBlockEntity be) {
            be.configure(kind, count);
        }
    }

    /**
     * A Kinetic Tripwire across a corridor: emitters at {@code a} and {@code b} (on one axis, in the walls), the
     * thread in the air between them. {@code a}'s emitter draws the thread.
     */
    void tripwire(BlockPos a, BlockPos b) {
        Direction ab = Direction.getNearest(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
        int length = a.distManhattan(b) - 1;
        set(a, StructureRegistry.KINETIC_EMITTER.get().defaultBlockState().setValue(KineticEmitterBlock.FACING, ab));
        set(b, StructureRegistry.KINETIC_EMITTER.get().defaultBlockState().setValue(KineticEmitterBlock.FACING, ab.getOpposite()));
        if (inside(a) && level.getBlockEntity(a) instanceof KineticEmitterBlockEntity be) {
            be.setLength(length);
        }
        for (int i = 1; i <= length; i++) {
            set(a.relative(ab, i), StructureRegistry.KINETIC_THREAD.get().defaultBlockState()
                    .setValue(KineticThreadBlock.AXIS, ab.getAxis()));
        }
    }

    /** A lift's updraft from {@code bottom} up {@code height} blocks; its top two nudge riders {@code exit}. */
    void updraft(BlockPos bottom, int height, Direction exit) {
        for (int i = 0; i < height; i++) {
            set(bottom.above(i), StructureRegistry.UPDRAFT.get().defaultBlockState()
                    .setValue(UpdraftBlock.TOP, i >= height - 2).setValue(UpdraftBlock.FACING, exit));
        }
    }
}
