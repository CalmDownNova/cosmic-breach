package com.cosmicbreach.client.gear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.gear.forge.ForgeCategory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Forge screen's words (1.1 design section 10): a name for every tab and the preview line, plain ASCII, no dashes. */
class ForgeScreenResourcesTest {
    private static final String LANG = "/assets/cosmicbreach_forge/lang/en_us.json";

    @Test
    void everyTabHasANameAndThePreviewHasItsLine() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (ForgeCategory category : ForgeCategory.values()) {
            if (!lang.has(category.translationKey())) {
                missing.add(category.translationKey());
            }
        }
        if (!lang.has("gui.cosmicbreach.forge.preview")) {
            missing.add("gui.cosmicbreach.forge.preview");
        }
        assertTrue(missing.isEmpty(), "missing " + missing);
    }

    @Test
    void theWordsArePlainAsciiWithoutDashes() throws IOException {
        for (Map.Entry<String, JsonElement> e : lang().entrySet()) {
            String text = e.getValue().getAsString();
            assertTrue(text.chars().allMatch(c -> c >= 32 && c < 127), e.getKey() + " is not plain ASCII");
            assertFalse(text.contains("--") || text.contains(" - "), e.getKey() + " has a dash");
        }
    }

    private static JsonObject lang() throws IOException {
        try (InputStream in = ForgeScreenResourcesTest.class.getResourceAsStream(LANG)) {
            assertTrue(in != null, "missing " + LANG);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
