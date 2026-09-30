package com.cosmicbreach.structure.sanctum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W7's files are all there: every Sanctum block's state and name, the three items' models and names, Voidsick's name
 * and icon, the sounds with their files and translated subtitles, the wings' loot, the Heart's Forge III recipe, the
 * structure and its one-per-world placement, the Codex page, and no dashes in the words.
 */
class SanctumResourcesTest {
    private static final String[] BLOCKS = {"sanctum_ivory", "sanctum_ivory_bricks", "sanctum_ivory_stairs", "sanctum_ivory_slab",
            "sanctum_gilt", "sanctum_ember", "sanctum_rift_lamp", "sanctum_umbral", "choir_pillar", "sanctum_gate", "throne_seal",
            "eclipse_lock", "sanctum_throne", "sanctum_vault"};
    private static final String[] ITEMS = {"dying_star_heart", "event_horizon_lens", "hourglass_of_vesper"};
    private static final String[] SOUNDS = {"gate_open", "gate_refuse", "lock_lit", "stair_open", "throne_hum", "fall_rescue"};

    private static InputStream open(String path) {
        return SanctumResourcesTest.class.getResourceAsStream(path);
    }

    private static boolean exists(String path) throws IOException {
        try (InputStream in = open(path)) {
            return in != null;
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = open(path)) {
            assertTrue(in != null, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void blocksItemsEffectAndNames() throws IOException {
        JsonObject lang = json("/assets/cosmicbreach_sanctum/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (String id : BLOCKS) {
            if (!exists("/assets/cosmicbreach/blockstates/" + id + ".json")) {
                missing.add("blockstate " + id);
            }
            if (!lang.has("block.cosmicbreach." + id)) {
                missing.add("name of block " + id);
            }
        }
        for (String id : ITEMS) {
            if (!exists("/assets/cosmicbreach/models/item/" + id + ".json") || !exists("/assets/cosmicbreach/textures/item/" + id + ".png")) {
                missing.add("model or icon of " + id);
            }
            if (!lang.has("item.cosmicbreach." + id)) {
                missing.add("name of item " + id);
            }
        }
        if (!lang.has("effect.cosmicbreach.voidsick") || !exists("/assets/cosmicbreach/textures/mob_effect/voidsick.png")) {
            missing.add("Voidsick's name or icon");
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void everySoundHasItsFileAndATranslatedSubtitle() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject lang = json("/assets/cosmicbreach_sanctum/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (String s : SOUNDS) {
            String key = "sanctum/" + s;
            if (!sounds.has(key)) {
                missing.add("sounds.json " + key);
                continue;
            }
            JsonObject e = sounds.getAsJsonObject(key);
            if (!lang.has(e.get("subtitle").getAsString())) {
                missing.add("subtitle of " + key);
            }
            for (JsonElement f : e.getAsJsonArray("sounds")) {
                String name = f.isJsonObject() ? f.getAsJsonObject().get("name").getAsString() : f.getAsString();
                if (!exists("/assets/cosmicbreach/sounds/" + name.substring("cosmicbreach:".length()) + ".ogg")) {
                    missing.add("file " + name);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void lootRecipeStructureAndCodex() throws IOException {
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/sanctum_lens.json").toString().contains("cosmicbreach:solar_ember"));
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/sanctum_lens.json").toString().contains("cosmicbreach:event_horizon_lens"));
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/sanctum_choir.json").toString().contains("cosmicbreach:hymn_crystal"));
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/sanctum_choir.json").toString().contains("cosmicbreach:hourglass_of_vesper"));
        JsonObject recipe = json("/data/cosmicbreach/recipe/forge/dying_star_heart.json");
        assertEquals(3, recipe.get("tier").getAsInt(), "Forge III");
        String ingredients = recipe.getAsJsonArray("ingredients").toString();
        for (String need : new String[] {"silent_sigil", "solar_ember", "hymn_crystal", "eclipsium_ingot"}) {
            assertTrue(ingredients.contains("cosmicbreach:" + need), need);
        }
        assertTrue(ingredients.contains("\"count\":4"), "four Eclipsium Ingots");
        JsonObject structure = json("/data/cosmicbreach/worldgen/structure/breach_sanctum.json");
        assertEquals("cosmicbreach:breach_sanctum", structure.get("type").getAsString());
        assertEquals("cosmicbreach:rift_abyss", structure.getAsJsonArray("biomes").get(0).getAsString());
        JsonObject placement = json("/data/cosmicbreach/worldgen/structure_set/breach_sanctum.json").getAsJsonObject("placement");
        assertEquals("cosmicbreach:breach_sanctum", placement.get("type").getAsString(), "placed once per world");
        assertTrue(exists("/assets/cosmicbreach/guides/cosmicbreach/codex/breach_sanctum.md"), "the Codex page");
        JsonObject lang = json("/assets/cosmicbreach_sanctum/lang/en_us.json");
        for (String k : lang.keySet()) {
            String text = lang.get(k).getAsString();
            assertTrue(text.indexOf(0x2014) < 0 && text.indexOf(0x2013) < 0, "no dashes: " + k);
        }
    }
}
