package com.cosmicbreach.client.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Fits a piece of text into a box on a screen (1.1 design section 10): wrapped at full size if it fits, else wrapped
 * smaller, and only as a last resort cut short with "..." (the screen then shows the whole text in a tooltip). Pure:
 * widths come from the caller, the game's font or a stand-in in tests.
 */
public final class TextFit {
    /** The sizes tried, largest first. */
    static final float[] SCALES = {1.0f, 0.75f};
    static final String ELLIPSIS = "...";

    /** The lines to draw at {@code scale} (each at most the box width divided by the scale), and whether text was cut. */
    public record Fit(List<String> lines, float scale, boolean shortened) {}

    private TextFit() {
    }

    public static Fit fit(String text, int boxWidth, int boxHeight, int lineHeight, ToIntFunction<String> width) {
        for (float scale : SCALES) {
            List<String> lines = wrap(text, room(boxWidth, scale), width, false);
            if (lines != null && lines.size() <= maxLines(boxHeight, lineHeight, scale)) {
                return new Fit(lines, scale, false);
            }
        }
        float scale = SCALES[SCALES.length - 1];
        int room = room(boxWidth, scale);
        int max = maxLines(boxHeight, lineHeight, scale);
        List<String> lines = wrap(text, room, width, true);
        if (lines.size() <= max) {
            return new Fit(lines, scale, false);
        }
        List<String> kept = new ArrayList<>(lines.subList(0, max));
        kept.set(max - 1, ellipsize(kept.get(max - 1), room, width));
        return new Fit(List.copyOf(kept), scale, true);
    }

    static int room(int boxWidth, float scale) {
        return (int) Math.floor(boxWidth / scale);
    }

    static int maxLines(int boxHeight, int lineHeight, float scale) {
        return Math.max(1, (int) Math.floor(boxHeight / (lineHeight * scale)));
    }

    /**
     * Greedy word wrap at spaces into lines at most {@code room} wide. A word wider than a line makes it return null,
     * unless {@code breakWords}, which splits the word between letters instead.
     */
    static @Nullable List<String> wrap(String text, int room, ToIntFunction<String> width, boolean breakWords) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            return lines;
        }
        String line = "";
        for (String word : text.split(" ")) {
            String joined = line.isEmpty() ? word : line + " " + word;
            if (width.applyAsInt(joined) <= room) {
                line = joined;
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line);
            }
            line = word;
            while (width.applyAsInt(line) > room) {
                if (!breakWords) {
                    return null;
                }
                int cut = longestPrefix(line, room, width);
                lines.add(line.substring(0, cut));
                line = line.substring(cut);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    /** {@code line} with "..." on the end, dropping whole words (letters only when one word is left) until it fits. */
    static String ellipsize(String line, int room, ToIntFunction<String> width) {
        String cut = line;
        while (!cut.isEmpty() && width.applyAsInt(cut + ELLIPSIS) > room) {
            int space = cut.lastIndexOf(' ');
            cut = space > 0 ? cut.substring(0, space) : cut.substring(0, cut.length() - 1);
        }
        return cut + ELLIPSIS;
    }

    /** How many leading letters of {@code word} fit in {@code room}; at least one, so a very narrow box still ends. */
    private static int longestPrefix(String word, int room, ToIntFunction<String> width) {
        int n = 1;
        while (n < word.length() && width.applyAsInt(word.substring(0, n + 1)) <= room) {
            n++;
        }
        return n;
    }
}
