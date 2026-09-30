package com.cosmicbreach.combat.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.combat.core.CombatRules;
import com.cosmicbreach.combat.core.CombatStateMachine;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What gear hooks add to the machine, folded together with cosmic weather: charges and Haste add up, gains and
 * costs multiply (with the weather's), the highest drift target wins, and any hook frees air dashes.
 */
class CombatHooksTest {
    private List<CombatHooks.Hook> registered = List.of();

    @BeforeEach
    void alone() {
        registered = CombatHooks.snapshotForTests(); // the mod's own hooks: set aside, put back afterwards
        CombatHooks.restoreForTests(List.of());
    }

    @AfterEach
    void putBack() {
        CombatHooks.restoreForTests(registered);
    }

    @Test
    void withNoHookTheMachineKeepsTheEnginesNumbers() {
        CombatStateMachine m = new CombatStateMachine(id -> null);
        CombatHooks.applyTo(m, null, 1.0, 1.0);
        assertEquals(0, m.gearDashCharges());
        assertEquals(0.0, m.gearHaste(), 1e-12);
        assertEquals(1.0, m.resonanceGain(), 1e-12);
        assertEquals(1.0, m.abilityCostScale(), 1e-12);
        assertEquals(CombatRules.DRIFT_TARGET, m.driftTarget(), 1e-12);
        assertFalse(m.freeAirDashes());
        assertEquals(1.0, CombatHooks.dashDistanceScale(null), 1e-12);
    }

    @Test
    void hooksFoldTogetherWithTheWeather() {
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public int dashChargeBonus(Player player) {
                return 1; // the Driftweave
            }

            @Override
            public double dashDistanceScale(Player player) {
                return 1.2;
            }

            @Override
            public double abilityCostScale(Player player) {
                return 0.9; // the Choir Regalia
            }

            @Override
            public double resonanceDriftTarget(Player player) {
                return 0.5;
            }
        });
        CombatHooks.register(new CombatHooks.Hook() {
            @Override
            public double hasteBonus(Player player) {
                return 30.0; // inside a Hymn
            }

            @Override
            public double resonanceGain(Player player) {
                return 2.0;
            }

            @Override
            public double resonanceDriftTarget(Player player) {
                return 0.4;
            }

            @Override
            public boolean freeAirDashes(Player player) {
                return true; // Drift
            }
        });
        CombatStateMachine m = new CombatStateMachine(id -> null);
        CombatHooks.applyTo(m, null, 2.0, 0.75); // a Solar Flare's gain, an Eclipse Surge's cost
        assertEquals(1, m.gearDashCharges());
        assertEquals(3, m.maxDashCharges());
        assertEquals(30.0, m.gearHaste(), 1e-12);
        assertEquals(4.0, m.resonanceGain(), 1e-12, "the Flare's x2 and the Hymn's x2");
        assertEquals(0.675, m.abilityCostScale(), 1e-12, "the Surge's x0.75 and the Regalia's x0.9");
        assertEquals(0.5, m.driftTarget(), 1e-12, "the highest target wins");
        assertTrue(m.freeAirDashes());
        assertEquals(1.2, CombatHooks.dashDistanceScale(null), 1e-12);
    }
}
