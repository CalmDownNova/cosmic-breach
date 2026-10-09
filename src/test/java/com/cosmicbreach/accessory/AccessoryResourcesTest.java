package com.cosmicbreach.accessory;

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
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G6a's files are all there: the Curios slots (two rings, a necklace, a charm on every player), each accessory in its
 * slot's tag with a model, an icon, a name and an effect line, the Compass's 16 needle frames, the sounds with their
 * files and translated subtitles, the damage types' death messages, the sources in the loot tables, and no dashes in
 * the words.
 */
class AccessoryResourcesTest {
    private static final String[] SOUNDS = {"equip", "comet_chain", "leech_heal", "gravity_well", "nova", "halo_block", "halo_regrow",
            "halo_cut", "black_hole", "vesper_slow"};

    private static InputStream open(String path) {
        return AccessoryResourcesTest.class.getResourceAsStream(path);
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

    private static Set<String> strings(JsonArray array) {
        Set<String> out = new TreeSet<>();
        for (JsonElement e : array) {
            out.add(e.getAsString());
        }
        return out;
    }

    @Test
    void everyPlayerHasTwoRingsANecklaceACharmAndABackSlot() throws IOException {
        assertEquals(2, json("/data/cosmicbreach/curios/slots/ring.json").get("size").getAsInt());
        JsonObject player = json("/data/cosmicbreach/curios/entities/player.json");
        assertEquals(Set.of("player"), strings(player.getAsJsonArray("entities")));
        assertEquals(Set.of("ring", "necklace", "charm", "back"), strings(player.getAsJsonArray("slots")));
    }

    @Test
    void eachAccessoryIsTaggedForItsSlotAndHasItsArtAndWords() throws IOException {
        JsonObject lang = json("/assets/cosmicbreach_curios/lang/en_us.json");
        List<String> missing = new ArrayList<>();
        for (Accessory a : Accessory.values()) {
            Set<String> tag = strings(json("/data/curios/tags/item/" + a.slot().id() + ".json").getAsJsonArray("values"));
            if (!tag.contains(a.id().toString())) {
                missing.add(a.path() + " in curios:" + a.slot().id());
            }
            if (!exists("/assets/cosmicbreach/models/item/" + a.path() + ".json") || !exists("/assets/cosmicbreach/textures/item/" + a.path() + ".png")) {
                missing.add("model or icon of " + a.path());
            }
            if (!lang.has("item.cosmicbreach." + a.path() + ".effect")) {
                missing.add("effect line of " + a.path());
            }
        }
        for (String id : new String[] {"twin_comet_band", "leechstar_signet", "perihelion_loop", "sunshard_compass"}) {
            if (!lang.has("item.cosmicbreach." + id)) {
                missing.add("name of " + id);
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void theCompassHasSixteenNeedleFrames() throws IOException {
        JsonArray overrides = json("/assets/cosmicbreach/models/item/sunshard_compass.json").getAsJsonArray("overrides");
        assertEquals(17, overrides.size(), "16 frames and the wrap back to the first");
        double last = -1;
        for (JsonElement e : overrides) {
            double at = e.getAsJsonObject().getAsJsonObject("predicate").get("cosmicbreach:needle").getAsDouble();
            assertTrue(at > last, "thresholds rise");
            last = at;
        }
        for (int k = 0; k < 16; k++) {
            String stem = String.format("sunshard_compass_%02d", k);
            assertTrue(exists("/assets/cosmicbreach/textures/item/" + stem + ".png"), stem);
            assertTrue(exists("/assets/cosmicbreach/models/item/" + stem + ".json"), stem);
        }
        assertTrue(exists("/assets/cosmicbreach/textures/fx/halo_shard.png"));
        assertTrue(exists("/assets/cosmicbreach/textures/mob_effect/vesper_slow.png"));
    }

    @Test
    void soundsHaveFilesAndTranslatedSubtitles() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject lang = json("/assets/cosmicbreach_curios/lang/en_us.json");
        for (String name : SOUNDS) {
            JsonObject event = sounds.getAsJsonObject("accessory/" + name);
            assertTrue(event != null, "sounds.json has accessory/" + name);
            String subtitle = event.get("subtitle").getAsString();
            assertTrue(lang.has(subtitle), subtitle);
            for (JsonElement file : event.getAsJsonArray("sounds")) {
                String path = file.getAsString().replace("cosmicbreach:", "");
                assertTrue(exists("/assets/cosmicbreach/sounds/" + path + ".ogg"), path);
            }
        }
        assertTrue(lang.has("effect.cosmicbreach.vesper_slow"));
        for (String type : new String[] {"dying_star", "halo_shard"}) {
            assertTrue(exists("/data/cosmicbreach/damage_type/" + type + ".json"), type);
            assertTrue(lang.has("death.attack.cosmicbreach." + type) && lang.has("death.attack.cosmicbreach." + type + ".player"), type);
        }
    }

    @Test
    void theSourcesAreWiredIntoTheLoot() throws IOException {
        String t1 = json("/data/cosmicbreach/loot_table/vaults/accessories_t1.json").toString();
        assertTrue(t1.contains("cosmicbreach:perihelion_loop") && t1.contains("cosmicbreach:sunshard_compass"), "the Reliquary's");
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/accessories_t2.json").toString().contains("cosmicbreach:twin_comet_band"),
                "the Observatory's");
        String charms = json("/data/cosmicbreach/loot_table/vaults/accessories_charms.json").toString();
        assertTrue(charms.contains("cosmicbreach:leechstar_signet") && charms.contains("cosmicbreach:sunshard_compass"), "the Crypt's");
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/reliquary.json").toString().contains("cosmicbreach:vaults/accessories_t1"));
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/observatory.json").toString().contains("cosmicbreach:vaults/accessories_t2"));
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/crypt.json").toString().contains("cosmicbreach:vaults/accessories_charms"));
        String knight = json("/data/cosmicbreach/loot_table/entities/gyre_knight.json").toString();
        assertTrue(knight.contains("cosmicbreach:gravity_loop") && knight.contains("0.08"), "the Gyre Knight's 8%");
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/sanctum_lens.json").toString().contains("cosmicbreach:event_horizon_lens"));
        assertTrue(json("/data/cosmicbreach/loot_table/vaults/sanctum_choir.json").toString().contains("cosmicbreach:hourglass_of_vesper"));
    }

    @Test
    void noDashesInTheWords() throws IOException {
        String text = json("/assets/cosmicbreach_curios/lang/en_us.json").toString();
        assertTrue(text.indexOf(0x2014) < 0 && text.indexOf(0x2013) < 0, "no em or en dashes");
    }
}
