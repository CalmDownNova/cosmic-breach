package com.cosmicbreach.guardian.heliarch;

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
 * G9a's files are all there: the Heliarch's model, animations and textures, its blocks' states and names, every sound
 * in sounds.json with its file and a translated subtitle (the voice's subtitles are its words, each line as long as its
 * take), the damage types and death messages, the advancement "Breach Sealed", the entity tags, and no dashes in the
 * words.
 */
class HeliarchResourcesTest {
    private static final String LANG = "/assets/cosmicbreach_heliarch/lang/en_us.json";

    private static InputStream open(String path) {
        return HeliarchResourcesTest.class.getResourceAsStream(path);
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

    @Test
    void theModelBlocksAndNames() throws IOException {
        JsonObject lang = json(LANG);
        List<String> missing = new ArrayList<>();
        for (String f : new String[] {"/assets/cosmicbreach/geo/entity/hollow_heliarch.geo.json",
                "/assets/cosmicbreach/animations/entity/hollow_heliarch.animation.json", "/assets/cosmicbreach/textures/entity/hollow_heliarch.png",
                "/assets/cosmicbreach/textures/entity/hollow_heliarch_glowmask.png", "/assets/cosmicbreach/textures/fx/heliarch_rift.png",
                "/assets/cosmicbreach/textures/fx/heliarch_seal.png", "/assets/cosmicbreach/models/block/heliarch_reliquary.json",
                "/assets/cosmicbreach/models/block/heliarch_reliquary_open.json"}) {
            if (!exists(f)) {
                missing.add(f);
            }
        }
        for (String b : new String[] {"heliarch_monolith", "heliarch_reliquary"}) {
            if (!exists("/assets/cosmicbreach/blockstates/" + b + ".json") || !lang.has("block.cosmicbreach." + b)) {
                missing.add("the state or name of " + b);
            }
        }
        for (int face = 0; face < 3; face++) {
            for (int pips = 0; pips <= HeliarchArena.PIPS; pips++) {
                if (!exists("/assets/cosmicbreach/models/block/heliarch_monolith_" + face + "_" + pips + ".json")) {
                    missing.add("monolith model " + face + " " + pips);
                }
            }
        }
        for (String e : new String[] {"hollow_heliarch", "star_seed"}) {
            if (!lang.has("entity.cosmicbreach." + e)) {
                missing.add("name of " + e);
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void everySoundHasItsFileAndATranslatedSubtitle() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject lang = json(LANG);
        List<String> missing = new ArrayList<>();
        int count = 0;
        for (String key : sounds.keySet()) {
            if (!key.startsWith("heliarch/") && !key.startsWith("music/heliarch")) {
                continue;
            }
            count++;
            JsonObject e = sounds.getAsJsonObject(key);
            String subtitle = e.get("subtitle").getAsString();
            if (!subtitle.equals("subtitles.cosmicbreach." + key.replace('/', '.')) || !lang.has(subtitle)) {
                missing.add("subtitle of " + key);
            }
            for (JsonElement s : e.getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                if (!exists("/assets/cosmicbreach/sounds/" + name.substring("cosmicbreach:".length()) + ".ogg")) {
                    missing.add("file " + name);
                }
                if (key.startsWith("music/")) {
                    assertTrue(s.isJsonObject() && s.getAsJsonObject().get("stream").getAsBoolean(), key + " streams");
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
        assertEquals(54, count, "the fight's sounds, its six lines and its three loops");
    }

    @Test
    void eachLineSaysItsWordsAndIsAsLongAsItsTake() throws IOException {
        JsonObject lang = json(LANG);
        for (HeliarchLine line : HeliarchLine.values()) {
            assertEquals("\"" + line.words() + "\"", lang.get(line.subtitleKey()).getAsString(), line.id() + "'s subtitle is its words, quoted");
            byte[] ogg;
            try (InputStream in = open("/assets/cosmicbreach/sounds/heliarch/voice_" + line.id() + ".ogg")) {
                assertTrue(in != null, line.id());
                ogg = in.readAllBytes();
            }
            double seconds = oggSamples(ogg) / 44100.0;
            assertEquals((int) Math.ceil(seconds * 20.0), line.lengthTicks(), line.id() + " is " + seconds + " s: update its lengthTicks");
        }
    }

    @Test
    void damageTheAdvancementTagsAndNoDashes() throws IOException {
        JsonObject lang = json(LANG);
        for (String d : new String[] {"heliarch_hand", "heliarch_solar", "heliarch_plate", "heliarch_eclipse", "heliarch_void"}) {
            JsonObject t = json("/data/cosmicbreach/damage_type/" + d + ".json");
            String id = "death.attack." + t.get("message_id").getAsString();
            assertTrue(lang.has(id) && lang.has(id + ".player"), "death messages of " + d);
        }
        JsonObject adv = json("/data/cosmicbreach/advancement/guardian/breach_sealed.json");
        assertTrue(adv.has("display"), "Breach Sealed shows");
        assertTrue(lang.has("advancements.cosmicbreach.guardian.breach_sealed.title"));
        assertTrue(json("/data/c/tags/entity_type/bosses.json").getAsJsonArray("values").toString().contains("cosmicbreach:hollow_heliarch"),
                "the Heliarch is one of c:bosses");
        String immune = json("/data/cosmicbreach/tags/entity_type/solar_immune.json").getAsJsonArray("values").toString();
        assertTrue(immune.contains("cosmicbreach:hollow_heliarch") && immune.contains("cosmicbreach:star_seed"), "a sun doesn't burn");
        for (String k : lang.keySet()) {
            String text = lang.get(k).getAsString();
            assertTrue(text.indexOf('\u2014') < 0 && text.indexOf('\u2013') < 0, "no dashes: " + k);
        }
    }

    /** The sample count of an Ogg Vorbis file: the granule position of its last page. */
    private static long oggSamples(byte[] ogg) {
        for (int i = ogg.length - 27; i >= 0; i--) {
            if (ogg[i] == 'O' && ogg[i + 1] == 'g' && ogg[i + 2] == 'g' && ogg[i + 3] == 'S') {
                long granule = 0;
                for (int b = 7; b >= 0; b--) {
                    granule = (granule << 8) | (ogg[i + 6 + b] & 0xFF);
                }
                return granule;
            }
        }
        throw new AssertionError("not an Ogg file");
    }
}
