package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatEvent.Denied;
import com.cosmicbreach.combat.core.CombatEvent.MoveStarted;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
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
 * The rules the Binary Edges add to the machine (GDD 4.2): the Weave (an attack soon after a dash starts
 * the chain at L4, after the dash attack's own window), an ability cooldown that starts when the thrown
 * blade comes back instead of at the cast, a second press an effect opens, i-frames from a move's own
 * phases (the blink), and a weapon swap that closes an open second press.
 */
class EdgesRulesInMachineTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("edges/l1");
    private static final ResourceLocation L2 = id("edges/l2");
    private static final ResourceLocation L4 = id("edges/l4");
    private static final ResourceLocation L5 = id("edges/l5");
    private static final ResourceLocation SCISSOR = id("edges/scissor");
    private static final ResourceLocation TETHER = id("edges/tether");
    private static final ResourceLocation BLINK = id("edges/blink");
    private static final ResourceLocation OTHER_L1 = id("other/l1");
    private static final Context G = Context.GROUNDED;
    /** 6 s at zero Haste. */
    private static final int COOLDOWN = 120;

    private static MoveDef move(MoveKind kind, int startup, int active, int recovery, ResourceLocation next, MoveTraits traits) {
        return new MoveDef(kind, new MoveDef.Timing(startup, active, recovery),
                new MoveDef.Hit(1.0, 0, 0, 3, 2, 1, 1, 0), new HitShape.Sphere(3.0, 0.0), Optional.ofNullable(next),
                id("anim"), Optional.empty(), 0.0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), traits);
    }

    private static final MoveTraits BLINK_TRAITS = new MoveTraits(0.0, List.of(), false, 1.0f, List.of(), Optional.empty(),
            Optional.of(Stage.STARTUP));

    private static final Map<ResourceLocation, MoveDef> MOVES = new HashMap<>();

    static {
        MOVES.put(L1, move(MoveKind.LIGHT, 2, 1, 3, L2, MoveTraits.NONE));
        MOVES.put(L2, move(MoveKind.LIGHT, 2, 1, 3, L1, MoveTraits.NONE));
        MOVES.put(L4, move(MoveKind.LIGHT, 3, 1, 4, L5, MoveTraits.NONE));
        MOVES.put(L5, move(MoveKind.LIGHT, 3, 3, 8, L1, MoveTraits.NONE));
        MOVES.put(SCISSOR, move(MoveKind.DASH_ATTACK, 1, 2, 5, null, MoveTraits.NONE));
        MOVES.put(TETHER, move(MoveKind.ABILITY, 3, 1, 7, null, MoveTraits.NONE));
        MOVES.put(BLINK, move(MoveKind.ABILITY, 6, 2, 6, null, BLINK_TRAITS));
        MOVES.put(OTHER_L1, move(MoveKind.LIGHT, 3, 2, 5, null, MoveTraits.NONE));
    }

    /** The Binary Edges as the machine sees them: Weave to L4 within 10 ticks, the Tether's blade comes back. */
    private static final WeaponDef EDGES = new WeaponDef(3.8, Map.of(), 2.8, List.of(L1, L2), Optional.empty(),
            Optional.empty(), Optional.of(SCISSOR),
            Optional.of(new WeaponDef.Ability(TETHER, 25, COOLDOWN, Optional.of(new WeaponDef.Recast(BLINK, 80, false)), true)),
            2, Optional.empty(), Map.of(), Optional.of(new WeaponDef.DashFollow(L4, 10)), Optional.empty());
    private static final WeaponDef OTHER = new WeaponDef(5.0, Map.of(), 3.0, List.of(OTHER_L1), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.of(new WeaponDef.Ability(OTHER_L1, 30, 100)), 1);

    private CombatStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new CombatStateMachine(MOVES::get);
        sm.setWeapon(EDGES);
    }

    private List<CombatEvent> run(int ticks) {
        List<CombatEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            all.addAll(sm.tick(G));
        }
        return all;
    }

    private static <T> Optional<T> first(List<CombatEvent> events, Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    /** Presses attack and returns the move it started this tick (the press is applied on the next tick). */
    private ResourceLocation attack() {
        sm.pressAttack(G);
        sm.releaseAttack(G);
        List<CombatEvent> events = sm.tick(G);
        return first(events, MoveStarted.class).map(s -> s.move().id()).orElse(null);
    }

    /** Dashes and runs until the dash is over (it ends on its own after 8 ticks). */
    private void dashToTheEnd() {
        sm.pressDash(G);
        int guard = 0;
        while (sm.isDashing() && guard++ < 20) {
            sm.tick(G);
        }
        assertFalse(sm.isDashing());
    }

    private void fillResonance() {
        sm.syncFromServer(100, sm.dashCharges(), 0);
    }

    // ------------------------------------------------------------------ the Weave

    @Test
    void anAttackSixTicksAfterADashStartsTheChainAtL4() {
        dashToTheEnd();
        run(6);
        assertEquals(L4, attack(), "the Weave");
        run(3 + 1 + 4);
        assertEquals(L5, attack(), "and the chain goes on from there");
    }

    @Test
    void anAttackTwelveTicksAfterADashStartsAtL1() {
        dashToTheEnd();
        run(12);
        assertEquals(L1, attack());
    }

    @Test
    void theWeaveCoversTheWindowUpToItsTenthTick() {
        for (int wait = 0; wait <= 12; wait++) {
            setUp();
            dashToTheEnd();
            run(wait);
            ResourceLocation started = attack();
            ResourceLocation expected = wait < CombatRules.DASH_ATTACK_WINDOW ? SCISSOR : wait < 10 ? L4 : L1;
            assertEquals(expected, started, "attack pressed " + wait + " ticks after the dash ended");
        }
    }

    @Test
    void theDashAttackStillComesOutOfTheDashItself() {
        sm.pressDash(G);
        run(5); // the dash's last 4 ticks
        sm.pressAttack(G);
        sm.releaseAttack(G);
        assertEquals(SCISSOR, sm.current().id());
        run(1 + 2 + 5 + 1);
        assertEquals(L1, attack(), "no Weave after a dash that turned into the Scissor");
    }

    @Test
    void aWeaponWithoutAFollowKeepsTheOldRule() {
        sm.setWeapon(OTHER);
        dashToTheEnd();
        run(6);
        assertEquals(OTHER_L1, attack());
    }

    // ------------------------------------------------------------------ the cooldown from the blade's return

    @Test
    void theCooldownWaitsForTheBladeToComeBack() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(75.0, sm.resonance(), 1e-9, "25 to throw");
        assertEquals(0, sm.abilityCooldown(), "no cooldown while the blade is out");
        assertEquals(COOLDOWN, sm.pendingCooldown());
        run(3 + 1);
        assertEquals(0, sm.recastWindow(), "the blade opens the blink when it sticks, not the throw");
        run(40);
        assertEquals(0, sm.abilityCooldown());
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(CombatEvent.Action.ABILITY_COOLDOWN,
                first(sm.tick(G), Denied.class).orElseThrow().action(), "no second throw while the first is out");
        sm.disarmRecast(); // the blade came back (it missed)
        assertEquals(COOLDOWN, sm.abilityCooldown(), "the full 6 s from the return");
        assertEquals(0, sm.pendingCooldown());
    }

    @Test
    void theBlinkStartsTheCooldownWhenItArrives() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(3 + 1 + 7);
        sm.armRecast(BLINK, 80); // the blade stuck
        run(10);
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(BLINK, sm.current().id());
        assertEquals(0, sm.recastWindow());
        for (int t = 0; t < 6; t++) {
            assertEquals(0, sm.abilityCooldown(), "travelling, tick " + t);
            sm.tick(G);
        }
        assertEquals(Phase.ACTIVE, sm.phase());
        assertEquals(0, sm.abilityCooldown());
        sm.tick(G); // the blink's first active tick: it arrives
        assertEquals(COOLDOWN, sm.abilityCooldown(), "the blade is back in hand on arrival");
    }

    @Test
    void theStickRunningOutStartsTheCooldown() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(4);
        sm.armRecast(BLINK, 80);
        run(79);
        assertEquals(0, sm.abilityCooldown());
        run(1);
        assertEquals(0, sm.recastWindow());
        assertEquals(COOLDOWN, sm.abilityCooldown());
    }

    @Test
    void aStaggerDuringTheBlinkStartsItsCooldown() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(4);
        sm.armRecast(BLINK, 80);
        sm.pressAbility(G);
        sm.releaseAbility();
        run(2);
        sm.onStaggered(10);
        assertNull(sm.current());
        assertEquals(COOLDOWN, sm.abilityCooldown());
    }

    @Test
    void theServersRunningCooldownTellsTheClientTheBladeCameBack() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(4);
        sm.armRecast(BLINK, 80);
        sm.syncFromServer(sm.resonance(), sm.dashCharges(), 90);
        assertEquals(0, sm.pendingCooldown());
        assertEquals(0, sm.recastWindow(), "the server's blade is back: no blink to it");
        assertEquals(90, sm.abilityCooldown());
    }

    @Test
    void aZeroCooldownFromTheServerLeavesTheBladeOut() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(4);
        sm.armRecast(BLINK, 80);
        sm.syncFromServer(50, sm.dashCharges(), 0);
        assertEquals(COOLDOWN, sm.pendingCooldown());
        assertTrue(sm.recastWindow() > 0);
    }

    @Test
    void switchingWeaponsCallsTheBladeBack() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(4);
        sm.armRecast(BLINK, 80);
        sm.setWeapon(OTHER);
        assertEquals(0, sm.recastWindow());
        assertEquals(COOLDOWN, sm.abilityCooldown());
    }

    @Test
    void anAbilityWithoutTheFlagStillCoolsDownAtTheCast() {
        sm.setWeapon(OTHER);
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(100, sm.abilityCooldown());
        assertEquals(0, sm.pendingCooldown());
    }

    // ------------------------------------------------------------------ i-frames from the move

    @Test
    void theBlinkIsInvulnerableThroughItsTravelOnly() {
        fillResonance();
        sm.pressAbility(G);
        sm.releaseAbility();
        run(4);
        sm.armRecast(BLINK, 80);
        assertFalse(sm.isInvulnerable());
        sm.pressAbility(G);
        sm.releaseAbility();
        List<Boolean> frames = new ArrayList<>();
        for (int t = 0; t < 14; t++) {
            frames.add(sm.isInvulnerable());
            sm.tick(G);
        }
        for (int t = 0; t < 14; t++) {
            assertEquals(t < 6, frames.get(t), "tick " + t);
        }
        assertFalse(sm.inPerfectDodgeWindow(), "a move's i-frames are no dodge");
    }

    @Test
    void invulnerableThroughActiveCoversBothPhases() {
        MoveTraits traits = new MoveTraits(0.0, List.of(), false, 1.0f, List.of(), Optional.empty(), Optional.of(Stage.ACTIVE));
        assertTrue(traits.invulnerableIn(Stage.STARTUP));
        assertTrue(traits.invulnerableIn(Stage.ACTIVE));
        assertFalse(traits.invulnerableIn(Stage.RECOVERY));
        assertFalse(MoveTraits.NONE.invulnerableIn(Stage.STARTUP));
    }
}
