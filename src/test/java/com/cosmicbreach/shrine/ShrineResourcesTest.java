package com.cosmicbreach.shrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shrines' files: states, particle models, GeckoLib model, animation, texture and glowmask per kind; sounds; words; Codex. */
class ShrineResourcesTest {
    private static final String A = "/assets/cosmicbreach/";

    private static String read(String path) throws IOException {
        try (InputStream in = ShrineResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    @Test
    void eachShrineHasItsStatesModelsAndArt() throws IOException {
        for (ShrineKind k : ShrineKind.values()) {
            String id = "shrine_" + k.id();
            JsonObject variants = json(A + "blockstates/" + id + ".json").getAsJsonObject("variants");
            for (String f : new String[] {"north", "east", "south", "west"}) {
                assertTrue(variants.has("facing=" + f), id + " facing " + f);
            }
            assertTrue(json(A + "models/block/" + id + ".json").getAsJsonObject("textures").has("particle"));
            JsonObject geo = json(A + "geo/block/" + id + ".geo.json");
            assertEquals("1.12.0", geo.get("format_version").getAsString());
            assertTrue(json(A + "animations/block/" + id + ".animation.json").getAsJsonObject("animations").has("idle"), id + " idle");
            assertNotNull(ShrineResourcesTest.class.getResource(A + "textures/block/" + id + ".png"));
            assertNotNull(ShrineResourcesTest.class.getResource(A + "textures/block/" + id + "_glowmask.png"));
        }
    }

    @Test
    void theSoundsHaveFilesAndSubtitlesAndTheWordsHaveNoDashes() throws IOException {
        JsonObject sounds = json(A + "sounds.json");
        JsonObject lang = json("/assets/cosmicbreach_shrines/lang/en_us.json");
        for (String s : new String[] {"save", "kept"}) {
            JsonObject e = sounds.getAsJsonObject("shrine/" + s);
            assertNotNull(e, "sounds.json has shrine/" + s);
            assertTrue(lang.has(e.get("subtitle").getAsString()));
            for (JsonElement f : e.getAsJsonArray("sounds")) {
                assertNotNull(ShrineResourcesTest.class.getResource(A + "sounds/" + f.getAsString().replace("cosmicbreach:", "") + ".ogg"));
            }
        }
        for (ShrineKind k : ShrineKind.values()) {
            assertTrue(lang.has("block.cosmicbreach.shrine_" + k.id()), "a name for " + k);
        }
        assertTrue(lang.has("cosmicbreach.shrine.saved") && lang.has("cosmicbreach.shrine.kept"));
        for (var e : lang.entrySet()) {
            String v = e.getValue().getAsString();
            assertTrue(v.indexOf(0x2014) < 0 && v.indexOf(0x2013) < 0, "a dash in " + e.getKey());
        }
    }

    @Test
    void theCodexHasAShrinesPageFromTheStart() throws IOException {
        String page = read(A + "guides/cosmicbreach/codex/shrines.md");
        assertTrue(page.contains("unlock: arrived"));
        assertTrue(page.contains("keeps your place") && page.contains("costs you nothing"));
        assertTrue(page.contains("**The way back up.**") && page.contains("golden current"), "the page tells of the currents");
        assertTrue(page.contains("Sneak") && page.contains("fall"), "and that sneaking lets go and a fall is never caught");
    }

    @Test
    void theSanctumPageTellsTheWayBackUpIsTheWayYouCame() throws IOException {
        String page = read(A + "guides/cosmicbreach/codex/breach_sanctum.md");
        assertTrue(page.contains("**The way back up**") && page.contains("causeway") && page.contains("Deep"), "the walk out is mentioned");
    }

    @Test
    void noWitherOrDragonCanRemoveAShrine() throws IOException {
        for (String tag : new String[] {"wither_immune", "dragon_immune"}) {
            String values = json("/data/minecraft/tags/block/" + tag + ".json").getAsJsonArray("values").toString();
            for (ShrineKind k : ShrineKind.values()) {
                assertTrue(values.contains("\"cosmicbreach:shrine_" + k.id() + "\""), "the " + tag + " tag lists the " + k + " shrine");
            }
        }
    }
}
