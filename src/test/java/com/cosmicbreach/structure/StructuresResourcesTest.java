package com.cosmicbreach.structure;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * W5's files are all there: every block's state and name, every item's model and name, every sound in
 * sounds.json with a translated subtitle, the vault loot tables (with the accessory sub-tables the Curios task
 * fills), the two structures and their placement, and the Codex page.
 */
class StructuresResourcesTest {
    private static InputStream open(String path) {
        return StructuresResourcesTest.class.getResourceAsStream(path);
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
        JsonObject lang = json("/assets/cosmicbreach_structures/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (var holder : StructureRegistry.BLOCKS.getEntries()) {
            String id = holder.getId().getPath();
            if (!exists("/assets/cosmicbreach/blockstates/" + id + ".json")) {
                missing.add("blockstate " + id);
            }
            if (!lang.has("block.cosmicbreach." + id)) {
                missing.add("name of block " + id);
            }
        }
        for (var holder : StructureRegistry.ITEMS.getEntries()) {
            String id = holder.getId().getPath();
            if (!exists("/assets/cosmicbreach/models/item/" + id + ".json")) {
                missing.add("item model " + id);
            }
            if (!lang.has("item.cosmicbreach." + id) && !lang.has("block.cosmicbreach." + id)) {
                missing.add("name of item " + id);
            }
        }
        assertTrue(missing.isEmpty(), "run ./gradlew runData or add names; missing: " + missing);
    }

    @Test
    void soundsHaveSubtitles() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject lang = json("/assets/cosmicbreach_structures/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (var holder : StructureRegistry.SOUNDS.getEntries()) {
            String id = holder.getId().getPath();
            if (!sounds.has(id)) {
                missing.add("sounds.json " + id);
                continue;
            }
            String subtitle = sounds.getAsJsonObject(id).get("subtitle").getAsString();
            if (!lang.has(subtitle)) {
                missing.add("subtitle " + subtitle);
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void lootStructuresAndCodex() throws IOException {
        for (String table : List.of("reliquary", "observatory", "accessories_t1", "accessories_t2")) {
            JsonObject t = json("/data/cosmicbreach/loot_table/vaults/" + table + ".json");
            assertTrue(t.get("type").getAsString().equals("minecraft:vault"));
        }
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/reliquary.json").toString().contains("cosmicbreach:vaults/accessories_t1"));
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/observatory.json").toString().contains("cosmicbreach:vaults/accessories_t2"));
        for (String s : List.of("spire_reliquary", "gyre_observatory")) {
            json("/data/cosmicbreach/worldgen/structure/" + s + ".json");
            JsonObject set = json("/data/cosmicbreach/worldgen/structure_set/" + s + ".json");
            int spacing = set.getAsJsonObject("placement").get("spacing").getAsInt();
            int separation = set.getAsJsonObject("placement").get("separation").getAsInt();
            assertTrue(s.equals("spire_reliquary") ? spacing == 24 && separation == 8 : spacing == 36 && separation == 12);
        }
        assertTrue(exists("/assets/cosmicbreach/guides/cosmicbreach/codex/lens_array.md"));
        assertTrue(exists("/data/cosmicbreach/damage_type/kinetic_bolt.json"));
    }
}
