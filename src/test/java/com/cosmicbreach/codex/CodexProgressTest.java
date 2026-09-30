package com.cosmicbreach.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodexProgressTest {
    private static CodexProgress.Facts facts(boolean inAetheria, boolean drift, boolean deep, boolean sanctum, boolean colossus,
            boolean leviathan, boolean unsung, boolean sealed) {
        return new CodexProgress.Facts(inAetheria, drift, deep, sanctum, colossus, leviathan, unsung, sealed);
    }

    private static final CodexProgress.Facts NOTHING = facts(false, false, false, false, false, false, false, false);

    @Test
    void keysRoundTrip() {
        for (CodexChapter c : CodexChapter.values()) {
            assertEquals(c, CodexChapter.byKey(c.key()).orElseThrow());
        }
        assertEquals(CodexChapter.MET_COLOSSUS, CodexChapter.byKey(" Met_Colossus ").orElseThrow());
        assertTrue(CodexChapter.byKey("nope").isEmpty());
    }

    @Test
    void bitsAreTheOrdinals() {
        // the save format: a chapter's bit never moves
        assertEquals(1L, CodexChapter.ARRIVED.bit());
        assertEquals(2L, CodexChapter.DRIFT.bit());
        assertEquals(1L << 8, CodexChapter.SEALED.bit());
    }

    @Test
    void conditions() {
        CodexProgress p = CodexProgress.NONE.with(CodexChapter.ARRIVED).with(CodexChapter.MET_COLOSSUS);
        assertTrue(p.satisfies(""));
        assertTrue(p.satisfies(null));
        assertTrue(p.satisfies("arrived"));
        assertFalse(p.satisfies("drift"));
        assertTrue(p.satisfies("!drift"));
        assertFalse(p.satisfies("!arrived"));
        assertTrue(p.satisfies("drift|met_colossus"));
        assertFalse(p.satisfies("drift|deep"));
        assertTrue(p.satisfies("arrived,!drift"));
        assertFalse(p.satisfies("arrived,drift"));
        assertFalse(p.satisfies("unknown"));
        assertFalse(p.satisfies("!unknown"));
        assertTrue(CodexProgress.valid("arrived, !drift | met_unsung"));
        assertFalse(CodexProgress.valid("arrived,dirft"));
    }

    @Test
    void nothingKnownOpensNothing() {
        assertEquals(0L, CodexProgress.derive(CodexProgress.NONE, NOTHING).bits());
    }

    @Test
    void arrivingOpensTheReach() {
        CodexProgress p = CodexProgress.derive(CodexProgress.NONE, facts(true, false, false, false, false, false, false, false));
        assertEquals(List.of(CodexChapter.ARRIVED), p.newSince(CodexProgress.NONE));
    }

    @Test
    void aChapterIsNeverTakenBack() {
        CodexProgress opened = CodexProgress.NONE.with(CodexChapter.ARRIVED).with(CodexChapter.MET_LEVIATHAN);
        assertEquals(opened, CodexProgress.derive(opened, NOTHING));
    }

    @Test
    void attunementsImplyArrivalAndMeetings() {
        CodexProgress p = CodexProgress.derive(CodexProgress.NONE, facts(false, true, true, true, false, false, false, false));
        for (CodexChapter c : List.of(CodexChapter.ARRIVED, CodexChapter.DRIFT, CodexChapter.DEEP, CodexChapter.SANCTUM,
                CodexChapter.MET_COLOSSUS, CodexChapter.MET_LEVIATHAN, CodexChapter.MET_UNSUNG)) {
            assertTrue(p.has(c), c.key());
        }
        assertFalse(p.has(CodexChapter.MET_HELIARCH));
        assertFalse(p.has(CodexChapter.SEALED));
    }

    @Test
    void killsImplyMeetings() {
        CodexProgress p = CodexProgress.derive(CodexProgress.NONE, facts(true, false, false, false, true, true, true, false));
        assertTrue(p.has(CodexChapter.MET_COLOSSUS));
        assertTrue(p.has(CodexChapter.MET_LEVIATHAN));
        assertTrue(p.has(CodexChapter.MET_UNSUNG));
        assertFalse(p.has(CodexChapter.DRIFT));
    }

    @Test
    void sealingTheBreachMeansTheHeliarchWasMet() {
        CodexProgress p = CodexProgress.derive(CodexProgress.NONE, facts(false, false, false, false, false, false, false, true));
        assertTrue(p.has(CodexChapter.SEALED));
        assertTrue(p.has(CodexChapter.MET_HELIARCH));
        assertTrue(p.has(CodexChapter.ARRIVED));
    }
}
