package com.cosmicbreach.combat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

/**
 * Resonance drained from outside the machine (the Unsung's Harmonize: Silence drains 30, Unsung design v1): the
 * server takes it away, never below zero, and it earns nothing (no gain, no "full"). The client catches up through the
 * usual sync.
 */
class DrainInMachineTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("meridian/l1");
    private static final ResourceLocation ZENITH = id("meridian/zenith");

    private static MoveDef move(MoveKind kind) {
        return new MoveDef(kind, new MoveDef.Timing(3, 2, 5),
                new MoveDef.Hit(1.0, 0, 0, 6, 6, 2, 1, 0),
                new HitShape.Arc(3.5, 110, 2.5), Optional.empty(), id("anim"), 0.0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static final Map<ResourceLocation, MoveDef> MOVES = Map.of(L1, move(MoveKind.LIGHT), ZENITH, move(MoveKind.ABILITY));
    private static final WeaponDef WEAPON = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B), 3.5, List.of(L1),
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new WeaponDef.Ability(ZENITH, 30, 100)), 1);

    private CombatStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new CombatStateMachine(MOVES::get);
        sm.setWeapon(WEAPON);
    }

    @Test
    void aDrainTakesResonanceAway() {
        sm.syncFromServer(50, 2, 0);
        assertEquals(30.0, sm.drainResonance(30.0), 1e-9, "all 30 taken");
        assertEquals(20.0, sm.resonance(), 1e-9);
    }

    @Test
    void neverBelowZero() {
        sm.syncFromServer(10, 2, 0);
        assertEquals(10.0, sm.drainResonance(30.0), 1e-9, "only what there was");
        assertEquals(0.0, sm.resonance(), 1e-9);
    }

    @Test
    void aDrainIsNotShapedByTheGainAndIgnoresNonsense() {
        sm.setResonanceGain(2.0);
        sm.syncFromServer(60, 2, 0);
        sm.drainResonance(30.0);
        assertEquals(30.0, sm.resonance(), 1e-9, "a Solar Flare doubles what is earned, not what is drained");
        assertEquals(0.0, sm.drainResonance(-5.0), 1e-9);
        assertEquals(0.0, sm.drainResonance(Double.NaN), 1e-9);
        assertEquals(30.0, sm.resonance(), 1e-9);
    }
}
