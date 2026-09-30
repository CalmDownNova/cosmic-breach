package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What armor sets add to the machine (GDD 3.4 "more from gear", 5.1): extra dash charges (the Driftweave's +1),
 * Haste from gear and buffs (the Hymn of Alignment's +30), the out-of-combat drift's target (the Choir Regalia's
 * 50%), and air dashes that cost no charge (the Driftweave's Drift). Both sides set them every tick from the
 * gear's hooks, so prediction agrees with the server.
 */
class GearInMachineTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("meridian/l1");
    private static final ResourceLocation ZENITH = id("meridian/zenith");
    private static final Context AIR = new Context(false, 0f, 70.0);

    private static MoveDef move(MoveKind kind) {
        return new MoveDef(kind, new MoveDef.Timing(3, 2, 5), new MoveDef.Hit(1.0, 0, 0, 6, 6, 2, 1, 0),
                new HitShape.Arc(3.5, 110, 2.5), Optional.empty(), id("anim"), 0.0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static final Map<ResourceLocation, MoveDef> MOVES = Map.of(L1, move(MoveKind.LIGHT), ZENITH, move(MoveKind.ABILITY));
    private static final WeaponDef WEAPON = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B), 3.5, List.of(L1),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(new WeaponDef.Ability(ZENITH, 30, 100)), 1);

    private static CombatStateMachine machine() {
        CombatStateMachine m = new CombatStateMachine(MOVES::get);
        m.setWeapon(WEAPON);
        return m;
    }

    private static void ticks(CombatStateMachine m, int n, Context ctx) {
        for (int i = 0; i < n; i++) {
            m.tick(ctx);
        }
    }

    private static boolean denied(List<CombatEvent> events) {
        return events.stream().anyMatch(e -> e instanceof CombatEvent.Denied d && d.action() == CombatEvent.Action.DASH);
    }

    @Test
    void noGearChangesNothing() {
        CombatStateMachine m = machine();
        assertEquals(0, m.gearDashCharges());
        assertEquals(0.0, m.gearHaste(), 1e-12);
        assertEquals(CombatRules.DRIFT_TARGET, m.driftTarget(), 1e-12);
        assertFalse(m.freeAirDashes());
        assertEquals(2, m.maxDashCharges());
    }

    @Test
    void gearAddsADashChargeThatRecharges() {
        CombatStateMachine m = machine();
        m.setGearDashCharges(1);
        assertEquals(3, m.maxDashCharges(), "2 at zero Agility, +1 from gear");
        assertEquals(2, m.dashCharges(), "the new charge isn't handed out, it recharges");
        ticks(m, 40, Context.GROUNDED);
        assertEquals(3, m.dashCharges());
        for (int i = 0; i < 3; i++) {
            m.pressDash(Context.GROUNDED);
            assertFalse(denied(m.tick(Context.GROUNDED)), "dash " + (i + 1) + " of 3");
            ticks(m, CombatRules.DASH_TICKS, Context.GROUNDED);
        }
        m.pressDash(Context.GROUNDED);
        assertTrue(denied(m.tick(Context.GROUNDED)), "a fourth dash has no charge left");
    }

    @Test
    void gearChargesStackWithAgility() {
        CombatStateMachine m = machine();
        m.setStats(new StatBlock(0, 20, 0, 0));
        m.setGearDashCharges(1);
        assertEquals(4, m.maxDashCharges(), "2, +1 at Agility 20, +1 from gear");
    }

    @Test
    void takingTheGearOffCutsTheCharges() {
        CombatStateMachine m = machine();
        m.setGearDashCharges(1);
        m.syncFromServer(0, 3, 0);
        assertEquals(3, m.dashCharges());
        m.setGearDashCharges(0);
        assertEquals(2, m.dashCharges());
        assertEquals(2, m.maxDashCharges());
    }

    @Test
    void gearHasteShortensTheAbilityCooldown() {
        CombatStateMachine m = machine();
        m.syncFromServer(100, 2, 0);
        m.setStats(new StatBlock(0, 0, 8, 0));
        m.pressAbility(Context.GROUNDED);
        assertEquals(90, m.abilityCooldown(), "100 x 100 / (100 + 12), rounded up");

        CombatStateMachine h = machine();
        h.syncFromServer(100, 2, 0);
        h.setStats(new StatBlock(0, 0, 8, 0));
        h.setGearHaste(30.0);
        h.pressAbility(Context.GROUNDED);
        assertEquals(71, h.abilityCooldown(), "100 x 100 / (100 + 12 + 30), rounded up");
    }

    @Test
    void theDriftSettlesAtItsTarget() {
        CombatStateMachine m = machine();
        m.setDriftTarget(0.5);
        m.syncFromServer(0, 2, 0);
        ticks(m, CombatRules.OUT_OF_COMBAT_TICKS + 400, Context.GROUNDED);
        assertEquals(50.0, m.resonance(), 1e-9, "half of 100");
        m.syncFromServer(90, 2, 0);
        ticks(m, 400, Context.GROUNDED);
        assertEquals(50.0, m.resonance(), 1e-9, "from above too");
        m.setStats(new StatBlock(0, 0, 8, 0));
        ticks(m, 400, Context.GROUNDED);
        assertEquals(62.0, m.resonance(), 1e-9, "half of 124 at Arcane 8");
    }

    @Test
    void theDriftTargetStaysAShare() {
        CombatStateMachine m = machine();
        m.setDriftTarget(1.7);
        assertEquals(1.0, m.driftTarget(), 1e-12);
        m.setDriftTarget(-1);
        assertEquals(0.0, m.driftTarget(), 1e-12);
        m.setDriftTarget(Double.NaN);
        assertEquals(CombatRules.DRIFT_TARGET, m.driftTarget(), 1e-12, "no say keeps the engine's 30%");
    }

    @Test
    void freeAirDashesCostNothingInTheAir() {
        CombatStateMachine m = machine();
        m.setFreeAirDashes(true);
        m.syncFromServer(0, 0, 0);
        m.pressDash(AIR);
        List<CombatEvent> events = m.tick(AIR);
        assertFalse(denied(events));
        assertTrue(events.stream().anyMatch(e -> e instanceof CombatEvent.DashStarted), "a dash with no charge left");
        assertEquals(0, m.dashCharges());
        ticks(m, CombatRules.DASH_TICKS, AIR);
        m.syncFromServer(0, 2, 0);
        m.pressDash(AIR);
        m.tick(AIR);
        assertEquals(2, m.dashCharges(), "an air dash leaves the charges alone");
    }

    @Test
    void freeAirDashesStillCostOnTheGround() {
        CombatStateMachine m = machine();
        m.setFreeAirDashes(true);
        m.syncFromServer(0, 2, 0);
        m.pressDash(Context.GROUNDED);
        m.tick(Context.GROUNDED);
        assertEquals(1, m.dashCharges());
        ticks(m, CombatRules.DASH_TICKS, Context.GROUNDED);
        m.syncFromServer(0, 0, 0);
        m.pressDash(Context.GROUNDED);
        assertTrue(denied(m.tick(Context.GROUNDED)));
    }

    @Test
    void withoutTheBuffAnAirDashCosts() {
        CombatStateMachine m = machine();
        m.syncFromServer(0, 1, 0);
        m.pressDash(AIR);
        m.tick(AIR);
        assertEquals(0, m.dashCharges());
        ticks(m, CombatRules.DASH_TICKS, AIR);
        m.pressDash(AIR);
        assertTrue(denied(m.tick(AIR)));
    }
}
