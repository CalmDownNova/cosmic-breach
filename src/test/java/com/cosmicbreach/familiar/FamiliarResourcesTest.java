package com.cosmicbreach.familiar;

import com.google.gson.JsonArray;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G10's files are all there: each familiar's model, animations (with every one the server triggers), texture and
 * glowmask; the egg's and the lantern's models and looks; the brazier's state, models, loot and Forge I recipe; the
 * statuses' icons; the sounds with files and translated subtitles; the names; the Star Eggs in the three vaults at 20,
 * 25 and 30%; the damage type; and no dashes in the words.
 */
class FamiliarResourcesTest {
    private static final String A = "/assets/cosmicbreach/";
    private static final String D = "/data/cosmicbreach/";
    private static final String[] SOUNDS = {"summon", "dismiss", "mode", "hatch", "egg_set", "hurt", "death", "wisp_strike", "scorch",
            "kindled", "hop", "slam", "taunt", "moth_strike", "refract", "refract_break", "cleanse"};

    private static InputStream open(String path) {
        return FamiliarResourcesTest.class.getResourceAsStream(path);
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

    private static JsonObject lang() throws IOException {
        return json("/assets/cosmicbreach_familiars/lang/en_us.json");
    }

    @Test
    void eachFamiliarHasItsModelAnimationsAndTextures() throws IOException {
        String[][] kinds = {{"emberwisp", "idle", "strike", "flare"}, {"gravikin", "idle", "strike", "hop", "taunt"},
                {"prism_moth", "fly", "strike", "glint"}};
        for (String[] k : kinds) {
            assertTrue(exists(A + "geo/entity/" + k[0] + ".geo.json"), k[0] + " model");
            assertTrue(exists(A + "textures/entity/" + k[0] + ".png"), k[0] + " texture");
            assertTrue(exists(A + "textures/entity/" + k[0] + "_glowmask.png"), k[0] + " glowmask");
            JsonObject anims = json(A + "animations/entity/" + k[0] + ".animation.json").getAsJsonObject("animations");
            for (int i = 1; i < k.length; i++) {
                assertTrue(anims.has(k[i]), k[0] + " animation " + k[i]);
            }
        }
        JsonObject wisp = json(A + "geo/entity/emberwisp.geo.json");
        String bones = wisp.toString();
        assertTrue(bones.contains("\"rays_outer\"") && bones.contains("\"rays_inner\""), "the wisp's crowns the game turns");
        assertTrue(json(A + "geo/entity/gravikin.geo.json").toString().contains("\"orbit\""), "the Gravikin's orbiting pebbles");
    }

    @Test
    void theEggAndTheLanternLookTheirPart() throws IOException {
        JsonArray egg = json(A + "models/item/star_egg.json").getAsJsonArray("overrides");
        assertEquals(3, egg.size());
        for (String k : new String[] {"emberwisp", "gravikin", "prism_moth"}) {
            assertTrue(exists(A + "textures/item/star_egg_" + k + ".png"));
            assertTrue(exists(A + "textures/item/familiar_lantern_" + k + ".png"));
        }
        JsonArray lantern = json(A + "models/item/familiar_lantern.json").getAsJsonArray("overrides");
        assertEquals(5, lantern.size(), "three kinds, out and dark");
        JsonObject last = lantern.get(4).getAsJsonObject();
        assertEquals(1.0, last.getAsJsonObject("predicate").get("cosmicbreach:lantern").getAsDouble(), 1e-9, "dark wins, last");
        assertEquals("cosmicbreach:item/familiar_lantern_dark", last.get("model").getAsString());
        for (String k : new String[] {"refract", "kindled", "gravity_drag"}) {
            assertTrue(exists(A + "textures/mob_effect/" + k + ".png"), k + " icon");
        }
    }

    @Test
    void theBrazierIsAForgeOneBlockThatDropsItself() throws IOException {
        JsonObject state = json(A + "blockstates/brazier_of_solenne.json").getAsJsonObject("variants");
        assertTrue(state.has("lit=false") && state.has("lit=true"));
        assertTrue(exists(A + "models/block/brazier_of_solenne.json"));
        assertTrue(json(A + "models/block/brazier_of_solenne_lit.json").toString().contains("block_light"), "lit embers glow");
        assertTrue(exists(A + "models/item/brazier_of_solenne.json"));
        assertTrue(json(D + "loot_table/blocks/brazier_of_solenne.json").toString().contains("cosmicbreach:brazier_of_solenne"));
        JsonObject recipe = json(D + "recipe/forge/brazier_of_solenne.json");
        assertEquals("cosmicbreach:astral_forge", recipe.get("type").getAsString());
        assertEquals(1, recipe.get("tier").getAsInt(), "Forge I");
        assertEquals("cosmicbreach:brazier_of_solenne", recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void starEggsAreInTheThreeVaultsAtTwentyTwentyFiveAndThirtyPercent() throws IOException {
        Object[][] vaults = {{"reliquary", 0.20, "emberwisp"}, {"observatory", 0.25, "gravikin"}, {"crypt", 0.30, "prism_moth"}};
        for (Object[] v : vaults) {
            JsonArray pools = json(D + "loot_table/vaults/" + v[0] + ".json").getAsJsonArray("pools");
            JsonObject egg = null;
            for (JsonElement p : pools) {
                if (p.toString().contains("cosmicbreach:star_egg")) {
                    egg = p.getAsJsonObject();
                }
            }
            assertNotNull(egg, v[0] + " has a Star Egg pool");
            double chance = egg.getAsJsonArray("conditions").get(0).getAsJsonObject().get("chance").getAsDouble();
            assertEquals((double) v[1], chance, 1e-9, v[0] + " chance");
            String kind = egg.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                    .getAsJsonObject("components").get("cosmicbreach:star_egg").getAsString();
            assertEquals(v[2], kind, v[0] + " egg's kind");
        }
    }

    @Test
    void everySoundHasAFileAndATranslatedSubtitle() throws IOException {
        JsonObject sounds = json(A + "sounds.json");
        JsonObject lang = lang();
        for (String s : SOUNDS) {
            JsonObject e = sounds.getAsJsonObject("familiar/" + s);
            assertNotNull(e, "sounds.json has familiar/" + s);
            String key = e.get("subtitle").getAsString();
            assertTrue(lang.has(key), "subtitle " + key);
            for (JsonElement f : e.getAsJsonArray("sounds")) {
                String file = f.getAsString().replace("cosmicbreach:", "");
                assertTrue(exists(A + "sounds/" + file + ".ogg"), "file of " + file);
            }
        }
    }

    @Test
    void theNamesAreThereWithoutDashes() throws IOException {
        JsonObject lang = lang();
        for (String k : new String[] {"entity.cosmicbreach.emberwisp", "entity.cosmicbreach.gravikin", "entity.cosmicbreach.prism_moth",
                "item.cosmicbreach.star_egg", "item.cosmicbreach.familiar_lantern", "block.cosmicbreach.brazier_of_solenne",
                "effect.cosmicbreach.refract", "effect.cosmicbreach.kindled", "effect.cosmicbreach.gravity_drag",
                "key.cosmicbreach.familiar", "cosmicbreach.familiar.mode.attack", "cosmicbreach.familiar.mode.guard",
                "cosmicbreach.familiar.mode.passive", "death.attack.cosmicbreach.familiar", "death.attack.cosmicbreach.familiar.player"}) {
            assertTrue(lang.has(k), "name " + k);
        }
        List<String> dashed = new ArrayList<>();
        for (var e : lang.entrySet()) {
            String v = e.getValue().getAsString();
            if (v.indexOf(0x2014) >= 0 || v.indexOf(0x2013) >= 0) {
                dashed.add(e.getKey());
            }
        }
        assertTrue(dashed.isEmpty(), "dashes in " + dashed);
        assertTrue(exists(D + "damage_type/familiar.json"));
        String immune = json(D + "tags/entity_type/solar_immune.json").toString();
        assertTrue(immune.contains("cosmicbreach:emberwisp") && immune.contains("cosmicbreach:gravikin") && immune.contains("cosmicbreach:prism_moth"));
        assertFalse(lang.toString().isEmpty());
    }
}
