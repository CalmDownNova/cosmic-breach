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
 * What the accessories add to the machine (GDD 3.4 "gear" terms, 5.2): max Resonance (the Choir Pendant's +20), parry
 * window ticks (the Event Horizon Lens's +2), a free dash each time in the air (the Twin Comet Band's +1, given back on
 * landing), and Resonance earned by gear's own rule ({@link CombatStateMachine#grantResonance}).
 */
class AccessoriesInMachineTest {
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

    private static boolean started(List<CombatEvent> events) {
        return events.stream().anyMatch(e -> e instanceof CombatEvent.DashStarted);
    }

    private static boolean denied(List<CombatEvent> events) {
        return events.stream().anyMatch(e -> e instanceof CombatEvent.Denied d && d.action() == CombatEvent.Action.DASH);
    }

    @Test
    void noAccessoryChangesNothing() {
        CombatStateMachine m = machine();
        assertEquals(100, m.maxResonance());
        assertEquals(4, m.parryWindow());
        assertEquals(0, m.airDashesLeft());
        assertEquals(0, m.gearResonance());
        assertEquals(0, m.gearParryWindow());
        assertEquals(0, m.gearAirDashes());
    }

    @Test
    void thePendantRaisesMaxResonanceAndTakingItOffCutsIt() {
        CombatStateMachine m = machine();
        m.setGearResonance(20);
        assertEquals(120, m.maxResonance(), "100 + 3 x 0 Arcane + 20");
        m.syncFromServer(118, 2, 0);
        assertEquals(118, m.resonance(), 1e-9, "Resonance above 100 is kept while it is worn");
        m.setGearResonance(0);
        assertEquals(100, m.resonance(), 1e-9, "taken off: cut to the new maximum");
        m.setGearResonance(-5);
        assertEquals(100, m.maxResonance(), "never below the engine's own");
    }

    @Test
    void grantedResonanceIsEarnedResonance() {
        CombatStateMachine m = machine();
        m.setGearResonance(20);
        m.grantResonance(15);
        assertEquals(15, m.resonance(), 1e-9);
        m.setResonanceGain(2.0);
        m.grantResonance(3);
        assertEquals(21, m.resonance(), 1e-9, "a Solar Flare doubles it like any earned Resonance");
        m.grantResonance(-4);
        m.grantResonance(Double.NaN);
        assertEquals(21, m.resonance(), 1e-9, "nothing negative, nothing undefined");
        m.grantResonance(500);
        assertEquals(120, m.resonance(), 1e-9, "capped at the raised maximum");
        List<CombatEvent> events = m.tick(Context.GROUNDED);
        assertTrue(events.stream().anyMatch(e -> e instanceof CombatEvent.ResonanceFull));
    }

    @Test
    void grantedResonanceMarksTheFightSoItDoesNotDrift() {
        CombatStateMachine m = machine();
        ticks(m, CombatRules.OUT_OF_COMBAT_TICKS + 1, Context.GROUNDED); // drifting up toward 30
        double drifting = m.resonance();
        assertTrue(drifting > 0);
        m.grantResonance(3);
        double after = m.resonance();
        ticks(m, 20, Context.GROUNDED);
        assertEquals(after, m.resonance(), 1e-9, "in combat again: no drift for a while");
    }

    @Test
    void theLensLengthensTheParryByTwoTicks() {
        CombatStateMachine m = machine();
        m.setGearParryWindow(2);
        assertEquals(6, m.parryWindow(), "4 + 0 Resilience + 2");
        m.pressParry();
        int whiffedOn = -1;
        for (int i = 1; i <= 20 && whiffedOn < 0; i++) {
            if (m.tick(Context.GROUNDED).stream().anyMatch(e -> e instanceof CombatEvent.ParryWhiffed)) {
                whiffedOn = i;
            }
        }
        assertEquals(6, whiffedOn, "the parry holds 6 ticks and whiffs on the 6th (the engine's own 4 whiff on the 4th)");
        m.setStats(new StatBlock(0, 0, 0, 30));
        assertEquals(8, m.parryWindow(), "4 + 30 / 15 + 2");
    }

    @Test
    void theEarlyWindowStaysTwoTicks() {
        CombatStateMachine m = machine();
        m.setGearParryWindow(2);
        m.pressParry();
        m.tick(Context.GROUNDED);
        assertTrue(m.inPerfectParryWindow());
        m.tick(Context.GROUNDED);
        assertFalse(m.inPerfectParryWindow(), "the Lens lengthens the parry, not its first two ticks");
        assertTrue(m.isParrying());
    }

    @Test
    void theBandGivesOneFreeDashEachTimeInTheAir() {
        CombatStateMachine m = machine();
        m.setGearAirDashes(1);
        m.syncFromServer(0, 2, 0);
        m.tick(AIR);
        assertEquals(1, m.airDashesLeft());
        m.pressDash(AIR);
        assertTrue(started(m.tick(AIR)));
        assertEquals(2, m.dashCharges(), "the band's dash goes first and costs no charge");
        assertEquals(0, m.airDashesLeft());
        ticks(m, 4, AIR);
        m.pressDash(AIR);
        assertTrue(started(m.tick(AIR)), "chained: a second air dash while the first still runs");
        assertEquals(1, m.dashCharges(), "the chain's second dash is a charge");
        ticks(m, CombatRules.DASH_TICKS, AIR);
        m.tick(Context.GROUNDED);
        assertEquals(1, m.airDashesLeft(), "landing gives it back");
    }

    @Test
    void theFreeAirDashWorksWithNoChargeLeftButNotOnTheGround() {
        CombatStateMachine m = machine();
        m.setGearAirDashes(1);
        m.syncFromServer(0, 0, 0);
        m.pressDash(Context.GROUNDED);
        assertTrue(denied(m.tick(Context.GROUNDED)), "on the ground a dash needs a charge");
        m.pressDash(AIR);
        List<CombatEvent> events = m.tick(AIR);
        assertFalse(denied(events));
        assertTrue(started(events), "in the air the band's dash needs none");
        ticks(m, CombatRules.DASH_TICKS, AIR);
        m.syncFromServer(0, 0, 0);
        m.pressDash(AIR);
        assertTrue(denied(m.tick(AIR)), "one each time in the air");
    }

    @Test
    void driftSpendsNothingSoTheBandWaitsForTheNextAirDash() {
        CombatStateMachine m = machine();
        m.setGearAirDashes(1);
        m.setFreeAirDashes(true);
        m.syncFromServer(0, 2, 0);
        m.pressDash(AIR);
        m.tick(AIR);
        assertEquals(1, m.airDashesLeft(), "a Drift dash is free already: the band's stays");
        assertEquals(2, m.dashCharges());
    }
}
