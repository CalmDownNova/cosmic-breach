package com.cosmicbreach.combat.server;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Friendly fire in multiplayer: another player's pet or mount is as safe as that player. And the lag allowance for swings. */
class TargetShieldTest {
    private static final UUID ME = new UUID(0, 1);
    private static final UUID FRIEND = new UUID(0, 2);

    @Test
    void aFriendsPetOrMountIsSafeWithoutPvp() {
        assertTrue(TargetShield.shelters(ME, FRIEND, false, false, false), "friend online, PvP off");
        assertTrue(TargetShield.shelters(ME, FRIEND, null, false, false), "friend offline, PvP off");
    }

    @Test
    void withPvpOnTheirThingsAreFairGameAsTheyAre() {
        assertFalse(TargetShield.shelters(ME, FRIEND, true, true, false), "friend online and hurtable");
        assertFalse(TargetShield.shelters(ME, FRIEND, null, true, false), "friend offline, PvP on");
        assertTrue(TargetShield.shelters(ME, FRIEND, false, true, false), "PvP on but the same team: still safe");
    }

    @Test
    void wildOrOwnThingsAreNotThisRulesBusiness() {
        assertFalse(TargetShield.shelters(ME, null, null, false, false), "nobody owns a wild animal");
        assertFalse(TargetShield.shelters(ME, ME, null, false, false), "my own pet is excluded elsewhere");
    }

    @Test
    void anythingCarryingAProtectedPlayerIsSafe() {
        assertTrue(TargetShield.shelters(ME, null, null, false, true), "a wild mount with my friend on it");
        assertTrue(TargetShield.shelters(ME, FRIEND, true, true, true), "the rider decides even when the owner could be hit");
    }

    @Test
    void theLagAllowanceIsSpeedTimesTheRoundTripCapped() {
        assertEquals(0.0, HitResolver.lagSlack(0.25, 0), 1e-9, "the host: nothing changes");
        assertEquals(0.0, HitResolver.lagSlack(0.0, 150), 1e-9, "a target standing still");
        assertEquals(0.5, HitResolver.lagSlack(0.25, 100), 1e-9, "0.25 a tick for a 2-tick round trip");
        assertEquals(0.15, HitResolver.lagSlack(0.1, 75), 1e-9);
        assertEquals(HitResolver.MAX_LAG_SLACK, HitResolver.lagSlack(0.6, 400), 1e-9, "capped");
        assertEquals(0.0, HitResolver.lagSlack(0.25, -20), 1e-9, "no negative latency");
        assertEquals(0.0, HitResolver.lagSlack(Double.NaN, 100), 1e-9, "no NaN speed");
    }
}
