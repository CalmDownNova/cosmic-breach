package com.cosmicbreach.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.client.combat.BodyMotion;
import com.cosmicbreach.world.AetheriaGravity;
import com.cosmicbreach.world.gen.DriftReach.Crossing;
import org.junit.jupiter.api.Test;

/** The reach model the Drift's stepping stones and the layout test share: the audit's running jump table and the air dash on top. */
class DriftReachTest {
    @Test
    void aRunningJumpReachesTheAuditsFigures() {
        assertEquals(10.4, DriftReach.jump(0), 1e-9, "level");
        assertEquals(7.7, DriftReach.jump(3), 1e-9, "3 higher");
        assertEquals(12.9, DriftReach.jump(-5), 1e-9, "5 lower");
        assertEquals(14.9, DriftReach.jump(-10), 1e-9, "10 lower");
        assertEquals(18.3, DriftReach.jump(-20), 1e-9, "20 lower");
        assertEquals(0.0, DriftReach.jump(4.1), 1e-9, "the apex is 4.1 blocks");
        assertEquals(0.0, DriftReach.jump(6), 1e-9, "no climbing past the apex");
        assertEquals(18.3 + 0.34 * 10, DriftReach.jump(-30), 1e-9, "past the table a block of fall buys a little more");
    }

    @Test
    void theReachFallsAsTheLandingRisesAndGrowsAsItDrops() {
        double last = Double.MAX_VALUE;
        for (double rise = -25; rise <= 5; rise += 0.5) {
            double reach = DriftReach.jump(rise);
            assertTrue(reach <= last + 1e-9, "the reach never grows as the landing rises: " + rise);
            last = reach;
        }
    }

    @Test
    void aComfortableHopLeavesAFifthOfTheReachToSpare() {
        assertEquals(0.8, DriftReach.COMFORT, 1e-9);
        assertTrue(DriftReach.comfortable(8.3, 0), "four fifths of a level reach is 8.32");
        assertFalse(DriftReach.comfortable(8.4, 0));
        assertTrue(DriftReach.comfortable(10.3, 5), "a drop of 5 reaches 12.9, four fifths of it is 10.32");
        assertTrue(DriftReach.comfortable(10.3, -5), "the sign of the difference does not matter");
        assertFalse(DriftReach.comfortable(10.4, 5));
        assertFalse(DriftReach.comfortable(10.4, 0), "the full reach is not comfortable");
    }

    /**
     * The table is the audit's estimate for the game's own numbers, and DriftBelts lays its stones by it: retune gravity, the jump
     * or the air dash and the stones silently follow the old physics, and any edit to the table changes which stones generate
     * (new chunks only: a seam in every world generated before the edit). So the inputs are pinned, and a short tick simulation
     * of vanilla air movement from them is compared with the table (A2 quality review, Minor 5). If this fails, remeasure the
     * table before touching the test.
     */
    @Test
    void theTableFollowsFromTheGamesOwnNumbers() {
        assertEquals(0.4, AetheriaGravity.DRIFT, 1e-9, "gravity in the Drift: the table was measured for 0.4");
        assertEquals(0.3, AetheriaGravity.LOW_GRAVITY_JUMP, 1e-9, "jump strength in low gravity: the table was measured for x1.3");
        assertEquals(3.5, BodyMotion.DASH_AIR_BLOCKS, 1e-9, "an air dash's length: DASH_BONUS was measured for 3.5 blocks");
        assertEquals(8, BodyMotion.DASH_TICKS, "an air dash's duration: DASH_BONUS was measured for 8 ticks");

        double gravity = 0.08 * AetheriaGravity.DRIFT;
        double jump = 0.42 * (1.0 + AetheriaGravity.LOW_GRAVITY_JUMP);
        assertEquals(4.1, apex(gravity, jump), 0.05, "the table's apex");
        for (double rise : new double[] {3.0, 0.0, -5.0, -10.0, -20.0}) {
            assertEquals(DriftReach.jump(rise), reach(rise, gravity, jump), 0.35, "the table's reach to a landing " + rise + " above the takeoff");
        }
    }

    /** The height a jump with this gravity a tick and this takeoff speed reaches: vanilla moves first, then slows. */
    private static double apex(double gravity, double jump) {
        double vy = jump;
        double y = 0;
        while (vy > 0) {
            y += vy;
            vy = (vy - gravity) * 0.98;
        }
        return y;
    }

    /**
     * How far a sprint jump carries, edge to edge, to a landing {@code rise} above the takeoff, in vanilla's terms: sprint speed on
     * the ground (0.13 a tick, 0.546 friction), the sprint jump's 0.2 boost, the jump tick still on ground friction, then 0.026 of
     * air control and 0.91 drag a tick, and (v - gravity) * 0.98 vertically after each move; plus 0.6 for the body's width (it
     * takes off with its centre up to 0.3 past the edge and lands once its edge touches the far one). The audit's table rounds to a
     * tenth and the simulation's takeoff and landing are simplified, hence the tolerance above.
     */
    private static double reach(double rise, double gravity, double jump) {
        double groundFriction = 0.6 * 0.91;
        double speed = 0.13;
        double vx = speed * groundFriction / (1 - groundFriction) + 0.2;
        double vy = jump;
        double x = 0;
        double y = 0;
        boolean ground = true;
        for (int tick = 0; tick < 400; tick++) {
            double step = vx + (ground ? speed : 0.026);
            double nx = x + step;
            double ny = y + vy;
            if (vy < 0 && ny <= rise) {
                return x + step * (y - rise) / (y - ny) + 0.6;
            }
            x = nx;
            y = ny;
            vx = step * (ground ? groundFriction : 0.91);
            vy = (vy - gravity) * 0.98;
            ground = false;
        }
        throw new AssertionError("the jump never came down to " + rise);
    }

    @Test
    void aGapIsJudgedFromTheHigherPad() {
        assertEquals(Crossing.JUMP, DriftReach.crossing(10.4, 0), "a level gap at the reach");
        assertEquals(Crossing.DASH, DriftReach.crossing(10.5, 0), "just past it");
        assertEquals(Crossing.DASH, DriftReach.crossing(10.4 + DriftReach.DASH_BONUS, 0), "at the dash");
        assertEquals(Crossing.MOUNT, DriftReach.crossing(10.5 + DriftReach.DASH_BONUS, 0), "past the dash");
        assertEquals(Crossing.JUMP, DriftReach.crossing(12.9, 5), "a drop of 5 reaches 12.9");
        assertEquals(Crossing.JUMP, DriftReach.crossing(12.9, -5), "the sign of the difference does not matter");
        assertEquals(Crossing.DASH, DriftReach.crossing(14.0, 5));
        assertEquals(Crossing.MOUNT, DriftReach.crossing(40.0, 0), "the lanes are for the window and the mounts");
    }
}
