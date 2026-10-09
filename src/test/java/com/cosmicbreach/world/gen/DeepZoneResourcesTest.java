package com.cosmicbreach.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.client.sky.ZoneFog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The layer 3 zones' data (Aetheria 1.2): one biome per zone, wired into the dimension, its features, spawns and structures. */
class DeepZoneResourcesTest {
    private static final List<String> ZONES = List.of("rift_abyss", "lichen_gardens", "hanging_wood", "shattered_field");

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = DeepZoneResourcesTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean exists(String path) throws IOException {
        try (InputStream in = DeepZoneResourcesTest.class.getResourceAsStream(path)) {
            return in != null;
        }
    }

    private static boolean contains(JsonArray a, String v) {
        for (JsonElement e : a) {
            if (e.getAsString().equals(v)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theDimensionAndRouterCarryTheZones() throws IOException {
        JsonObject source = json("/data/cosmicbreach/dimension/aetheria.json").getAsJsonObject("generator").getAsJsonObject("biome_source");
        for (String zone : ZONES) {
            assertEquals("cosmicbreach:" + zone, source.get(zone).getAsString());
        }
        JsonObject router = json("/data/cosmicbreach/worldgen/noise_settings/aetheria.json").getAsJsonObject("noise_router");
        assertEquals("cosmicbreach:deep_zone", router.getAsJsonObject("vegetation").get("type").getAsString());
    }

    @Test
    void zoneValuesSurviveTheClimateQuantizing() {
        for (int zone = 0; zone < DeepZones.COUNT; zone++) {
            long quantized = (long) (DeepZoneDensity.value(zone) * 10000.0F);
            assertEquals(zone, DeepZoneDensity.zoneOf(quantized / 10000.0));
        }
    }

    @Test
    void everyZoneBiomeListsRealFeaturesAndTheDeepsSound() throws IOException {
        for (String zone : ZONES) {
            JsonObject biome = json("/data/cosmicbreach/worldgen/biome/" + zone + ".json");
            assertEquals("cosmicbreach:music/deep", biome.getAsJsonObject("effects").getAsJsonObject("music").get("sound").getAsString());
            int n = 0;
            for (JsonElement step : biome.getAsJsonArray("features")) {
                for (JsonElement f : step.getAsJsonArray()) {
                    String id = f.getAsString().substring("cosmicbreach:".length());
                    assertTrue(exists("/data/cosmicbreach/worldgen/placed_feature/" + id + ".json"), zone + " lists " + id);
                    n++;
                }
            }
            assertTrue(n >= 4, zone + " has " + n + " features");
            for (JsonElement step : biome.getAsJsonArray("features")) {
                for (JsonElement f : step.getAsJsonArray()) {
                    String id = f.getAsString().substring("cosmicbreach:".length());
                    for (JsonElement m : json("/data/cosmicbreach/worldgen/placed_feature/" + id + ".json").getAsJsonArray("placement")) {
                        JsonObject mod = m.getAsJsonObject();
                        if (mod.has("max_steps")) {
                            int steps = mod.get("max_steps").getAsInt();
                            assertTrue(steps >= 1 && steps <= 32, id + ": an environment scan takes 1 to 32 steps, not " + steps);
                        }
                    }
                }
            }
            String all = biome.getAsJsonArray("features").toString();
            assertTrue(all.contains("ore_eclipsium") && all.contains("umbral_cap_patch"), zone + ": the Deep's ore and food");
            assertTrue(all.contains("neon_lichen_magenta") && all.contains("neon_lichen_teal"), zone + ": both lichens");
        }
        assertTrue(json("/data/cosmicbreach/worldgen/biome/lichen_gardens.json").toString().contains("giant_umbral_cap"));
        assertTrue(json("/data/cosmicbreach/worldgen/biome/lichen_gardens.json").toString().contains("lichen_carpet"));
        assertTrue(json("/data/cosmicbreach/worldgen/biome/hanging_wood.json").toString().contains("crystal_chandelier_dense"));
        assertTrue(json("/data/cosmicbreach/worldgen/biome/shattered_field.json").toString().contains("underside_ore"));
        assertTrue(json("/data/cosmicbreach/worldgen/biome/shattered_field.json").toString().contains("rift_glass_shards"));
    }

    @Test
    void theHollowStalkerSpawnsInEveryZoneAtTheZonesWeights() throws IOException {
        Map<String, Integer> weight = new HashMap<>();
        for (String name : List.of("hollow_stalker_spawns", "hollow_stalker_spawns_gardens", "hollow_stalker_spawns_shattered")) {
            JsonObject m = json("/data/cosmicbreach/neoforge/biome_modifier/" + name + ".json");
            int w = m.getAsJsonObject("spawners").get("weight").getAsInt();
            JsonElement biomes = m.get("biomes");
            if (biomes.isJsonArray()) {
                biomes.getAsJsonArray().forEach(b -> weight.put(b.getAsString(), w));
            } else {
                weight.put(biomes.getAsString(), w);
            }
        }
        assertEquals(20, weight.get("cosmicbreach:rift_abyss"));
        assertEquals(10, weight.get("cosmicbreach:lichen_gardens"));
        assertEquals(20, weight.get("cosmicbreach:hanging_wood"));
        assertEquals(12, weight.get("cosmicbreach:shattered_field"));
    }

    @Test
    void structuresStandInTheZonesThatSuitThem() throws IOException {
        JsonArray crypt = json("/data/cosmicbreach/tags/worldgen/biome/has_structure/hollow_crypt.json").getAsJsonArray("values");
        JsonArray centre = json("/data/cosmicbreach/worldgen/structure/breach_sanctum.json").getAsJsonArray("biomes");
        JsonArray arena = json("/data/cosmicbreach/worldgen/structure/silent_nave.json").getAsJsonArray("biomes");
        for (String zone : ZONES) {
            assertTrue(contains(crypt, "cosmicbreach:" + zone), "crypt in " + zone);
            assertTrue(contains(centre, "cosmicbreach:" + zone), "centre in " + zone);
        }
        assertTrue(contains(arena, "cosmicbreach:rift_abyss") && contains(arena, "cosmicbreach:hanging_wood")
                && contains(arena, "cosmicbreach:shattered_field"));
        assertFalse(contains(arena, "cosmicbreach:lichen_gardens"), "no void beside a Gardens landmass for the arena");
    }

    @Test
    void theZoneBlocksHaveTheirFiles() throws IOException {
        for (String block : List.of("giant_umbral_cap", "umbral_stem")) {
            assertTrue(exists("/assets/cosmicbreach/blockstates/" + block + ".json"), block);
            assertTrue(exists("/data/cosmicbreach/loot_table/blocks/" + block + ".json"), block);
            assertTrue(json("/assets/cosmicbreach_world/lang/en_us.json").has("block.cosmicbreach." + block), block);
        }
        for (String tex : List.of("giant_umbral_cap", "umbral_stem", "umbral_stem_top")) {
            assertTrue(exists("/assets/cosmicbreach/textures/block/" + tex + ".png"), tex);
        }
        JsonObject lang = json("/assets/cosmicbreach_world/lang/en_us.json");
        for (String zone : ZONES) {
            assertTrue(lang.has("biome.cosmicbreach." + zone), zone);
        }
    }

    @Test
    void zoneFogIsTheSpansUntintedAndEachOtherZoneLeansItsWay() throws IOException {
        float[] t = ZoneFog.target(ZoneFog.REFERENCE, new float[3]);
        assertEquals(1f, t[0], 1e-6);
        assertEquals(1f, t[1], 1e-6);
        assertEquals(1f, t[2], 1e-6);
        int abyss = json("/data/cosmicbreach/worldgen/biome/rift_abyss.json").getAsJsonObject("effects").get("fog_color").getAsInt();
        assertEquals(ZoneFog.REFERENCE, abyss);
        float[] gardens = ZoneFog.target(fog("lichen_gardens"), new float[3]);
        float[] hanging = ZoneFog.target(fog("hanging_wood"), new float[3]);
        float[] shattered = ZoneFog.target(fog("shattered_field"), new float[3]);
        assertTrue(gardens[0] > 1.2 && gardens[2] > 1.2, "the Gardens' fog lifts");
        assertTrue(hanging[1] > hanging[0] && hanging[0] < 1, "the Hanging Wood's leans teal and darker");
        assertTrue(shattered[0] > shattered[1] && shattered[0] > 1.2, "the Shattered Field's leans magenta");
    }

    private static int fog(String biome) throws IOException {
        return json("/data/cosmicbreach/worldgen/biome/" + biome + ".json").getAsJsonObject("effects").get("fog_color").getAsInt();
    }
}
