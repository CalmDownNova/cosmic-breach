package com.cosmicbreach.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.entity.gyre.GyreModes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The Gyre Knight's Codex page says what the flyer does since 1.1 (the Dive, the slow stun, where its drops go), in the
 * game's own numbers: retune the Dive and this test sends you to the page.
 */
class GyreKnightPageTest {
    private static final String PAGE = "src/main/resources/assets/cosmicbreach/guides/cosmicbreach/codex/gyre_knight.md";

    private static String page() throws IOException {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(PAGE))) {
                return Files.readString(dir.resolve(PAGE), StandardCharsets.UTF_8).replace("\n  ", " ").replace("\n", " ");
            }
        }
        throw new AssertionError("no " + PAGE + " above " + Path.of("").toAbsolutePath());
    }

    @Test
    void thePageTellsTheDiveInTheGamesNumbers() throws IOException {
        String text = page();
        assertTrue(text.contains("- **Dive:**"), "the Dive has its bullet beside the other modes");
        assertEquals(2, GyreModes.VOLLEYS_MIN);
        assertEquals(3, GyreModes.VOLLEYS_MAX);
        assertTrue(text.contains("two or three volleys"), "the page says two or three volleys: " + text);
        assertEquals(2.0, GyreModes.DIVE_HANG / 20.0, 0.25);
        assertTrue(text.contains("hangs there in reach for two seconds"), "the page says how long it hangs in reach");
        assertTrue(text.contains("cuts once (parry it)"), "the cut is parryable and the page says so");
        assertTrue(text.contains("blades glow **gold**"), "the tell's colour is the sweep's, the parry cue");
    }

    @Test
    void thePageTellsTheStunAndTheDrops() throws IOException {
        String text = page();
        assertTrue(text.contains("it sinks, stunned, to your level and no further"), "a stunned Knight sinks to the player's level");
        assertTrue(text.contains("over open sky"), "the page says where a kill delivers its drops");
        assertTrue(text.contains("straight into your pack"), "and to whom");
    }
}
