package com.cosmicbreach.client.combat;

import com.cosmicbreach.client.combat.CombatHudModel.ChargeStage;
import com.cosmicbreach.client.combat.CombatHudModel.Pip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatHudModelTest {

    private static CombatHudModel hud(double resonance, int charges, double recharge) {
        return CombatHudModel.of(resonance, 100, charges, 2, recharge, false, 0, 12, 24);
    }

    @Test
    void theArcFillsWithResonance() {
        assertEquals(0f, hud(0, 2, 0).resonanceFill());
        assertEquals(0.5f, hud(50, 2, 0).resonanceFill(), 1e-6);
        assertEquals(1f, hud(100, 2, 0).resonanceFill());
        assertTrue(hud(100, 2, 0).resonanceFull());
        assertFalse(hud(99.5, 2, 0).resonanceFull());
        assertEquals(1f, hud(140, 2, 0).resonanceFill(), "never past full");
    }

    @Test
    void pipsShowReadyRechargingAndWaiting() {
        CombatHudModel two = hud(0, 2, 0);
        assertEquals(Pip.FULL, two.pip(0));
        assertEquals(Pip.FULL, two.pip(1));
        assertEquals(0f, two.rechargeFill(), "nothing recharges with every charge ready");

        CombatHudModel one = hud(0, 1, 0.25);
        assertEquals(Pip.FULL, one.pip(0));
        assertEquals(Pip.RECHARGING, one.pip(1));
        assertEquals(0.25f, one.rechargeFill(), 1e-6);

        CombatHudModel none = hud(0, 0, 0.6);
        assertEquals(Pip.RECHARGING, none.pip(0), "the first one comes back first");
        assertEquals(Pip.EMPTY, none.pip(1));
        assertEquals(2, none.maxDashCharges());
    }

    @Test
    void theRingFillsToTheMinimumAndBrightensAtFull() {
        assertEquals(ChargeStage.NONE, hud(0, 2, 0).charge());
        CombatHudModel filling = CombatHudModel.of(0, 100, 2, 2, 0, true, 6, 12, 24);
        assertEquals(ChargeStage.FILLING, filling.charge());
        assertEquals(0.5f, filling.chargeFill(), 1e-6);
        CombatHudModel ready = CombatHudModel.of(0, 100, 2, 2, 0, true, 12, 12, 24);
        assertEquals(ChargeStage.READY, ready.charge());
        assertEquals(1f, ready.chargeFill());
        CombatHudModel full = CombatHudModel.of(0, 100, 2, 2, 0, true, 30, 12, 24);
        assertEquals(ChargeStage.FULL, full.charge());
        assertEquals(1f, full.chargeFill());
    }
}
