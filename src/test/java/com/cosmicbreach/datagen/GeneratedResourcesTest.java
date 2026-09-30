package com.cosmicbreach.datagen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModMaterials;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fails when a block or item was registered without rerunning the data run ({@code ./gradlew runData}):
 * every block needs a block state, a loot table and a name in {@code src/generated/resources}, every
 * item a model and a name. Reads them from the classpath, where the build packs them.
 */
class GeneratedResourcesTest {
    private static final String ASSETS = "/assets/cosmicbreach/";
    private static final String DATA = "/data/cosmicbreach/";
    /** Blocks planted from another item or shown only inside another: no item of their own. */
    private static final Set<String> NO_ITEM = Set.of("halo_moss_plant", "potted_starbloom", "starbloom_crop");

    @Test
    void everyBlockHasItsGeneratedFiles() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (var holder : ModBlocks.BLOCKS.getEntries()) {
            String id = holder.getId().getPath();
            if (!exists(ASSETS + "blockstates/" + id + ".json")) {
                missing.add("blockstate " + id);
            }
            if (!exists(DATA + "loot_table/blocks/" + id + ".json")) {
                missing.add("loot table " + id);
            }
            if (!lang.has("block.cosmicbreach." + id)) {
                missing.add("name of block " + id);
            }
            if (!NO_ITEM.contains(id) && !exists(ASSETS + "models/item/" + id + ".json")) {
                missing.add("item model " + id);
            }
        }
        assertTrue(missing.isEmpty(), "run ./gradlew runData; missing: " + missing);
    }

    @Test
    void everyItemHasAModelAndAName() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        List<String> items = new ArrayList<>();
        ModMaterials.ITEMS.getEntries().forEach(h -> items.add(h.getId().getPath()));
        items.add("starbloom_seeds");
        for (String id : items) {
            if (!exists(ASSETS + "models/item/" + id + ".json")) {
                missing.add("item model " + id);
            }
            if (!lang.has("item.cosmicbreach." + id)) {
                missing.add("name of item " + id);
            }
        }
        assertTrue(missing.isEmpty(), "run ./gradlew runData; missing: " + missing);
    }

    @Test
    void titleCaseNames() {
        assertTrue(ModLanguageProvider.title("starfall_stone_brick_slab").equals("Starfall Stone Brick Slab"));
        assertTrue(ModLanguageProvider.title("raw_eclipsium").equals("Raw Eclipsium"));
    }

    private static boolean exists(String resource) {
        return GeneratedResourcesTest.class.getResource(resource) != null;
    }

    private static JsonObject lang() throws IOException {
        String path = "/assets/" + ModLanguageProvider.NAMESPACE + "/lang/en_us.json";
        try (InputStream in = GeneratedResourcesTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "no " + path + " on the classpath: run ./gradlew runData");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
