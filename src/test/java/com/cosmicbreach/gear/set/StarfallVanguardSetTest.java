package com.cosmicbreach.gear.set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.gear.vanguard.StarfallVanguard;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ArmorItem;
import org.junit.jupiter.api.Test;

/** The Starfall Vanguard's table (GDD 5.1) as the set framework holds it. */
class StarfallVanguardSetTest {
    private static final ArmorSet SET = StarfallVanguard.SET;

    @Test
    void armorPerPieceAndInAll() {
        assertEquals(2, SET.piece(ArmorItem.Type.HELMET).orElseThrow().armor());
        assertEquals(6, SET.piece(ArmorItem.Type.CHESTPLATE).orElseThrow().armor());
        assertEquals(5, SET.piece(ArmorItem.Type.LEGGINGS).orElseThrow().armor());
        assertEquals(2, SET.piece(ArmorItem.Type.BOOTS).orElseThrow().armor());
        assertEquals(15, SET.totalArmor(), "iron's 15");
        assertEquals(1.0f, SET.toughness(), 1e-6);
        assertEquals(0.05f, SET.knockbackResistance(), 1e-6);
        assertEquals(1, SET.tier());
    }

    @Test
    void eachPieceGivesTwoPowerAndOneResilience() {
        for (ArmorSet.Piece piece : SET.pieces()) {
            assertEquals(Map.of(Stat.POWER, 2, Stat.RESILIENCE, 1), piece.stats(), piece.name());
        }
        assertEquals(Map.of(Stat.POWER, 8, Stat.RESILIENCE, 4), SET.fullSetStats());
    }

    @Test
    void meteorCallNeedsTheFullSetAndHasAThirtySecondCooldown() {
        SetAbility ability = SET.ability().orElseThrow();
        assertEquals(600, ability.baseCooldown());
        assertEquals(ArmorSet.FULL_SET, ability.piecesRequired());
    }

    @Test
    void theSetIsRegisteredByItsId() {
        assertSame(SET, ArmorSets.byId(SET.id()));
        assertTrue(ArmorSets.all().contains(SET));
        assertThrows(IllegalStateException.class, () -> ArmorSets.register(SET));
    }

    @Test
    void aSetHasFourPieces() {
        assertThrows(IllegalArgumentException.class, () -> new ArmorSet(SET.id(), 1, List.of(), 0f, 0f, 15, 0,
                SetBehavior.NONE, null));
    }
}
