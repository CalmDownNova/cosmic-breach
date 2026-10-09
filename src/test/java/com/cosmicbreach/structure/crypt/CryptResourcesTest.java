package com.cosmicbreach.structure.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
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
 * W6's files are all there: every block's state and name, every item's model, every sound in sounds.json with a
 * translated subtitle, the damage types with their death messages, the crypt's structure and placement, its vault's
 * loot table ending in the charms' sub-table for the Curios task, and the Codex page.
 */
class CryptResourcesTest {
    private static InputStream open(String path) {
        return CryptResourcesTest.class.getResourceAsStream(path);
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
    void blocksItemsAndNames() throws IOException {
        JsonObject lang = json("/assets/cosmicbreach_crypt/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (var holder : CryptRegistry.BLOCKS.getEntries()) {
            String id = holder.getId().getPath();
            if (!exists("/assets/cosmicbreach/blockstates/" + id + ".json")) {
                missing.add("blockstate " + id);
            }
            if (!lang.has("block.cosmicbreach." + id)) {
                missing.add("name of block " + id);
            }
        }
        for (var holder : CryptRegistry.ITEMS.getEntries()) {
            String id = holder.getId().getPath();
            if (!exists("/assets/cosmicbreach/models/item/" + id + ".json")) {
                missing.add("item model " + id);
            }
        }
        assertTrue(missing.isEmpty(), "run ./gradlew runData or add names; missing: " + missing);
    }

    @Test
    void everySoundHasAFileAndATranslatedSubtitle() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject lang = json("/assets/cosmicbreach_crypt/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (var holder : CryptRegistry.SOUNDS.getEntries()) {
            String id = holder.getId().getPath();
            if (!sounds.has(id)) {
                missing.add("sounds.json " + id);
                continue;
            }
            JsonObject entry = sounds.getAsJsonObject(id);
            String subtitle = entry.get("subtitle").getAsString();
            if (!lang.has(subtitle)) {
                missing.add("subtitle " + subtitle);
            }
            for (var file : entry.getAsJsonArray("sounds")) {
                String name = file.getAsString().replace("cosmicbreach:", "");
                if (!exists("/assets/cosmicbreach/sounds/" + name + ".ogg")) {
                    missing.add("file " + name);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
        for (int i = 1; i <= 8; i++) {
            assertTrue(lang.get("subtitles.cosmicbreach.choir.pad_" + i).getAsString().startsWith("Chime, "), "a note names its pad");
        }
    }

    @Test
    void damageTypesStructureLootAndCodex() throws IOException {
        JsonObject lang = json("/assets/cosmicbreach_crypt/lang/en_us.json");
        for (String type : new String[] {"discord", "star_rock", "gravity_piston"}) {
            assertTrue(exists("/data/cosmicbreach/damage_type/" + type + ".json"), type);
            assertTrue(lang.has("death.attack.cosmicbreach." + type), "death message " + type);
        }
        JsonObject set = json("/data/cosmicbreach/worldgen/structure_set/hollow_crypt.json");
        assertEquals(38, set.getAsJsonObject("placement").get("spacing").getAsInt());
        assertEquals(13, set.getAsJsonObject("placement").get("separation").getAsInt());
        assertTrue(exists("/data/cosmicbreach/worldgen/structure/hollow_crypt.json"));
        JsonObject biomes = json("/data/cosmicbreach/tags/worldgen/biome/has_structure/hollow_crypt.json");
        assertEquals("cosmicbreach:rift_abyss", biomes.getAsJsonArray("values").get(0).getAsString());
        JsonArray pools = json("/data/cosmicbreach/loot_table/vaults/crypt.json").getAsJsonArray("pools");
        JsonObject last = pools.get(pools.size() - 1).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("cosmicbreach:vaults/accessories_charms", last.get("value").getAsString(), "the charms' pool comes last");
        assertTrue(exists("/data/cosmicbreach/loot_table/vaults/accessories_charms.json"));
        assertTrue(exists("/assets/cosmicbreach/guides/cosmicbreach/codex/choir_floor.md"));
        for (String fx : new String[] {"choir_fill", "choir_glyphs", "choir_ring", "rift_cracks", "gravity_rings", "chute_glint"}) {
            assertTrue(exists("/assets/cosmicbreach/textures/fx/" + fx + ".png"), fx);
        }
    }
}
