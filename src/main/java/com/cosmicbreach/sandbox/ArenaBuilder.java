package com.cosmicbreach.sandbox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Places {@link ArenaLayout} in the world round a centre block (the floor block under the middle). */
final class ArenaBuilder {
    private ArenaBuilder() {
    }

    /**
     * Builds the arena, layer by layer from the floor up so every lantern and cluster has its block
     * under it. Clients are told; neighbours are not updated (nothing falls, drops or flows).
     */
    static void build(ServerLevel level, BlockPos centre) {
        int r = ArenaLayout.RADIUS + 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= ArenaLayout.CLEAR_HEIGHT; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockState state = stateFor(ArenaLayout.at(dx, dy, dz));
                    if (state != null) {
                        at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                        if (level.getBlockState(at) != state) {
                            level.setBlock(at, state, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    private static BlockState stateFor(ArenaLayout.Kind kind) {
        return switch (kind) {
            case KEEP -> null;
            case AIR -> Blocks.AIR.defaultBlockState();
            case FLOOR -> Blocks.POLISHED_TUFF.defaultBlockState();
            case PATTERN, RIM -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
            case LANTERN -> Blocks.LANTERN.defaultBlockState();
            case PILLAR -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case CLUSTER -> Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
        };
    }
}
