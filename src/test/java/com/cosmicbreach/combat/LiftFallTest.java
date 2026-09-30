package com.cosmicbreach.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's side of Zenith's lift: coming down from it is safe, dropping below where it started is not. */
class LiftFallTest {
    @Test
    void landingWhereTheLiftStartedCountsNoFall() {
        PlayerCombat.ServerState s = new PlayerCombat.ServerState();
        s.lifted(64.0, 0.0);
        assertTrue(s.lifting());
        assertEquals(0.0, s.liftedFall(3.9, 64.0), 1e-9, "rose 3, hung, sank and came down: nothing counts");
        assertFalse(s.lifting(), "one landing ends it");
        assertEquals(3.9, s.liftedFall(3.9, 64.0), 1e-9, "the next fall counts as usual");
    }

    @Test
    void aDropBelowTheStartStillCounts() {
        PlayerCombat.ServerState s = new PlayerCombat.ServerState();
        s.lifted(64.0, 0.0);
        assertEquals(10.0, s.liftedFall(13.0, 54.0), 1e-9, "hovered off a ledge and fell 10 below: 10 counts");
        s.lifted(70.0, 2.0);
        assertEquals(2.5, s.liftedFall(8.0, 69.5), 1e-9, "already falling 2 when it lifted, landed half a block lower");
        s.lifted(64.0, 0.0);
        assertEquals(1.0, s.liftedFall(1.0, 60.0), 1e-9, "never more than the body measured");
    }

    @Test
    void theLiftEndsOnceItIsBackOnTheGround() {
        PlayerCombat.ServerState s = new PlayerCombat.ServerState();
        s.lifted(64.0, 0.0);
        s.tickLift(true);
        s.tickLift(true);
        assertTrue(s.lifting(), "the client's rise may reach the server a few ticks late");
        s.tickLift(false);
        s.tickLift(true);
        assertFalse(s.lifting(), "up and back down: over");
        s.lifted(64.0, 0.0);
        for (int i = 0; i < 250; i++) {
            s.tickLift(true);
        }
        assertFalse(s.lifting(), "a lift that never left the ground is dropped");
    }
}
