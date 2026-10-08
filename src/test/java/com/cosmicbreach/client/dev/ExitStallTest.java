package com.cosmicbreach.client.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A game that does not exit after its result is a failed run whatever the scenario said (A2 quality review, Important 1): the
 * harness's watchdog rewrites {@code result.txt} before it halts the game, so nothing that reads the file calls a stalled stop
 * clean.
 */
class ExitStallTest {
    @TempDir
    Path dir;

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void aPassThatNeverExitedReadsAsAFailureAndSaysWhatItWas() throws IOException {
        Path file = dir.resolve("result.txt");
        Files.writeString(file, "PASS\n", StandardCharsets.UTF_8);
        String written = ExitStall.mark(file, 60);
        assertEquals(written + "\n", read(file), "the file holds the text, one line");
        assertTrue(written.startsWith("FAIL "), "a script that reads the first word sees FAIL: " + written);
        assertTrue(written.contains("did not exit 60 s after the result"), written);
        assertTrue(written.contains("(was: PASS)"), written);
    }

    @Test
    void aResultThatAlreadyFailedKeepsItsReason() throws IOException {
        Path file = dir.resolve("result.txt");
        Files.writeString(file, "FAIL time budget of 120 s used up at step 7\n", StandardCharsets.UTF_8);
        String written = ExitStall.mark(file, 60);
        assertTrue(written.startsWith("FAIL "), written);
        assertTrue(written.contains("(was: FAIL time budget of 120 s used up at step 7)"), written);
    }

    @Test
    void noResultFileAtAllIsAFailureToo() throws IOException {
        Path file = dir.resolve("never-written.txt");
        String written = ExitStall.mark(file, 60);
        assertTrue(written.startsWith("FAIL "), written);
        assertTrue(written.contains("(was: no result)"), written);
        assertTrue(Files.exists(file), "the failure is written even when there was nothing to read");
    }

    @Test
    void theTextIsOnePlainAsciiLine() throws IOException {
        Path file = dir.resolve("result.txt");
        Files.writeString(file, "PASS\n", StandardCharsets.UTF_8);
        String written = ExitStall.mark(file, 60);
        assertFalse(written.contains("\n") || written.contains("\r"), "one line");
        for (char c : written.toCharArray()) {
            assertTrue(c >= 32 && c < 127, "plain ASCII, no dashes: " + (int) c);
        }
    }

    @Test
    void aFileThatCannotBeWrittenIsReportedNotThrownIntoTheWatchdog() {
        Path notAFile = dir; // a directory: writing it fails
        String written = ExitStall.mark(notAFile, 60);
        assertTrue(written.startsWith("FAIL "), "the text is still made: " + written);
    }
}
