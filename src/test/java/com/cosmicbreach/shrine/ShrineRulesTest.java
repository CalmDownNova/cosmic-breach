package com.cosmicbreach.shrine;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shrines' rules (1.1 design section 9): when a save counts and when a death keeps everything. */
class ShrineRulesTest {
    private static final String AETHERIA = "cosmicbreach:aetheria";
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void aSaveCountsOnlyWhileItIsStillWhereThePlayerRespawns() {
        BlockPos shrine = new BlockPos(10, 340, -20);
        assertTrue(ShrineRules.saveActive(shrine, AETHERIA, shrine, AETHERIA));
        assertFalse(ShrineRules.saveActive(shrine, AETHERIA, new BlockPos(11, 340, -20), AETHERIA), "slept in a bed");
        assertFalse(ShrineRules.saveActive(shrine, AETHERIA, shrine, OVERWORLD), "same spot, another dimension");
        assertFalse(ShrineRules.saveActive(shrine, AETHERIA, null, OVERWORLD), "no spawn point at all");
        assertFalse(ShrineRules.saveActive(null, AETHERIA, shrine, AETHERIA), "never saved");
    }

    @Test
    void aDeathInAetheriaWhileSavedKeepsEverything() {
        assertTrue(ShrineRules.keeps(true, true, false, false));
        assertFalse(ShrineRules.keeps(false, true, false, false), "a death in the overworld drops as usual");
        assertFalse(ShrineRules.keeps(true, false, false, false), "not saved");
        assertFalse(ShrineRules.keeps(true, true, true, false), "keepInventory already keeps it");
        assertFalse(ShrineRules.keeps(true, true, false, true), "spectators drop nothing anyway");
    }

    @Test
    void eachLairsGuardianHasItsShrine() {
        assertEquals(ShrineKind.COLOSSUS, ShrineKind.ofGuardian("colossus"));
        assertEquals(ShrineKind.LEVIATHAN, ShrineKind.ofGuardian("leviathan"));
        assertEquals(ShrineKind.UNSUNG, ShrineKind.ofGuardian("unsung"));
        assertNull(ShrineKind.ofGuardian("nobody"));
        assertEquals(4, ShrineKind.values().length);
        assertEquals(4, ShrineKind.HELIARCH.boss());
    }
}
