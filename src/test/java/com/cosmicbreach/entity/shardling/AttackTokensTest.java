package com.cosmicbreach.entity.shardling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackTokensTest {
    private static final int PLAYER = 100;
    private static final int OTHER_PLAYER = 200;

    @Test
    void twoTokensPerTargetAtMost() {
        AttackTokens tokens = new AttackTokens();
        assertTrue(tokens.tryAcquire(PLAYER, 1));
        assertTrue(tokens.tryAcquire(PLAYER, 2));
        assertFalse(tokens.tryAcquire(PLAYER, 3), "a third attacker on one player");
        assertEquals(2, tokens.held(PLAYER));
        assertTrue(tokens.tryAcquire(OTHER_PLAYER, 3), "another player's tokens are separate");
    }

    @Test
    void holdingAgainIsFineAndReleasingFreesTheSlot() {
        AttackTokens tokens = new AttackTokens();
        tokens.tryAcquire(PLAYER, 1);
        tokens.tryAcquire(PLAYER, 2);
        assertTrue(tokens.tryAcquire(PLAYER, 1), "a holder asking again keeps its token");
        assertEquals(2, tokens.held(PLAYER));
        assertTrue(tokens.release(1));
        assertFalse(tokens.release(1), "nothing left to give back");
        assertTrue(tokens.tryAcquire(PLAYER, 3));
        assertEquals(java.util.Set.of(2, 3), tokens.holders(PLAYER));
    }

    @Test
    void oneTokenPerHolder() {
        AttackTokens tokens = new AttackTokens();
        tokens.tryAcquire(PLAYER, 1);
        assertTrue(tokens.tryAcquire(OTHER_PLAYER, 1), "switching targets gives the old token back");
        assertEquals(0, tokens.held(PLAYER));
        assertEquals(OTHER_PLAYER, tokens.targetOf(1));
        assertEquals(-1, tokens.targetOf(2));
    }
}
