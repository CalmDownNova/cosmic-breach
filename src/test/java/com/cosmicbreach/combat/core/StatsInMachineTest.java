package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The machine with a player's stats: dash charges, i-frames, parry window and the Resonance cap follow them. */
class StatsInMachineTest {
    private static CombatStateMachine machine() {
        return new CombatStateMachine(id -> null);
    }

    @Test
    void statsAreReadBack() {
        CombatStateMachine m = machine();
        assertEquals(StatBlock.ZERO, m.stats());
        StatBlock s = new StatBlock(1, 2, 3, 4);
        m.setStats(s);
        assertEquals(s, m.stats());
    }

    @Test
    void agilityTwentyRechargesAThirdCharge() {
        CombatStateMachine m = machine();
        assertEquals(2, m.dashCharges());
        m.setStats(new StatBlock(0, 20, 0, 0));
        assertEquals(3, m.maxDashCharges());
        assertEquals(2, m.dashCharges(), "the third charge isn't handed out, it recharges");
        int ticks = 0;
        while (m.dashCharges() < 3 && ticks < 100) {
            m.tick(Context.GROUNDED);
            ticks++;
        }
        assertEquals(3, m.dashCharges());
        assertEquals(29, ticks, "40 / (1 + 0.02 x 20) = 28.6 ticks");
    }

    @Test
    void agilityLengthensTheIframes() {
        CombatStateMachine m = machine();
        m.setStats(new StatBlock(0, 30, 0, 0));
        m.pressDash(Context.GROUNDED);
        int invulnerable = 0;
        for (int t = 0; t < 8; t++) {
            if (m.isInvulnerable()) {
                invulnerable++;
            }
            m.tick(Context.GROUNDED);
        }
        assertEquals(6, invulnerable, "3 + floor(30 / 10)");
    }

    @Test
    void resilienceLengthensTheParry() {
        CombatStateMachine m = machine();
        m.setStats(new StatBlock(0, 0, 0, 30));
        m.pressParry();
        int active = 0;
        for (int t = 0; t < 12; t++) {
            if (m.isParrying()) {
                active++;
            }
            m.tick(Context.GROUNDED);
        }
        assertEquals(6, active, "4 + floor(30 / 15)");
    }

    @Test
    void aLowerCapCutsResonanceAndCharges() {
        CombatStateMachine m = machine();
        m.setStats(new StatBlock(0, 20, 30, 0));
        m.syncFromServer(190, 3, 0);
        assertEquals(190, m.resonance(), 1e-9);
        assertTrue(m.resonance() >= m.maxResonance());
        m.setStats(StatBlock.ZERO);
        assertEquals(100, m.resonance(), 1e-9, "a respec from Arcane 30 leaves a full bar, not an overfull one");
        assertEquals(2, m.dashCharges());
        assertFalse(m.dashCharges() > m.maxDashCharges());
    }
}
