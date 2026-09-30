package com.cosmicbreach.gear.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The Astral Forge's tier table and reforge rule (GDD 3.5). */
class ForgeTiersTest {
    @Test
    void eachTierHasItsRelic() {
        assertEquals(Optional.of(ForgeTiers.PRISM_HEART), ForgeTiers.upgradeItem(2));
        assertEquals(Optional.of(ForgeTiers.LEVIATHAN_PEARL), ForgeTiers.upgradeItem(3));
        assertEquals(Optional.of(ForgeTiers.SOLAR_HEART), ForgeTiers.upgradeItem(4));
        assertTrue(ForgeTiers.upgradeItem(1).isEmpty(), "tier I is the Forge itself");
        assertTrue(ForgeTiers.upgradeItem(5).isEmpty());
    }

    @Test
    void onlyTheNextRelicRaisesTheForge() {
        assertEquals(Optional.of(2), ForgeTiers.upgradeWith(1, ForgeTiers.PRISM_HEART));
        assertEquals(Optional.of(3), ForgeTiers.upgradeWith(2, ForgeTiers.LEVIATHAN_PEARL));
        assertEquals(Optional.of(4), ForgeTiers.upgradeWith(3, ForgeTiers.SOLAR_HEART));
        assertTrue(ForgeTiers.upgradeWith(1, ForgeTiers.LEVIATHAN_PEARL).isEmpty(), "no skipping tier II");
        assertTrue(ForgeTiers.upgradeWith(2, ForgeTiers.PRISM_HEART).isEmpty(), "a used relic does nothing more");
        assertTrue(ForgeTiers.upgradeWith(4, ForgeTiers.SOLAR_HEART).isEmpty(), "IV is the top");
        assertTrue(ForgeTiers.upgradeWith(1, ForgeTiers.NEBULITE_INGOT).isEmpty());
    }

    @Test
    void relicTiers() {
        assertEquals(2, ForgeTiers.relicTier(ForgeTiers.PRISM_HEART));
        assertEquals(3, ForgeTiers.relicTier(ForgeTiers.LEVIATHAN_PEARL));
        assertEquals(4, ForgeTiers.relicTier(ForgeTiers.SOLAR_HEART));
        assertEquals(0, ForgeTiers.relicTier(ForgeTiers.ECLIPSIUM_INGOT));
    }

    @Test
    void reforgeCostsTheNextTiersMetal() {
        assertEquals(new ForgeTiers.Cost(ForgeTiers.NEBULITE_INGOT, 4), ForgeTiers.reforgeCost(2).orElseThrow());
        assertEquals(new ForgeTiers.Cost(ForgeTiers.ECLIPSIUM_INGOT, 4), ForgeTiers.reforgeCost(3).orElseThrow());
        assertEquals(new ForgeTiers.Cost(ForgeTiers.SOLAR_HEART, 1), ForgeTiers.reforgeCost(4).orElseThrow());
        assertTrue(ForgeTiers.reforgeCost(1).isEmpty());
        assertTrue(ForgeTiers.reforgeCost(5).isEmpty());
    }

    @Test
    void reforgeNeedsAPieceTheForgesTierAndTheMetal() {
        assertEquals(ForgeTiers.Check.OK, ForgeTiers.check(1, 2, 4), "T1 to T2 at Forge II with 4 Nebulite");
        assertEquals(ForgeTiers.Check.OK, ForgeTiers.check(1, 4, 9), "a higher Forge is fine");
        assertEquals(ForgeTiers.Check.FORGE_TOO_LOW, ForgeTiers.check(1, 1, 64), "Forge I reforges up to T1 only");
        assertEquals(ForgeTiers.Check.FORGE_TOO_LOW, ForgeTiers.check(2, 2, 64), "T3 needs Forge III");
        assertEquals(ForgeTiers.Check.MISSING_METAL, ForgeTiers.check(1, 2, 3));
        assertEquals(ForgeTiers.Check.OK, ForgeTiers.check(3, 4, 1), "one Solar Heart to T4");
        assertEquals(ForgeTiers.Check.MAX_TIER, ForgeTiers.check(4, 4, 64));
        assertEquals(ForgeTiers.Check.NOT_REFORGEABLE, ForgeTiers.check(0, 4, 64));
    }

    @Test
    void theForgeBrightensWithItsTier() {
        assertEquals(6, ForgeTiers.light(1));
        assertEquals(12, ForgeTiers.light(4));
        assertTrue(ForgeTiers.light(2) < ForgeTiers.light(3));
    }
}
