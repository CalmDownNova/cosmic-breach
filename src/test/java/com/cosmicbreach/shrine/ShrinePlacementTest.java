package com.cosmicbreach.shrine;

import java.util.Arrays;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placing a shrine in an existing world never deletes what a player built (1.1 design section 9). */
class ShrinePlacementTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    @Test
    void onlyAirAndThingsThatYieldToAnyBlockMayBeBuiltOver() {
        assertTrue(ShrinePlacer.clearToBuild(s(Blocks.AIR)));
        assertTrue(ShrinePlacer.clearToBuild(s(Blocks.SHORT_GRASS)), "grass yields to any block");
        assertTrue(ShrinePlacer.clearToBuild(s(Blocks.FERN)));
    }

    @Test
    void whatAPlayerPlacesIsNeverBuiltOverEvenWhenItHasNoCollision() {
        for (Block b : List.of(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.REDSTONE_TORCH, Blocks.LANTERN, Blocks.OAK_SIGN,
                Blocks.OAK_WALL_SIGN, Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.LEVER, Blocks.STONE_BUTTON, Blocks.REDSTONE_WIRE,
                Blocks.STONE_PRESSURE_PLATE, Blocks.WHITE_BANNER, Blocks.TRIPWIRE, Blocks.REPEATER, Blocks.LADDER, Blocks.CHEST,
                Blocks.OAK_PLANKS, Blocks.WATER, Blocks.LAVA, Blocks.BEDROCK)) {
            assertFalse(ShrinePlacer.clearToBuild(s(b)), b + " stays");
        }
    }

    @Test
    void theSearchTriesTheSpotItselfFirstAndThenNearestFirst() {
        int[] order = ShrinePlacer.heightOrder();
        assertEquals(0, order[0], "the intended ground comes before a roof or a canopy above it");
        for (int i = 1; i < order.length; i++) {
            assertTrue(Math.abs(order[i]) >= Math.abs(order[i - 1]), "never further before nearer: " + order[i - 1] + " then " + order[i]);
        }
        assertTrue(Arrays.stream(order).anyMatch(d -> d == 4) && Arrays.stream(order).anyMatch(d -> d == -6), "still reaches four up and six down");
    }
}
