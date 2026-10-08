package com.cosmicbreach.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a game that does not exit does to its run's result. The harness writes {@code result.txt} when the scenario ends and
 * then quits; if the game is still alive {@link AutoTest}'s exit grace later, its watchdog halts it. Before it does, it rewrites
 * the result with {@link #mark}, so a hung stop (a server that never finishes saving, say) reads as a FAIL in the file and in
 * everything that reads it, not as the PASS the scenario wrote (A2 quality review, Important 1). A hang at exit is a defect
 * in any scenario, so this is the shared behaviour.
 */
final class ExitStall {
    private ExitStall() {
    }

    /** The failure text for a result that was {@code was} when the game failed to exit within {@code graceSeconds}. One line, ASCII. */
    static String failure(String was, int graceSeconds) {
        return "FAIL the game did not exit " + graceSeconds + " s after the result (was: " + was + ")";
    }

    /**
     * Rewrites {@code resultFile} as the failure, keeping what it said, and returns the text. Never throws: it runs on the
     * watchdog thread just before the game is halted, and a file that cannot be written must not stop the halt.
     */
    static String mark(Path resultFile, int graceSeconds) {
        String was = "no result";
        try {
            String text = Files.readString(resultFile, StandardCharsets.UTF_8).strip();
            if (!text.isEmpty()) {
                was = text.replaceAll("\\s*[\\r\\n]+\\s*", " ");
            }
        } catch (IOException e) {
            // nothing to keep: the run never wrote a result
        }
        String text = failure(was, graceSeconds);
        try {
            Files.writeString(resultFile, text + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            // the watchdog halts the game next; the caller has already logged why
        }
        return text;
    }
}
