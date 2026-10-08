package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The loud sounds of a boss that its lines wait for: how long each takes to be over, checked against the sound files. */
class BossVoiceSoundsTest {
    /** The last Ogg page's granule position: the sample count. */
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
        return -1;
    }

    @Test
    void theWatchedSoundsAreTheWakeAndPhaseSoundsLinesSitUnder() {
        assertEquals(100, BossVoiceSounds.clearTicks("leviathan/awaken"));
        assertEquals(80, BossVoiceSounds.clearTicks("unsung/awaken"));
        assertEquals(55, BossVoiceSounds.clearTicks("unsung/dim"));
        assertEquals(42, BossVoiceSounds.clearTicks("colossus/fracture"));
        assertEquals(52, BossVoiceSounds.clearTicks("colossus/shatter"));
        assertEquals(84, BossVoiceSounds.clearTicks("leviathan/song"), "her intro song, whole: the opener waits for it");
        assertEquals(0, BossVoiceSounds.clearTicks("leviathan/swell"), "an attack's warning is never waited out");
        assertEquals(0, BossVoiceSounds.clearTicks("leviathan/death"));
    }

    @Test
    void noWaitIsLongerThanItsFileAndEveryFileExists() throws IOException {
        JsonObject sounds;
        try (InputStream in = BossVoiceSoundsTest.class.getResourceAsStream("/assets/cosmicbreach/sounds.json")) {
            sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (String name : BossVoiceSounds.names()) {
            assertTrue(sounds.has(name), "no sound event " + name);
            JsonArray files = sounds.getAsJsonObject(name).getAsJsonArray("sounds");
            assertTrue(files.size() > 0, name + " has no files");
            // a sound with several takes (the song has three) is waited out for the shortest of them
            for (JsonElement file : files) {
                String path = (file.isJsonObject() ? file.getAsJsonObject().get("name").getAsString() : file.getAsString()).replace("cosmicbreach:", "");
                byte[] ogg;
                try (InputStream in = BossVoiceSoundsTest.class.getResourceAsStream("/assets/cosmicbreach/sounds/" + path + ".ogg")) {
                    assertTrue(in != null, "no file for " + path);
                    ogg = in.readAllBytes();
                }
                long ticks = (long) Math.ceil(oggSamples(ogg) / 44100.0 * 20.0);
                assertTrue(BossVoiceSounds.clearTicks(name) <= ticks,
                        path + " is " + ticks + " ticks long, " + name + " is waited out for " + BossVoiceSounds.clearTicks(name));
            }
        }
    }
}
