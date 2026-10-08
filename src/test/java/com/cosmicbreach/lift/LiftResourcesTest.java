package com.cosmicbreach.lift;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lifts' files: the rising-air loop with its subtitle and range, and the Codex's word on falling in the Rift. */
class LiftResourcesTest {
    private static final String A = "/assets/cosmicbreach/";

    private static String read(String path) throws IOException {
        try (InputStream in = LiftResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    @Test
    void theRisingAirHasItsFileItsRangeAndItsSubtitle() throws IOException {
        JsonObject e = json(A + "sounds.json").getAsJsonObject("lift/stream");
        assertNotNull(e, "sounds.json has lift/stream");
        JsonObject first = e.getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals("cosmicbreach:lift/stream", first.get("name").getAsString());
        assertEquals(48, first.get("attenuation_distance").getAsInt());
        assertNotNull(LiftResourcesTest.class.getResource(A + "sounds/lift/stream.ogg"));
        assertTrue(json("/assets/cosmicbreach_lift/lang/en_us.json").has(e.get("subtitle").getAsString()));
    }

    @Test
    void theCodexTellsOfTheLiftAndTheVentsOnceYouHaveMetIt() throws IOException {
        String page = read(A + "guides/cosmicbreach/codex/thalassine_leviathan.md");
        String sealed = page.substring(page.indexOf("<Gate when=\"!met_leviathan\""), page.indexOf("<Gate when=\"met_leviathan\""));
        String met = page.substring(page.indexOf("<Gate when=\"met_leviathan\""));
        assertTrue(met.contains("**Falling.**") && met.contains("air vents"), "the met section mentions falling and the vents");
        assertTrue(met.contains("sneak to sink"), "and how to stay down for what you dropped");
        assertTrue(!sealed.contains("air vents"), "the sealed page keeps its secrets");
    }
}
