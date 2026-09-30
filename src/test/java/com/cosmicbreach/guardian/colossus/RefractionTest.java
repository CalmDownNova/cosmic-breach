package com.cosmicbreach.guardian.colossus;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.cosmicbreach.guardian.colossus.Refraction.CORE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Refraction: crystals turning, the beam's path, and how the Colossus aims (Prism Colossus design v1). */
class RefractionTest {
    @Test
    void aTurnStepsClockwiseThroughTheFiveCrystalsAndTheCore() {
        int target = 1;
        int[] seen = new int[6];
        for (int i = 0; i < 6; i++) {
            seen[i] = target;
            target = Refraction.turn(0, target);
        }
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, CORE}, seen);
        assertEquals(1, target, "six turns come round again");
        assertEquals(CORE, Refraction.turn(3, 2), "crystal 3's target after 2 is the core (it never points at itself)");
        assertEquals(4, Refraction.turn(3, CORE));
    }

    @Test
    void stepsToCoreCountTheTurns() {
        for (int crystal = 0; crystal < 6; crystal++) {
            for (int target = 0; target <= 6; target++) {
                if (!Refraction.validTarget(crystal, target)) {
                    continue;
                }
                int t = target;
                int turns = 0;
                while (t != CORE) {
                    t = Refraction.turn(crystal, t);
                    turns++;
                }
                assertEquals(turns, Refraction.stepsToCore(crystal, target), "crystal " + crystal + " target " + target);
            }
        }
    }

    @Test
    void theColossusAimsWithTheCoreOneToThreeTurnsAway() {
        for (int crystal = 0; crystal < 6; crystal++) {
            int[] candidates = Refraction.aimCandidates(crystal);
            assertEquals(3, candidates.length);
            for (int c : candidates) {
                int steps = Refraction.stepsToCore(crystal, c);
                assertTrue(steps >= 1 && steps <= 3, "crystal " + crystal + " candidate " + c + " is " + steps + " turns away");
                assertNotEquals(crystal, c);
                assertNotEquals(CORE, c);
            }
        }
    }

    @Test
    void thePathVisitsThreeCrystalsAtMost() {
        int[] targets = Refraction.restingTargets();
        assertArrayEquals(new int[] {3, 4, 5, 0, 1, 2}, targets, "at rest each crystal points across the arena");
        assertArrayEquals(new int[] {0, 3, 0}, Refraction.path(0, targets), "0 to 3 and back to 0, where it dies out");
        targets[0] = 5;
        assertArrayEquals(new int[] {0, 5, 2}, Refraction.path(0, targets));
        assertFalse(Refraction.endsAtCore(Refraction.path(0, targets)));
    }

    @Test
    void aTargetOnTheCoreEndsThePathThere() {
        int[] targets = Refraction.restingTargets();
        targets[1] = CORE;
        assertArrayEquals(new int[] {1, CORE}, Refraction.path(1, targets));
        assertTrue(Refraction.endsAtCore(Refraction.path(1, targets)));
        targets[1] = 4;
        targets[4] = CORE;
        assertArrayEquals(new int[] {1, 4, CORE}, Refraction.path(1, targets), "the second crystal can end it too");
    }

    @Test
    void theColossusSendsTheBeamAcrossTheMostPlayersButNeverIntoItsCore() {
        int[] targets = Refraction.restingTargets();
        // players stand only on the segment from crystal 2 to crystal 5
        int aim = Refraction.chooseTarget(2, targets, path -> crosses(path, 2, 5) ? 1 : 0, n -> 0);
        assertEquals(5, aim, "crystal 5 is 3 turns before the core and the only path across the player");
        // crystal 1 is 1 turn from the core for crystal 2; make it lead into the core
        targets[1] = CORE;
        int[] seen = new int[3];
        for (int pick = 0; pick < 3; pick++) {
            int p = pick;
            seen[pick] = Refraction.chooseTarget(2, targets, path -> 0, n -> p);
        }
        for (int s : seen) {
            assertNotEquals(1, s, "a path through crystal 1 would end in the core");
        }
    }

    @Test
    void aPlayerCanAlwaysTurnTheBeamBackInThreeHitsOrFewer() {
        for (int crystal = 0; crystal < 6; crystal++) {
            for (int aim : Refraction.aimCandidates(crystal)) {
                int t = aim;
                int hits = 0;
                while (t != CORE) {
                    t = Refraction.turn(crystal, t);
                    hits++;
                }
                assertTrue(hits >= 1 && hits <= 3);
                int[] targets = Refraction.restingTargets();
                targets[crystal] = t;
                assertTrue(Refraction.endsAtCore(Refraction.path(crystal, targets)));
            }
        }
    }

    @Test
    void tiesArePickedAmongTheBestOnly() {
        int[] targets = Refraction.restingTargets();
        Set<Integer> picked = new HashSet<>();
        for (int pick = 0; pick < 6; pick++) {
            int p = pick;
            picked.add(Refraction.chooseTarget(0, targets, path -> 2, n -> p));
        }
        assertEquals(Set.of(5, 4, 3), picked, "all three candidates tie at 2 players, and each can come up");
    }

    @Test
    void phaseTwoLightsTwoDifferentCrystals() {
        int[] targets = Refraction.restingTargets();
        int[] lit = Refraction.light(2, targets, path -> Arrays.stream(path).anyMatch(n -> n == 4) ? 3 : 0, n -> 0);
        assertEquals(2, lit.length);
        assertNotEquals(lit[0], lit[1]);
        for (int k : lit) {
            int steps = Refraction.stepsToCore(k, targets[k]);
            assertTrue(steps >= 1 && steps <= 3, "each lit crystal is aimed with the core 1 to 3 turns away");
            assertFalse(Refraction.endsAtCore(Refraction.path(k, targets)));
        }
    }

    @Test
    void anyScoreWorksEvenWhenNobodyIsCrossed() {
        // the Colossus scores a path by players crossed, then by how near a player the lit crystal is: often negative
        int[] targets = Refraction.restingTargets();
        int[] lit = Refraction.light(2, targets, path -> -10 * (path[0] + 1), n -> 0);
        assertArrayEquals(new int[] {0, 1}, lit, "the highest (least negative) scores win");
        int aim = Refraction.chooseTarget(3, targets, path -> -5, n -> 0);
        assertTrue(Refraction.stepsToCore(3, aim) >= 1 && Refraction.stepsToCore(3, aim) <= 3);
    }

    @Test
    void afterTheBeamCrystalsOnTheCoreSettleBackAcross() {
        int[] targets = Refraction.restingTargets();
        targets[2] = CORE;
        targets[5] = 1;
        Refraction.settle(targets);
        assertEquals(5, targets[2], "back to the opposite crystal");
        assertEquals(1, targets[5], "a crystal left on another crystal keeps it");
    }

    private static boolean crosses(int[] path, int a, int b) {
        for (int i = 0; i + 1 < path.length; i++) {
            if ((path[i] == a && path[i + 1] == b) || (path[i] == b && path[i + 1] == a)) {
                return true;
            }
        }
        return false;
    }
}
