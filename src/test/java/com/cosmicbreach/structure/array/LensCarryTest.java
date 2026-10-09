package com.cosmicbreach.structure.array;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/** Where a lifted piece goes: the hand, a free hotbar slot, any free inventory slot, or nowhere (it stays on the grid). */
class LensCarryTest {
    private static final int HOTBAR = 9;
    private static final int SIZE = 36;

    private static IntPredicate emptyOnly(Integer... slots) {
        Set<Integer> free = Set.of(slots);
        return free::contains;
    }

    @Test
    void anEmptyHandTakesIt() {
        assertEquals(4, LensCoreBlockEntity.carrySlot(4, HOTBAR, SIZE, emptyOnly(1, 4, 20)));
    }

    @Test
    void aFullHandSendsItToTheFirstFreeHotbarSlot() {
        assertEquals(1, LensCoreBlockEntity.carrySlot(4, HOTBAR, SIZE, emptyOnly(1, 7, 20)));
    }

    @Test
    void aFullHotbarSendsItIntoTheInventory() {
        assertEquals(20, LensCoreBlockEntity.carrySlot(4, HOTBAR, SIZE, emptyOnly(20, 35)));
        assertEquals(35, LensCoreBlockEntity.carrySlot(0, HOTBAR, SIZE, emptyOnly(35)));
    }

    @Test
    void aFullInventoryLiftsNothing() {
        assertEquals(-1, LensCoreBlockEntity.carrySlot(4, HOTBAR, SIZE, i -> false));
    }

    @Test
    void everyFreeSlotIsFoundWhateverIsSelected() {
        for (int selected = 0; selected < HOTBAR; selected++) {
            for (int free = 0; free < SIZE; free++) {
                int f = free;
                assertEquals(free, LensCoreBlockEntity.carrySlot(selected, HOTBAR, SIZE, i -> i == f), "selected " + selected);
            }
        }
    }
}
