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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cosmic weather's say in the machine (GDD 2.5, W3b): a Solar Flare doubles the Resonance earned from hits,
 * perfect dodges and parries ({@link CombatStateMachine#setResonanceGain}); an Eclipse Surge makes abilities
 * cost 25% less ({@link CombatStateMachine#setAbilityCostScale}). Both sides set the same numbers each tick.
 */
class WeatherInMachineTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("meridian/l1");
    private static final ResourceLocation ZENITH = id("meridian/zenith");

    private static MoveDef move(MoveKind kind, int resonance, MoveDef.Launch launch) {
        return new MoveDef(kind, new MoveDef.Timing(3, 2, 5),
                new MoveDef.Hit(1.0, 0, 0, 6, resonance, 2, 1, 0),
                new HitShape.Arc(3.5, 110, 2.5), Optional.empty(), id("anim"), 0.0,
                Optional.empty(), Optional.ofNullable(launch), Optional.empty(), Optional.empty());
    }

    private static final Map<ResourceLocation, MoveDef> MOVES = Map.of(
            L1, move(MoveKind.LIGHT, 6, null),
            ZENITH, move(MoveKind.ABILITY, 0, new MoveDef.Launch(3.5, 30, 40, 3.0)));

    private static final WeaponDef WEAPON = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B), 3.5, List.of(L1),
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new WeaponDef.Ability(ZENITH, 30, 100)), 1);

    private static final Context G = Context.GROUNDED;

    private CombatStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new CombatStateMachine(MOVES::get);
        sm.setWeapon(WEAPON);
    }

    private void tap() {
        sm.pressAttack(G);
        sm.releaseAttack(G);
    }

    @Test
    void calmWeatherChangesNothing() {
        assertEquals(1.0, sm.resonanceGain(), 1e-12);
        assertEquals(1.0, sm.abilityCostScale(), 1e-12);
        assertEquals(30.0, sm.abilityCost(), 1e-12);
        sm.syncFromServer(0, 2, 0);
        tap();
        sm.onHitLanded(sm.current(), 1);
        assertEquals(6.0, sm.resonance(), 1e-9);
    }

    @Test
    void aFlareDoublesHitsDodgesAndParries() {
        sm.setResonanceGain(2.0);
        sm.syncFromServer(0, 2, 0);
        tap();
        sm.onHitLanded(sm.current(), 5);
        assertEquals(36.0, sm.resonance(), 1e-9, "6 a target, at most 3 targets, doubled");
        sm.onPerfectDodge();
        assertEquals(66.0, sm.resonance(), 1e-9, "15 doubled");
        sm.onParrySuccess();
        assertEquals(100.0, sm.resonance(), 1e-9, "25 doubled is 50, capped at the maximum");
    }

    @Test
    void theGainNeverRaisesTheCap() {
        sm.setResonanceGain(2.0);
        sm.syncFromServer(90, 2, 0);
        sm.onParrySuccess();
        assertEquals(sm.maxResonance(), sm.resonance(), 1e-9);
    }

    @Test
    void theOutOfCombatDriftIsNotAGain() {
        CombatStateMachine calm = new CombatStateMachine(MOVES::get);
        calm.setWeapon(WEAPON);
        sm.setResonanceGain(2.0);
        sm.syncFromServer(10, 2, 0);
        calm.syncFromServer(10, 2, 0);
        for (int i = 0; i < 20; i++) {
            sm.tick(G);
            calm.tick(G);
        }
        assertEquals(calm.resonance(), sm.resonance(), 1e-9);
        assertEquals(15.0, sm.resonance(), 1e-9, "5 a second toward 30, whatever the weather");
    }

    @Test
    void aSurgeMakesTheAbilityCheaper() {
        sm.setAbilityCostScale(0.75);
        assertEquals(22.5, sm.abilityCost(), 1e-12);
        sm.syncFromServer(25, 2, 0);
        sm.pressAbility(G);
        sm.releaseAbility();
        assertEquals(ZENITH, sm.current().id(), "25 covers the Surge's 22.5");
        assertEquals(2.5, sm.resonance(), 1e-9);
        assertEquals(100, sm.abilityCooldown(), "the cooldown is the weapon's own");
    }

    @Test
    void withoutTheSurgeTheSameResonanceIsNotEnough() {
        sm.syncFromServer(25, 2, 0);
        sm.pressAbility(G);
        sm.releaseAbility();
        assertNull(sm.current(), "25 is under the cost of 30");
        List<CombatEvent> events = sm.tick(G);
        assertTrue(events.stream().anyMatch(e -> e instanceof CombatEvent.Denied d && d.action() == CombatEvent.Action.ABILITY_RESONANCE));
    }

    @Test
    void noWeaponCostsNothingAndNegativeValuesClampToZero() {
        sm.setWeapon(null);
        assertEquals(0.0, sm.abilityCost(), 1e-12);
        sm.setResonanceGain(-1.0);
        sm.setAbilityCostScale(-3.0);
        assertEquals(0.0, sm.resonanceGain(), 1e-12);
        assertEquals(0.0, sm.abilityCostScale(), 1e-12);
    }
}
