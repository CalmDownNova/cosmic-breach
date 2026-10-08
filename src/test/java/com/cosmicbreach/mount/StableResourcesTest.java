package com.cosmicbreach.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Stable Crystal's files: its Forge I recipe from level 1 materials, models, textures, words; the mounts' tag. */
class StableResourcesTest {
    private static final String A = "/assets/cosmicbreach/";
    private static final String D = "/data/cosmicbreach/";

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = StableResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean exists(String path) {
        return StableResourcesTest.class.getResource(path) != null;
    }

    @Test
    void itIsForgedAtTierOneFromLevelOneMaterials() throws IOException {
        JsonObject r = json(D + "recipe/forge/stable_crystal.json");
        assertEquals("cosmicbreach:astral_forge", r.get("type").getAsString());
        assertEquals("mount_gear", r.get("category").getAsString(), "it sits in the Forge's Mount Gear tab");
        assertEquals(1, r.get("tier").getAsInt());
        assertEquals("cosmicbreach:stable_crystal", r.getAsJsonObject("result").get("id").getAsString());
        Set<String> inputs = new HashSet<>();
        for (var e : r.getAsJsonArray("ingredients")) {
            inputs.add(e.getAsJsonObject().get("item").getAsString());
        }
        assertEquals(Set.of("cosmicbreach:spire_quartz", "cosmicbreach:starsteel_ingot", "cosmicbreach:starbloom"), inputs);
    }

    @Test
    void itHasItsModelsTexturesAndWords() throws IOException {
        JsonArray overrides = json(A + "models/item/stable_crystal.json").getAsJsonArray("overrides");
        assertEquals("cosmicbreach:item/stable_crystal_full", overrides.get(0).getAsJsonObject().get("model").getAsString());
        assertTrue(exists(A + "models/item/stable_crystal_full.json"));
        assertTrue(exists(A + "textures/item/stable_crystal.png") && exists(A + "textures/item/stable_crystal_full.png"));
        JsonObject lang = json("/assets/cosmicbreach_stable/lang/en_us.json");
        for (String k : new String[] {"item.cosmicbreach.stable_crystal", "item.cosmicbreach.stable_crystal.empty",
                "item.cosmicbreach.stable_crystal.holds", "item.cosmicbreach.stable_crystal.release", "cosmicbreach.stable.full",
                "cosmicbreach.stable.not_yours", "cosmicbreach.stable.no_room", "cosmicbreach.stable.no_ground", "cosmicbreach.stable.unknown",
                "cosmicbreach.stable.already_out", "cosmicbreach.stable.came_back"}) {
            assertTrue(lang.has(k), "words for " + k);
        }
        for (var e : lang.entrySet()) {
            String v = e.getValue().getAsString();
            assertTrue(v.indexOf(0x2014) < 0 && v.indexOf(0x2013) < 0, "a dash in " + e.getKey());
        }
    }

    private static String codex(String page) throws IOException {
        try (InputStream in = StableResourcesTest.class.getResourceAsStream(A + "guides/cosmicbreach/codex/" + page + ".md")) {
            assertNotNull(in, "missing codex page " + page);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theCodexPointsPlayersToTheCrystalAndToHowParkingWorks() throws IOException {
        // a player who never leaves the game finds the crystal in the forge's list; the pages of the two mounts and the
        // forge's own page say what it is for and how a parked mount is kept (CodexPagesTest keeps the wording plain)
        for (String page : new String[] {"lumen_stag", "drift_manta", "forge"}) {
            assertTrue(codex(page).contains("Stable Crystal"), page + " names the crystal");
        }
        for (String page : new String[] {"lumen_stag", "drift_manta"}) {
            String text = codex(page).replaceAll("\\s+", " "); // the paragraphs wrap
            assertTrue(text.contains("Keeping it safe"), page + " has the parking paragraph");
            assertTrue(text.contains("stow") && text.contains("set it down beside you"), page + " says how to stow and release");
            assertTrue(text.contains("climbs back"), page + " says a fallen mount climbs back");
            assertTrue(text.contains("any blow from a player who may not hurt you") && text.contains("where PvP is off"),
                    page + " says whose blows a mount shrugs off, as the server's PvP setting decides them");
            assertTrue(text.contains("does not burn or vanish") && text.contains("waits for you if you are dead or away")
                    && text.contains("in your pack when you respawn or log in"), page + " says what a crystal that holds a mount does when it falls out of the world");
        }
        assertFalse(codex("lumen_stag").contains("stays where you leave it"), "a tamed stag still strolls about");
        assertTrue(codex("drift_manta").contains("holds the Chime") || codex("drift_manta").contains("Hold the Chime"),
                "the manta's page says a wild one comes within reach for a player holding the Chime");
    }

    @Test
    void bothMountsAreCelestialMounts() throws IOException {
        String tag = json(D + "tags/entity_type/celestial_mounts.json").toString();
        assertTrue(tag.contains("cosmicbreach:lumen_stag") && tag.contains("cosmicbreach:drift_manta"));
    }
}
