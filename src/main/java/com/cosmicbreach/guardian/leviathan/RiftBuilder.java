package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.guardian.GuardianAltarBlockEntity;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.structure.StructureRegistry;
import com.cosmicbreach.structure.trap.UpdraftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds a {@link RiftLayout} into the world, all of it or the part inside a box (a chunk, during world generation), and
 * sets up the bell's altar block entity (which registers the lair and raises the sleeping Leviathan).
 */
public final class RiftBuilder {
    private RiftBuilder() {
    }

    /** Builds the part of {@code layout} inside {@code clip}. Flags 2: no neighbour updates, clients told. Returns blocks set. */
    public static int build(WorldGenLevel level, RiftLayout layout, BoundingBox clip) {
        int[] b = layout.bounds();
        int x0 = Math.max(b[0], clip.minX());
        int x1 = Math.min(b[3], clip.maxX());
        int z0 = Math.max(b[2], clip.minZ());
        int z1 = Math.min(b[5], clip.maxZ());
        int y0 = Math.max(Math.max(b[1], clip.minY()), level.getMinBuildHeight());
        int y1 = Math.min(Math.min(b[4], clip.maxY()), level.getMaxBuildHeight() - 1);
        if (x0 > x1 || z0 > z1 || y0 > y1) {
            return 0;
        }
        RiftLayout.Slice slice = layout.slice(x0, z0, x1, z1);
        States s = new States();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int set = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    RiftLayout.Kind kind = layout.kind(slice, x, y, z);
                    if (kind == RiftLayout.Kind.KEEP) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (kind == RiftLayout.Kind.AIR) {
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, s.air, Block.UPDATE_CLIENTS);
                            set++;
                        }
                        continue;
                    }
                    level.setBlock(pos, s.of(kind, layout, x, z), Block.UPDATE_CLIENTS);
                    set++;
                    if (kind == RiftLayout.Kind.BELL && level.getBlockEntity(pos) instanceof GuardianAltarBlockEntity altar) {
                        altar.setup(layout.centreBlock());
                    }
                }
            }
        }
        return set;
    }

    private static final class States {
        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockState driftstone = ModBlocks.DRIFTSTONE.get().defaultBlockState();
        final BlockState bricks = ModBlocks.DRIFTSTONE_BRICKS.get().defaultBlockState();
        final BlockState rimeglass = ModBlocks.RIMEGLASS.get().defaultBlockState();
        final BlockState singing = LeviathanRegistry.SINGING_RIMEGLASS.get().defaultBlockState();
        final BlockState nebulite = ModBlocks.NEBULITE_ORE.get().defaultBlockState();
        final BlockState planks = ModBlocks.DRIFTWOOD_PLANKS.get().defaultBlockState();
        final BlockState slab = ModBlocks.DRIFTWOOD_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        final BlockState bell = LeviathanRegistry.RIFT_BELL.get().defaultBlockState();
        final BlockState updraft = StructureRegistry.UPDRAFT.get().defaultBlockState();

        BlockState of(RiftLayout.Kind kind, RiftLayout layout, int x, int z) {
            return switch (kind) {
                case KEEP, AIR -> air;
                case DRIFTSTONE -> driftstone;
                case BRICKS -> bricks;
                case RIMEGLASS -> rimeglass;
                case SINGING_RIMEGLASS -> singing;
                case NEBULITE -> nebulite;
                case PLANKS -> planks;
                case SLAB -> slab;
                case BELL -> bell;
                case UPDRAFT, UPDRAFT_TOP -> {
                    RiftLayout.Updraft u = null;
                    for (RiftLayout.Updraft each : layout.updrafts()) {
                        if (each.x() == x && each.z() == z) {
                            u = each;
                        }
                    }
                    Direction out = u == null ? Direction.NORTH : Direction.fromDelta(u.stepX(), 0, u.stepZ());
                    yield updraft.setValue(UpdraftBlock.TOP, kind == RiftLayout.Kind.UPDRAFT_TOP)
                            .setValue(UpdraftBlock.FACING, out == null ? Direction.NORTH : out);
                }
            };
        }
    }
}
