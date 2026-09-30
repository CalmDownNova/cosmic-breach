package com.cosmicbreach.structure.crypt.trap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** GDD 6.4's trap timings: Void Rift tiles, Crushing Gravity Plates, Starfall Chutes and the Void Pocket. */
class CryptTrapRulesTest {
    private static final double WALK = 0.21585;
    private static final double SNEAK = WALK * 0.3;
    private static final double SPRINT = WALK * 1.3;
    private static final double DASH = 5.0 / 8.0;

    @Test
    void standingStillSpringsTheRiftOnItsSixthTickAndOpensOnTickSixteen() {
        int count = 0;
        for (int t = 0; t < 5; t++) {
            count = VoidRiftRules.count(count, true, VoidRiftRules.standing(true, false, 0.0));
            assertFalse(VoidRiftRules.springs(count), "tick " + t);
        }
        count = VoidRiftRules.count(count, true, VoidRiftRules.standing(true, false, 0.0));
        assertTrue(VoidRiftRules.springs(count), "the sixth standing tick springs it");
        assertEquals(16, VoidRiftRules.crossing(new double[0], new boolean[0]), "standing still: it opens on tick 16");
        assertEquals(11, VoidRiftRules.openDelay());
        assertEquals(60, VoidRiftRules.OPEN_TICKS);
    }

    @Test
    void onlyTrueStandingCounts() {
        assertTrue(VoidRiftRules.standing(true, false, 0.0));
        assertTrue(VoidRiftRules.standing(true, false, WALK / 5), "a shuffle is still standing");
        assertFalse(VoidRiftRules.standing(true, false, WALK / 4), "a quarter of walking pace is moving");
        assertFalse(VoidRiftRules.standing(false, false, 0.0), "in the air");
        assertFalse(VoidRiftRules.standing(true, true, 0.0), "dashing, even if the server thinks you are still");
        assertEquals(3, VoidRiftRules.count(3, true, false), "moving on the cluster keeps the count");
        assertEquals(4, VoidRiftRules.count(3, true, true));
        assertEquals(0, VoidRiftRules.count(5, false, true), "stepping off starts it again");
    }

    @Test
    void walkingSprintingSneakingAndDashingAcrossNeverSpringIt() {
        boolean[] none = new boolean[0];
        assertEquals(-1, VoidRiftRules.crossing(VoidRiftRules.steady(WALK, 40), none));
        assertEquals(-1, VoidRiftRules.crossing(VoidRiftRules.steady(SPRINT, 40), none));
        assertEquals(-1, VoidRiftRules.crossing(VoidRiftRules.steady(SNEAK, 80), none), "a sneak is not standing still");
        boolean[] dash = new boolean[8];
        java.util.Arrays.fill(dash, true);
        assertEquals(-1, VoidRiftRules.crossing(VoidRiftRules.steady(DASH, 8), dash));
    }

    @Test
    void bunchedMovementUnderLoadNeverSpringsIt() {
        // a walk whose packets reach a busy server in bursts: two ticks with none, then three ticks' walking at once
        double[] walk = new double[60];
        int[] walkPackets = new int[60];
        for (int t = 0; t < walk.length; t++) {
            walk[t] = t % 3 == 2 ? 3 * WALK : 0.0;
            walkPackets[t] = t % 3 == 2 ? 3 : 0;
        }
        assertEquals(-1, VoidRiftRules.crossing(walk, walkPackets, new boolean[0]));
        // after a long server tick: two ticks' walking in one, then a tick with nothing left to arrive
        double[] after = new double[60];
        int[] afterPackets = new int[60];
        for (int t = 0; t < after.length; t++) {
            int k = t % 4;
            after[t] = k == 2 ? 2 * WALK : k == 3 ? 0.0 : WALK;
            afterPackets[t] = k == 2 ? 2 : k == 3 ? 0 : 1;
        }
        assertEquals(-1, VoidRiftRules.crossing(after, afterPackets, new boolean[0]));
        // a dash the server sees late: four ticks with nothing on the patch, then all of it at once
        double[] dashed = {0, 0, 0, 0, 0, 5.0, 0, 0};
        int[] dashPackets = {0, 0, 0, 0, 0, 8, 0, 0};
        boolean[] dashing = {true, true, true, true, true, true, true, true};
        assertEquals(-1, VoidRiftRules.crossing(dashed, dashPackets, dashing));
        // short stalls between bursts never add up to six standing ticks
        double[] stall = {0.1, 0, 0, 1.2, 0, 0, 1.2, 0, 0, 1.2};
        int[] stallPackets = {1, 0, 0, 3, 0, 0, 3, 0, 0, 3};
        assertEquals(-1, VoidRiftRules.crossing(stall, stallPackets, new boolean[0]));
    }

    @Test
    void stoppingOnThePatchStillDropsYou() {
        double[] stop = new double[40];
        stop[0] = 0.8;
        stop[1] = 0.2;
        int opened = VoidRiftRules.crossing(stop, new boolean[0]);
        assertEquals(VoidRiftRules.OPEN_AT, opened, "walk on, stop: it opens on tick 16 of the standing");
    }

    @Test
    void gravityPlateTimeline() {
        long start = 1000;
        assertTrue(GravityPlateRules.heavy(start, start));
        assertTrue(GravityPlateRules.heavy(start, start + 59));
        assertFalse(GravityPlateRules.heavy(start, start + 60));
        assertTrue(GravityPlateRules.slams(start, start + 40));
        assertFalse(GravityPlateRules.slams(start, start + 39));
        assertEquals(0.0, GravityPlateRules.drop(36), 1e-9);
        assertEquals(1.0, GravityPlateRules.drop(40), 1e-9);
        assertEquals(1.0, GravityPlateRules.drop(46), 1e-9);
        assertEquals(0.0, GravityPlateRules.drop(56), 1e-9);
        assertFalse(GravityPlateRules.rearmed(start, start + 99));
        assertTrue(GravityPlateRules.rearmed(start, start + 100));
        assertEquals(3.0, GravityPlateRules.GRAVITY);
        assertEquals(0.4, GravityPlateRules.SPEED, 1e-9, "speed -60%");
        assertEquals(10.0f, GravityPlateRules.PISTON_DAMAGE);
        // at -60% walking pace, from the sigil's middle to its edge (2.5 blocks) takes 29 ticks: out before the piston
        double heavyWalk = WALK * GravityPlateRules.SPEED;
        assertTrue(2.5 / heavyWalk < GravityPlateRules.PISTON_TICK, "you can walk out before it slams");
        assertTrue(5.0 / heavyWalk > GravityPlateRules.PISTON_TICK, "but not cross the whole sigil");
    }

    @Test
    void chutesGlintTwentyTicksBeforeEachDrop() {
        ChuteRules.Pattern p = ChuteRules.Pattern.WAVE;
        int phase = p.phase(1);
        long release = 480 + phase;
        assertTrue(ChuteRules.releases(release, p.period, phase));
        assertTrue(ChuteRules.glinting(release - 20, p.period, phase));
        assertTrue(ChuteRules.glinting(release - 1, p.period, phase));
        assertFalse(ChuteRules.glinting(release - 21, p.period, phase));
        assertFalse(ChuteRules.glinting(release, p.period, phase));
        assertTrue(ChuteRules.lands(release + ChuteRules.FALL_TICKS, p.period, phase));
        assertEquals(8.0f, ChuteRules.DAMAGE);
        for (ChuteRules.Pattern pattern : ChuteRules.Pattern.values()) {
            assertEquals(0, pattern.period % 12, "patterns keep Vesper's beat");
            for (int i = 0; i < 3; i++) {
                assertEquals(0, pattern.phase(i) % 6, "releases fall on beats and half beats");
            }
        }
    }

    @Test
    void everyChutePatternCanBeWalkedThroughWithTiming() {
        int[] strips = {1, 4, 7};
        for (ChuteRules.Pattern p : ChuteRules.Pattern.values()) {
            assertTrue(ChuteRules.walkable(p, strips, WALK, 0.6), p + " at walking pace");
            assertTrue(ChuteRules.walkable(p, strips, SPRINT, 0.6), p + " sprinting");
        }
    }

    @Test
    void thePocketOpensWhenItsStalkersDieOrAfterFortyFiveSeconds() {
        assertFalse(PocketRules.opens(0, 899, 3, 1));
        assertTrue(PocketRules.opens(0, 900, 3, 1));
        assertTrue(PocketRules.opens(0, 200, 3, 0), "all three dead");
        assertFalse(PocketRules.opens(0, 200, 0, 0), "with no Stalker in the game yet, only the timer opens it");
        assertTrue(PocketRules.opens(0, 900, 0, 0));
        assertFalse(PocketRules.opens(-1, 5000, 0, 0), "not woken");
    }
}
