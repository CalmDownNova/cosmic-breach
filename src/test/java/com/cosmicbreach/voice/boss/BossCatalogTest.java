package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.heliarch.HeliarchLine;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The boss voice catalogs: their format (the production manifest's, plus each boss's settings) and the four shipped files. */
class BossCatalogTest {
    static final String SETTINGS = """
            "global_gap_seconds": 20, "wait_seconds": 8, "caption_color": "#E8E2F4",
            "reference": {"weapon_tier": 3, "armor": 20, "toughness": 8, "resilience": 24, "level_min": 32, "level_max": 36, "typical_hit": 20},
            """;

    static JsonObject json(String lines) {
        return JsonParser.parseString("{\"boss\": \"unsung\", " + SETTINGS + "\"lines\": [" + lines + "]}").getAsJsonObject();
    }

    static final String ONE_GONE = """
            {"id": "one_gone", "trigger": "hp_threshold:67", "condition": "none", "priority": 95, "repeat": "once per fight",
             "words": "Who will sing its part now?", "delivery": "Shaken.",
             "variants": {
               "12": {"event": "cosmicbreach:bossvoice/unsung_one_gone_12", "subtitle": "subtitles.cosmicbreach.bossvoice.unsung_one_gone_12",
                      "length_ticks": 106, "speech_start_ticks": 2, "speech_ticks": 71, "over_music_lu": 6.6,
                      "fragments": [{"mask": 1, "start_ticks": 2, "text": "Who will sing"}, {"mask": 2, "start_ticks": 38, "text": "its part now?"}]},
               "23": {"event": "cosmicbreach:bossvoice/unsung_one_gone_23", "subtitle": "subtitles.cosmicbreach.bossvoice.unsung_one_gone_23",
                      "length_ticks": 104, "speech_start_ticks": 2, "speech_ticks": 70,
                      "fragments": [{"mask": 2, "start_ticks": 2, "text": "Who will sing"}, {"mask": 3, "start_ticks": 37, "text": "its part now?"}]}}}
            """;

    static String line(String id, String trigger, String condition, String repeat, String words, String variants) {
        return "{\"id\": \"" + id + "\", \"trigger\": \"" + trigger + "\", \"condition\": \"" + condition + "\", \"priority\": 50, \"repeat\": \""
                + repeat + "\", \"words\": \"" + words + "\", \"variants\": " + variants + "}";
    }

    static final String TAKE = "{\"all\": {\"event\": \"cosmicbreach:bossvoice/x\", \"subtitle\": \"s\", \"length_ticks\": 40}}";

    @Test
    void aManifestShapedCatalogReads() {
        BossCatalog c = BossCatalog.parse("unsung", json(ONE_GONE + ", " + line("left", "player_left", "all_gone, away>=5s", "cooldown 30 s",
                "Wait.", TAKE)));
        assertEquals(400, c.globalGapTicks());
        assertEquals(160, c.waitTicks());
        assertEquals(0xE8E2F4, c.captionColor());
        VoiceLine gone = c.line("one_gone");
        assertNotNull(gone);
        assertEquals(95, gone.priority());
        assertFalse(gone.repeats());
        assertEquals(106, gone.variant("12").lengthTicks());
        assertEquals(71, gone.variant("12").speechTicks());
        assertEquals(2, gone.variant("23").fragments().get(0).mask());
        assertEquals("its part now?", gone.variant("23").fragments().get(1).text());
        assertEquals(null, gone.variant("13"), "no take for those masks and no single take");
        VoiceLine left = c.line("left");
        assertEquals(30, left.cooldownSeconds());
        assertEquals(40, left.variant("123").lengthTicks(), "a single take serves any masks");
        assertEquals(List.of(67), List.copyOf(c.thresholds()));
        assertEquals(List.of(5), List.copyOf(c.awaySteps()));
    }

    @Test
    void aBadCatalogFailsNamingTheLine() {
        for (String bad : List.of(
                line("a", "fight_begin", "none", "once per fight", "Hm.", TAKE),
                line("b", "taunt", "on:roar", "once per fight", "Hm.", TAKE),
                line("c", "taunt", "on:reform", "twice per fight", "Hm.", TAKE),
                line("d", "taunt", "on:reform", "once per fight", "Hm " + (char) 0x2014 + " no.", TAKE),
                line("e", "taunt", "on:reform", "once per fight", "Hm.", "{\"21\": {\"event\": \"cosmicbreach:x\", \"subtitle\": \"s\", \"length_ticks\": 4}}"),
                line("f", "taunt", "on:reform", "once per fight", "Hm.", "{\"all\": {\"event\": \"cosmicbreach:x\", \"subtitle\": \"s\", \"length_ticks\": 40, "
                        + "\"speech_ticks\": 41}}"),
                line("g", "taunt", "on:reform", "once per fight", "Hm.", "{}"))) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> BossCatalog.parse("unsung", json(bad)), bad);
            assertTrue(e.getMessage().startsWith("unsung line "), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> BossCatalog.parse("colossus", json(ONE_GONE)), "a file names its boss");
    }

    @Test
    void theFourShippedCatalogsLoadAndTheRegentsOldLinesMatchTheirTakes() {
        for (String boss : BossCatalog.BOSSES) {
            BossCatalog c = BossCatalog.of(boss);
            assertEquals(boss, c.boss());
            for (VoiceLine l : c.lines()) {
                assertFalse(l.variants().isEmpty(), boss + " " + l.id());
                l.variants().values().forEach(v -> assertEquals("cosmicbreach", v.sound().getNamespace(), boss + " " + l.id()));
            }
        }
        BossCatalog heliarch = BossCatalog.of("heliarch");
        for (HeliarchLine shipped : HeliarchLine.values()) {
            VoiceLine l = heliarch.line(shipped.id());
            assertNotNull(l, shipped.id());
            VoiceLine.Variant take = l.variant(VoiceLine.ALL);
            assertEquals("cosmicbreach:heliarch/voice_" + shipped.id(), take.sound().toString());
            assertEquals(shipped.lengthTicks(), take.lengthTicks(), shipped.id());
            assertEquals(shipped.words(), l.words(), shipped.id());
            assertEquals(shipped.subtitleKey(), take.subtitle());
        }
    }
}
