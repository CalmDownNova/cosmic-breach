package com.cosmicbreach.structure.trap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** GDD 6.4's tripwire rules: walking is safe, rushing is not, damage 4 x speed ratio up to 12. */
class KineticRulesTest {
    private static final double WALK = 0.21585;
    private static final double SNEAK = WALK * 0.3;
    private static final double SPRINT = WALK * 1.3;
    private static final double SPRINT_JUMP = 0.36;
    private static final double DASH = 1.05;

    @Test
    void walkingAndSneakingAreSafe() {
        assertFalse(KineticRules.triggers(WALK));
        assertFalse(KineticRules.triggers(WALK * 1.1), "a little jitter over walking pace is still walking");
        assertFalse(KineticRules.triggers(SNEAK));
        assertFalse(KineticRules.triggers(0.0));
    }

    @Test
    void sprintingAndDashingFire() {
        assertTrue(KineticRules.triggers(SPRINT));
        assertTrue(KineticRules.triggers(SPRINT_JUMP));
        assertTrue(KineticRules.triggers(DASH));
        assertFalse(KineticRules.triggers(5.0), "a teleport is not a run");
    }

    @Test
    void damageIsFourTimesTheSpeedRatioCappedAtTwelve() {
        assertEquals(5.2, KineticRules.damage(SPRINT), 1e-4);
        assertEquals(4.0 * SPRINT_JUMP / WALK, KineticRules.damage(SPRINT_JUMP), 1e-4);
        assertEquals(12.0, KineticRules.damage(DASH), 1e-6);
        assertEquals(12.0, KineticRules.damage(WALK * 3.0), 1e-6, "exactly three times walking pace is the cap");
        assertEquals(4.0, KineticRules.damage(WALK), 1e-4);
    }

    @Test
    void carefulMovementRevealsThreads() {
        assertTrue(KineticRules.careful(false, WALK));
        assertTrue(KineticRules.careful(false, SNEAK));
        assertTrue(KineticRules.careful(false, 0.0));
        assertFalse(KineticRules.careful(true, SPRINT));
        assertFalse(KineticRules.careful(false, DASH), "dashing shows nothing");
    }

    @Test
    void threeBoltsTwoTicksApart() {
        assertEquals(3, KineticRules.BOLTS);
        assertEquals(0, KineticRules.boltTick(0));
        assertEquals(2, KineticRules.boltTick(1));
        assertEquals(4, KineticRules.boltTick(2));
    }
}
