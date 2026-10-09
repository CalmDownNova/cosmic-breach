package com.cosmicbreach.guardian.unsung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * G8's files are all there: the lair blocks' states and names, the Choir Pendant's model, every sound in sounds.json
 * with a translated subtitle (the music's 27 blocks preloaded, so they start in step), the damage types and death
 * messages, the advancement "Unsung" and the Sanctum attunement, the Nave's structure and placement, the masks' models,
 * animations and textures, and no dashes in the words.
 */
class UnsungResourcesTest {
    private static InputStream open(String path) {
        return UnsungResourcesTest.class.getResourceAsStream(path);
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

    private static final String[] BLOCKS = {"hymnal_altar", "lichen_window", "silence_circle"};

    @Test
    void blocksItemsAndNames() throws IOException {
        JsonObject lang = json("/assets/cosmicbreach_unsung/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (String id : BLOCKS) {
            if (!exists("/assets/cosmicbreach/blockstates/" + id + ".json")) {
                missing.add("blockstate " + id);
            }
            if (!lang.has("block.cosmicbreach." + id)) {
                missing.add("name of block " + id);
            }
        }
        if (!exists("/assets/cosmicbreach/models/item/choir_pendant.json") || !lang.has("item.cosmicbreach.choir_pendant")) {
            missing.add("the Choir Pendant's model or name");
        }
        for (String e : new String[] {"unsung", "unsung_mask", "song_note"}) {
            if (!lang.has("entity.cosmicbreach." + e)) {
                missing.add("name of " + e);
            }
        }
        for (String v : new String[] {"alto", "tenor", "bass"}) {
            for (String f : new String[] {"/assets/cosmicbreach/geo/entity/unsung_" + v + ".geo.json",
                    "/assets/cosmicbreach/animations/entity/unsung_" + v + ".animation.json",
                    "/assets/cosmicbreach/textures/entity/unsung_" + v + ".png", "/assets/cosmicbreach/textures/entity/unsung_" + v + "_glowmask.png",
                    "/assets/cosmicbreach/textures/entity/unsung_" + v + "_broken.png",
                    "/assets/cosmicbreach/textures/entity/unsung_" + v + "_broken_glowmask.png"}) {
                if (!exists(f)) {
                    missing.add(f);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void everySoundHasItsFilesAndATranslatedSubtitle() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject lang = json("/assets/cosmicbreach_unsung/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        int music = 0;
        for (String key : sounds.keySet()) {
            if (!key.startsWith("unsung/") && !key.startsWith("music/unsung/")) {
                continue;
            }
            JsonObject e = sounds.getAsJsonObject(key);
            if (!lang.has(e.get("subtitle").getAsString())) {
                missing.add("subtitle of " + key);
            }
            for (JsonElement s : e.getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                if (!exists("/assets/cosmicbreach/sounds/" + name.substring("cosmicbreach:".length()) + ".ogg")) {
                    missing.add("file " + name);
                }
                if (key.startsWith("music/")) {
                    music++;
                    assertTrue(s.isJsonObject() && s.getAsJsonObject().get("preload").getAsBoolean(), key + " is preloaded");
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
        assertEquals(27, music, "3 voices x (4 chords x sung and hummed + the rise)");
        assertEquals(27, UnsungRegistry.musicNames().size());
    }

    @Test
    void damageAdvancementsAndTheNave() throws IOException {
        JsonObject lang = json("/assets/cosmicbreach_unsung/lang/en_us.json");
        for (String d : new String[] {"unsung_note", "unsung_harmonize"}) {
            JsonObject t = json("/data/cosmicbreach/damage_type/" + d + ".json");
            assertTrue(lang.has("death.attack." + t.get("message_id").getAsString()), "death message of " + d);
        }
        assertTrue(json("/data/cosmicbreach/advancement/guardian/unsung.json").has("display"), "the visible advancement");
        assertTrue(lang.has("advancements.cosmicbreach.guardian.unsung.title"));
        assertTrue(exists("/data/cosmicbreach/advancement/attunement/sanctum.json"), "the Sanctum attunement");
        JsonObject structure = json("/data/cosmicbreach/worldgen/structure/silent_nave.json");
        assertEquals("cosmicbreach:silent_nave", structure.get("type").getAsString());
        assertEquals("cosmicbreach:rift_abyss", structure.getAsJsonArray("biomes").get(0).getAsString());
        JsonObject placement = json("/data/cosmicbreach/worldgen/structure_set/silent_nave.json").getAsJsonObject("placement");
        assertEquals(30, placement.get("spacing").getAsInt()); // layer 3 zones: fewer starts find a site, so more starts
        assertEquals(11, placement.get("separation").getAsInt());
        for (String v : lang.keySet()) {
            String text = lang.get(v).getAsString();
            assertTrue(text.indexOf('—') < 0 && text.indexOf('–') < 0, "no dashes: " + v);
        }
    }
}
