package com.cosmicbreach.client.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Fitting text into a box (1.1 design section 10): wrap first, then shrink, and only then cut short with "...". */
class TextFitTest {
    /** A stand-in for the game font: 6 pixels a character, 4 a space (the vanilla font's common advances). */
    private static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += c == ' ' ? 4 : 6;
        }
        return w;
    }

    private static TextFit.Fit fit(String text, int boxWidth, int boxHeight) {
        return TextFit.fit(text, boxWidth, boxHeight, 9, TextFitTest::width);
    }

    @Test
    void aShortNameStaysOnOneLineAtFullSize() {
        TextFit.Fit fit = fit("Meridian", 108, 18);
        assertEquals(List.of("Meridian"), fit.lines());
        assertEquals(1.0f, fit.scale());
        assertFalse(fit.shortened());
    }

    @Test
    void aLongNameWrapsBeforeItShrinks() {
        TextFit.Fit fit = fit("Starfall Vanguard Chestplate", 108, 18);
        assertEquals(List.of("Starfall Vanguard", "Chestplate"), fit.lines());
        assertEquals(1.0f, fit.scale());
        assertFalse(fit.shortened());
    }

    @Test
    void whenTwoLinesAreNotEnoughItShrinks() {
        // three lines at full size in 60 by 18; at 0.75 the room is 80 wide and still two lines tall
        TextFit.Fit fit = fit("Alpha Beta Gamma Delta", 60, 18);
        assertEquals(List.of("Alpha Beta", "Gamma Delta"), fit.lines());
        assertEquals(0.75f, fit.scale());
        assertFalse(fit.shortened());
    }

    @Test
    void aOneLineLabelShrinksRatherThanWraps() {
        TextFit.Fit fit = fit("Needs Forge III", 80, 9);
        assertEquals(List.of("Needs Forge III"), fit.lines());
        assertEquals(0.75f, fit.scale());
        assertFalse(fit.shortened());
    }

    @Test
    void asALastResortTheTextIsCutShortAtAWord() {
        TextFit.Fit fit = fit("One two three four five six seven eight nine ten", 60, 18);
        assertTrue(fit.shortened());
        assertEquals(0.75f, fit.scale());
        assertEquals(List.of("One two three", "four five..."), fit.lines());
        for (String line : fit.lines()) {
            assertTrue(width(line) <= 80, line + " is wider than the room");
        }
    }

    @Test
    void aWordWiderThanTheBoxIsSplitOnlyAtTheSmallestSize() {
        TextFit.Fit fit = fit("Supercalifragilistic", 60, 18);
        assertEquals(List.of("Supercalifrag", "ilistic"), fit.lines());
        assertEquals(0.75f, fit.scale());
        assertFalse(fit.shortened(), "every letter still shows");
    }

    @Test
    void nothingToDrawFitsAsNothing() {
        TextFit.Fit fit = fit("", 108, 18);
        assertTrue(fit.lines().isEmpty());
        assertFalse(fit.shortened());
    }
}
