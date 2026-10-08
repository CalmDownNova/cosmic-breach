package com.cosmicbreach.gear.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every Forge recipe names its screen tab in its data (1.1 design section 10), read as files from both places Forge
 * recipes live: the data run's output and the hand-written ones, in any namespace and any subfolder of its recipe
 * folder. The 29 recipes of 1.0.3 keep the tabs chosen for them; any other recipe only has to name a tab that exists.
 * The screen shows four ingredients, so no recipe may need more.
 */
class ForgeRecipeCategoriesTest {
    private static final List<String> ROOTS = List.of("src/generated/resources", "src/main/resources");

    private static final Map<String, String> SHIPPED = Map.ofEntries(
            Map.entry("meridian", "weapons"),
            Map.entry("comet_maul", "weapons"),
            Map.entry("binary_edges", "weapons"),
            Map.entry("choir_astrolabe", "weapons"),
            Map.entry("starfall_vanguard_helm", "armor"),
            Map.entry("starfall_vanguard_chestplate", "armor"),
            Map.entry("starfall_vanguard_greaves", "armor"),
            Map.entry("starfall_vanguard_boots", "armor"),
            Map.entry("driftweave_hood", "armor"),
            Map.entry("driftweave_coat", "armor"),
            Map.entry("driftweave_leggings", "armor"),
            Map.entry("driftweave_boots", "armor"),
            Map.entry("choir_regalia_circlet", "armor"),
            Map.entry("choir_regalia_vestment", "armor"),
            Map.entry("choir_regalia_tassets", "armor"),
            Map.entry("choir_regalia_sabatons", "armor"),
            Map.entry("astral_saddle", "mount_gear"),
            Map.entry("starsteel_barding", "mount_gear"),
            Map.entry("comet_bridle", "mount_gear"),
            Map.entry("halo_reins", "mount_gear"),
            Map.entry("resonance_chime", "mount_gear"),
            Map.entry("drift_harness", "mount_gear"),
            Map.entry("nebula_reins", "mount_gear"),
            Map.entry("gale_fins", "mount_gear"),
            Map.entry("nebulite_barding", "mount_gear"),
            Map.entry("reverie_draught", "other"),
            Map.entry("guardian_echo", "other"),
            Map.entry("brazier_of_solenne", "other"),
            Map.entry("dying_star_heart", "other"));

    @Test
    void everyForgeRecipeNamesATabThatExists() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : forgeRecipes().entrySet()) {
            JsonObject recipe = e.getValue();
            if (!recipe.has("category")) {
                problems.add(e.getKey() + " has no category");
            } else if (ForgeCategory.byName(recipe.get("category").getAsString()) == null) {
                problems.add(e.getKey() + " names an unknown tab " + recipe.get("category").getAsString());
            }
            if (recipe.getAsJsonArray("ingredients").size() > 4) {
                problems.add(e.getKey() + " needs more than the 4 ingredients the Forge screen shows");
            }
        }
        assertTrue(problems.isEmpty(), "give each Forge recipe a line \"category\": \"weapons\" | \"armor\" | \"tools\" | "
                + "\"mount_gear\" | \"other\" right after its \"type\" (Forge Screen plan, F2): " + problems);
    }

    @Test
    void theShippedRecipesSitInTheirTabs() throws IOException {
        assertEquals(29, SHIPPED.size());
        Map<String, JsonObject> recipes = forgeRecipes();
        List<String> wrong = new ArrayList<>();
        SHIPPED.forEach((name, tab) -> {
            JsonObject recipe = recipes.get("cosmicbreach:forge/" + name);
            if (recipe == null) {
                wrong.add(name + " is missing");
            } else if (!recipe.has("category") || !tab.equals(recipe.get("category").getAsString())) {
                wrong.add(name + " belongs in " + tab);
            }
        });
        assertTrue(wrong.isEmpty(), String.valueOf(wrong));
    }

    /**
     * Every Forge recipe file under {@code data/<namespace>/recipe/} of both roots, subfolders included, by
     * {@code namespace:path} without ".json" (a hand-written Forge recipe of this mod is {@code cosmicbreach:forge/<name>}).
     */
    private static Map<String, JsonObject> forgeRecipes() throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        for (String root : ROOTS) {
            Path data = projectFile(root + "/data");
            try (Stream<Path> namespaces = Files.list(data)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    Path recipes = namespace.resolve("recipe");
                    if (!Files.isDirectory(recipes)) {
                        continue;
                    }
                    try (Stream<Path> files = Files.walk(recipes)) {
                        for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                            JsonObject json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                            if (json.has("type") && "cosmicbreach:astral_forge".equals(json.get("type").getAsString())) {
                                String path = recipes.relativize(f).toString().replace('\\', '/');
                                out.put(namespace.getFileName() + ":" + path.substring(0, path.length() - ".json".length()), json);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** A file of the source tree, found by walking up from the test's working directory (build/minecraft-junit). */
    private static Path projectFile(String path) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(path))) {
                return dir.resolve(path);
            }
        }
        throw new AssertionError("no " + path + " above " + Path.of("").toAbsolutePath());
    }
}
