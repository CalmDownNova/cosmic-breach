package com.cosmicbreach.guardian.colossus;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

import static com.cosmicbreach.guardian.colossus.Refraction.CORE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Refraction: crystals turning, the beam's path, the body in the middle, and how the Colossus aims (1.2.1 rework). */
class RefractionTest {
    // ------------------------------------------------------------------ the body

    @Test
    void onlyALegToTheOppositeCrystalCrossesTheBody() {
        for (int a = 0; a < 6; a++) {
            for (int b = 0; b < 6; b++) {
                if (a == b) {
                    continue;
                }
                boolean opposite = b == Refraction.opposite(a);
                assertEquals(opposite, Refraction.crossesBody(a, b), "crystal " + a + " to " + b);
            }
        }
    }

    @Test
    void theBodyRadiusMatchesTheModelAndLeavesTheOtherLegsWellClear() {
        assertTrue(CrownArena.BODY_RADIUS >= 2.5 && CrownArena.BODY_RADIUS <= 3.0, "about the model's torso and shoulders");
        // a leg two crystals round passes 7 blocks from the centre, a neighbour leg 12: a body twice as wide is still clear
        assertFalse(Refraction.segmentNearCentre(14, 0, -7, 12.12, 6.9));
        assertTrue(Refraction.segmentNearCentre(14, 0, -14, 0, 0.1));
    }

    @Test
    void aLegThroughTheBodyEndsThereAsTurnedBack() {
        int[] targets = Refraction.restingTargets();
        targets[0] = 3; // set by hand across the arena (no rule ever aims it there)
        assertArrayEquals(new int[] {0, CORE}, Refraction.path(0, targets), "it ends at the body: the same as aiming at the core");
        assertTrue(Refraction.endsAtCore(Refraction.path(0, targets)));
        targets[0] = 1;
        targets[1] = 4;
        assertArrayEquals(new int[] {0, 1, CORE}, Refraction.path(0, targets), "the second leg too");
    }

    @Test
    void theOppositeCrystalIsNeverATargetTheDirectionToItIsTheCore() {
        for (int k = 0; k < 6; k++) {
            assertFalse(Refraction.validTarget(k, Refraction.opposite(k)));
            assertTrue(Refraction.validTarget(k, CORE));
            assertFalse(Refraction.validTarget(k, k));
            assertArrayEquals(new int[] {(k + 1) % 6, (k + 2) % 6, CORE, (k + 4) % 6, (k + 5) % 6}, Refraction.cycle(k));
        }
    }

    // ------------------------------------------------------------------ rest

    @Test
    void atRestEveryCrystalPointsTwoRoundAndNoRestingLegCrossesTheBody() {
        int[] targets = Refraction.restingTargets();
        assertArrayEquals(new int[] {2, 3, 4, 5, 0, 1}, targets);
        for (int k = 0; k < 6; k++) {
            assertTrue(Refraction.validTarget(k, targets[k]));
            assertFalse(Refraction.crossesBody(k, targets[k]), "crystal " + k + " at rest");
            int[] path = Refraction.path(k, targets);
            assertEquals(3, path.length);
            assertFalse(Refraction.endsAtCore(path), "a resting path never reaches the body");
        }
        assertArrayEquals(new int[] {0, 2, 4}, Refraction.path(0, targets), "a triangle round the body");
    }

    @Test
    void afterTheBeamEveryCrystalSettlesBackToRest() {
        int[] targets = Refraction.restingTargets();
        targets[2] = CORE;
        targets[5] = 1;
        targets[0] = 3;
        Refraction.settle(targets);
        assertArrayEquals(Refraction.restingTargets(), targets);
    }

    // ------------------------------------------------------------------ turning

    @Test
    void aTurnStepsClockwiseThroughFourCrystalsAndTheCore() {
        int target = 1;
        int[] seen = new int[5];
        for (int i = 0; i < 5; i++) {
            seen[i] = target;
            target = Refraction.turn(0, target);
        }
        assertArrayEquals(new int[] {1, 2, CORE, 4, 5}, seen);
        assertEquals(1, target, "five turns come round again");
        assertEquals(CORE, Refraction.turn(3, 5), "crystal 3's target after 5 is the core (0, behind the body, is the core)");
        assertEquals(1, Refraction.turn(3, CORE));
        assertEquals(Refraction.resting(4), Refraction.turn(4, 4), "an invalid target turns to rest");
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

    // ------------------------------------------------------------------ aiming

    @Test
    void theColossusAimsWithTheCoreOneToThreeTurnsAway() {
        for (int crystal = 0; crystal < 6; crystal++) {
            int[] candidates = Refraction.aimCandidates(crystal);
            assertArrayEquals(new int[] {(crystal + 2) % 6, (crystal + 1) % 6, (crystal + 5) % 6}, candidates, "nearest the core first");
            for (int c : candidates) {
                int steps = Refraction.stepsToCore(crystal, c);
                assertTrue(steps >= 1 && steps <= 3, "crystal " + crystal + " candidate " + c + " is " + steps + " turns away");
                assertNotEquals(crystal, c);
                assertNotEquals(CORE, c);
                assertFalse(Refraction.crossesBody(crystal, c));
            }
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
                assertArrayEquals(new int[] {crystal, CORE}, Refraction.path(crystal, targets));
            }
        }
    }

    @Test
    void theColossusNeverAimsAPathThroughItsBodyFromAnyRing() {
        // every ring a player can leave behind (any crystal on any valid target) and every way of scoring paths
        int[][] scorers = {{0}, {1}, {2}};
        int rings = 0;
        int[] valid = new int[6 * 5];
        for (int lit = 0; lit < 6; lit++) {
            for (int other = 0; other < 6; other++) {
                if (other == lit) {
                    continue;
                }
                for (int t : Refraction.cycle(other)) {
                    if (t == CORE) {
                        continue; // settle runs before every Refraction; a crystal on the core is the unsettled case below
                    }
                    int[] targets = Refraction.restingTargets();
                    targets[other] = t;
                    for (int[] s : scorers) {
                        int mode = s[0];
                        int aim = Refraction.chooseTarget(lit, targets, path -> mode == 0 ? 0 : mode == 1 ? path.length : -path[path.length - 1],
                                n -> 0);
                        targets[lit] = aim;
                        int[] path = Refraction.path(lit, targets);
                        assertFalse(Refraction.endsAtCore(path), "lit " + lit + " ring " + Arrays.toString(targets));
                        for (int i = 0; i + 1 < path.length; i++) {
                            assertFalse(Refraction.crossesBody(path[i], path[i + 1]));
                        }
                        rings++;
                    }
                }
            }
        }
        assertTrue(rings > 100);
    }

    @Test
    void theColossusSendsTheBeamAcrossTheMostPlayersButNeverIntoItsCore() {
        int[] targets = Refraction.restingTargets();
        // players stand only on the segment from crystal 2 to crystal 1
        int aim = Refraction.chooseTarget(2, targets, path -> crosses(path, 2, 1) ? 1 : 0, n -> 0);
        assertEquals(1, aim, "crystal 1 is 3 turns before the core and the only path across the player");
        // make crystal 3 lead into the core: a path through it would end there
        targets[3] = CORE;
        for (int pick = 0; pick < 3; pick++) {
            int p = pick;
            assertNotEquals(3, Refraction.chooseTarget(2, targets, path -> 0, n -> p), "a path through crystal 3 would end in the core");
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
        assertEquals(Set.of(2, 1, 5), picked, "all three candidates tie at 2 players, and each can come up");
    }

    // ------------------------------------------------------------------ phase 2: two lit crystals

    @Test
    void phaseTwoLightsTwoCrystalsNeverOppositeEachOther() {
        for (int favour = 0; favour < 6; favour++) {
            int f = favour;
            int[] targets = Refraction.restingTargets();
            int[] lit = Refraction.light(2, targets, path -> Arrays.stream(path).anyMatch(n -> n == f) ? 3 : 0, n -> 0);
            assertEquals(2, lit.length);
            assertNotEquals(lit[0], lit[1]);
            assertNotEquals(Refraction.opposite(lit[0]), lit[1], "the Colossus faces between them");
            for (int k : lit) {
                int steps = Refraction.stepsToCore(k, targets[k]);
                assertTrue(steps >= 1 && steps <= 3, "each lit crystal is aimed with the core 1 to 3 turns away");
                int[] path = Refraction.path(k, targets);
                assertFalse(Refraction.endsAtCore(path));
                for (int i = 0; i + 1 < path.length; i++) {
                    assertFalse(Refraction.crossesBody(path[i], path[i + 1]));
                }
            }
        }
    }

    @Test
    void inPhaseTwoEachLitCrystalCanStillBeTurnedBackOnItsOwn() {
        for (int favour = 0; favour < 6; favour++) {
            int f = favour;
            int[] base = Refraction.restingTargets();
            int[] lit = Refraction.light(2, base, path -> path[path.length - 1] == f ? 1 : 0, n -> 0);
            for (int k : lit) {
                int[] targets = base.clone();
                int hits = 0;
                while (targets[k] != CORE) {
                    targets[k] = Refraction.turn(k, targets[k]);
                    hits++;
                }
                assertTrue(hits >= 1 && hits <= 3);
                assertTrue(Refraction.endsAtCore(Refraction.path(k, targets)), "its own beam turns back");
                int other = lit[0] == k ? lit[1] : lit[0];
                int[] otherPath = Refraction.path(other, targets);
                // the other beam turns back too only if it bounces on off this crystal (as its second; the third is where it dies out)
                int[] otherBase = Refraction.path(other, base);
                assertEquals(otherBase[1] == k, Refraction.endsAtCore(otherPath));
            }
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

    // ------------------------------------------------------------------ the beam's points in the arena

    @Test
    void inTheArenaNoAimedLegComesNearTheBodyAndATurnedBackLegEndsOnIt() {
        CrownArena arena = new CrownArena(1000, 450, -2000);
        Vec3 eye = arena.eye(0f);
        Vec3 core = arena.core(0f, false);
        for (int lit = 0; lit < 6; lit++) {
            for (int aim : Refraction.aimCandidates(lit)) {
                int[] targets = Refraction.restingTargets();
                targets[lit] = aim;
                List<Vec3> pts = arena.beamPoints(Refraction.path(lit, targets), eye, core);
                assertEquals(4, pts.size());
                for (int i = 1; i + 1 < pts.size(); i++) {
                    Vec3 a = pts.get(i);
                    Vec3 b = pts.get(i + 1);
                    assertFalse(Refraction.segmentNearCentre(a.x - arena.x(), a.z - arena.z(), b.x - arena.x(), b.z - arena.z(),
                            CrownArena.BODY_RADIUS + 3.0), "lit " + lit + " aim " + aim + " leg " + i);
                }
                targets[lit] = CORE;
                pts = arena.beamPoints(Refraction.path(lit, targets), eye, core);
                assertEquals(3, pts.size());
                Vec3 end = pts.get(2);
                assertEquals(CrownArena.BODY_RADIUS, arena.distance(end.x, end.z), 1e-6, "it ends on the body's skin");
            }
        }
    }

    @Test
    void eachLegEndsOnTheFaceOfTheCrystalItStrikes() {
        CrownArena arena = new CrownArena(0, 64, 0);
        int[] targets = Refraction.restingTargets();
        targets[0] = 1;
        List<Vec3> pts = arena.beamPoints(Refraction.path(0, targets), arena.eye(90f), arena.core(90f, false));
        int[] path = Refraction.path(0, targets);
        for (int j = 0; j < path.length; j++) {
            Vec3 p = pts.get(j + 1);
            Vec3 axis = arena.crystalPoint(path[j]);
            double face = Math.max(Math.abs(p.x - axis.x), Math.abs(p.z - axis.z));
            assertEquals(CrownArena.CRYSTAL_HALF, face, 1e-6, "on crystal " + path[j] + "'s skin, not its middle");
            assertEquals(arena.floorY() + CrownArena.BEAM_HEIGHT, p.y, 1e-9);
        }
        // the bounce point faces both legs: nearer to where the light came from and where it goes than the axis is
        Vec3 bounce = pts.get(2);
        Vec3 axis = arena.crystalPoint(1);
        assertTrue(bounce.distanceTo(pts.get(1)) < axis.distanceTo(pts.get(1)));
        assertTrue(bounce.distanceTo(pts.get(3)) < axis.distanceTo(pts.get(3)));
    }

    @Test
    void theBodyHitIsWhereTheBeamEntersTheCylinder() {
        CrownArena arena = new CrownArena(0, 0, 0);
        Vec3 hit = arena.bodyHit(new Vec3(14, 1, 0), new Vec3(0, 5, 0));
        assertEquals(CrownArena.BODY_RADIUS, hit.x, 1e-9);
        assertEquals(1 + 4 * (14 - CrownArena.BODY_RADIUS) / 14, hit.y, 1e-9);
        Vec3 inside = new Vec3(1, 1, 0);
        assertEquals(new Vec3(0, 5, 0), arena.bodyHit(inside, new Vec3(0, 5, 0)), "from inside: the core itself");
        Vec3 outside = new Vec3(3.5, 2, 0);
        assertEquals(outside, arena.bodyHit(new Vec3(14, 1, 0), outside), "a core outside the body (a Break): reached first");
    }

    @Test
    void theFiredLightArrivesOnTheTickItsReachPassesAPoint() {
        for (double length = 0.5; length < 80.0; length += 0.7) {
            int tick = ColossusMoves.beamArrival(length);
            assertTrue(ColossusMoves.beamReach(tick) >= length, "reached by tick " + tick);
            assertTrue(tick == 0 || ColossusMoves.beamReach(tick - 1) < length, "not a tick before");
        }
        CrownArena arena = new CrownArena(0, 64, 0);
        int[] targets = Refraction.restingTargets();
        List<Vec3> pts = arena.beamPoints(Refraction.path(0, targets), arena.eye(-90f), arena.core(-90f, false));
        double full = ColossusMoves.along(pts, pts.size() - 1);
        assertTrue(ColossusMoves.beamArrival(full) <= 8, "a whole three-crystal path lights within 8 ticks of the fire: " + full);
        assertEquals(pts.get(0).distanceTo(pts.get(1)), ColossusMoves.along(pts, 1), 1e-9);
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
