package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cosmicbreach.voice.boss.WeaponCategory.Delivery;
import com.cosmicbreach.voice.boss.WeaponCategory.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The gear check (each fighter's score, the party's verdict) and what a hit's weapon counts as. */
class GearAndHitsTest {
    static final GearCheck.Reference COLOSSUS = new GearCheck.Reference(1, 15, 4, 8, 10, 14, 16);

    @Test
    void theReferenceLoadoutScoresEvenAndStrongerGearScoresOver() {
        assertEquals(0, GearCheck.score(new GearCheck.Loadout(1, 15, 4, 8, 12), COLOSSUS));
        assertEquals(GearCheck.Verdict.EVEN, GearCheck.verdict(0));
        GearCheck.Loadout strong = new GearCheck.Loadout(3, 20, 8, 24, 30);
        assertEquals(GearCheck.Verdict.OVER, GearCheck.verdict(GearCheck.score(strong, COLOSSUS)));
        GearCheck.Loadout naked = new GearCheck.Loadout(0, 0, 0, 0, 2);
        assertEquals(GearCheck.Verdict.UNDER, GearCheck.verdict(GearCheck.score(naked, COLOSSUS)));
    }

    @Test
    void thePartyIsOverWithHalfOverAndNoneUnderAndUnderWithHalfUnder() {
        GearCheck.Verdict o = GearCheck.Verdict.OVER;
        GearCheck.Verdict e = GearCheck.Verdict.EVEN;
        GearCheck.Verdict u = GearCheck.Verdict.UNDER;
        assertEquals(o, GearCheck.party(List.of(o)));
        assertEquals(o, GearCheck.party(List.of(o, e)));
        assertEquals(e, GearCheck.party(List.of(o, e, e)));
        assertEquals(u, GearCheck.party(List.of(o, u)), "half under, even with one over");
        assertEquals(e, GearCheck.party(List.of(o, o, u)), "one under spoils over; a third under is not half");
        assertEquals(u, GearCheck.party(List.of(u, u, e)));
        assertEquals(e, GearCheck.party(List.of()));
    }

    @Test
    void vanillaWeaponsCountByTheParityRule() {
        assertEquals(1, GearCheck.vanillaTier("minecraft:iron_sword"));
        assertEquals(2, GearCheck.vanillaTier("minecraft:diamond_axe"));
        assertEquals(3, GearCheck.vanillaTier("minecraft:netherite_sword"));
        assertEquals(0, GearCheck.vanillaTier("minecraft:stone_sword"));
        assertEquals(1, GearCheck.vanillaTier("minecraft:crossbow"));
        assertEquals(2, GearCheck.vanillaTier("minecraft:mace"));
        assertEquals(0, GearCheck.vanillaTier("minecraft:stick"));
    }

    @Test
    void aHitCountsAsTheScriptsWeaponCategories() {
        assertEquals(WeaponCategory.FORGE_TIER_3, WeaponCategory.of(Delivery.MELEE, 3, "cosmicbreach", Kind.OTHER));
        assertEquals(WeaponCategory.FORGE_TIER_2, WeaponCategory.of(Delivery.ARROW, 2, "cosmicbreach", Kind.OTHER));
        assertEquals(WeaponCategory.BARE_HANDS, WeaponCategory.of(Delivery.MELEE, 0, "", Kind.OTHER));
        assertEquals(WeaponCategory.VANILLA_MELEE, WeaponCategory.of(Delivery.MELEE, 0, "minecraft", Kind.BLADE));
        assertEquals(WeaponCategory.BOW, WeaponCategory.of(Delivery.ARROW, 0, "minecraft", Kind.BOW));
        assertEquals(WeaponCategory.CROSSBOW, WeaponCategory.of(Delivery.ARROW, 0, "minecraft", Kind.CROSSBOW));
        assertEquals(WeaponCategory.TRIDENT, WeaponCategory.of(Delivery.TRIDENT, 0, "", Kind.OTHER), "thrown");
        assertEquals(WeaponCategory.TRIDENT, WeaponCategory.of(Delivery.MELEE, 0, "minecraft", Kind.TRIDENT), "held");
        assertEquals(WeaponCategory.MOD_WEAPON, WeaponCategory.of(Delivery.MELEE, 0, "othermod", Kind.BLADE));
        assertEquals(WeaponCategory.MOD_WEAPON, WeaponCategory.of(Delivery.ARROW, 0, "othermod", Kind.OTHER));
        assertNull(WeaponCategory.of(Delivery.MELEE, 0, "minecraft", Kind.OTHER), "a stick says nothing");
        assertNull(WeaponCategory.of(Delivery.OTHER, 0, "", Kind.OTHER), "a set ability or a familiar says nothing");
        assertNull(WeaponCategory.of(Delivery.MELEE, 0, "cosmicbreach", Kind.OTHER), "a pickaxe of the mod says nothing");
    }

    @Test
    void armorCountsWholeSetsPiecesOtherArmorAndNothing() {
        assertEquals(ArmorCategory.MOD_FULL_SET, ArmorCategory.of(4, 4, 0));
        assertEquals(ArmorCategory.MOD_PARTIAL, ArmorCategory.of(2, 3, 1));
        assertEquals(ArmorCategory.VANILLA, ArmorCategory.of(0, 0, 2));
        assertEquals(ArmorCategory.NONE, ArmorCategory.of(0, 0, 0));
        assertEquals("vanguard", ArmorCategory.SET_NAMES.get("starfall_vanguard"));
        assertEquals("regalia", ArmorCategory.SET_NAMES.get("choir_regalia"));
    }
}
