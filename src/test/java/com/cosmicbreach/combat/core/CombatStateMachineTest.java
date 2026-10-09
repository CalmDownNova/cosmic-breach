package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatEvent.ActiveTick;
import com.cosmicbreach.combat.core.CombatEvent.ChargeCancelled;
import com.cosmicbreach.combat.core.CombatEvent.ChargeFull;
import com.cosmicbreach.combat.core.CombatEvent.ChargeReady;
import com.cosmicbreach.combat.core.CombatEvent.ChargeStarted;
import com.cosmicbreach.combat.core.CombatEvent.DashEnded;
import com.cosmicbreach.combat.core.CombatEvent.Denied;
import com.cosmicbreach.combat.core.CombatEvent.MoveEnded;
import com.cosmicbreach.combat.core.CombatEvent.MoveStarted;
import com.cosmicbreach.combat.core.CombatEvent.ParryWhiffed;
import com.cosmicbreach.combat.core.CombatEvent.PlungeLanded;
import com.cosmicbreach.combat.core.CombatEvent.ResonanceFull;
import com.cosmicbreach.combat.core.CombatEvent.Rise;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The engine rules of GDD section 4.1, tick by tick, on Meridian's frame data. */
class CombatStateMachineTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("meridian/l1");
    private static final ResourceLocation L2 = id("meridian/l2");
    private static final ResourceLocation L3 = id("meridian/l3");
    private static final ResourceLocation LINE = id("meridian/line");
    private static final ResourceLocation STAR = id("meridian/falling_star");
    private static final ResourceLocation PASS = id("meridian/pass");
    private static final ResourceLocation ZENITH = id("meridian/zenith");

    private static MoveDef move(MoveKind kind, int startup, int active, int recovery, double mv, double mvMax,
                                double perBlock, int resonance, ResourceLocation next,
                                MoveDef.Charge charge, MoveDef.Launch launch) {
        return new MoveDef(kind, new MoveDef.Timing(startup, active, recovery),
                new MoveDef.Hit(mv, mvMax, perBlock, 6, resonance, 2, 1, 0),
                new HitShape.Arc(3.5, 110, 2.5), Optional.ofNullable(next), id("anim"), 0.0,
                Optional.ofNullable(charge), Optional.ofNullable(launch), Optional.empty(), Optional.empty());
    }

    private static final Map<ResourceLocation, MoveDef> MOVES = Map.of(
            L1, move(MoveKind.LIGHT, 3, 2, 5, 1.0, 0, 0, 6, L2, null, null),
            L2, move(MoveKind.LIGHT, 3, 2, 6, 1.0, 0, 0, 6, L3, null, null),
            L3, move(MoveKind.LIGHT, 5, 2, 9, 1.6, 0, 0, 6, L1, null, null),
            LINE, move(MoveKind.CHARGED, 4, 3, 12, 2.2, 3.0, 0, 6, null, new MoveDef.Charge(12, 24), null),
            STAR, move(MoveKind.PLUNGE, 2, 0, 10, 1.4, 2.4, 0.1, 6, null, null, null),
            PASS, move(MoveKind.DASH_ATTACK, 2, 3, 7, 1.2, 0, 0, 6, null, null, null),
            ZENITH, move(MoveKind.ABILITY, 6, 3, 10, 1.4, 0, 0, 0, null, null, new MoveDef.Launch(3.5, 30, 40, 3.0)));

    private static final WeaponDef MERIDIAN = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B, Stat.AGILITY, Grade.C),
            3.5, List.of(L1, L2, L3), Optional.of(LINE), Optional.of(STAR), Optional.of(PASS),
            Optional.of(new WeaponDef.Ability(ZENITH, 30, 100)), 1);

    private static final Context G = Context.GROUNDED;

    private CombatStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new CombatStateMachine(MOVES::get);
        sm.setWeapon(MERIDIAN);
    }

    private List<List<CombatEvent>> run(int ticks, Context ctx) {
        List<List<CombatEvent>> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            out.add(sm.tick(ctx));
        }
        return out;
    }

    private List<List<CombatEvent>> run(int ticks) {
        return run(ticks, G);
    }

    private void tap() {
        sm.pressAttack(G);
        sm.releaseAttack(G);
    }

    private static boolean has(List<CombatEvent> events, Class<?> type) {
        return events.stream().anyMatch(type::isInstance);
    }

    private static <T> Optional<T> first(List<CombatEvent> events, Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    // ------------------------------------------------------------------ the light chain

    @Test
    void tapRunsRisingCutOnItsFrameData() {
        tap();
        List<List<CombatEvent>> t = run(10);
        assertEquals(L1, first(t.get(0), MoveStarted.class).orElseThrow().move().id());
        for (int i = 0; i < 10; i++) {
            assertEquals(i == 3 || i == 4, has(t.get(i), ActiveTick.class), "active on tick " + i);
        }
        assertFalse(first(t.get(9), MoveEnded.class).orElseThrow().cancelled());
        assertEquals(Phase.IDLE, sm.phase());
    }

    @Test
    void chainLoopsL1L2L3L1() {
        tap();
        run(10);
        tap();
        assertEquals(L2, sm.current().id());
        run(11);
        tap();
        assertEquals(L3, sm.current().id());
        run(16);
        tap();
        assertEquals(L1, sm.current().id());
    }

    @Test
    void comboWindowIsEightTicks() {
        tap();
        run(10);
        run(7);
        tap();
        assertEquals(L2, sm.current().id(), "7 idle ticks keeps the chain");

        setUp();
        tap();
        run(10);
        run(8);
        tap();
        assertEquals(L1, sm.current().id(), "8 idle ticks resets it");
    }

    @Test
    void pressesDuringStartupAndActiveAreIgnored() {
        tap();
        run(1);
        tap();
        run(3);
        tap();
        List<List<CombatEvent>> t = run(6);
        assertTrue(has(t.get(5), MoveEnded.class));
        assertFalse(t.stream().anyMatch(e -> has(e, MoveStarted.class)), "no second move was queued");
    }

    @Test
    void bufferAcceptsOnlyTheLastFiveRecoveryTicks() {
        startMeridianChop();
        run(7);                     // startup 5 + active 2: recovery tick 0 next
        run(3);                     // recovery ticks 0..2 done; 6 left
        tap();                      // too early
        List<List<CombatEvent>> t = run(6);
        assertTrue(has(t.get(5), MoveEnded.class));
        assertFalse(has(t.get(5), MoveStarted.class));

        setUp();
        startMeridianChop();
        run(7);
        run(4);                     // 5 recovery ticks left
        tap();                      // buffered
        t = run(5);
        assertTrue(has(t.get(4), MoveEnded.class));
        assertEquals(L1, first(t.get(4), MoveStarted.class).orElseThrow().move().id());
    }

    private void startMeridianChop() {
        tap();
        run(10);
        tap();
        run(11);
        tap();
        assertEquals(L3, sm.current().id());
    }

    // ------------------------------------------------------------------ charge

    @Test
    void holdingGoesStraightIntoTheChargeWithNoSwingAndReleaseScalesIt() {
        sm.pressAttack(G);
        List<List<CombatEvent>> t = run(18);
        long swings = t.stream().flatMap(List::stream).filter(e -> e instanceof MoveStarted).count();
        assertEquals(0, swings, "a hold never swings the light attack");
        assertTrue(has(t.get(CombatRules.HOLD_WINDOW - 1), ChargeStarted.class), "the charge starts when the hold window ends");
        assertTrue(has(t.get(11), ChargeReady.class));
        assertEquals(Phase.CHARGING, sm.phase());
        assertEquals(0.4f, sm.movementMultiplier());
        sm.releaseAttack(G);
        assertEquals(LINE, sm.current().id());
        assertEquals(2.6, sm.current().mv(), 1e-9);
    }

    @Test
    void tapInsideTheWindowFiresExactlyOneLightAttackOnRelease() {
        sm.pressAttack(G);
        List<List<CombatEvent>> before = run(CombatRules.HOLD_WINDOW - 1);
        assertEquals(0, before.stream().flatMap(List::stream).filter(e -> e instanceof MoveStarted).count(), "nothing swings while the window is open");
        assertEquals(Phase.IDLE, sm.phase());
        sm.releaseAttack(G);
        assertEquals(L1, sm.current().id());
        List<List<CombatEvent>> after = run(30);
        assertEquals(1, after.stream().flatMap(List::stream).filter(e -> e instanceof MoveStarted).count());
        assertEquals(0, after.stream().flatMap(List::stream).filter(e -> e instanceof ChargeStarted).count());
    }

    @Test
    void releaseOnTheVeryFirstTickOfTheWindowStillTaps() {
        sm.pressAttack(G);
        sm.releaseAttack(G);
        assertEquals(L1, sm.current().id());
        assertEquals(0, run(1).stream().flatMap(List::stream).filter(e -> e instanceof ChargeStarted).count());
    }

    @Test
    void releaseAfterTheWindowIsAChargeNotATap() {
        sm.pressAttack(G);
        run(CombatRules.HOLD_WINDOW);
        assertEquals(Phase.CHARGING, sm.phase());
        sm.releaseAttack(G);
        assertEquals(Phase.IDLE, sm.phase(), "released before the charge minimum: cancelled, no swing");
        assertNull(sm.current());
    }

    private long count(List<List<CombatEvent>> ticks, Class<?> type) {
        return ticks.stream().flatMap(List::stream).filter(type::isInstance).count();
    }

    // ---- the server decides tap or hold by the hold count the player's client reports, not by when packets arrived

    @Test
    void aLateReleaseOfATapStillFindsTheServersDecisionOpen() {
        sm.setHoldGrace(CombatRules.HOLD_GRACE);
        sm.pressAttack(G);
        List<List<CombatEvent>> before = run(5); // the release packet is 5 ticks on its way
        assertEquals(0, count(before, ChargeStarted.class), "the server has not committed to a charge yet");
        sm.releaseAttack(G, 2); // the player held it 2 ticks
        assertEquals(L1, sm.current().id());
        List<List<CombatEvent>> after = run(30);
        assertEquals(1, count(after, MoveStarted.class));
        assertEquals(0, count(after, ChargeStarted.class));
    }

    @Test
    void aTapThatArrivesAfterTheServerCommittedBecomesTheTapItWas() {
        sm.setHoldGrace(CombatRules.HOLD_GRACE);
        sm.pressAttack(G);
        List<List<CombatEvent>> before = run(8); // far later than the window and the grace: the server is charging
        assertEquals(1, count(before, ChargeStarted.class));
        assertEquals(Phase.CHARGING, sm.phase());
        sm.releaseAttack(G, 3); // but the player held it 3 ticks
        assertEquals(L1, sm.current().id(), "the late tap fires its light attack");
        assertTrue(has(run(1).get(0), ChargeCancelled.class) || sm.phase() != Phase.CHARGING);
    }

    @Test
    void aRealHoldStillChargesWithTheGraceAndAHonestReport() {
        sm.setHoldGrace(CombatRules.HOLD_GRACE);
        sm.pressAttack(G);
        List<List<CombatEvent>> t = run(20);
        assertEquals(0, count(t, MoveStarted.class), "no swing");
        assertEquals(1, count(t, ChargeStarted.class));
        sm.releaseAttack(G, 20);
        assertEquals(LINE, sm.current().id());
    }

    @Test
    void aReportOfALongHoldDecidesAHoldEvenIfTheServerStillWaits() {
        sm.setHoldGrace(CombatRules.HOLD_GRACE);
        sm.pressAttack(G);
        run(CombatRules.HOLD_WINDOW); // inside the grace
        sm.releaseAttack(G, 9);       // the player held it 9 ticks: a hold, released before the charge minimum
        assertEquals(0, sm.current() == null ? 0 : 1, "no light attack");
        assertEquals(Phase.IDLE, sm.phase());
    }

    @Test
    void theClientPredictsWithNoGraceAndTheServerKeepsItsOwnDecision() {
        // the client machine (no grace) charges at the window; the server's (grace) a little later: both resolve a tap alike
        CombatStateMachine client = new CombatStateMachine(MOVES::get);
        client.setWeapon(MERIDIAN);
        client.pressAttack(G);
        long clientCharge = 0;
        for (int i = 0; i < CombatRules.HOLD_WINDOW; i++) {
            clientCharge += client.tick(G).stream().filter(e -> e instanceof ChargeStarted).count();
        }
        assertEquals(1, clientCharge);
        sm.setHoldGrace(CombatRules.HOLD_GRACE);
        sm.pressAttack(G);
        assertEquals(0, count(run(CombatRules.HOLD_WINDOW), ChargeStarted.class));
    }

    @Test
    void theServersMachineChainsThreeTapsWithItsGraceAndReports() {
        sm.setHoldGrace(CombatRules.HOLD_GRACE);
        List<net.minecraft.resources.ResourceLocation> started = new ArrayList<>();
        java.util.function.Consumer<List<CombatEvent>> note = evs -> evs.forEach(e -> {
            if (e instanceof MoveStarted m) {
                started.add(m.move().id());
            }
        });
        sm.pressAttack(G);
        note.accept(sm.tick(G));
        sm.releaseAttack(G, 1);
        for (int i = 0; i < 7; i++) {
            note.accept(sm.tick(G));
        }
        sm.pressAttack(G);
        note.accept(sm.tick(G));
        sm.releaseAttack(G, 1);
        for (int i = 0; i < 8; i++) {
            note.accept(sm.tick(G));
        }
        sm.pressAttack(G);
        note.accept(sm.tick(G));
        sm.releaseAttack(G, 1);
        for (int i = 0; i < 30; i++) {
            note.accept(sm.tick(G));
        }
        assertEquals(List.of(L1, L2, L3), started);
    }

    @Test
    void chainsStillChainWithTheWindow() {
        tap();
        run(10);
        tap();
        assertEquals(L2, sm.current().id());
        run(11);
        tap();
        assertEquals(L3, sm.current().id());
    }

    @Test
    void aDashCancelsAPendingHold() {
        sm.pressAttack(G);
        run(1);
        sm.pressDash(G);
        run(CombatRules.HOLD_WINDOW + 2);
        assertFalse(sm.phase() == Phase.CHARGING, "the dash took over: no charge from the old press");
    }

    @Test
    void weaponsWithoutAChargeSwingAtOnce() {
        WeaponDef plain = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B), 3.5, List.of(L1, L2, L3),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);
        sm.setWeapon(plain);
        sm.pressAttack(G);
        assertEquals(L1, sm.current().id(), "no charge to wait for: the press swings now");
    }

    @Test
    void fullChargeCapsAtThree() {
        sm.pressAttack(G);
        List<List<CombatEvent>> t = run(40);
        assertTrue(has(t.get(23), ChargeFull.class));
        sm.releaseAttack(G);
        assertEquals(3.0, sm.current().mv(), 1e-9);
    }

    @Test
    void earlyReleaseCancelsTheChargeWithNoChainProgress() {
        sm.pressAttack(G);
        run(8);
        sm.releaseAttack(G);
        assertEquals(Phase.IDLE, sm.phase());
        assertTrue(has(run(1).get(0), ChargeCancelled.class));
        tap();
        assertEquals(L1, sm.current().id(), "the hold never swung, so the chain starts over");
    }

    // ------------------------------------------------------------------ plunge

    @Test
    void attackingAirborneAimingDownPlungesAndScalesWithTheFall() {
        Context air = new Context(false, 70f, 80.0);
        sm.pressAttack(air);
        sm.releaseAttack(air);
        List<List<CombatEvent>> t = run(2, air);
        assertEquals(STAR, first(t.get(0), MoveStarted.class).orElseThrow().move().id());
        assertEquals(Phase.PLUNGING, sm.phase());
        run(5, new Context(false, 70f, 75.0));
        List<CombatEvent> landed = sm.tick(new Context(true, 70f, 70.0));
        assertEquals(10.0, first(landed, PlungeLanded.class).orElseThrow().fallBlocks(), 1e-9);
        assertEquals(Phase.RECOVERY, sm.phase());
        assertTrue(has(run(10).get(9), MoveEnded.class));
    }

    @Test
    void attackingAirborneAimingLevelSwingsNormally() {
        Context air = new Context(false, 20f, 80.0);
        sm.pressAttack(air);
        assertEquals(L1, sm.current().id());
    }

    // ------------------------------------------------------------------ dash

    @Test
    void dashHasThreeInvulnerableTicksAndTwoPerfectOnes() {
        sm.pressDash(G);
        assertTrue(sm.isInvulnerable());
        assertTrue(sm.inPerfectDodgeWindow());
        sm.tick(G);
        assertTrue(sm.isInvulnerable());
        assertTrue(sm.inPerfectDodgeWindow());
        sm.tick(G);
        assertTrue(sm.isInvulnerable());
        assertFalse(sm.inPerfectDodgeWindow());
        sm.tick(G);
        assertFalse(sm.isInvulnerable());
        assertEquals(1, sm.dashCharges());
        assertTrue(has(run(5).get(4), DashEnded.class), "8 ticks in all");
    }

    @Test
    void dashChargesRechargeInTwoSeconds() {
        sm.pressDash(G);
        run(39);
        assertEquals(1, sm.dashCharges());
        run(1);
        assertEquals(2, sm.dashCharges());
    }

    @Test
    void dashIsDeniedWithoutCharges() {
        sm.pressDash(G);
        sm.pressDash(G);
        assertEquals(0, sm.dashCharges());
        sm.pressDash(G);
        assertTrue(first(run(1).get(0), Denied.class).map(d -> d.action() == CombatEvent.Action.DASH).orElse(false));
    }

    @Test
    void dashCancelsRecoveryFromItsThirdTick() {
        tap();
        run(5);                      // recovery tick 0 next
        run(1);
        sm.pressDash(G);
        assertFalse(sm.isDashing(), "recovery tick 1 is too early");
        run(1);
        sm.pressDash(G);
        assertTrue(sm.isDashing());
        assertTrue(first(run(1).get(0), MoveEnded.class).orElseThrow().cancelled());
    }

    @Test
    void dashIsIgnoredDuringStartupAndActive() {
        tap();
        run(1);
        sm.pressDash(G);
        assertFalse(sm.isDashing());
        run(2);
        assertEquals(Phase.ACTIVE, sm.phase());
        sm.pressDash(G);
        assertFalse(sm.isDashing());
    }

    @Test
    void dashAttackComesFromTheEndOfADashOrJustAfter() {
        sm.pressDash(G);
        run(2);
        tap();
        assertNull(sm.current(), "too early in the dash");

        setUp();
        sm.pressDash(G);
        run(5);
        tap();
        assertEquals(PASS, sm.current().id());
        assertFalse(sm.isDashing());

        setUp();
        sm.pressDash(G);
        run(8);
        run(2);
        tap();
        assertEquals(PASS, sm.current().id(), "2 ticks after the dash");

        setUp();
        sm.pressDash(G);
        run(8);
        run(4);
        tap();
        assertEquals(L1, sm.current().id(), "4 ticks after is too late");
    }

    @Test
    void latencyGraceExtendsInvulnerabilityUpToTwoTicks() {
        sm.setLatencyGrace(5);
        sm.pressDash(G);
        run(4);
        assertTrue(sm.isInvulnerable());
        run(1);
        assertFalse(sm.isInvulnerable());
    }

    // ------------------------------------------------------------------ parry

    @Test
    void parryWhiffLocksOutAttacksAndDashesBriefly() {
        sm.pressParry();
        assertTrue(sm.isParrying());
        assertTrue(has(run(4).get(3), ParryWhiffed.class));
        assertFalse(sm.isParrying());
        sm.pressAttack(G);
        sm.releaseAttack(G);
        assertNull(sm.current());
        run(5);
        sm.pressDash(G);
        assertFalse(sm.isDashing(), "first 6 whiff ticks can't dash");
        run(1);
        sm.pressDash(G);
        assertTrue(sm.isDashing());
    }

    @Test
    void parrySuccessGivesResonanceAndOneRiposte() {
        sm.pressParry();
        sm.tick(G);
        sm.syncFromServer(0, 2, 0);
        sm.onParrySuccess();
        assertFalse(sm.isParrying());
        assertEquals(25.0, sm.resonance(), 1e-9);
        assertTrue(sm.consumeRiposte());
        assertFalse(sm.consumeRiposte());
    }

    // ------------------------------------------------------------------ perfect dodge

    @Test
    void perfectDodgeRefundsTheChargeAndGuaranteesTheNextCrit() {
        sm.pressDash(G);
        sm.onPerfectDodge();
        assertEquals(2, sm.dashCharges());
        run(8);
        tap();
        assertEquals(PASS, sm.current().id());
        assertTrue(sm.current().critGuaranteed());
        run(12);
        tap();
        assertFalse(sm.current().critGuaranteed(), "the guarantee is used up");
    }

    @Test
    void perfectDodgeCritExpiresAfterTwentyTicks() {
        sm.pressDash(G);
        sm.onPerfectDodge();
        run(8);
        run(20);
        tap();
        assertEquals(L1, sm.current().id());
        assertFalse(sm.current().critGuaranteed());
    }

    // ------------------------------------------------------------------ ability

    @Test
    void abilityCostsResonanceAndHasACooldown() {
        sm.syncFromServer(20, 2, 0);
        sm.pressAbility(G);
        sm.releaseAbility();
        assertNull(sm.current(), "20 is under the cost of 30");
        sm.syncFromServer(50, 2, 0);
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(ZENITH, sm.current().id());
        assertEquals(20.0, sm.resonance(), 1e-9);
        assertEquals(100, sm.abilityCooldown());
        run(19);
        assertNull(sm.current());
        sm.pressAbility(G);
        assertTrue(first(run(1).get(0), Denied.class)
                .map(d -> d.action() == CombatEvent.Action.ABILITY_COOLDOWN).orElse(false));
    }

    @Test
    void holdingTheAbilityRisesWithTheLaunch() {
        sm.syncFromServer(50, 2, 0);
        sm.pressAbility(G);
        List<List<CombatEvent>> t = run(7);
        assertEquals(3.0, first(t.get(6), Rise.class).orElseThrow().height(), 1e-9);
        assertEquals(30, first(t.get(6), Rise.class).orElseThrow().hoverTicks(), "held up as long as the launched foes");

        setUp();
        sm.syncFromServer(50, 2, 0);
        sm.pressAbility(G);
        sm.releaseAbility();
        assertFalse(run(7).stream().anyMatch(e -> has(e, Rise.class)));
    }

    @Test
    void abilityCancelsRecoveryImmediately() {
        sm.syncFromServer(50, 2, 0);
        tap();
        run(5);
        assertEquals(Phase.RECOVERY, sm.phase());
        sm.pressAbility(G);
        assertEquals(ZENITH, sm.current().id());
        assertTrue(first(run(1).get(0), MoveEnded.class).orElseThrow().cancelled());
    }

    // ------------------------------------------------------------------ resonance

    @Test
    void hitsFillResonanceCountingAtMostThreeTargets() {
        sm.syncFromServer(0, 2, 0);
        tap();
        sm.onHitLanded(sm.current(), 5);
        assertEquals(18.0, sm.resonance(), 1e-9);
    }

    @Test
    void resonanceCapsAtMaxAndAnnouncesIt() {
        sm.syncFromServer(97, 2, 0);
        tap();
        sm.onHitLanded(sm.current(), 1);
        assertEquals(100.0, sm.resonance(), 1e-9);
        assertTrue(has(run(1).get(0), ResonanceFull.class));
    }

    @Test
    void outOfCombatResonanceDriftsToThirtyPercent() {
        sm.syncFromServer(0, 2, 0);
        run(120);
        assertEquals(30.0, sm.resonance(), 1e-9);
        tap();
        sm.onHitLanded(sm.current(), 3);
        assertEquals(48.0, sm.resonance(), 1e-9);
        run(99);
        assertEquals(48.0, sm.resonance(), 1e-9, "no drift inside 5 s of combat");
        run(1);
        assertEquals(47.75, sm.resonance(), 1e-9);
    }

    // ------------------------------------------------------------------ weapons

    @Test
    void swappingWeaponsCancelsTheMove() {
        tap();
        run(1);
        sm.setWeapon(null);
        assertEquals(Phase.IDLE, sm.phase());
        assertTrue(first(run(1).get(0), MoveEnded.class).orElseThrow().cancelled());
        tap();
        assertNull(sm.current(), "no weapon, no move");
    }
}
