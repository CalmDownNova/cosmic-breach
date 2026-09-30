package com.cosmicbreach.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianType;
import com.cosmicbreach.world.Layer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The Starfall's voice: which line plays when, once per player, one at a time, and its files. */
class EchoTest {
    // ------------------------------------------------------------------ the lines

    @Test
    void everyLineHasItsSoundAndSubtitleNames() {
        Set<String> ids = new HashSet<>();
        for (EchoLine line : EchoLine.values()) {
            assertTrue(ids.add(line.id()), "unique id " + line.id());
            assertEquals("echo/" + line.id(), line.soundPath());
            assertEquals("subtitles.cosmicbreach.echo." + line.id(), line.subtitleKey());
            assertEquals(line, EchoLine.byId(line.id()).orElseThrow());
        }
        assertEquals(List.of("first_shard", "ring_open", "arrival", "drift", "deep", "sanctum", "sealed"),
                java.util.Arrays.stream(EchoLine.values()).map(EchoLine::id).toList());
        assertTrue(EchoLine.byId("nothing").isEmpty());
    }

    @Test
    void onlyTheArrivalWaitsForTheWhiteToClear() {
        for (EchoLine line : EchoLine.values()) {
            assertEquals(line == EchoLine.ARRIVAL, line.afterFallUp(), line.id());
        }
    }

    @Test
    void eachLineIsAsLongAsItsSound() throws IOException {
        for (EchoLine line : EchoLine.values()) {
            byte[] ogg;
            try (InputStream in = EchoTest.class.getResourceAsStream("/assets/cosmicbreach/sounds/" + line.soundPath() + ".ogg")) {
                assertNotNull(in, line.soundPath());
                ogg = in.readAllBytes();
            }
            double seconds = oggSamples(ogg) / 44100.0;
            assertEquals((int) Math.ceil(seconds * 20.0), line.lengthTicks(), line.id() + " is " + seconds + " s: update its lengthTicks");
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

    @Test
    void leadInsLetEachMomentSoundFirst() {
        // the ring's chord blooms for about 1.5 s before its line; a guardian's fall and the advancement's fanfare
        // need longer; the pickup needs only a beat
        assertTrue(EchoLine.FIRST_SHARD.leadTicks() <= 20);
        assertTrue(EchoLine.RING_OPEN.leadTicks() >= 30);
        for (EchoLine guardian : List.of(EchoLine.DRIFT, EchoLine.DEEP, EchoLine.SANCTUM, EchoLine.SEALED)) {
            assertTrue(guardian.leadTicks() >= 60, guardian.id());
        }
    }

    // ------------------------------------------------------------------ once per player

    @Test
    void eachLineIsSaidOncePerPlayer() {
        EchoMemory memory = EchoMemory.NEW;
        for (EchoLine line : EchoLine.values()) {
            if (line == EchoLine.FIRST_SHARD) {
                continue;
            }
            assertTrue(memory.shouldSay(line), line.id());
            memory = memory.with(line);
            assertTrue(memory.heard(line));
            assertFalse(memory.shouldSay(line), "never twice: " + line.id());
        }
        assertEquals(memory, memory.with(EchoLine.DRIFT));
    }

    @Test
    void theShardsLineIsDroppedOnceTheWayHomeIsOpen() {
        EchoMemory fresh = EchoMemory.NEW;
        assertTrue(fresh.shouldSay(EchoLine.FIRST_SHARD));
        assertFalse(fresh.with(EchoLine.RING_OPEN).shouldSay(EchoLine.FIRST_SHARD), "after opening a ring (a Fallen Rift's shard)");
        assertFalse(fresh.with(EchoLine.ARRIVAL).shouldSay(EchoLine.FIRST_SHARD), "after arriving another way");
        assertTrue(fresh.with(EchoLine.DRIFT).shouldSay(EchoLine.FIRST_SHARD));
        // nothing else is ever superseded
        for (EchoLine line : EchoLine.values()) {
            if (line != EchoLine.FIRST_SHARD) {
                assertTrue(line.supersededBy().isEmpty(), line.id());
            }
        }
    }

    @Test
    void theMemorySavesAndKeepsLinesItDoesNotKnow() {
        EchoMemory memory = new EchoMemory(Set.of("arrival", "a_line_from_a_later_version")).with(EchoLine.DRIFT);
        Tag nbt = EchoMemory.CODEC.encodeStart(NbtOps.INSTANCE, memory).getOrThrow();
        EchoMemory back = EchoMemory.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(memory, back);
        assertTrue(back.heard(EchoLine.ARRIVAL) && back.heard(EchoLine.DRIFT) && !back.heard(EchoLine.DEEP));
        assertTrue(back.heard().contains("a_line_from_a_later_version"));
        assertEquals(EchoMemory.NEW, EchoMemory.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[]")).getOrThrow());
    }

    // ------------------------------------------------------------------ one at a time

    /** Runs the queue from tick {@code from} to {@code to}, blocked or not, and returns the tick a line started. */
    private static long runUntilStart(EchoQueue q, long from, long to, boolean blocked) {
        for (long t = from; t <= to; t++) {
            if (q.next(t, blocked) != null) {
                return t;
            }
        }
        return -1;
    }

    @Test
    void aLineStartsAfterItsLeadIn() {
        EchoQueue q = new EchoQueue();
        assertTrue(q.offer(EchoLine.RING_OPEN, 100));
        assertEquals(100 + EchoLine.RING_OPEN.leadTicks(), runUntilStart(q, 100, 400, false));
        assertEquals(EchoLine.RING_OPEN, q.playing());
    }

    @Test
    void theVoiceNeverOverlapsItself() {
        EchoQueue q = new EchoQueue();
        q.offer(EchoLine.FIRST_SHARD, 0);
        q.offer(EchoLine.RING_OPEN, 1);
        long first = runUntilStart(q, 0, 100, false);
        assertEquals(EchoLine.FIRST_SHARD.leadTicks(), first);
        // the second line's lead-in is long over, but the first is still playing for 130 ticks
        assertEquals(-1, runUntilStart(q, first + 1, first + 130, false));
        assertEquals(EchoLine.FIRST_SHARD, q.playing());
        q.finished(first + 130);
        assertNull(q.playing());
        long second = runUntilStart(q, first + 131, first + 400, false);
        assertEquals(first + 130 + EchoQueue.GAP, second, "a breath between two lines");
        assertEquals(EchoLine.RING_OPEN, q.playing());
    }

    @Test
    void linesPlayInTheOrderTheyWereSaid() {
        EchoQueue q = new EchoQueue();
        q.offer(EchoLine.DRIFT, 0);
        q.offer(EchoLine.FIRST_SHARD, 0);          // a shorter lead-in doesn't jump the queue
        long t = runUntilStart(q, 0, 500, false);
        assertEquals(EchoLine.DRIFT, q.playing());
        q.finished(t + 50);
        runUntilStart(q, t + 51, t + 500, false);
        assertEquals(EchoLine.FIRST_SHARD, q.playing());
    }

    @Test
    void aLinePlayingOrWaitingIsNotQueuedTwice() {
        EchoQueue q = new EchoQueue();
        assertTrue(q.offer(EchoLine.ARRIVAL, 0));
        assertFalse(q.offer(EchoLine.ARRIVAL, 3), "waiting");
        runUntilStart(q, 0, 100, false);
        assertFalse(q.offer(EchoLine.ARRIVAL, 50), "playing");
        assertEquals(List.of(), q.waiting());
        q.finished(80);
        assertTrue(q.offer(EchoLine.ARRIVAL, 90), "the client doesn't judge a line heard: the server does");
    }

    @Test
    void theArrivalWaitsForTheWhiteOfFallingUpToClear() {
        EchoQueue q = new EchoQueue();
        runUntilStart(q, 0, 10, false);
        // the pull starts at 20 (white), the new world arrives at 44 and the line with it, the white clears at 110
        assertEquals(-1, runUntilStart(q, 20, 43, true));
        q.offer(EchoLine.ARRIVAL, 44);
        assertEquals(-1, runUntilStart(q, 44, 109, true), "nothing starts while the screen is white");
        assertEquals(110 + EchoLine.ARRIVAL.leadTicks(), runUntilStart(q, 110, 300, false));
    }

    @Test
    void anArrivalWithoutAFallUpStartsAfterItsLeadIn() {
        EchoQueue q = new EchoQueue();
        runUntilStart(q, 0, 500, false);
        q.offer(EchoLine.ARRIVAL, 501);
        assertEquals(501 + EchoLine.ARRIVAL.leadTicks(), runUntilStart(q, 501, 700, false));
    }

    @Test
    void nothingStartsWhileAWorldLoadsAndOtherLinesDoNotWaitExtraAfterIt() {
        EchoQueue q = new EchoQueue();
        q.offer(EchoLine.DRIFT, 0);
        assertEquals(-1, runUntilStart(q, 0, 200, true));
        assertEquals(201, runUntilStart(q, 201, 400, false), "its lead-in ran out while loading");
    }

    @Test
    void clearingForgetsEverything() {
        EchoQueue q = new EchoQueue();
        q.offer(EchoLine.DEEP, 0);
        q.offer(EchoLine.SEALED, 0);
        runUntilStart(q, 0, 200, false);
        q.clear();
        assertNull(q.playing());
        assertEquals(List.of(), q.waiting());
    }

    // ------------------------------------------------------------------ guardians

    @Test
    void aGuardianSaysNothingUnlessItsTypeHasALine() {
        ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> lair =
                ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("test_lair"));
        GuardianType quiet = new GuardianType(CosmicBreach.id("test_quiet"), "test_quiet", Layer.DRIFT,
                CosmicBreach.id("guardian/test"), lair, (level, centre, altar) -> null, null);
        assertNull(quiet.echo());
        GuardianType speaks = new GuardianType(CosmicBreach.id("test_speaks"), "test_speaks", Layer.DEEP,
                CosmicBreach.id("guardian/test"), lair, (level, centre, altar) -> null, null, EchoLine.DEEP);
        assertEquals(EchoLine.DEEP, speaks.echo());
    }

    // ------------------------------------------------------------------ files

    @Test
    void everyLineHasItsFileSoundEntryAndQuotedSubtitle() throws IOException {
        JsonObject sounds = json("/assets/cosmicbreach/sounds.json");
        JsonObject names = json("/assets/cosmicbreach_voice/lang/en_us.json");
        JsonObject manifest = JsonParser.parseString(Files.readString(projectFile("tools/sound/manifest.json"))).getAsJsonObject();
        for (EchoLine line : EchoLine.values()) {
            JsonObject entry = sounds.getAsJsonObject(line.soundPath());
            assertNotNull(entry, "sounds.json entry " + line.soundPath());
            assertEquals(line.subtitleKey(), entry.get("subtitle").getAsString());
            assertEquals("cosmicbreach:" + line.soundPath(), entry.getAsJsonArray("sounds").get(0).getAsString());
            String quoted = "\"" + line.words() + "\"";
            assertEquals(quoted, names.get(line.subtitleKey()).getAsString(), "subtitle of " + line.id());
            assertEquals(quoted, manifest.getAsJsonObject(line.soundPath()).get("subtitle").getAsString(),
                    "the sound build (tools/sound/voice.py) has the same words for " + line.id());
            try (InputStream in = EchoTest.class.getResourceAsStream("/assets/cosmicbreach/sounds/" + line.soundPath() + ".ogg")) {
                assertNotNull(in, "sound file " + line.soundPath());
                assertTrue(in.readAllBytes().length > 20_000, "a whole line in " + line.soundPath());
            }
            assertFalse(line.words().indexOf(0x2014) >= 0 || line.words().indexOf(0x2013) >= 0, "no dashes in " + line.id());
        }
        assertTrue(manifest.getAsJsonObject("onboarding/breach_wind").get("loop").getAsBoolean(), "the Breach's wind is a loop");
    }

    /** A file of the project (tests may run in a folder under it). */
    private static Path projectFile(String path) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(path))) {
                return dir.resolve(path);
            }
        }
        throw new AssertionError("no " + path + " above " + Path.of("").toAbsolutePath());
    }

    private static JsonObject json(String resource) throws IOException {
        try (InputStream in = EchoTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing " + resource);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
