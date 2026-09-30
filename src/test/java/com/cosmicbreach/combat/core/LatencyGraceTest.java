package com.cosmicbreach.combat.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Combat's latency grace counts the whole round trip (a reaction arrives that late), rounded, capped at two ticks. */
class LatencyGraceTest {
    @Test
    void theWholeRoundTripRoundedAndCapped() {
        assertEquals(0, CombatRules.latencyGraceTicks(0), "the host");
        assertEquals(0, CombatRules.latencyGraceTicks(20));
        assertEquals(1, CombatRules.latencyGraceTicks(30), "30 ms rounds to one tick");
        assertEquals(1, CombatRules.latencyGraceTicks(60));
        assertEquals(2, CombatRules.latencyGraceTicks(90), "under 100 ms used to give none");
        assertEquals(2, CombatRules.latencyGraceTicks(100));
        assertEquals(CombatRules.MAX_LATENCY_GRACE, CombatRules.latencyGraceTicks(1000), "capped");
        assertEquals(0, CombatRules.latencyGraceTicks(-5), "never negative");
    }
}
