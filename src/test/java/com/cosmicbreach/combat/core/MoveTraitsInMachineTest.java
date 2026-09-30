package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatEvent.ActiveTick;
import com.cosmicbreach.combat.core.CombatEvent.Denied;
import com.cosmicbreach.combat.core.CombatEvent.DashStarted;
import com.cosmicbreach.combat.core.CombatEvent.MoveCancelled;
import com.cosmicbreach.combat.core.CombatEvent.MoveEnded;
import com.cosmicbreach.combat.core.CombatEvent.MoveStarted;
import com.cosmicbreach.combat.core.CombatEvent.Staggered;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.combat.data.MoveTraits.CancelBy;
import com.cosmicbreach.combat.data.MoveTraits.CancelWindow;
import com.cosmicbreach.combat.data.MoveTraits.Stage;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules {@link MoveTraits} add to the machine: hyper armor, cancel windows (with the Gravity Well's
 * dash from its 12th active tick), root and walk speed, the ability's second press, and the stagger.
 */
class MoveTraitsInMachineTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("heavy/l1");
    private static final ResourceLocation L2 = id("heavy/l2");
    private static final ResourceLocation DIVE = id("heavy/dive");
    private static final ResourceLocation WELL = id("heavy/well");
    private static final ResourceLocation THROW = id("twin/throw");
    private static final ResourceLocation BLINK = id("twin/blink");
    private static final ResourceLocation QUICK = id("twin/quick");
    private static final ResourceLocation QUICK2 = id("twin/quick2");
    private static final Context G = Context.GROUNDED;

    private static MoveDef move(MoveKind kind, int startup, int active, int recovery, ResourceLocation next, MoveTraits traits) {
        return new MoveDef(kind, new MoveDef.Timing(startup, active, recovery),
                new MoveDef.Hit(1.0, 0, 0, 20, 12, 3, 1, 0), new HitShape.Sphere(3.0, 0.0), Optional.ofNullable(next),
                id("anim"), Optional.empty(), 0.0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), traits);
    }

    private static MoveTraits traits(double hyper, boolean root, float movement, CancelWindow... cancels) {
        return new MoveTraits(hyper, List.of(cancels), root, movement, List.of(), Optional.empty());
    }

    private static final Map<ResourceLocation, MoveDef> MOVES = new HashMap<>();

    static {
        MOVES.put(L1, move(MoveKind.LIGHT, 7, 2, 10, L2, traits(30, false, 0.4f)));
        MOVES.put(L2, move(MoveKind.LIGHT, 6, 3, 11, L1, traits(30, false, 0.4f)));
        MOVES.put(DIVE, move(MoveKind.PLUNGE, 3, 0, 14, null, traits(30, false, 1f)));
        MOVES.put(WELL, move(MoveKind.ABILITY, 8, 31, 12, null,
                traits(30, true, 1f, new CancelWindow(CancelBy.DASH, Stage.ACTIVE, 11))));
        MOVES.put(THROW, move(MoveKind.ABILITY, 2, 2, 4, null, MoveTraits.NONE));
        MOVES.put(BLINK, move(MoveKind.ABILITY, 0, 6, 4, null, MoveTraits.NONE));
        MOVES.put(QUICK, move(MoveKind.LIGHT, 2, 1, 6, QUICK2, traits(0, false, 1f, new CancelWindow(CancelBy.ATTACK, Stage.RECOVERY, 1))));
        MOVES.put(QUICK2, move(MoveKind.LIGHT, 2, 1, 6, QUICK, MoveTraits.NONE));
    }

    private static final WeaponDef HEAVY = new WeaponDef(11.0, Map.of(), 3.2, List.of(L1, L2), Optional.empty(),
            Optional.of(DIVE), Optional.empty(), Optional.of(new WeaponDef.Ability(WELL, 40, 180)), 2);
    private static final WeaponDef TWIN = new WeaponDef(3.8, Map.of(), 2.8, List.of(QUICK, QUICK2), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Optional.of(new WeaponDef.Ability(THROW, 25, 120, Optional.of(new WeaponDef.Recast(BLINK, 80)))), 2);

    private CombatStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new CombatStateMachine(MOVES::get);
        sm.setWeapon(HEAVY);
    }

    private List<List<CombatEvent>> run(int ticks) {
        List<List<CombatEvent>> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            out.add(sm.tick(G));
        }
        return out;
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

    private void fillResonance() {
        sm.syncFromServer(100, sm.dashCharges(), 0);
    }

    // ------------------------------------------------------------------ hyper armor

    @Test
    void hyperArmorHoldsThroughStartupAndActiveOnly() {
        assertEquals(0.0, sm.poiseBonus());
        tap();
        List<Double> bonus = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            bonus.add(sm.poiseBonus());
            sm.tick(G);
        }
        // L1: startup 7 (ticks 0 to 6), active 2 (7, 8), recovery 10 (9 to 18)
        for (int t = 0; t < 19; t++) {
            assertEquals(t <= 8 ? 30.0 : 0.0, bonus.get(t), "tick " + t);
        }
        assertEquals(0.0, sm.poiseBonus());
    }

    @Test
    void aPlungesDiveCountsAsActiveForHyperArmor() {
        sm.pressAttack(new Context(false, 70f, 80.0));
        sm.releaseAttack(new Context(false, 70f, 80.0));
        for (int i = 0; i < 6; i++) {
            sm.tick(new Context(false, 70f, 80.0 - i));
        }
        assertEquals(Phase.PLUNGING, sm.phase());
        assertEquals(30.0, sm.poiseBonus());
        sm.tick(new Context(true, 70f, 70.0));
        assertEquals(Phase.RECOVERY, sm.phase());
        assertEquals(0.0, sm.poiseBonus());
    }

    @Test
    void aMoveWithoutHyperArmorAddsNothing() {
        sm.setWeapon(TWIN);
        tap();
        assertEquals(0.0, sm.poiseBonus());
    }

    // ------------------------------------------------------------------ walk speed and root

    @Test
    void aHeavySwingSlowsWalkingUntilItEnds() {
        assertEquals(1.0f, sm.movementMultiplier());
        tap();
        for (int i = 0; i < 18; i++) {
            assertEquals(0.4f, sm.movementMultiplier(), "tick " + i);
            assertFalse(sm.isRooted());
            sm.tick(G);
        }
        sm.tick(G);
        assertEquals(Phase.IDLE, sm.phase());
        assertEquals(1.0f, sm.movementMultiplier());
    }

    @Test
    void theWellRootsFromThePressToTheEndOfRecovery() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(WELL, sm.current().id());
        for (int i = 0; i < 8 + 31 + 12; i++) {
            assertTrue(sm.isRooted(), "tick " + i);
            assertEquals(0.0f, sm.movementMultiplier());
            sm.tick(G);
        }
        assertEquals(Phase.IDLE, sm.phase());
        assertFalse(sm.isRooted());
        assertEquals(1.0f, sm.movementMultiplier());
    }

    // ------------------------------------------------------------------ cancel windows

    @Test
    void aDashBeforeTheWindowDoesNothing() {
        fillResonance();
        sm.pressAbility(G);
        run(8 + 10); // active tick 10 is next
        assertEquals(10, sm.phaseTick());
        sm.pressDash(G);
        assertFalse(sm.isDashing());
        assertEquals(WELL, sm.current().id());
        List<CombatEvent> events = sm.tick(G);
        assertFalse(has(events, DashStarted.class));
        assertFalse(has(events, MoveCancelled.class));
    }

    @Test
    void aDashFromTheTwelfthActiveTickFreesThePlayerAndReportsTheCancel() {
        fillResonance();
        sm.pressAbility(G);
        run(8 + 11);
        assertEquals(11, sm.phaseTick());
        sm.pressDash(G);
        assertTrue(sm.isDashing());
        assertNull(sm.current());
        assertFalse(sm.isRooted());
        assertEquals(1.0f, sm.movementMultiplier());
        List<CombatEvent> events = sm.tick(G);
        MoveCancelled cancelled = first(events, MoveCancelled.class).orElseThrow();
        assertEquals(WELL, cancelled.move().id());
        assertEquals(CancelBy.DASH, cancelled.by());
        assertEquals(Stage.ACTIVE, cancelled.stage());
        assertEquals(11, cancelled.tick());
        assertTrue(first(events, MoveEnded.class).orElseThrow().cancelled());
        assertTrue(events.indexOf(cancelled) < events.indexOf(first(events, MoveEnded.class).orElseThrow()));
        assertTrue(has(events, DashStarted.class));
    }

    @Test
    void startupStaysUncancellableWithoutAWindowThatSaysSo() {
        tap();
        run(3);
        sm.pressDash(G);
        sm.pressParry();
        assertFalse(sm.isDashing());
        assertFalse(sm.isParrying());
        assertEquals(L1, sm.current().id());
    }

    @Test
    void theEnginesRecoveryCancelsStillWorkAndAreReported() {
        tap();
        run(7 + 2 + 2); // recovery tick 2
        assertEquals(Phase.RECOVERY, sm.phase());
        assertEquals(2, sm.phaseTick());
        sm.pressDash(G);
        List<CombatEvent> events = sm.tick(G);
        MoveCancelled cancelled = first(events, MoveCancelled.class).orElseThrow();
        assertEquals(CancelBy.DASH, cancelled.by());
        assertEquals(Stage.RECOVERY, cancelled.stage());
        assertEquals(2, cancelled.tick());
    }

    @Test
    void cancelWindowsOpenFromTheirTickThroughEveryLaterPhase() {
        CancelWindow w = new CancelWindow(CancelBy.DASH, Stage.ACTIVE, 11);
        assertFalse(w.opens(Stage.STARTUP, 50));
        assertFalse(w.opens(Stage.ACTIVE, 10));
        assertTrue(w.opens(Stage.ACTIVE, 11));
        assertTrue(w.opens(Stage.ACTIVE, 30));
        assertTrue(w.opens(Stage.RECOVERY, 0));
    }

    @Test
    void anAttackWindowStartsTheNextMoveAtOnce() {
        sm.setWeapon(TWIN);
        tap();
        run(2 + 1 + 1); // recovery tick 1 of QUICK
        assertEquals(Phase.RECOVERY, sm.phase());
        assertEquals(1, sm.phaseTick());
        tap();
        assertEquals(QUICK2, sm.current().id());
        assertEquals(Phase.STARTUP, sm.phase());
        List<CombatEvent> events = sm.tick(G);
        assertEquals(CancelBy.ATTACK, first(events, MoveCancelled.class).orElseThrow().by());
        assertEquals(QUICK2, first(events, MoveStarted.class).orElseThrow().move().id());
    }

    @Test
    void withoutAnAttackWindowAPressInRecoveryIsOnlyBuffered() {
        tap();
        run(7 + 2 + 6); // L1 recovery tick 6: inside the 5-tick buffer
        tap();
        assertEquals(L1, sm.current().id());
        run(4);
        assertEquals(L2, sm.current().id());
    }

    // ------------------------------------------------------------------ the second press

    @Test
    void theSecondPressStartsTheRecastMoveForFree() {
        sm.setWeapon(TWIN);
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(75.0, sm.resonance(), 1e-9);
        assertEquals(0, sm.recastWindow());
        run(2 + 1); // the throw's first active tick arms the recast
        assertEquals(80, sm.recastWindow());
        run(1 + 4); // the throw ends
        assertEquals(Phase.IDLE, sm.phase());
        int cooldown = sm.abilityCooldown();
        assertTrue(cooldown > 0);
        double before = sm.resonance(); // drifting out of combat, but the second press costs nothing
        sm.pressAbility(G);
        assertEquals(BLINK, sm.current().id());
        assertEquals(before, sm.resonance(), 1e-9);
        assertEquals(0, sm.recastWindow());
        sm.releaseAbility();
        sm.tick(G);
        assertTrue(sm.abilityCooldown() > 0 && sm.abilityCooldown() < cooldown);
    }

    @Test
    void theRecastWindowCloses() {
        sm.setWeapon(TWIN);
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(3 + 80);
        assertEquals(0, sm.recastWindow());
        sm.pressAbility(G);
        List<CombatEvent> events = sm.tick(G);
        assertEquals(CombatEvent.Action.ABILITY_COOLDOWN, first(events, Denied.class).orElseThrow().action());
    }

    @Test
    void anEffectCanDisarmTheRecast() {
        sm.setWeapon(TWIN);
        fillResonance();
        sm.pressAbility(G);
        run(3);
        sm.disarmRecast();
        run(5);
        sm.pressAbility(G);
        assertNull(sm.current());
    }

    // ------------------------------------------------------------------ stagger

    @Test
    void aStaggerStopsTheMoveAndLocksInputsForItsTicks() {
        tap();
        run(3);
        sm.onStaggered(10);
        assertNull(sm.current());
        assertTrue(sm.isStaggered());
        List<CombatEvent> events = sm.tick(G);
        assertTrue(first(events, MoveEnded.class).orElseThrow().cancelled());
        assertEquals(10, first(events, Staggered.class).orElseThrow().ticks());
        for (int i = 1; i < 10; i++) {
            tap();
            sm.pressDash(G);
            sm.pressParry();
            List<CombatEvent> during = sm.tick(G);
            assertTrue(has(during, Denied.class), "tick " + i);
            assertNull(sm.current());
            assertFalse(sm.isDashing());
            assertFalse(sm.isParrying());
        }
        assertFalse(sm.isStaggered());
        tap();
        assertEquals(L1, sm.current().id());
    }

    @Test
    void activeTicksStillFireForHeavyMoves() {
        tap();
        List<List<CombatEvent>> t = run(19);
        for (int i = 0; i < 19; i++) {
            assertEquals(i == 7 || i == 8, has(t.get(i), ActiveTick.class), "tick " + i);
        }
    }
}
