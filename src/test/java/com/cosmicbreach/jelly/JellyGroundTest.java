package com.cosmicbreach.jelly;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The ground probe a jelly hovers by and spawns by: the air between a spot and the first thing solid under it. */
class JellyGroundTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A world of air with some blocks in it, from y 0. */
    private static final class Blocks3 implements BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 256;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }
    }

    @Test
    void countsTheAirBlocksBetweenTheSpotAndTheGround() {
        Blocks3 world = new Blocks3();
        world.blocks.put(new BlockPos(5, 60, 5), Blocks.STONE.defaultBlockState());
        assertEquals(0, DriftJelly.groundDepth(world, new BlockPos(5, 61, 5)), "standing on it");
        assertEquals(3, DriftJelly.groundDepth(world, new BlockPos(5, 64, 5)), "three blocks of air between");
        assertEquals(JellyRules.PROBE_DEPTH, DriftJelly.groundDepth(world, new BlockPos(5, 60 + JellyRules.PROBE_DEPTH + 1, 5)), "at the end of its reach");
    }

    @Test
    void noGroundWithinReachIsAVoid() {
        Blocks3 world = new Blocks3();
        world.blocks.put(new BlockPos(5, 20, 5), Blocks.STONE.defaultBlockState());
        assertEquals(JellyRules.UNKNOWN_GROUND, DriftJelly.groundDepth(world, new BlockPos(5, 60, 5)), "too far down to see");
        assertEquals(JellyRules.UNKNOWN_GROUND, DriftJelly.groundDepth(world, new BlockPos(6, 60, 5)), "nothing under it at all");
        assertEquals(JellyRules.UNKNOWN_GROUND, DriftJelly.groundDepth(world, new BlockPos(6, 3, 5)), "the bottom of the world is the end of the search");
    }

    @Test
    void whatYouCannotStandOnIsNotGround() {
        Blocks3 world = new Blocks3();
        world.blocks.put(new BlockPos(5, 60, 5), Blocks.TORCH.defaultBlockState());
        world.blocks.put(new BlockPos(5, 58, 5), Blocks.STONE.defaultBlockState());
        assertEquals(2, DriftJelly.groundDepth(world, new BlockPos(5, 61, 5)), "a torch has no collision: the stone under it is the ground");
    }
}
