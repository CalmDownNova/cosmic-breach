package com.cosmicbreach.gear.set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.gear.driftweave.Driftweave;
import com.cosmicbreach.gear.regalia.ChoirRegalia;
import java.util.Map;
import net.minecraft.world.item.ArmorItem;
import org.junit.jupiter.api.Test;

/** The Driftweave's and the Choir Regalia's tables (GDD 5.1) as the set framework holds them. */
class DriftweaveAndRegaliaSetTest {
    @Test
    void driftweaveArmorAndStats() {
        ArmorSet set = Driftweave.SET;
        assertEquals(3, set.piece(ArmorItem.Type.HELMET).orElseThrow().armor());
        assertEquals(7, set.piece(ArmorItem.Type.CHESTPLATE).orElseThrow().armor());
        assertEquals(5, set.piece(ArmorItem.Type.LEGGINGS).orElseThrow().armor());
        assertEquals(3, set.piece(ArmorItem.Type.BOOTS).orElseThrow().armor());
        assertEquals(18, set.totalArmor(), "18 (diamond: 20)");
        assertEquals(1.0f, set.toughness(), 1e-6, "4 for the set");
        assertEquals(2, set.tier());
        for (ArmorSet.Piece piece : set.pieces()) {
            assertEquals(Map.of(Stat.AGILITY, 2, Stat.POWER, 1), piece.stats(), piece.name());
        }
        assertEquals(Map.of(Stat.AGILITY, 8, Stat.POWER, 4), set.fullSetStats());
        assertEquals("driftweave_hood", set.piece(ArmorItem.Type.HELMET).orElseThrow().name());
        assertEquals("driftweave_coat", set.piece(ArmorItem.Type.CHESTPLATE).orElseThrow().name());
    }

    @Test
    void regaliaArmorAndStats() {
        ArmorSet set = ChoirRegalia.SET;
        assertEquals(3, set.piece(ArmorItem.Type.HELMET).orElseThrow().armor());
        assertEquals(8, set.piece(ArmorItem.Type.CHESTPLATE).orElseThrow().armor());
        assertEquals(6, set.piece(ArmorItem.Type.LEGGINGS).orElseThrow().armor());
        assertEquals(3, set.piece(ArmorItem.Type.BOOTS).orElseThrow().armor());
        assertEquals(20, set.totalArmor(), "20 (netherite: 20)");
        assertEquals(2.5f, set.toughness(), 1e-6, "10 for the set");
        assertEquals(3, set.tier());
        for (ArmorSet.Piece piece : set.pieces()) {
            assertEquals(Map.of(Stat.ARCANE, 2, Stat.RESILIENCE, 1), piece.stats(), piece.name());
        }
        assertEquals(Map.of(Stat.ARCANE, 8, Stat.RESILIENCE, 4), set.fullSetStats());
        assertEquals("choir_regalia_sabatons", set.piece(ArmorItem.Type.BOOTS).orElseThrow().name());
    }

    @Test
    void abilitiesNeedTheFullSetWithTheirCooldowns() {
        SetAbility drift = Driftweave.SET.ability().orElseThrow();
        assertEquals(560, drift.baseCooldown(), "28 s");
        assertEquals(ArmorSet.FULL_SET, drift.piecesRequired());
        SetAbility hymn = ChoirRegalia.SET.ability().orElseThrow();
        assertEquals(800, hymn.baseCooldown(), "40 s");
        assertEquals(ArmorSet.FULL_SET, hymn.piecesRequired());
        assertEquals(715, ArmorSets.cooldownTicks(800, 8), "the Hymn at the Regalia's own Arcane 8");
    }

    @Test
    void bothAreRegisteredByTheirIds() {
        assertSame(Driftweave.SET, ArmorSets.byId(Driftweave.SET.id()));
        assertSame(ChoirRegalia.SET, ArmorSets.byId(ChoirRegalia.SET.id()));
    }
}
