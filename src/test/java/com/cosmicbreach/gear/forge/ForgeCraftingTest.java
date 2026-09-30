package com.cosmicbreach.gear.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Crafting from carried stacks: counts add up across stacks, and one stack is never counted for two ingredients. */
class ForgeCraftingTest {
    private record Stack(String item, int count) {}

    private static Predicate<Stack> is(String item) {
        return s -> s.item().equals(item);
    }

    private static int[][] plan(List<Stack> stacks, List<Predicate<Stack>> accepts, int... amounts) {
        return ForgeCrafting.plan(stacks, Stack::count, accepts, amounts);
    }

    @Test
    void countsAddUpAcrossStacks() {
        List<Stack> stacks = List.of(new Stack("starsteel", 2), new Stack("stick", 1), new Stack("starsteel", 5));
        int[][] takes = plan(stacks, List.of(is("starsteel"), is("stick")), 3, 1);
        assertNotNull(takes);
        assertArrayEquals(new int[] {2, 0, 1}, takes[0], "the first stack, then one from the next");
        assertArrayEquals(new int[] {0, 1, 0}, takes[1]);
    }

    @Test
    void missingAnythingMeansNoCraft() {
        List<Stack> stacks = List.of(new Stack("starsteel", 2), new Stack("stick", 1));
        assertNull(plan(stacks, List.of(is("starsteel"), is("stick")), 3, 1), "3 ingots needed, 2 carried");
        assertNull(plan(stacks, List.of(is("quartz")), 1));
    }

    @Test
    void twoIngredientsThatAcceptTheSameItemDoNotShareIt() {
        Predicate<Stack> anyLog = s -> s.item().endsWith("log");
        List<Stack> stacks = List.of(new Stack("driftwood_log", 3));
        assertNull(plan(stacks, List.of(is("driftwood_log"), anyLog), 2, 2), "4 logs needed in all, 3 carried");
        assertNotNull(plan(stacks, List.of(is("driftwood_log"), anyLog), 2, 1));
    }
}
