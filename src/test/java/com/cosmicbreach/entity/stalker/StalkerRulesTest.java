package com.cosmicbreach.entity.stalker;

import com.cosmicbreach.entity.stalker.StalkerRules.Attack;
import com.cosmicbreach.entity.stalker.StalkerRules.StepCandidate;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hollow Stalker's rules (GDD 7.1): shadow routing's prices, your back, the caught freeze, Shadow Step, its attacks. */
class StalkerRulesTest {
    private static final Vec3 FEET = new Vec3(0.5, 64, 0.5);
    private static final Vec3 EYE = FEET.add(0, 1.62, 0);
    /** Looking south (+Z). */
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);

    // ------------------------------------------------------------------ shadow routing

    @Test
    void theNumbersAreTheGdds() {
        assertEquals(40.0, StalkerRules.HEALTH);
        assertEquals(6.0, StalkerRules.ARMOR);
        assertEquals(20.0, StalkerRules.POISE);
        assertEquals(0.30, StalkerRules.SPEED);
        assertEquals(2.6f, StalkerRules.HEIGHT);
        assertEquals(180, StalkerRules.XP);
        assertEquals(100, StalkerRules.STEP_COOLDOWN);
        assertEquals(10.0, StalkerRules.STEP_RANGE);
        assertEquals(12.0, StalkerRules.REND_DAMAGE);
        assertEquals(600, StalkerRules.GRASP_COOLDOWN, "once per 30 s");
    }

    @Test
    void litGroundCostsEightTimesAStepAndBrightLightIsAWall() {
        assertEquals(0.0f, StalkerRules.pathMalus(7, 7, 0.0f), "dark: an ordinary step");
        float lit = StalkerRules.pathMalus(8, 8, 0.0f);
        assertEquals(8.0, 1.0 + lit, 1e-6, "a straight step (1) on lit ground costs 8");
        assertEquals(8.0, 1.0 + StalkerRules.pathMalus(15, 0, 0.0f), 1e-6, "sky light counts as lit too");
        assertEquals(-1.0f, StalkerRules.pathMalus(12, 12, 0.0f), "block light 12: never entered");
        assertEquals(-1.0f, StalkerRules.pathMalus(15, 15, 0.0f));
        assertEquals(-1.0f, StalkerRules.pathMalus(3, 3, -1.0f), "a wall stays a wall");
        assertEquals(10.0f, StalkerRules.pathMalus(9, 0, 3.0f), 1e-6, "on top of the node's own malus");
    }

    @Test
    void skyLightFadesWithTheDayAndTheDeepsShade() {
        assertEquals(15, StalkerRules.effectiveLight(0, 15, 0, false), "noon, open sky: lit");
        assertEquals(4, StalkerRules.effectiveLight(0, 15, 11, false), "midnight: dark");
        assertEquals(6, StalkerRules.effectiveLight(0, 15, 0, true), "the Deep at noon: shaded to 6, dark");
        assertEquals(12, StalkerRules.effectiveLight(12, 15, 0, true), "a torch is a torch anywhere");
        assertTrue(StalkerRules.dark(7, 7));
        assertFalse(StalkerRules.dark(8, 0));
        assertFalse(StalkerRules.dark(3, 12), "can't be dark where it won't stand");
    }

    // ------------------------------------------------------------------ your back, caught

    @Test
    void itKnowsWhenYourBackIsTurned() {
        Vec3 ahead = FEET.add(0, 2.3, 6);
        Vec3 behind = FEET.add(0, 2.3, -6);
        Vec3 side = FEET.add(6, 2.3, 0);
        assertFalse(StalkerRules.turnedAway(EYE, SOUTH, ahead));
        assertTrue(StalkerRules.turnedAway(EYE, SOUTH, behind));
        assertFalse(StalkerRules.turnedAway(EYE, SOUTH, side), "90 degrees off is not more than 100");
        Vec3 behindSide = FEET.add(6, 2.3, -2); // about 108 degrees off the view
        assertTrue(StalkerRules.turnedAway(EYE, SOUTH, behindSide));
    }

    @Test
    void lookedAtDirectlyNearbyItIsCaught() {
        assertTrue(StalkerRules.caught(5.0, 3.0, true, false));
        assertTrue(StalkerRules.caught(6.0, 15.0, true, false), "the edges count");
        assertFalse(StalkerRules.caught(6.5, 3.0, true, false), "too far");
        assertFalse(StalkerRules.caught(4.0, 20.0, true, false), "not looked at directly");
        assertFalse(StalkerRules.caught(4.0, 3.0, false, false), "a wall between");
        assertFalse(StalkerRules.caught(4.0, 3.0, true, true), "just after being caught");
        assertEquals(10, StalkerRules.CAUGHT_TICKS);
    }

    // ------------------------------------------------------------------ Shadow Step

    @Test
    void aStepGoesToTheDarkSpotNearestBehindYou() {
        Vec3 from = new Vec3(8.5, 64, -6.5);
        List<StepCandidate> spots = List.of(
                new StepCandidate(new Vec3(0.5, 64, -2.0), 3, 3),     // right behind: the one
                new StepCandidate(new Vec3(0.5, 64, -8.0), 3, 3),     // further behind
                new StepCandidate(new Vec3(0.5, 64, 3.0), 3, 3),      // in front: seen
                new StepCandidate(new Vec3(1.0, 64, -2.2), 10, 10));  // behind but lit
        Optional<Vec3> step = StalkerRules.chooseStep(spots, from, FEET, EYE, SOUTH, 10.0);
        assertEquals(new Vec3(0.5, 64, -2.0), step.orElseThrow());
    }

    @Test
    void aStepNeverLandsInLightWhereYouLookOrBeyondItsReach() {
        Vec3 from = new Vec3(0.5, 64, -20);
        List<StepCandidate> spots = List.of(
                new StepCandidate(new Vec3(0.5, 64, -2.5), 3, 3),      // ideal, but 17.5 away
                new StepCandidate(new Vec3(0.5, 64, -11.0), 2, 2));    // 9 away: in reach
        assertEquals(new Vec3(0.5, 64, -11.0), StalkerRules.chooseStep(spots, from, FEET, EYE, SOUTH, 10.0).orElseThrow());
        assertEquals(new Vec3(0.5, 64, -2.5), StalkerRules.chooseStep(spots, from, FEET, EYE, SOUTH,
                StalkerRules.stepRange(2.0)).orElseThrow(), "an Eclipse Surge doubles its reach");
        List<StepCandidate> bad = List.of(
                new StepCandidate(new Vec3(0.5, 64, -2.5), 12, 12),    // bright
                new StepCandidate(new Vec3(0.5, 64, 4.0), 0, 0),       // in view
                new StepCandidate(new Vec3(0.5, 64, -0.5), 0, 0));     // on top of you
        assertTrue(StalkerRules.chooseStep(bad, from, FEET, EYE, SOUTH, 30.0).isEmpty());
    }

    @Test
    void itStepsOnlyUnwatchedOffCooldownAndFromAfar() {
        assertTrue(StalkerRules.wantsStep(0, true, 8.0));
        assertFalse(StalkerRules.wantsStep(1, true, 8.0), "cooling down");
        assertFalse(StalkerRules.wantsStep(0, false, 8.0), "watched");
        assertFalse(StalkerRules.wantsStep(0, true, 3.0), "already close: it walks");
        assertEquals(20.0, StalkerRules.stepRange(2.0));
        assertEquals(10.0, StalkerRules.stepRange(1.0));
    }

    // ------------------------------------------------------------------ attacks

    @Test
    void graspOnlyInTheDarkFromBehindOnceItIsReady() {
        assertTrue(StalkerRules.canGrasp(true, true, 0, 2.0));
        assertFalse(StalkerRules.canGrasp(false, true, 0, 2.0), "you stand in light");
        assertFalse(StalkerRules.canGrasp(true, false, 0, 2.0), "you face it");
        assertFalse(StalkerRules.canGrasp(true, true, 20, 2.0), "used in the last 30 s");
        assertFalse(StalkerRules.canGrasp(true, true, 0, 3.0), "too far");
    }

    @Test
    void theHoldHurtsEveryTenTicksFourTimes() {
        int hits = 0;
        for (int t = 0; t <= 60; t++) {
            if (StalkerRules.graspHitsAt(t)) {
                hits++;
                assertEquals(0, t % 10);
            }
        }
        assertEquals(4, hits, "3 damage at 10, 20, 30 and 40");
    }

    @Test
    void itPicksItsAttackFromWhereYouLook() {
        assertEquals(Attack.GRASP, StalkerRules.pickAttack(true, true, 0, 2.0, 0));
        assertEquals(Attack.REND, StalkerRules.pickAttack(false, true, 0, 2.0, 0), "lit: no Grasp, a Rend");
        assertEquals(Attack.REND, StalkerRules.pickAttack(true, true, 100, 2.0, 0), "Grasp spent: a Rend");
        assertNull(StalkerRules.pickAttack(true, false, 0, 2.0, 0), "you face it: it waits");
        assertEquals(Attack.REND, StalkerRules.pickAttack(true, false, 0, 2.0, StalkerRules.PATIENCE),
                "until its patience runs out");
        assertNull(StalkerRules.pickAttack(true, true, 100, 5.0, 0), "too far for either");
    }

    @Test
    void naturalSpawnsNeedDarkGroundAndComeHalfAgainAsOftenInASurge() {
        assertTrue(StalkerRules.spawnRule(true, 4, 0, 0.3f, 1.0));
        assertFalse(StalkerRules.spawnRule(false, 4, 0, 0.3f, 1.0), "no ground");
        assertFalse(StalkerRules.spawnRule(true, 9, 0, 0.3f, 1.0), "lit ground");
        assertFalse(StalkerRules.spawnRule(true, 5, 5, 0.3f, 1.0), "lichen light keeps them off");
        assertFalse(StalkerRules.spawnRule(true, 4, 0, 0.6f, 1.0), "one attempt in two");
        assertTrue(StalkerRules.spawnRule(true, 4, 0, 0.6f, 1.5), "three in four in an Eclipse Surge");
    }

    @Test
    void theRendHitsInReachAndArcAndGlintsAtSixteen() {
        assertTrue(StalkerRules.rendHits(2.9, 30));
        assertFalse(StalkerRules.rendHits(3.2, 0), "dashed out of reach");
        assertFalse(StalkerRules.rendHits(2.0, 80), "slipped round its side");
        assertFalse(StalkerRules.rendGlinting(15));
        assertTrue(StalkerRules.rendGlinting(16));
        assertTrue(StalkerRules.rendGlinting(20));
    }
}
