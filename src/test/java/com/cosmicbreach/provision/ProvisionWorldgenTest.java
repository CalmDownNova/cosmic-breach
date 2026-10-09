package com.cosmicbreach.provision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.provision.ReachabilityTest.Kind;
import com.cosmicbreach.provision.ReachabilityTest.Source;
import com.cosmicbreach.world.Layer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The two world features of 1.1 (fallen Driftwood on the Reach, Umbral Caps in the Deep) are what put level 1's wood and level
 * 3's food in newly generated chunks, and {@link ReachabilityTest} counts them as sources only for a new world. Nothing in
 * that model reads world generation, so this test does: every source the model says only a world feature creates
 * ({@link Source#via()}) is checked against the data and the registries.
 *
 * <ul>
 *   <li>the feature type exists in the feature registry, and every block and item a feature file names is registered;</li>
 *   <li>every biome of the source's level lists a placed feature that places it, so the feature is placed in that level;</li>
 *   <li>the placement stays inside the level's height band, in its biome, spread over the chunk, in the vegetal decoration step,
 *       and is not inert (a count of at least 1, a rarity no rarer than 1 in 16);</li>
 *   <li>the model's three new-chunk-only sources stay marked as such (a source cannot be quietly turned into an existing one).</li>
 * </ul>
 *
 * <p>It also checks the creatures behind the model's renewable creature sources: the game has no spawner outside the debug
 * commands, so each must be spawned in a biome of its level (the biome's own lists or a NeoForge add_spawns modifier).
 */
class ProvisionWorldgenTest {
    private static final String WORLDGEN = "main/resources/data/cosmicbreach/worldgen/";
    private static final String BIOME_MODIFIERS = "main/resources/data/cosmicbreach/neoforge/biome_modifier";
    /** The index of the vegetal decoration step in a biome's feature lists: after the terrain and its top layer stand. */
    private static final int VEGETAL_DECORATION = 9;
    /** A placed feature rarer than one chunk in this many leaves a level without its source for long walks. */
    private static final int MAX_RARITY = 16;

    /** The biomes of each level (level 4, the Sanctum, stands in the Deep) and the layer whose height band they fill. */
    private static final Map<Integer, List<String>> BIOMES = Map.of(1, List.of("shattered_spires", "sunfield_terraces"),
            2, List.of("drift_belt"), 3, List.of("rift_abyss", "lichen_gardens", "hanging_wood", "shattered_field"),
            4, List.of("rift_abyss", "lichen_gardens", "hanging_wood", "shattered_field"));
    private static final Map<Integer, Layer> LAYERS = Map.of(1, Layer.REACH, 2, Layer.DRIFT, 3, Layer.DEEP, 4, Layer.DEEP);

    /** What only the 1.1 world features provide, as the model must declare it: "level:source" to the feature. */
    private static final Map<String, String> NEW_CHUNKS_ONLY = Map.of(
            "1:cosmicbreach:driftwood_log", "cosmicbreach:fallen_driftwood",
            "3:cosmicbreach:umbral_cap", "cosmicbreach:umbral_cap_patch",
            "4:cosmicbreach:umbral_cap", "cosmicbreach:umbral_cap_patch");

    private static List<Source> worldFeatureSources() {
        List<Source> out = new ArrayList<>();
        for (Source s : ReachabilityTest.SOURCES) {
            if (s.via() != null) {
                out.add(s);
            }
        }
        return out;
    }

    @Test
    void theModelDeclaresTheNewChunkSourcesAsSuch() {
        for (Map.Entry<String, String> e : NEW_CHUNKS_ONLY.entrySet()) {
            int level = Integer.parseInt(e.getKey().substring(0, 1));
            String id = e.getKey().substring(2);
            Source found = null;
            for (Source s : ReachabilityTest.SOURCES) {
                if (s.level() == level && s.id().equals(id)) {
                    found = s;
                }
            }
            assertNotNull(found, "the model has no source " + e.getKey());
            assertEquals(e.getValue(), found.via(), e.getKey() + " exists only where " + e.getValue() + " places it: say so in ReachabilityTest.SOURCES");
        }
        assertEquals(NEW_CHUNKS_ONLY.size(), worldFeatureSources().size(), "a source marked as a world feature's that is not in NEW_CHUNKS_ONLY");
    }

    @Test
    void everyWorldFeatureIsPlacedInEveryBiomeOfItsLevel() {
        for (Source s : worldFeatureSources()) {
            for (String biome : BIOMES.get(s.level())) {
                List<String> placedHere = new ArrayList<>();
                for (String placed : listedFeatures(biome)) {
                    if (s.via().equals(placed(placed).get("feature").getAsString())) {
                        placedHere.add(placed);
                    }
                }
                assertTrue(!placedHere.isEmpty(), "level " + s.level() + ": biome " + biome + " lists no placed feature of " + s.via()
                        + " (it lists " + listedFeatures(biome) + "), so " + s.id() + " is never placed there");
            }
        }
    }

    @Test
    void thePlacementKeepsToItsLevelAndBiome() {
        for (Source s : worldFeatureSources()) {
            Layer layer = LAYERS.get(s.level());
            for (String biome : BIOMES.get(s.level())) {
                for (String placed : listedFeatures(biome)) {
                    JsonObject feature = placed(placed);
                    if (!s.via().equals(feature.get("feature").getAsString())) {
                        continue;
                    }
                    Set<String> modifiers = new TreeSet<>();
                    for (JsonElement m : feature.getAsJsonArray("placement")) {
                        JsonObject mod = m.getAsJsonObject();
                        String type = mod.get("type").getAsString();
                        modifiers.add(type);
                        switch (type) {
                            case "minecraft:height_range" -> {
                                int[] band = absoluteBand(placed, mod.get("height"));
                                assertTrue(band[0] >= layer.bandMinY && band[1] < layer.bandMaxY, placed + " places at " + band[0] + " to "
                                        + band[1] + ", outside " + layer + " (" + layer.bandMinY + " to " + (layer.bandMaxY - 1) + ")");
                            }
                            case "minecraft:count" -> {
                                JsonElement count = mod.get("count");
                                if (!count.isJsonPrimitive()) {
                                    fail("unsupported count in " + placed + ": " + count + " (this test reads a plain number; teach it the new form)");
                                }
                                assertTrue(count.getAsInt() >= 1, placed + " places " + count + " a chunk: inert");
                            }
                            case "minecraft:rarity_filter" -> {
                                int chance = mod.get("chance").getAsInt();
                                assertTrue(chance <= MAX_RARITY, placed + " is placed one chunk in " + chance + ", rarer than one in " + MAX_RARITY);
                            }
                            default -> { }
                        }
                    }
                    assertTrue(modifiers.contains("minecraft:biome"), placed + " is not kept to its biome");
                    assertTrue(modifiers.contains("minecraft:in_square"), placed + " is not spread over the chunk");
                    assertEquals(VEGETAL_DECORATION, stepOf(biome, placed), placed + " is listed by " + biome + " outside the vegetal decoration step");
                }
            }
        }
    }

    /**
     * The model counts the wild creatures of each level as renewable (food, hide, cores), and that rests on their spawning: the
     * game has no spawner outside the debug commands. Each renewable creature source must spawn in a biome of its level, through
     * the biome's own spawner lists or a NeoForge {@code add_spawns} modifier that names the biome.
     */
    @Test
    void everyRenewableCreatureSourceSpawnsInABiomeOfItsLevel() {
        int checked = 0;
        for (Source s : ReachabilityTest.SOURCES) {
            if (s.kind() != Kind.MOB || !s.renewable()) {
                continue;
            }
            checked++;
            assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(s.id())), s.id() + " is not a registered entity type");
            List<String> where = new ArrayList<>();
            for (String biome : BIOMES.get(s.level())) {
                if (spawnsIn(biome, s.id())) {
                    where.add(biome);
                }
            }
            assertTrue(!where.isEmpty(), "level " + s.level() + ": the model counts " + s.id() + " as a renewable source, but it spawns in none of "
                    + BIOMES.get(s.level()) + " (not in their spawner lists, not through a neoforge:add_spawns modifier)");
        }
        assertTrue(checked >= 5, "renewable creature sources checked: " + checked);
    }

    @Test
    void theFeaturesAndTheBlocksTheyNameAreRegistered() {
        assertTrue(BuiltInRegistries.FEATURE.containsKey(CosmicBreach.id("fallen_driftwood")), "the fallen log feature is not registered");
        assertSame(ProvisionRegistry.FALLEN_DRIFTWOOD.get(), BuiltInRegistries.FEATURE.get(CosmicBreach.id("fallen_driftwood")),
                "the registered feature is not the fallen log feature");
        assertTrue(BuiltInRegistries.BLOCK.containsKey(CosmicBreach.id("umbral_cap")), "the cap is not registered");
        Set<String> unknown = new TreeSet<>();
        for (Source s : worldFeatureSources()) {
            JsonObject configured = read("configured_feature/" + path(s.via()));
            assertTrue(BuiltInRegistries.FEATURE.containsKey(ResourceLocation.parse(configured.get("type").getAsString())),
                    s.via() + " is of a feature type that is not registered: " + configured.get("type").getAsString());
            collect(configured, unknown);
            for (String biome : BIOMES.get(s.level())) {
                for (String placed : listedFeatures(biome)) {
                    JsonObject feature = placed(placed);
                    if (s.via().equals(feature.get("feature").getAsString())) {
                        collect(feature, unknown);
                    }
                }
            }
        }
        unknown.removeIf(ProvisionWorldgenTest::known);
        assertTrue(unknown.isEmpty(), "named in the world features but not registered: " + unknown);
    }

    /** The cap patch places the cap and nothing else: the model's source and the feature's block are one thing. */
    @Test
    void theCapPatchPlacesTheCapOnBasalt() {
        JsonObject patch = read("configured_feature/umbral_cap_patch");
        assertEquals("minecraft:random_patch", patch.get("type").getAsString());
        JsonObject placed = patch.getAsJsonObject("config").getAsJsonObject("feature").getAsJsonObject("feature");
        assertEquals("minecraft:simple_block", placed.get("type").getAsString());
        assertEquals("cosmicbreach:umbral_cap", placed.getAsJsonObject("config").getAsJsonObject("to_place").getAsJsonObject("state")
                .get("Name").getAsString());
        Set<String> named = new TreeSet<>();
        collect(patch, named);
        // on the Deep's basalt: the block itself, or the tag of every zone's stone (layer 3 zones), which holds it
        boolean onBasalt = named.contains("cosmicbreach:umbral_basalt") || (named.contains("cosmicbreach:deep_stone")
                && readTag("deep_stone").contains("cosmicbreach:umbral_basalt"));
        assertTrue(onBasalt, "the caps are placed on the Deep's basalt: " + named);
    }

    // ------------------------------------------------------------------ the data

    /** Every cosmicbreach id (a string value or a block state's name) under {@code e}. */
    private static void collect(JsonElement e, Set<String> out) {
        if (e.isJsonObject()) {
            e.getAsJsonObject().entrySet().forEach(en -> collect(en.getValue(), out));
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(i -> collect(i, out));
        } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && e.getAsString().startsWith("cosmicbreach:")) {
            out.add(e.getAsString());
        }
    }

    private static String readTag(String name) {
        try {
            return Files.readString(projectFile().resolve("main/resources/data/cosmicbreach/tags/block/" + name + ".json"));
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** A block, an item or a feature of the mod, one of its worldgen files, or one of its block tags. */
    private static boolean known(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        return BuiltInRegistries.BLOCK.containsKey(rl) || BuiltInRegistries.ITEM.containsKey(rl) || BuiltInRegistries.FEATURE.containsKey(rl)
                || Files.exists(projectFile().resolve(WORLDGEN + "configured_feature/" + rl.getPath() + ".json"))
                || Files.exists(projectFile().resolve(WORLDGEN + "placed_feature/" + rl.getPath() + ".json"))
                || Files.exists(projectFile().resolve("main/resources/data/cosmicbreach/tags/block/" + rl.getPath() + ".json"));
    }

    /**
     * The placed features a biome lists that are the mod's own, in every decoration step. A vanilla feature (a spring, glow
     * lichen) cannot place a source of this mod, and there is no file of it in the mod's folder to read.
     */
    private static List<String> listedFeatures(String biome) {
        List<String> out = new ArrayList<>();
        JsonArray steps = read("biome/" + biome).getAsJsonArray("features");
        for (JsonElement step : steps) {
            for (JsonElement f : step.getAsJsonArray()) {
                if (f.getAsString().startsWith("cosmicbreach:")) {
                    out.add(f.getAsString());
                }
            }
        }
        return out;
    }

    /** The decoration step (the index of the list in the biome's features) that lists the placed feature, or -1. */
    private static int stepOf(String biome, String placed) {
        JsonArray steps = read("biome/" + biome).getAsJsonArray("features");
        for (int i = 0; i < steps.size(); i++) {
            for (JsonElement f : steps.get(i).getAsJsonArray()) {
                if (f.getAsString().equals(placed)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** The lowest and highest Y of a height_range: only a uniform range between absolute anchors is read here. */
    private static int[] absoluteBand(String placed, JsonElement height) {
        JsonObject h = height.isJsonObject() ? height.getAsJsonObject() : new JsonObject();
        boolean uniform = h.has("type") && h.get("type").getAsString().equals("minecraft:uniform");
        if (!uniform || !isAbsolute(h.get("min_inclusive")) || !isAbsolute(h.get("max_inclusive"))) {
            fail("unsupported height provider in " + placed + ": " + height + " (this test reads a uniform range between absolute anchors; teach it the new form)");
        }
        return new int[] {h.getAsJsonObject("min_inclusive").get("absolute").getAsInt(), h.getAsJsonObject("max_inclusive").get("absolute").getAsInt()};
    }

    private static boolean isAbsolute(JsonElement anchor) {
        return anchor != null && anchor.isJsonObject() && anchor.getAsJsonObject().has("absolute");
    }

    /** Whether {@code entity} spawns in {@code biome}: in the biome's own spawner lists, or through an add_spawns modifier naming the biome. */
    private static boolean spawnsIn(String biome, String entity) {
        for (var category : read("biome/" + biome).getAsJsonObject("spawners").entrySet()) {
            for (JsonElement e : category.getValue().getAsJsonArray()) {
                if (spawns(e.getAsJsonObject(), entity)) {
                    return true;
                }
            }
        }
        List<Path> modifiers;
        try (Stream<Path> files = Files.list(projectFile().resolve(BIOME_MODIFIERS))) {
            modifiers = files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path f : modifiers) {
            JsonObject m = readFile(f);
            if (!"neoforge:add_spawns".equals(m.get("type").getAsString()) || !selects(f, m.get("biomes"), "cosmicbreach:" + biome)) {
                continue;
            }
            JsonElement list = m.get("spawners");
            JsonArray entries = new JsonArray();
            if (list.isJsonArray()) {
                entries = list.getAsJsonArray();
            } else {
                entries.add(list);
            }
            for (JsonElement e : entries) {
                if (spawns(e.getAsJsonObject(), entity)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A spawner entry that spawns {@code entity}: the right type, with a weight and a count that let it spawn at all. */
    private static boolean spawns(JsonObject spawner, String entity) {
        return entity.equals(spawner.get("type").getAsString()) && spawner.get("weight").getAsInt() >= 1 && spawner.get("maxCount").getAsInt() >= 1;
    }

    private static final String BIOME_TAGS = "main/resources/data/cosmicbreach/tags/worldgen/biome/";

    /** Whether a biome modifier's {@code biomes} names {@code biome}: an id, a list of ids, or a tag of the mod's own (read from its file). */
    private static boolean selects(Path file, JsonElement biomes, String biome) {
        List<String> named = new ArrayList<>();
        if (biomes.isJsonArray()) {
            biomes.getAsJsonArray().forEach(b -> named.add(b.getAsString()));
        } else {
            named.add(biomes.getAsString());
        }
        List<String> ids = new ArrayList<>();
        for (String n : named) {
            resolve(file, n, ids, 0);
        }
        return ids.contains(biome);
    }

    /** Adds the biome ids {@code name} stands for to {@code out}: itself, or, for a tag in the mod's namespace, its values (tags in tags too). */
    private static void resolve(Path file, String name, List<String> out, int depth) {
        if (!name.startsWith("#")) {
            out.add(name);
            return;
        }
        if (depth > 8 || !name.startsWith("#cosmicbreach:")) {
            fail(file.getFileName() + " selects biomes by the tag " + name + " (this test reads the mod's own biome tags, to a depth of eight)");
        }
        Path tag = projectFile().resolve(BIOME_TAGS + name.substring("#cosmicbreach:".length()) + ".json");
        assertTrue(Files.exists(tag), file.getFileName() + " names the tag " + name + " but " + tag + " is not there");
        for (JsonElement v : readFile(tag).getAsJsonArray("values")) {
            resolve(file, v.getAsString(), out, depth + 1);
        }
    }

    private static JsonObject placed(String id) {
        return read("placed_feature/" + path(id));
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static JsonObject read(String file) {
        Path f = projectFile().resolve(WORLDGEN + file + ".json");
        assertTrue(Files.exists(f), "no worldgen file " + f);
        return readFile(f);
    }

    private static JsonObject readFile(Path f) {
        try {
            return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path projectFile() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("src").resolve("main/resources/data/cosmicbreach"))) {
                return dir.resolve("src");
            }
        }
        throw new AssertionError("no src above " + Path.of("").toAbsolutePath());
    }
}
