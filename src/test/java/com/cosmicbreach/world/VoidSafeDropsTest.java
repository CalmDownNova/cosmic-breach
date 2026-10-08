package com.cosmicbreach.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Drops that would fall into the void go to the killer (1.1, Aetheria 1.1 Design section 3). */
class VoidSafeDropsTest {
    @Test
    void killsOverTheVoidPayTheKiller() {
        assertTrue(VoidSafeDrops.toKiller(true, true, false, -1), "a lane: the drops go to the killer");
        assertFalse(VoidSafeDrops.toKiller(true, true, false, 12), "a rock below: they fall as ever");
        assertFalse(VoidSafeDrops.toKiller(false, true, false, -1), "the Overworld keeps vanilla's rules");
        assertFalse(VoidSafeDrops.toKiller(true, false, false, -1), "no killer to give them to");
        assertFalse(VoidSafeDrops.toKiller(true, true, true, -1), "a player's own drops are never moved");
    }

    @Test
    void groundCountsOnlyWithinSixtyFourBlocksBelow() {
        assertEquals(64, VoidSafeDrops.VOID_DEPTH);
        assertEquals(0, VoidSafeDrops.firstSolid(d -> true, VoidSafeDrops.VOID_DEPTH), "standing in a block: ground at once");
        assertEquals(12, VoidSafeDrops.firstSolid(d -> d >= 12, VoidSafeDrops.VOID_DEPTH));
        assertEquals(64, VoidSafeDrops.firstSolid(d -> d == 64, VoidSafeDrops.VOID_DEPTH), "64 down still catches it");
        assertEquals(-1, VoidSafeDrops.firstSolid(d -> d == 65, VoidSafeDrops.VOID_DEPTH), "65 down is the void's");
        assertEquals(-1, VoidSafeDrops.firstSolid(d -> false, VoidSafeDrops.VOID_DEPTH));
        assertEquals(-1, VoidSafeDrops.firstSolid(d -> true, -1), "nothing below the world's bottom to look at");
        assertEquals(3, VoidSafeDrops.firstSolid(d -> d >= 3, 5), "the scan stops at the world's bottom, not past it");
        assertEquals(-1, VoidSafeDrops.firstSolid(d -> d >= 8, 5));
    }
}
