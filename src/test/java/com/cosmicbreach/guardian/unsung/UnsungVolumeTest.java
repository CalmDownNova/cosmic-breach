package com.cosmicbreach.guardian.unsung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The fight is rhythm-driven and is played by ear, so all of it sits on one vanilla sound slider (Music) and the
 * reminder names that slider.
 */
class UnsungVolumeTest {
    private static final String SOURCES = "src/main/java/com/cosmicbreach";

    /** The project file at {@code path}, found by walking up from the test's working directory. */
    private static Path project(String path) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(path))) {
                return dir.resolve(path);
            }
        }
        throw new AssertionError("no " + path + " above " + Path.of("").toAbsolutePath());
    }

    @Test
    void theReminderNamesTheMusicSlider() throws IOException {
        assertEquals("soundCategory.music", Unsung.VOLUME_SLIDER_KEY, "vanilla's key for the Music slider");
        JsonObject lang = JsonParser.parseString(Files.readString(
                project("src/main/resources/assets/cosmicbreach_unsung/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(lang.has("message.cosmicbreach.unsung.volume"), "the reminder's text");
        assertTrue(lang.get("message.cosmicbreach.unsung.volume").getAsString().contains("%s"), "it takes the slider's name");
    }

    @Test
    void everySoundOfHerFightPlaysOnTheMusicSlider() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(project(SOURCES + "/guardian/unsung"))) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        int sounds = 0;
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            for (String other : new String[] {"SoundSource.HOSTILE", "SoundSource.BLOCKS", "SoundSource.VOICE", "SoundSource.RECORDS", "SoundSource.NEUTRAL", "SoundSource.PLAYERS", "SoundSource.AMBIENT"}) {
                // her fight's own sounds; the fire put out by the fight's rules is a block sound and not hers to carry
                if (text.contains(other) && !f.getFileName().toString().equals("Unsung.java")) {
                    assertFalse(true, f.getFileName() + " plays a sound on " + other + ", not the Music slider");
                }
            }
            if (text.contains("SoundSource.MUSIC")) {
                sounds++;
            }
        }
        assertTrue(sounds >= 4, "her cues are on the Music slider, found in " + sounds + " files");
        String unsung = Files.readString(project(SOURCES + "/guardian/unsung/Unsung.java"), StandardCharsets.UTF_8);
        assertTrue(unsung.contains("pitch, SoundSource.MUSIC)"), "her cues default to the Music slider");
        assertFalse(unsung.contains("pitch, SoundSource.HOSTILE)"), "no cue defaults to hostile creatures");
    }
}
