package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The script's trigger and condition vocabulary: every word it uses parses, typos fail, and each condition tests its moment. */
class TriggerAndConditionTest {
    @Test
    void theTriggerVocabularyParsesAndTyposFail() {
        for (String t : List.of("fight_start", "hp_threshold:0", "hp_threshold:67", "hp_threshold:100", "player_death", "player_low_health",
                "player_fell", "player_left", "player_returned", "weapon:forge_tier_1", "weapon:forge_tier_4", "weapon:vanilla_melee",
                "weapon:bow", "weapon:crossbow", "weapon:trident", "weapon:mod_weapon", "weapon:bare_hands", "armor:mod_full_set",
                "armor:mod_partial", "armor:vanilla", "armor:none", "gear:over", "gear:under", "gear:even", "attack_gap", "boss_kill",
                "taunt")) {
            assertEquals(t, Trigger.parse(t).key());
        }
        for (String bad : List.of("fight_begin", "hp_threshold", "hp_threshold:101", "weapon:sword", "armor:full", "gear:great",
                "taunt:reform", "boss_kill:now")) {
            assertThrows(IllegalArgumentException.class, () -> Trigger.parse(bad), bad);
        }
    }

    @Test
    void everyConditionTheScriptUsesParsesAndTyposFail() {
        String all = "players:solo, players:group, first, returning, masks:3, health<=25%, all_gone, away>=5s, last_alive, "
                + "also:forge_tier_3, item:choir_astrolabe, set:vanguard, set:driftweave, set:regalia, on:reform, on:break, "
                + "on:nova_return, on:nova_broken, on:nova_detonated, on:corona_flare, on:soft_enrage, on:harmonize_all_safe, "
                + "while:mounted, target_far";
        assertEquals(24, Condition.parseAll(all).size());
        assertEquals(List.of(), Condition.parseAll("none"));
        for (String bad : List.of("players:duo", "masks:4", "health<=25", "away>=5", "also:sword", "set:plate", "on:roar", "while:flying",
                "item:Bad Name", "lonely")) {
            assertThrows(IllegalArgumentException.class, () -> Condition.parse(bad), bad);
        }
    }

    @Test
    void eachConditionTestsItsMoment() {
        Context solo = Context.of(1, false, 3);
        Context group = Context.of(3, true, 2);
        assertTrue(Condition.parse("players:solo").test(solo));
        assertFalse(Condition.parse("players:solo").test(group));
        assertTrue(Condition.parse("players:group").test(group));
        assertTrue(Condition.parse("first").test(solo));
        assertTrue(Condition.parse("returning").test(group));
        assertTrue(Condition.parse("masks:3").test(solo));
        assertFalse(Condition.parse("masks:3").test(group));
        assertTrue(Condition.parse("health<=25%").test(solo.withHealth(0.25)));
        assertFalse(Condition.parse("health<=25%").test(solo.withHealth(0.26)));
        assertTrue(Condition.parse("away>=5s").test(solo.withAway(5, true)));
        assertFalse(Condition.parse("away>=10s").test(solo.withAway(9, false)));
        assertTrue(Condition.parse("all_gone").test(solo.withAway(5, true)));
        assertTrue(Condition.parse("last_alive").test(group.withLastAlive(true)));
        assertTrue(Condition.parse("item:choir_astrolabe").test(solo.withItem("cosmicbreach:choir_astrolabe")));
        assertFalse(Condition.parse("item:choir_astrolabe").test(solo.withItem("minecraft:bow")));
        assertTrue(Condition.parse("set:regalia").test(solo.withArmorSet("regalia")));
        assertTrue(Condition.parse("on:reform").test(solo.withEvent("reform")));
        assertFalse(Condition.parse("on:reform").test(solo.withEvent("break")));
        assertTrue(Condition.parse("while:mounted").test(solo.withMounted(true)));
        assertTrue(Condition.parse("target_far").test(solo.withTargetFar(true)));
    }

    @Test
    void alsoWidensAWeaponLinesTrigger() {
        VoiceLine.Variant take = new VoiceLine.Variant(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "bossvoice/x"), "s", 20, 0, 20, List.of());
        VoiceLine heart = new VoiceLine("colossus", "my_heart", Trigger.parse("weapon:forge_tier_2"),
                Condition.parseAll("also:forge_tier_3, also:forge_tier_4"), 50, 0, "My heart... in your blade.", Map.of("all", take), 0);
        assertTrue(heart.answers(Trigger.parse("weapon:forge_tier_2")));
        assertTrue(heart.answers(Trigger.parse("weapon:forge_tier_4")));
        assertFalse(heart.answers(Trigger.parse("weapon:forge_tier_1")));
        assertFalse(heart.answers(Trigger.parse("armor:none")));
        assertTrue(heart.fits(Context.of(1, false, 0)), "also: never filters");
    }
}
