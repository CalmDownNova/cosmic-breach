package com.cosmicbreach.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The way in's files, read from the classpath the way the game will: generated models, loot and tags
 * (rerun {@code ./gradlew runData} if these fail), the hand-written names, sounds, worldgen data, and the
 * Codex's pages (every page the code opens exists, and no page has a dash in it).
 */
class OnboardingResourcesTest {
    private static final List<String> PAGES = List.of("index", "starfall", "build_a_ring", "fall_up", "landing");

    @Test
    void blocksAndItemsHaveTheirGeneratedFiles() throws IOException {
        for (String block : List.of("starfall_shard", "breach")) {
            assertTrue(exists("/assets/cosmicbreach/blockstates/" + block + ".json"), "blockstate " + block);
        }
        assertTrue(exists("/data/cosmicbreach/loot_table/blocks/starfall_shard.json"));
        assertTrue(exists("/assets/cosmicbreach/models/block/breach_frame_active.json"));
        assertTrue(exists("/data/cosmicbreach/tags/block/starfall_crater_replaceable.json"));
        JsonObject names = json("/assets/cosmicbreach_onboarding/lang/en_us.json");
        for (String item : List.of("starfall_shard", "starfall_codex", "torn_codex_page")) {
            assertTrue(exists("/assets/cosmicbreach/models/item/" + item + ".json"), "item model " + item);
            assertTrue(exists("/assets/cosmicbreach/textures/item/" + item + ".png"), "item texture " + item);
            assertTrue(names.has("item.cosmicbreach." + item), "name of " + item);
        }
        assertTrue(names.has("block.cosmicbreach.starfall_shard"));
        assertTrue(names.has("block.cosmicbreach.breach"));
    }

    @Test
    void everySoundHasAFileAndASubtitleAndTheBoomCarries128Blocks() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject names = json("/assets/cosmicbreach_onboarding/lang/en_us.json");
        for (String s : List.of("starfall_streak", "starfall_boom", "shard_hum", "ring_activate", "breach_wind", "fall_up")) {
            JsonObject entry = sounds.getAsJsonObject("onboarding/" + s);
            assertTrue(entry != null, "sounds.json entry " + s);
            assertTrue(exists("/assets/cosmicbreach/sounds/onboarding/" + s + ".ogg"), "sound file " + s);
            assertTrue(names.has(entry.get("subtitle").getAsString()), "subtitle " + s);
        }
        JsonObject boom = sounds.getAsJsonObject("onboarding/starfall_boom").getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals(128, boom.get("attenuation_distance").getAsInt());
    }

    @Test
    void theFrameRecipeMakesSixSoTwoCraftsMakeTheTwelveFrameRing() throws IOException {
        JsonObject recipe = json("/data/cosmicbreach/recipe/breach_frame.json");
        assertEquals(6, recipe.getAsJsonObject("result").get("count").getAsInt());
        // copper in the four corners, stone in the other five: 4 copper and 5 stone a craft
        String pattern = String.join("", recipe.getAsJsonArray("pattern").asList().stream().map(e -> e.getAsString()).toList());
        assertEquals("CSCSSSCSC", pattern);
        assertEquals("minecraft:copper_ingot", recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        assertEquals("minecraft:stone_crafting_materials", recipe.getAsJsonObject("key").getAsJsonObject("S").get("tag").getAsString());
    }

    @Test
    void theFallenRiftIsPlacedBySpacing28Separation10() throws IOException {
        JsonObject set = json("/data/cosmicbreach/worldgen/structure_set/fallen_rift.json");
        JsonObject placement = set.getAsJsonObject("placement");
        assertEquals("minecraft:random_spread", placement.get("type").getAsString());
        assertEquals(28, placement.get("spacing").getAsInt());
        assertEquals(10, placement.get("separation").getAsInt());
        assertTrue(exists("/data/cosmicbreach/worldgen/structure/fallen_rift.json"));
        assertTrue(exists("/data/cosmicbreach/tags/worldgen/biome/has_structure/fallen_rift.json"));
        String loot = text("/data/cosmicbreach/loot_table/chests/fallen_rift.json");
        for (String item : List.of("cosmicbreach:starfall_shard", "minecraft:copper_ingot", "cosmicbreach:starsteel_nugget",
                "cosmicbreach:torn_codex_page")) {
            assertTrue(loot.contains(item), "the chest holds " + item);
        }
    }

    @Test
    void theCodexHasEveryPageTheCodeOpensAndNoDashes() throws IOException {
        // since F1 the Codex is built in code (client.codex.CodexGuide, for its own tags); a data-driven guide with
        // the same id would be a second guide
        assertFalse(exists("/assets/cosmicbreach/guideme_guides/codex.json"));
        for (String page : PAGES) {
            String md = text("/assets/cosmicbreach/guides/cosmicbreach/codex/" + page + ".md");
            assertTrue(md.startsWith("---"), page + " starts with frontmatter");
            assertFalse(md.indexOf(0x2014) >= 0 || md.indexOf(0x2013) >= 0, page + " has a dash");
        }
        assertEquals("cosmicbreach:build_a_ring.md", Codex.BUILD_A_RING.toString());
        assertEquals("cosmicbreach:codex", Codex.GUIDE.toString());
    }

    private static boolean exists(String resource) {
        return OnboardingResourcesTest.class.getResource(resource) != null;
    }

    private static String text(String resource) throws IOException {
        try (InputStream in = OnboardingResourcesTest.class.getResourceAsStream(resource)) {
            assertTrue(in != null, "missing " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(String resource) throws IOException {
        return JsonParser.parseString(text(resource)).getAsJsonObject();
    }
}
