package com.cosmicbreach.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.ModItemIds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every item this mod registers has its tooltip words; a new item without them fails here. */
class ItemTooltipsTest {
    private static JsonObject json(String path) throws IOException {
        try (InputStream in = ItemTooltipsTest.class.getResourceAsStream("/" + path)) {
            assertNotNull(in, path + " is missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Set<String> materials() throws IOException {
        Set<String> out = new HashSet<>();
        json("data/cosmicbreach/tags/item/satchel_materials.json").getAsJsonArray("values")
                .forEach(e -> out.add(e.getAsString().replace("cosmicbreach:", "")));
        return out;
    }

    @Test
    void everyRegisteredItemHasItsWhatAndFromLines() throws IOException {
        JsonObject lang = json("assets/cosmicbreach_tooltips/lang/en_us.json");
        Set<String> items = ModItemIds.paths();
        assertTrue(items.size() >= 170, "the scan found only " + items.size() + " items");
        List<String> missing = new ArrayList<>();
        for (String path : items) {
            if (!lang.has(ItemTooltips.what(path))) {
                missing.add(ItemTooltips.what(path));
            }
            if (!lang.has(ItemTooltips.from(path))) {
                missing.add(ItemTooltips.from(path));
            }
        }
        assertTrue(missing.isEmpty(), "missing tooltip words: " + missing);
    }

    @Test
    void everyMaterialSaysWhatItIsUsedIn() throws IOException {
        JsonObject lang = json("assets/cosmicbreach_tooltips/lang/en_us.json");
        Set<String> items = ModItemIds.paths();
        List<String> missing = new ArrayList<>();
        for (String path : materials()) {
            if (items.contains(path) && !lang.has(ItemTooltips.used(path))) {
                missing.add(ItemTooltips.used(path));
            }
        }
        assertTrue(missing.isEmpty(), "materials without a used-in line: " + missing);
    }

    @Test
    void bossDropsNeverNameTheBossOnlyItsLayer() throws IOException {
        JsonObject lang = json("assets/cosmicbreach_tooltips/lang/en_us.json");
        for (String path : new String[]{"prism_heart", "leviathan_pearl", "leviathan_scale", "silent_sigil", "solar_heart", "last_light", "umbra_cantor"}) {
            String from = lang.get(ItemTooltips.from(path)).getAsString();
            assertTrue(from.matches(".*a boss on layer [1-4].*"), path + ": " + from);
        }
        for (var e : lang.entrySet()) {
            for (String name : new String[]{"Prism Colossus", "Thalassine Leviathan", "The Unsung", "Hollow Heliarch", "the Unsung", "the Heliarch"}) {
                assertFalse(e.getValue().getAsString().contains(name), e.getKey() + " names a boss");
            }
        }
    }

    @Test
    void noDashesInTheWords() throws IOException {
        for (var e : json("assets/cosmicbreach_tooltips/lang/en_us.json").entrySet()) {
            String t = e.getValue().getAsString();
            assertFalse(t.contains("—") || t.contains("–"), e.getKey());
        }
    }

    @Test
    void collapsedShowsOneDimLineAndShiftShowsTheDetails() {
        Set<String> keys = Set.of(ItemTooltips.what("x"), ItemTooltips.from("x"), ItemTooltips.used("x"));
        assertEquals(1, ItemTooltips.lines("x", false, keys::contains).size());
        assertEquals(3, ItemTooltips.lines("x", true, keys::contains).size());
        assertEquals(2, ItemTooltips.lines("x", true, Set.of(ItemTooltips.what("x"), ItemTooltips.from("x"))::contains).size());
        assertTrue(ItemTooltips.lines("y", true, keys::contains).isEmpty(), "an item without words adds nothing");
    }

    @Test
    void everyForgeResultsFromLineNamesEveryIngredient() throws Exception {
        JsonObject lang = json("assets/cosmicbreach_tooltips/lang/en_us.json");
        JsonObject names = json("assets/cosmicbreach_names/lang/en_us.json");
        java.nio.file.Path dir = java.nio.file.Paths.get(ItemTooltipsTest.class.getResource("/data/cosmicbreach/recipe/forge").toURI());
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (java.nio.file.Path d : new java.nio.file.Path[]{dir}) {
            try (var files = java.nio.file.Files.list(d)) {
                for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
                    JsonObject recipe = JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
                    String result = recipe.getAsJsonObject("result").get("id").getAsString().replace("cosmicbreach:", "");
                    if (!lang.has(ItemTooltips.from(result))) {
                        continue;
                    }
                    String from = lang.get(ItemTooltips.from(result)).getAsString();
                    checked++;
                    for (var ing : recipe.getAsJsonArray("ingredients")) {
                        var o = ing.getAsJsonObject();
                        if (!o.has("item")) {
                            continue; // a tag: named in words
                        }
                        String id = o.get("item").getAsString().replace("cosmicbreach:", "").replace("minecraft:", "");
                        String key1 = "item.cosmicbreach." + id;
                        String key2 = "block.cosmicbreach." + id;
                        String display = names.has(key1) ? names.get(key1).getAsString() : names.has(key2) ? names.get(key2).getAsString() : titleCase(id);
                        if (!from.contains(display)) {
                            wrong.add(result + " leaves out " + display);
                        }
                    }
                }
            }
        }
        assertTrue(checked > 25, "checked " + checked);
        assertTrue(wrong.isEmpty(), wrong.toString());
    }

    private static String titleCase(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) {
            sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    @Test
    void theFinalForgeItemNamesItsTrueSources() throws IOException {
        String from = json("assets/cosmicbreach_tooltips/lang/en_us.json").get(ItemTooltips.from("dying_star_heart")).getAsString();
        assertFalse(from.contains("three boss drops"), from);
        assertTrue(from.contains("Silent Sigil") && from.contains("Solar Ember") && from.contains("Hymn Crystal"), from);
    }
}
