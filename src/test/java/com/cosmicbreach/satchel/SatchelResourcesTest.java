package com.cosmicbreach.satchel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.registry.ModMaterials;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Satchel's files are all there: tags, recipe, Curios slot, model, texture and words. */
class SatchelResourcesTest {
    private static JsonObject json(String path) throws IOException {
        try (InputStream in = SatchelResourcesTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, path + " is missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Set<String> values(String path) throws IOException {
        Set<String> out = new HashSet<>();
        json(path).getAsJsonArray("values").forEach(e -> out.add(e.getAsString()));
        return out;
    }

    @Test
    void everyModMaterialIsInTheMaterialsTag() throws IOException {
        Set<String> tag = values("data/cosmicbreach/tags/item/satchel_materials.json");
        ModMaterials.ITEMS.getEntries().forEach(entry -> assertTrue(tag.contains(entry.getId().toString()), entry.getId() + " is missing from the tag"));
        assertTrue(tag.contains("cosmicbreach:starshard"));
    }

    @Test
    void theBasicsAreThereAndTorchesAreNot() throws IOException {
        Set<String> tag = values("data/cosmicbreach/tags/item/satchel_basics.json");
        for (String id : new String[]{"minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:dirt", "minecraft:sand", "minecraft:gravel",
                "#minecraft:logs", "#minecraft:planks", "minecraft:stick", "minecraft:coal", "minecraft:raw_iron", "minecraft:iron_ingot",
                "minecraft:string", "minecraft:leather", "minecraft:bone", "minecraft:rotten_flesh"}) {
            assertTrue(tag.contains(id), id);
        }
        assertTrue(tag.stream().noneMatch(id -> id.contains("torch") || id.contains("arrow")), "torches and arrows stay out");
    }

    @Test
    void theSatchelWearsInTheBackSlotAndThePlayerHasOne() throws IOException {
        assertTrue(values("data/curios/tags/item/back.json").contains("cosmicbreach:satchel"));
        Set<String> slots = new HashSet<>();
        json("data/cosmicbreach/curios/entities/player.json").getAsJsonArray("slots").forEach(e -> slots.add(e.getAsString()));
        assertTrue(slots.contains("back"));
    }

    @Test
    void itIsMadeAtTheForgeFromLayerOneMaterials() throws IOException {
        JsonObject recipe = json("data/cosmicbreach/recipe/forge/satchel.json");
        assertEquals("cosmicbreach:astral_forge", recipe.get("type").getAsString());
        assertEquals(1, recipe.get("tier").getAsInt());
        assertEquals("cosmicbreach:satchel", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(3, recipe.getAsJsonArray("ingredients").size());
    }

    @Test
    void modelTextureAndWordsExist() throws IOException {
        assertEquals("cosmicbreach:item/satchel", json("assets/cosmicbreach/models/item/satchel.json").getAsJsonObject("textures").get("layer0").getAsString());
        try (InputStream in = SatchelResourcesTest.class.getResourceAsStream("/assets/cosmicbreach/textures/item/satchel.png")) {
            assertNotNull(in);
        }
        JsonObject lang = json("assets/cosmicbreach/lang/en_us.json");
        for (String key : new String[]{"item.cosmicbreach.satchel", "key.cosmicbreach.satchel", "gui.cosmicbreach.satchel",
                "gui.cosmicbreach.satchel.tab.materials", "gui.cosmicbreach.satchel.tab.gear"}) {
            assertTrue(lang.has(key), key);
        }
    }
}
