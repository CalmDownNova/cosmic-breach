package com.cosmicbreach.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatActionTest {

    @Test
    void everyActionRoundTripsThroughItsByte() {
        for (CombatAction action : CombatAction.values()) {
            assertEquals(action, CombatAction.byId(action.id()));
        }
        assertEquals(6, CombatAction.values().length);
    }

    @Test
    void unknownBytesAreNoAction() {
        assertNull(CombatAction.byId(-1));
        assertNull(CombatAction.byId(6));
        assertNull(CombatAction.byId(127));
    }

    @Test
    void onlyAttackAndAbilityPressesNeedAWeapon() {
        assertTrue(CombatAction.ATTACK_PRESS.needsWeapon());
        assertTrue(CombatAction.ABILITY_PRESS.needsWeapon());
        assertFalse(CombatAction.ATTACK_RELEASE.needsWeapon());
        assertFalse(CombatAction.ABILITY_RELEASE.needsWeapon());
        assertFalse(CombatAction.DASH.needsWeapon());
        assertFalse(CombatAction.PARRY.needsWeapon());
    }
}
