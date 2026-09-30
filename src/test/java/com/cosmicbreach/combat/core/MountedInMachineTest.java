package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mounted combat (GDD 8.1): on a mount only the light chain's first two moves (L1, L2) and the weapon's ability work.
 * No third light, no charged move, no plunge or aerial in the air, no dash attack, and the dash key is the mount's.
 */
class MountedInMachineTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("w/l1");
    private static final ResourceLocation L2 = id("w/l2");
    private static final ResourceLocation L3 = id("w/l3");
    private static final ResourceLocation LINE = id("w/line");
    private static final ResourceLocation PLUNGE = id("w/plunge");
    private static final ResourceLocation AERIAL = id("w/aerial");
    private static final ResourceLocation PASS = id("w/pass");
    private static final ResourceLocation ABILITY = id("w/ability");
    /** Riding: never on the ground (a passenger isn't), looking down hard enough for a plunge. */
    private static final Context MOUNTED = new Context(false, 70f, 80.0, true);
    private static final Context AIR_DOWN = new Context(false, 70f, 80.0);

    private static MoveDef move(MoveKind kind, ResourceLocation next) {
        return new MoveDef(kind, new MoveDef.Timing(2, 2, 6), new MoveDef.Hit(1.0, 0, 0, 6, 6, 2, 1, 0),
                new HitShape.Arc(3.5, 110, 2.5), Optional.ofNullable(next), id("anim"), 0.0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static final Map<ResourceLocation, MoveDef> MOVES = Map.of(
            L1, move(MoveKind.LIGHT, L2), L2, move(MoveKind.LIGHT, L3), L3, move(MoveKind.LIGHT, null),
            LINE, move(MoveKind.CHARGED, null), PLUNGE, move(MoveKind.PLUNGE, null), AERIAL, move(MoveKind.LIGHT, null),
            PASS, move(MoveKind.LIGHT, null), ABILITY, move(MoveKind.ABILITY, null));

    private static WeaponDef weapon(boolean aerial) {
        return new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B), 3.5, List.of(L1, L2, L3), Optional.of(LINE),
                Optional.of(PLUNGE), Optional.of(PASS), Optional.of(new WeaponDef.Ability(ABILITY, 0, 100)), 1,
                Optional.empty(), Map.of(), Optional.empty(), Optional.empty(), aerial ? Optional.of(AERIAL) : Optional.empty());
    }

    private static CombatStateMachine machine(boolean aerial) {
        CombatStateMachine m = new CombatStateMachine(MOVES::get);
        m.setWeapon(weapon(aerial));
        return m;
    }

    /** Presses attack, lets the move play out to its recovery's end (its combo window opens), returns the move started. */
    private static ResourceLocation swing(CombatStateMachine m, Context ctx) {
        List<ResourceLocation> started = new ArrayList<>();
        m.pressAttack(ctx);
        m.releaseAttack(ctx);
        List<CombatEvent> events = new ArrayList<>(m.tick(ctx));
        for (int i = 0; i < 9; i++) {
            events.addAll(m.tick(ctx));
        }
        for (CombatEvent e : events) {
            if (e instanceof CombatEvent.MoveStarted s) {
                started.add(s.move().id());
            }
        }
        if (m.current() != null && started.isEmpty()) {
            started.add(m.current().id());
        }
        return started.isEmpty() ? null : started.get(0);
    }

    @Test
    void mountedTheChainIsL1ThenL2ThenL1Again() {
        CombatStateMachine m = machine(false);
        List<ResourceLocation> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            seen.add(swing(m, MOUNTED));
        }
        assertEquals(List.of(L1, L2, L1, L2, L1), seen, "only L1 and L2 while riding; L3 never comes");
    }

    @Test
    void onFootTheSameInputsReachL3() {
        CombatStateMachine m = machine(false);
        List<ResourceLocation> seen = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            seen.add(swing(m, Context.GROUNDED));
        }
        assertEquals(List.of(L1, L2, L3), seen);
    }

    @Test
    void mountedLookingDownIsNoPlungeAndNoAerial() {
        CombatStateMachine off = machine(false);
        off.pressAttack(AIR_DOWN);
        assertEquals(PLUNGE, off.current().id(), "on foot in the air, looking down: the plunge");

        CombatStateMachine m = machine(true);
        m.pressAttack(MOUNTED);
        assertEquals(L1, m.current().id(), "riding: the first light, never the plunge or the aerial");
    }

    @Test
    void mountedHoldingAttackNeverCharges() {
        CombatStateMachine m = machine(false);
        m.pressAttack(MOUNTED);
        boolean charged = false;
        for (int i = 0; i < 60; i++) {
            for (CombatEvent e : m.tick(MOUNTED)) {
                charged |= e instanceof CombatEvent.ChargeStarted;
            }
        }
        assertFalse(charged, "held attack while riding: no charged move");
        assertTrue(m.phase() != CombatStateMachine.Phase.CHARGING);

        CombatStateMachine foot = machine(false);
        foot.pressAttack(Context.GROUNDED);
        boolean footCharged = false;
        for (int i = 0; i < 60; i++) {
            for (CombatEvent e : foot.tick(Context.GROUNDED)) {
                footCharged |= e instanceof CombatEvent.ChargeStarted;
            }
        }
        assertTrue(footCharged, "the same hold on foot charges");
    }

    @Test
    void mountedTheDashIsRefusedAndTheAbilityWorks() {
        CombatStateMachine m = machine(false);
        int charges = m.dashCharges();
        m.pressDash(MOUNTED);
        List<CombatEvent> events = m.tick(MOUNTED);
        assertFalse(m.isDashing(), "the dash key belongs to the mount");
        assertTrue(events.stream().anyMatch(e -> e instanceof CombatEvent.Denied d && d.action() == CombatEvent.Action.DASH));
        assertEquals(charges, m.dashCharges(), "no charge spent");

        m.pressAbility(MOUNTED);
        assertEquals(ABILITY, m.current().id(), "the weapon's ability works while riding");
    }

    @Test
    void anAttackCancelWhileMountedStartsL1NotL3() {
        CombatStateMachine m = machine(false);
        assertEquals(L1, swing(m, MOUNTED));
        assertEquals(L2, swing(m, MOUNTED));
        // L2 finished with its combo window pointing at L3: a press now must start L1
        m.pressAttack(MOUNTED);
        assertEquals(L1, m.current().id());
    }

    @Test
    void theContextWithoutAMountFlagIsNotMounted() {
        assertFalse(new Context(true, 0f, 64.0).mounted());
        assertFalse(Context.GROUNDED.mounted());
        assertTrue(MOUNTED.mounted());
    }
}
