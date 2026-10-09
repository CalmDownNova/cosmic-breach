package com.cosmicbreach.jelly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drift jelly's files are all there and agree with its rules: the model (a bell 3 blocks wide, 6 to 8 tendrils of 3 to
 * 4 bones hanging 4 to 6 blocks, two frills, a core), its animations, textures and icons; the names and the three
 * tooltip lines of each gel; the spawn modifiers and their biome tags; the loot, the cooking recipes; the sting's damage
 * type without knockback; the Codex page; and no dashes in the words.
 */
class JellyResourcesTest {
    private static final String A = "/assets/cosmicbreach/";
    private static final String D = "/data/cosmicbreach/";
    private static final String G = "/assets/cosmicbreach/";

    private static InputStream open(String path) {
        return JellyResourcesTest.class.getResourceAsStream(path);
    }

    private static boolean exists(String path) throws IOException {
        try (InputStream in = open(path)) {
            return in != null;
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = open(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String text(String path) throws IOException {
        try (InputStream in = open(path)) {
            assertNotNull(in, "missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static BufferedImage png(String path) throws IOException {
        try (InputStream in = open(path)) {
            assertNotNull(in, "missing " + path);
            return ImageIO.read(in);
        }
    }

    private static JsonObject lang() throws IOException {
        return json("/assets/cosmicbreach_jelly/lang/en_us.json");
    }

    private static JsonArray bones() throws IOException {
        return json(A + "geo/entity/drift_jelly.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
    }

    // ------------------------------------------------------------------ the model

    @Test
    void theModelHasEightTendrilsOfFourBonesHangingFourToFiveAndAHalfBlocks() throws IOException {
        List<String> names = new ArrayList<>();
        double[] lowest = new double[16];
        int[] segments = new int[16];
        int tendrils = 0;
        for (JsonElement e : bones()) {
            JsonObject b = e.getAsJsonObject();
            String name = b.get("name").getAsString();
            names.add(name);
            if (name.matches("t\\d+_\\d+")) {
                int t = Integer.parseInt(name.substring(1, name.indexOf('_')));
                segments[t]++;
                tendrils = Math.max(tendrils, t + 1);
                for (JsonElement c : b.getAsJsonArray("cubes")) {
                    JsonObject cube = c.getAsJsonObject();
                    lowest[t] = Math.min(lowest[t], cube.getAsJsonArray("origin").get(1).getAsDouble());
                }
            }
        }
        assertTrue(tendrils >= 6 && tendrils <= 8, "six to eight tendrils: " + tendrils);
        double shortest = Double.MAX_VALUE;
        for (int t = 0; t < tendrils; t++) {
            assertTrue(segments[t] >= 3 && segments[t] <= 4, "tendril " + t + " has " + segments[t] + " bones");
            double hang = -lowest[t] / 16.0;       // blocks below the flaps' bottom (y 0)
            assertTrue(hang >= 4.0 && hang <= 6.0, "tendril " + t + " hangs " + hang + " blocks");
            shortest = Math.min(shortest, hang);
        }
        assertTrue(shortest >= JellyRules.TENDRIL_HANG, "the sting reaches no further than the shortest tendril: " + shortest);
        assertTrue(names.contains("frill_0_0") && names.contains("frill_1_0"), "two inner frills");
        assertFalse(names.contains("frill_2_0"));
        assertTrue(names.contains("core") && names.contains("glass") && names.contains("bell"));
        assertEquals("glass", names.get(names.size() - 1), "the glass is drawn last, so it blends over what it holds");
    }

    @Test
    void theBellIsThreeBlocksWideAndItsHitboxMatches() throws IOException {
        double min = 1e9;
        double max = -1e9;
        double top = 0;
        for (JsonElement e : bones()) {
            JsonObject b = e.getAsJsonObject();
            String name = b.get("name").getAsString();
            if (!(name.startsWith("rim_") || name.equals("collar") || name.equals("glass") || name.equals("bell"))) {
                continue;
            }
            for (JsonElement c : b.getAsJsonArray("cubes")) {
                JsonObject cube = c.getAsJsonObject();
                double x = cube.getAsJsonArray("origin").get(0).getAsDouble();
                double w = cube.getAsJsonArray("size").get(0).getAsDouble();
                min = Math.min(min, x);
                max = Math.max(max, x + w);
                top = Math.max(top, cube.getAsJsonArray("origin").get(1).getAsDouble() + cube.getAsJsonArray("size").get(1).getAsDouble());
            }
        }
        assertEquals(48.0, max - min, 2.0, "about three blocks across at the rim");
        assertEquals(JellyRules.HEIGHT * 16.0, top, 1.5, "the bell's top is the hitbox's");
        assertEquals(JellyRules.WIDTH * 16.0, max - min, 2.0);
    }

    @Test
    void everyAnimationTheEntityPlaysExistsAndSwaysTheTendrils() throws IOException {
        JsonObject anims = json(A + "animations/entity/drift_jelly.animation.json").getAsJsonObject("animations");
        for (String name : new String[] {"idle", "hurt", "squish", "death"}) {
            assertTrue(anims.has(name), name);
        }
        assertTrue(anims.getAsJsonObject("idle").get("loop").getAsBoolean());
        JsonObject idle = anims.getAsJsonObject("idle").getAsJsonObject("bones");
        for (int t = 0; t < 8; t++) {
            for (int j = 0; j < 4; j++) {
                assertTrue(idle.has("t" + t + "_" + j), "idle sways t" + t + "_" + j);
            }
        }
        assertTrue(idle.has("bell") && idle.get("bell").getAsJsonObject().has("scale"), "the bell pulses");
        JsonObject death = anims.getAsJsonObject("death");
        assertEquals("hold_on_last_frame", death.get("loop").getAsString());
        assertTrue(death.get("animation_length").getAsDouble() <= 1.3, "a death is over in about a second");
    }

    @Test
    void theTexturesAreTheSizeTheModelSaysAndTheIconsAreRight() throws IOException {
        JsonObject desc = json(A + "geo/entity/drift_jelly.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonObject("description");
        int w = desc.get("texture_width").getAsInt();
        int h = desc.get("texture_height").getAsInt();
        BufferedImage tex = png(A + "textures/entity/drift_jelly.png");
        BufferedImage glow = png(A + "textures/entity/drift_jelly_glowmask.png");
        assertEquals(w, tex.getWidth());
        assertEquals(h, tex.getHeight());
        assertEquals(w, glow.getWidth());
        assertEquals(h, glow.getHeight());
        assertEquals(18, png(A + "textures/mob_effect/skim.png").getWidth(), "the effect icon is 18 by 18");
        assertEquals(18, png(A + "textures/mob_effect/skim.png").getHeight());
        for (String item : new String[] {"drift_gel", "candied_gel"}) {
            BufferedImage icon = png(A + "textures/item/" + item + ".png");
            assertEquals(16, icon.getWidth(), item);
            assertEquals(16, icon.getHeight(), item);
        }
        // the glowmask lights the core and markings only: some texels lit, most dark
        int lit = 0;
        int opaque = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((glow.getRGB(x, y) >>> 24) > 0) {
                    lit++;
                }
                if ((tex.getRGB(x, y) >>> 24) > 0) {
                    opaque++;
                }
            }
        }
        assertTrue(lit > 200 && lit < opaque / 2, "lit " + lit + " of " + opaque);
    }

    // ------------------------------------------------------------------ names, tooltips

    @Test
    void everyNewThingHasANameAndEachGelItsThreeTooltipLines() throws IOException {
        JsonObject lang = lang();
        for (String key : new String[] {"entity.cosmicbreach.drift_jelly", "item.cosmicbreach.drift_gel", "item.cosmicbreach.candied_gel",
                "item.cosmicbreach.drift_jelly_spawn_egg", "effect.cosmicbreach.skim", "death.attack.cosmicbreach.drift_sting"}) {
            assertTrue(lang.has(key), key);
        }
        for (String gel : new String[] {"drift_gel", "candied_gel"}) {
            for (String part : new String[] {"tooltip", "tooltip.source", "tooltip.use"}) {
                String key = "item.cosmicbreach." + gel + "." + part;
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
            }
        }
        assertTrue(lang.get("item.cosmicbreach.drift_gel.tooltip.source").getAsString().contains("layer 3"));
        // what the game shows: the mod's tooltip system (what, comes from, used in), with the Skim named on the egg
        JsonObject shown = json("/assets/cosmicbreach_tooltips/lang/en_us.json");
        for (String key : new String[] {com.cosmicbreach.item.ItemTooltips.what("drift_gel"), com.cosmicbreach.item.ItemTooltips.from("drift_gel"),
                com.cosmicbreach.item.ItemTooltips.used("drift_gel"), com.cosmicbreach.item.ItemTooltips.what("candied_gel"),
                com.cosmicbreach.item.ItemTooltips.from("candied_gel")}) {
            assertTrue(shown.has(key) && !shown.get(key).getAsString().isBlank(), key);
        }
        // candied gel is only eaten, so it has no used-in line
        assertFalse(shown.has(com.cosmicbreach.item.ItemTooltips.used("candied_gel")));
        assertTrue(shown.get(com.cosmicbreach.item.ItemTooltips.from("drift_gel")).getAsString().contains("layer 3"));
        assertTrue(shown.get(com.cosmicbreach.item.ItemTooltips.what("drift_jelly_spawn_egg")).getAsString().contains("Skim"));
    }

    @Test
    void noDashesInTheWords() throws IOException {
        for (String s : new String[] {text("/assets/cosmicbreach_jelly/lang/en_us.json"), text(G + "guides/cosmicbreach/codex/drift_jelly.md")}) {
            assertFalse(s.contains("—") || s.contains("–"), "an em or en dash");
        }
    }

    // ------------------------------------------------------------------ spawns, drops, recipes

    @Test
    void theSpawnModifiersListTheTagsAndTheTagsListTheBiomes() throws IOException {
        JsonObject spawns = json(D + "neoforge/biome_modifier/drift_jelly_spawns.json");
        assertEquals("neoforge:add_spawns", spawns.get("type").getAsString());
        assertEquals("#cosmicbreach:drift_jelly_spawns", spawns.get("biomes").getAsString());
        assertEquals("cosmicbreach:drift_jelly", spawns.getAsJsonObject("spawners").get("type").getAsString());
        JsonObject dense = json(D + "neoforge/biome_modifier/drift_jelly_dense.json");
        assertEquals("#cosmicbreach:drift_jelly_dense", dense.get("biomes").getAsString());
        // every biome of layer 3 (the Spans and the three zones); dense in the Hanging Wood and the Shattered Field
        java.util.List<String> all = new java.util.ArrayList<>();
        json(D + "tags/worldgen/biome/drift_jelly_spawns.json").getAsJsonArray("values").forEach(v -> all.add(v.getAsString()));
        assertEquals(java.util.Set.of("cosmicbreach:rift_abyss", "cosmicbreach:lichen_gardens", "cosmicbreach:hanging_wood",
                "cosmicbreach:shattered_field"), new java.util.HashSet<>(all));
        java.util.List<String> denseIds = new java.util.ArrayList<>();
        json(D + "tags/worldgen/biome/drift_jelly_dense.json").getAsJsonArray("values").forEach(v -> denseIds.add(v.getAsString()));
        assertEquals(java.util.Set.of("cosmicbreach:hanging_wood", "cosmicbreach:shattered_field"), new java.util.HashSet<>(denseIds));
        for (String id : all) {
            assertTrue(exists(D + "worldgen/biome/" + id.substring("cosmicbreach:".length()) + ".json"), id + " has no biome file");
        }
    }

    @Test
    void aJellyDropsDriftGel() throws IOException {
        String loot = text(D + "loot_table/entities/drift_jelly.json");
        assertTrue(loot.contains("cosmicbreach:drift_gel"));
        assertFalse(loot.contains("candied"), "candied gel is cooked, never dropped");
    }

    @Test
    void driftGelCooksOnAFurnaceASmokerAndACampfire() throws IOException {
        JsonObject furnace = json(D + "recipe/candied_gel.json");
        assertEquals("minecraft:smelting", furnace.get("type").getAsString());
        assertEquals(200, furnace.get("cookingtime").getAsInt());
        JsonObject smoker = json(D + "recipe/candied_gel_from_smoking.json");
        assertEquals("minecraft:smoking", smoker.get("type").getAsString());
        assertEquals(100, smoker.get("cookingtime").getAsInt());
        JsonObject campfire = json(D + "recipe/candied_gel_from_campfire_cooking.json");
        assertEquals("minecraft:campfire_cooking", campfire.get("type").getAsString());
        assertEquals(600, campfire.get("cookingtime").getAsInt());
        for (JsonObject r : new JsonObject[] {furnace, smoker, campfire}) {
            assertEquals("cosmicbreach:drift_gel", r.getAsJsonObject("ingredient").get("item").getAsString());
            assertEquals("cosmicbreach:candied_gel", r.getAsJsonObject("result").get("id").getAsString());
        }
    }

    @Test
    void theStingHasNoKnockback() throws IOException {
        assertTrue(exists(D + "damage_type/drift_sting.json"));
        JsonArray values = json("/data/minecraft/tags/damage_type/no_knockback.json").getAsJsonArray("values");
        boolean found = false;
        for (JsonElement v : values) {
            found |= v.getAsString().equals("cosmicbreach:drift_sting");
        }
        assertTrue(found, "cosmicbreach:drift_sting is in minecraft:no_knockback");
    }

    // ------------------------------------------------------------------ the Codex

    @Test
    void theCodexPageSaysWhatTheJellyDoesInTheGamesNumbers() throws IOException {
        String page = text(G + "guides/cosmicbreach/codex/drift_jelly.md").replace("\r\n", "\n");
        assertTrue(page.startsWith("---\nnavigation:\n  title: The Drift Jelly\n  parent: deep.md\n"), "it hangs under the Deep");
        assertTrue(page.contains("unlock: deep"));
        assertTrue(page.contains("fifteen to twenty five"), "a bloom is 15 to 25");
        assertEquals(15, JellyRules.bloomCount(0.0));
        assertEquals(25, JellyRules.bloomCount(0.99999));
        assertTrue(page.contains("30 percent"), "Skim is 30 percent");
        assertTrue(page.contains("campfire") && page.contains("furnace") && page.contains("smoker"));
        assertTrue(page.contains("Candied Gel") && page.contains("Drift Gel"));
        assertTrue(page.contains("night vision"));
    }
}
