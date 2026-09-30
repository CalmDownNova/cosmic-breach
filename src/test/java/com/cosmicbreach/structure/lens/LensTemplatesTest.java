package com.cosmicbreach.structure.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The handmade fallbacks: every difficulty has one, and each passes the generator's own checks. */
class LensTemplatesTest {
    @Test
    void everyTemplateIsAFairPuzzleOfItsDifficulty() {
        Set<String> covered = new HashSet<>();
        for (LensTemplates.Template t : LensTemplates.all()) {
            LensPuzzle p = LensTemplates.parse(t);
            assertTrue(p.template());
            assertNull(LensGeneratorTest.problem(p, t.difficulty()), t.difficulty().name() + ":\n" + p);
            LensSolver.Answer exact = LensSolver.solve(p, p.solutionMoves());
            assertEquals(p.minMoves(), exact.moves());
            assertTrue(exact.moves() <= p.solutionMoves());
            covered.add(t.difficulty().name());
        }
        for (LensDifficulty d : LensDifficulty.ALL) {
            assertTrue(covered.contains(d.name()), "no template for " + d.name());
            LensPuzzle fallback = LensTemplates.fallback(d, 12345L);
            assertEquals(d.size(), fallback.size());
        }
    }
}
