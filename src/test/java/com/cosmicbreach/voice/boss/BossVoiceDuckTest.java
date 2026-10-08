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
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * What a boss line pulls down while it plays (Aetheria 1.1 plan, "Voice over music"): its own boss's music by about 6 dB, in over
 * 3 ticks and out over 10 after the line. Never another boss's, never a sound effect (the boss's loud sounds are waited out
 * instead, {@link BossVoiceSounds}: the engine clamps a loud effect's gain, so no factor can lower it), never the voices.
 */
class BossVoiceDuckTest {
    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    @Test
    void aBossLinePullsDownItsOwnBossesMusicOnly() {
        assertTrue(BossVoiceDuck.ducks("colossus", id("music/colossus")));
        assertTrue(BossVoiceDuck.ducks("leviathan", id("music/leviathan")));
        assertTrue(BossVoiceDuck.ducks("unsung", id("music/unsung/alto_sung_2")));
        assertTrue(BossVoiceDuck.ducks("unsung", id("music/unsung/bass_rise")));
        assertTrue(BossVoiceDuck.ducks("heliarch", id("music/heliarch_collapse")));
        assertFalse(BossVoiceDuck.ducks("colossus", id("music/leviathan")), "another boss's music");
        assertFalse(BossVoiceDuck.ducks("leviathan", id("music/deep")), "the level's own music");
        assertFalse(BossVoiceDuck.ducks("colossus", id("colossus/death")), "an effect is waited out, not ducked");
        assertFalse(BossVoiceDuck.ducks("leviathan", id("leviathan/swell")), "an attack's warning");
        assertFalse(BossVoiceDuck.ducks("leviathan", id("bossvoice/leviathan_kill")), "the voice itself");
        assertFalse(BossVoiceDuck.ducks("heliarch", id("heliarch/voice_nova")));
        assertFalse(BossVoiceDuck.ducks("colossus", ResourceLocation.fromNamespaceAndPath("minecraft", "music/colossus")));
    }

    @Test
    void theMusicGoesDownSixDecibelsAtAVolumeTheEngineDoesNotClamp() {
        assertEquals(-6.0, 20 * Math.log10(BossVoiceDuck.MUSIC), 0.03);
        assertEquals(0.5f, BossVoiceDuck.factor("unsung", id("music/unsung/bass_hum_1"), 140, 100, 180), 1e-6);
    }

    @Test
    void theEnvelopeFadesInOverThreeTicksAndOutOverTenAfterTheLine() {
        long start = 100;
        long end = 180;
        assertEquals(0f, BossVoiceDuck.envelope(99, start, end), 1e-6);
        assertEquals(0f, BossVoiceDuck.envelope(100, start, end), 1e-6);
        assertEquals(1f / 3f, BossVoiceDuck.envelope(101, start, end), 1e-6);
        assertEquals(1f, BossVoiceDuck.envelope(103, start, end), 1e-6);
        assertEquals(1f, BossVoiceDuck.envelope(180, start, end), 1e-6);
        assertEquals(0.5f, BossVoiceDuck.envelope(185, start, end), 1e-6);
        assertEquals(0f, BossVoiceDuck.envelope(190, start, end), 1e-6);
        assertEquals(0f, BossVoiceDuck.envelope(500, start, end), 1e-6);
    }

    @Test
    void theFactorIsOneOutsideALineAndTheTargetInsideIt() {
        ResourceLocation music = id("music/colossus");
        assertEquals(1f, BossVoiceDuck.factor("colossus", music, 50, 100, 180), 1e-6);
        assertEquals(0.5f, BossVoiceDuck.factor("colossus", music, 140, 100, 180), 1e-6);
        assertEquals(1f, BossVoiceDuck.factor("leviathan", music, 140, 100, 180), 1e-6, "another boss is speaking");
        assertEquals(0.75f, BossVoiceDuck.factor("colossus", music, 185, 100, 180), 1e-6, "halfway back up");
    }

    @Test
    void everyDuckedNameIsASoundOfTheMod() throws IOException {
        JsonObject sounds;
        try (InputStream in = BossVoiceDuckTest.class.getResourceAsStream("/assets/cosmicbreach/sounds.json")) {
            sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (String name : BossVoiceDuck.names()) {
            boolean found = false;
            for (String key : sounds.keySet()) {
                found |= key.startsWith(name);
            }
            assertTrue(found, name + " matches no sound in sounds.json");
        }
    }
}
