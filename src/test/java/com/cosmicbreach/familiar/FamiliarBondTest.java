package com.cosmicbreach.familiar;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Everything about a familiar lives on its lantern: the bond survives a save and a load whole. */
class FamiliarBondTest {
    @Test
    void theBondSavesAndLoadsWhole() {
        FamiliarBond b = new FamiliarBond(FamiliarKind.PRISM_MOTH, new UUID(123, 456), FamiliarMode.PASSIVE, 0.4f, 9000L, 10200L);
        JsonElement saved = FamiliarBond.CODEC.encodeStart(JsonOps.INSTANCE, b).getOrThrow();
        assertTrue(saved.toString().contains("\"prism_moth\""));
        assertTrue(saved.toString().contains("\"passive\""));
        FamiliarBond loaded = FamiliarBond.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(b, loaded);
    }

    @Test
    void anOldBondWithOnlyItsKindAndIdLoadsWithDefaults() {
        JsonElement bare = FamiliarBond.CODEC.encodeStart(JsonOps.INSTANCE, FamiliarBond.hatch(FamiliarKind.EMBERWISP, new UUID(1, 1)))
                .getOrThrow();
        FamiliarBond loaded = FamiliarBond.CODEC.parse(JsonOps.INSTANCE, bare).getOrThrow();
        assertEquals(FamiliarMode.GUARD, loaded.mode());
        assertEquals(1.0f, loaded.health());
        assertEquals(0L, loaded.darkUntil());
    }

    @Test
    void theKindsKeepTheirNamesAndOrder() {
        assertEquals("emberwisp", FamiliarKind.EMBERWISP.getSerializedName());
        assertEquals(FamiliarKind.GRAVIKIN, FamiliarKind.byId("GRAVIKIN"));
        assertEquals(0, FamiliarKind.EMBERWISP.ordinal(), "the item looks' 0.1, 0.2, 0.3 follow this order");
        assertEquals(2, FamiliarKind.PRISM_MOTH.ordinal());
        assertEquals(1.5, FamiliarKind.GRAVIKIN.healthScale(), 1e-9);
    }
}
