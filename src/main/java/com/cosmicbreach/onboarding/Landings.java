package com.cosmicbreach.onboarding;

import com.cosmicbreach.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The Landings in Aetheria (GDD 1.3), saved with the dimension ({@code cosmicbreach_landings.dat}): the
 * origin of each one's return ring (its Breach's north-west block). A new arrival builds one ({@link #build})
 * unless another lies within {@link #SHARED} blocks, and no mob spawns within {@link #QUIET} blocks of any.
 */
public final class Landings extends SavedData {
    public static final String NAME = "cosmicbreach_landings";
    /** A Landing this close is shared instead of building another. */
    public static final double SHARED = 32.0;
    /** Nothing spawns this close to a Landing. */
    public static final double QUIET = 24.0;
    private static final SavedData.Factory<Landings> FACTORY = new SavedData.Factory<>(Landings::new, Landings::load);

    private final List<BlockPos> centres = new ArrayList<>();

    public static Landings get(ServerLevel aetheria) {
        return aetheria.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The Landing centre nearest {@code pos} (horizontally) within {@code within} blocks. */
    public Optional<BlockPos> nearest(BlockPos pos, double within) {
        BlockPos best = null;
        double bestD = within * within;
        for (BlockPos c : centres) {
            double dx = c.getX() - pos.getX();
            double dz = c.getZ() - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                best = c;
                bestD = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** True if a mob spawning at (x, y, z) would be within {@link #QUIET} blocks of a Landing's middle. */
    public boolean quiet(double x, double y, double z) {
        for (BlockPos c : centres) {
            double dx = c.getX() + 1.0 - x;
            double dy = c.getY() + 0.5 - y;
            double dz = c.getZ() + 1.0 - z;
            if (dx * dx + dy * dy + dz * dz < QUIET * QUIET) {
                return true;
            }
        }
        return false;
    }

    public List<BlockPos> centres() {
        return List.copyOf(centres);
    }

    /**
     * Builds a Landing whose ring's origin is {@code origin} (on the island's surface level, {@link LandingLayout}):
     * the bricks and the lit ring replace the ground, three blocks above are cleared, gaps under the platform are
     * filled with Starfall Stone, and the ring opens. Remembered at once.
     */
    public void build(ServerLevel level, BlockPos origin) {
        BlockState brick = ModBlocks.STARFALL_STONE_BRICKS.get().defaultBlockState();
        BlockState frame = ModBlocks.BREACH_FRAME.get().defaultBlockState().setValue(BreachFrameBlock.ACTIVE, true);
        BlockState fill = ModBlocks.STARFALL_STONE.get().defaultBlockState();
        for (int dx = LandingLayout.MIN; dx <= LandingLayout.MAX; dx++) {
            for (int dz = LandingLayout.MIN; dz <= LandingLayout.MAX; dz++) {
                BlockPos p = origin.offset(dx, 0, dz);
                for (int up = 1; up <= 3; up++) {
                    if (!level.getBlockState(p.above(up)).isAir()) {
                        level.setBlock(p.above(up), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                for (int down = 1; down <= 3 && level.getBlockState(p.below(down)).canBeReplaced(); down++) {
                    level.setBlock(p.below(down), fill, 3);
                }
                switch (LandingLayout.partAt(dx, dz)) {
                    case BRICK -> level.setBlock(p, brick, 3);
                    case FRAME -> level.setBlock(p, frame, 3);
                    default -> {
                    }
                }
            }
        }
        BreachRings.open(level, origin);
        centres.add(origin.immutable());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos c : centres) {
            list.add(LongTag.valueOf(c.asLong()));
        }
        tag.put("landings", list);
        return tag;
    }

    private static Landings load(CompoundTag tag, HolderLookup.Provider registries) {
        return load(tag);
    }

    /** Reads the saved Landings (package-visible for tests). */
    static Landings load(CompoundTag tag) {
        Landings data = new Landings();
        for (Tag t : tag.getList("landings", Tag.TAG_LONG)) {
            data.centres.add(BlockPos.of(((LongTag) t).getAsLong()));
        }
        return data;
    }
}
