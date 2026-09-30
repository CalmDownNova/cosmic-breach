package com.cosmicbreach.structure.trap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Speed over three ticks, timed by the client's movement packets: a walker whose packets reach a busy server bunched,
 * after a stall or right after two ordinary ticks, never looks like a sprinter to a Kinetic Tripwire; sprinting and
 * dashing still fire it, bunched or not; standing still sends nothing and reads as nothing.
 */
class MotionWindowTest {
    private static final double WALK = KineticRules.WALK_SPEED;
    private static final double SPRINT = WALK * 1.3;
    private static final double DASH = 5.0 / 8.0;

    /** Steady movement, a packet a tick: {@code ticks} ticks of {@code speed}, ending at x. */
    private static double run(MotionWindow w, double x, double speed, int ticks) {
        for (int i = 0; i < ticks; i++) {
            x += speed;
            w.endTick(x, 0, 1);
        }
        return x;
    }

    @Test
    void aSteadyWalkIsWalkingPace() {
        MotionWindow w = new MotionWindow();
        double x = run(w, 0, WALK, 10);
        assertEquals(WALK, w.average(), 1e-9);
        assertEquals(WALK, w.averageWith(x + WALK, 0, 1), 1e-9, "mid-tick, with this tick's packet");
        assertFalse(KineticRules.triggers(w.averageWith(x + WALK, 0, 1)));
    }

    @Test
    void aWalkersPacketsBunchedRightAfterOrdinaryTicksNeverLookLikeASprint() {
        // a long server tick: two ticks of walking reach the next one together, just after two ordinary ticks
        MotionWindow w = new MotionWindow();
        double x = run(w, 0, WALK, 10);
        double first = w.averageWith(x + WALK, 0, 1);
        double second = w.averageWith(x + 2 * WALK, 0, 2);
        assertEquals(WALK, first, 1e-9);
        assertEquals(WALK, second, 1e-9, "four ticks' walking over the four packets that carried it");
        assertFalse(KineticRules.triggers(second));
        assertTrue(KineticRules.triggers((WALK + WALK + 2 * WALK) / 3), "timed by server ticks, the same walk fires the thread");
        w.endTick(x + 2 * WALK, 0, 2);
        w.endTick(x + 2 * WALK, 0, 0);
        assertEquals(WALK, w.average(), 1e-9, "and the empty tick after it is no time at all");
    }

    @Test
    void aWalkersPacketsBunchedAfterAStallNeverLookLikeASprint() {
        MotionWindow w = new MotionWindow();
        double x = run(w, 0, WALK, 10);
        // nothing arrives for two server ticks, then three ticks of walking in one, packet by packet
        w.endTick(x, 0, 0);
        w.endTick(x, 0, 0);
        for (int packet = 1; packet <= 3; packet++) {
            double mid = w.averageWith(x + WALK * packet, 0, packet);
            assertEquals(WALK, mid, 1e-9, "packet " + packet);
            assertFalse(KineticRules.triggers(mid), "packet " + packet + " reads " + mid);
            if (packet >= 2) {
                assertTrue(KineticRules.triggers(WALK * packet), "a per-tick reading fires on packet " + packet);
            }
        }
        w.endTick(x + 3 * WALK, 0, 3);
        assertEquals(WALK, w.average(), 1e-9, "three ticks' walking over three packets");
    }

    @Test
    void sprintingAndDashingStillFire() {
        MotionWindow w = new MotionWindow();
        double x = run(w, 0, SPRINT, 8);
        assertTrue(KineticRules.triggers(w.averageWith(x + SPRINT, 0, 1)), "a steady sprint");
        MotionWindow b = new MotionWindow();
        double y = run(b, 0, SPRINT, 8);
        b.endTick(y, 0, 0);
        b.endTick(y, 0, 0);
        assertTrue(KineticRules.triggers(b.averageWith(y + 3 * SPRINT, 0, 3)), "a sprint's packets bunched after a stall");
        MotionWindow c = new MotionWindow();
        double z = run(c, 0, SPRINT, 8);
        assertTrue(KineticRules.triggers(c.averageWith(z + 2 * SPRINT, 0, 2)), "a sprint's packets bunched right after ordinary ticks");
        MotionWindow d = new MotionWindow();
        double q = run(d, 0, WALK, 5);
        q = run(d, q, DASH, 2);
        assertTrue(KineticRules.triggers(d.averageWith(q + DASH, 0, 1)), "a dash from a walk");
        MotionWindow e = new MotionWindow();
        double r = run(e, 0, WALK, 5);
        e.endTick(r, 0, 0);
        e.endTick(r, 0, 0);
        assertTrue(KineticRules.triggers(e.averageWith(r + 3 * DASH, 0, 3)), "a dash the server sees all at once");
    }

    @Test
    void standingStillReadsAsStill() {
        MotionWindow w = new MotionWindow();
        double x = run(w, 0, WALK, 5);
        for (int i = 0; i < MotionWindow.TICKS; i++) {
            w.endTick(x, 0, 0);
        }
        assertEquals(0, w.average(), 1e-9, "three ticks with nothing sent: standing");
        for (int i = 0; i < MotionWindow.TICKS; i++) {
            w.endTick(x, 0, 1);
        }
        assertEquals(0, w.average(), 1e-9, "turning on the spot sends packets that move nothing");
        assertEquals(1, MotionWindow.clientTicks(0.2, 0), "moved by the server without a packet: a tick");
        assertEquals(0, MotionWindow.clientTicks(0.0, 0));
        assertEquals(2, MotionWindow.clientTicks(0.4, 2));
    }

    @Test
    void aTeleportStartsTheWindowOver() {
        MotionWindow w = new MotionWindow();
        run(w, 0, SPRINT, 5);
        w.endTick(100, 0, 1);
        assertEquals(0, w.average(), 1e-9);
        assertEquals(0, w.averageWith(200, 0, 1), 1e-9, "a jump mid-tick is a teleport too");
        assertEquals(0, new MotionWindow().averageWith(5, 5, 1), 1e-9, "nothing known yet: standing");
    }
}
