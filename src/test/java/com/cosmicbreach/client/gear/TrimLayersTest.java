package com.cosmicbreach.client.gear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The forge tier trim draws on the item's faces only, so its edge walls never z-fight the base layer's (1.1 design 8). */
class TrimLayersTest {
    @Test
    void aTierTrimAboveTheBaseLayerKeepsItsFacesOnly() {
        assertTrue(TrimLayers.facesOnly(1, "cosmicbreach", "item/meridian_trim"));
        assertTrue(TrimLayers.facesOnly(1, "cosmicbreach", "item/binary_edges_right_trim"));
        assertTrue(TrimLayers.facesOnly(1, "cosmicbreach", "item/driftweave_hood_trim"));
    }

    @Test
    void everythingElseKeepsItsEdges() {
        assertFalse(TrimLayers.facesOnly(0, "cosmicbreach", "item/meridian_trim"), "a base layer keeps its edges");
        assertFalse(TrimLayers.facesOnly(1, "cosmicbreach", "item/meridian_glow1"), "not a trim");
        assertFalse(TrimLayers.facesOnly(1, "minecraft", "item/spawn_egg_overlay"), "vanilla's layers");
        assertFalse(TrimLayers.facesOnly(1, "othermod", "item/sword_trim"), "another mod's layers");
    }
}
