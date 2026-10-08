package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every take in the boss voice catalogs is in the mod (A5.2): its sound event in sounds.json with the catalog's subtitle
 * key and its .ogg, a caption that is its words in quotes (bosses 1 to 3 in {@code cosmicbreach_bossvoice}, boss 4 in
 * {@code cosmicbreach_heliarch}), and a length that is the .ogg's rounded up to a tick, with the words inside it. And the
 * other way round: no sound event or caption of the voices that no take uses.
 */
class BossVoiceResourcesTest {
    private static InputStream open(String path) {
        return BossVoiceResourcesTest.class.getResourceAsStream(path);
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = open(path)) {
            assertTrue(in != null, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

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
    void everyTakeHasItsEventFileCaptionAndLength() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject ours = json("/assets/cosmicbreach_bossvoice/lang/en_us.json");
        JsonObject regent = json("/assets/cosmicbreach_heliarch/lang/en_us.json");
        int takes = 0;
        Set<String> events = new HashSet<>();
        Set<String> captions = new HashSet<>();
        for (String boss : BossCatalog.BOSSES) {
            for (VoiceLine line : BossCatalog.of(boss).lines()) {
                for (var e : line.variants().entrySet()) {
                    VoiceLine.Variant v = e.getValue();
                    String where = boss + "/" + line.id() + "/" + e.getKey();
                    String path = v.sound().getPath();
                    assertTrue(sounds.has(path), where + ": no sounds.json entry " + path);
                    JsonObject entry = sounds.getAsJsonObject(path);
                    assertEquals(v.subtitle(), entry.get("subtitle").getAsString(), where + ": the subtitle key");
                    assertEquals(1, entry.getAsJsonArray("sounds").size(), where + ": one file");
                    var file = entry.getAsJsonArray("sounds").get(0);
                    assertEquals("cosmicbreach:" + path, file.isJsonObject() ? file.getAsJsonObject().get("name").getAsString() : file.getAsString(),
                            where + ": the entry plays its own file");
                    JsonObject lang = path.startsWith("heliarch/") ? regent : ours;
                    assertTrue(lang.has(v.subtitle()), where + ": no caption " + v.subtitle());
                    assertEquals("\"" + line.words() + "\"", lang.get(v.subtitle()).getAsString(), where + ": the caption is the words");
                    byte[] ogg;
                    try (InputStream in = open("/assets/cosmicbreach/sounds/" + path + ".ogg")) {
                        assertTrue(in != null, where + ": no .ogg");
                        ogg = in.readAllBytes();
                    }
                    double seconds = oggSamples(ogg) / 44100.0;
                    assertEquals((int) Math.ceil(seconds * 20.0), v.lengthTicks(), where + " is " + seconds + " s");
                    assertTrue(v.speechStartTicks() <= v.speechTicks() && v.speechTicks() <= v.lengthTicks(), where + ": the words inside the take");
                    assertTrue(events.add(path), where + ": a sound event used by two takes");
                    captions.add(v.subtitle());
                    takes++;
                }
            }
        }
        assertEquals(75, takes, "45 takes of bosses 1 to 3 and the regent's 30");
        // nothing left over: every boss voice event and caption is a take's
        for (String key : sounds.keySet()) {
            if (key.startsWith("bossvoice/") || key.startsWith("heliarch/voice_")) {
                assertTrue(events.contains(key), "sound event " + key + " is in no catalog");
            }
        }
        for (String key : ours.keySet()) {
            assertTrue(captions.contains(key), "caption " + key + " belongs to no take");
        }
        for (String key : regent.keySet()) {
            if (key.startsWith("subtitles.cosmicbreach.heliarch.voice_")) {
                assertTrue(captions.contains(key), "caption " + key + " belongs to no take");
            }
        }
    }

    @Test
    void aRelayedSentencesFragmentsMakeItsCaption() {
        int relayed = 0;
        for (VoiceLine line : BossCatalog.of("unsung").lines()) {
            for (var e : line.variants().entrySet()) {
                VoiceLine.Variant v = e.getValue();
                if (v.fragments().isEmpty()) {
                    continue;
                }
                relayed++;
                String joined = String.join(" ", v.fragments().stream().map(VoiceLine.Fragment::text).toList());
                assertEquals(line.words(), joined, line.id() + "/" + e.getKey() + ": the pieces say the caption");
                int last = -1;
                for (VoiceLine.Fragment f : v.fragments()) {
                    assertTrue(f.startTicks() >= last && f.startTicks() <= v.lengthTicks(), line.id() + ": fragments in order inside the take");
                    last = f.startTicks();
                    assertTrue("123".indexOf('0' + f.mask()) >= 0, line.id() + ": a mask from 1 to 3");
                    assertTrue(e.getKey().equals(VoiceLine.ALL) || e.getKey().indexOf('0' + f.mask()) >= 0,
                            line.id() + "/" + e.getKey() + ": spoken by the masks still singing");
                }
            }
        }
        assertEquals(17, relayed, "all 17 takes of the Unsung's 13 sentences are relayed");
        for (VoiceLine line : BossCatalog.of("colossus").lines()) {
            line.variants().values().forEach(v -> assertFalse(v.fragments().size() > 0, "only the Unsung relays"));
        }
    }
}
