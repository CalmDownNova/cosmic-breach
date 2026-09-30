package com.cosmicbreach.combat.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.cosmicbreach.combat.core.CombatMath.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The numbers in GDD section 3 and 4.3, checked. */
class CombatMathTest {

    @Test
    void statCurveHalvesAtThirty() {
        assertEquals(0.0, statCurve(0));
        assertEquals(0.25, statCurve(10), 1e-12);
        assertEquals(0.40, statCurve(20), 1e-12);
        assertEquals(0.50, statCurve(30), 1e-12);
    }

    @Test
    void meridianBuildScaling() {
        StatBlock build = new StatBlock(30, 24, 0, 0);
        double expected = 1 + 0.55 * 0.5 + 0.30 * (24.0 / 54.0);
        assertEquals(expected, scaling(Map.of(Stat.POWER, Grade.B, Stat.AGILITY, Grade.C), build), 1e-12);
        assertEquals(1.408, expected, 0.001);
    }

    @Test
    void abilitiesAddArcaneAtGradeBOnlyWithoutAnArcaneGrade() {
        StatBlock arcane30 = new StatBlock(0, 0, 30, 0);
        assertEquals(1 + 0.55 * 0.5, abilityScaling(Map.of(Stat.POWER, Grade.A), arcane30), 1e-12);
        assertEquals(1 + 1.0 * 0.5, abilityScaling(Map.of(Stat.ARCANE, Grade.S), arcane30), 1e-12);
    }

    @Test
    void cooldownNeverReachesZero() {
        double haste = haste(new StatBlock(0, 0, 30, 0), 0);
        assertEquals(45.0, haste, 1e-12);
        assertEquals(100 * 100 / 145.0, cooldownTicks(100, haste), 1e-9);
        assertEquals(100 * 100 / 10_100.0, cooldownTicks(100, 10_000), 1e-9);
    }

    @Test
    void critNumbers() {
        assertEquals(0.05, critChance(StatBlock.ZERO, 0), 1e-12);
        assertEquals(0.23, critChance(new StatBlock(0, 30, 0, 0), 0), 1e-12);
        assertEquals(0.50, critChance(new StatBlock(0, 100, 0, 0), 0), 1e-12);
        assertEquals(1.8, critMultiplier(new StatBlock(30, 0, 0, 0), 0), 1e-12);
        assertEquals(2.5, critMultiplier(new StatBlock(500, 0, 0, 0), 0), 1e-12);
    }

    @Test
    void dashAndParryNumbers() {
        StatBlock agility30 = new StatBlock(0, 30, 0, 0);
        assertEquals(3, dashIframes(StatBlock.ZERO));
        assertEquals(6, dashIframes(agility30));
        assertEquals(2, maxDashCharges(StatBlock.ZERO, 0));
        assertEquals(3, maxDashCharges(agility30, 0));
        assertEquals(40.0, dashRechargeTicks(StatBlock.ZERO), 1e-12);
        assertEquals(25.0, dashRechargeTicks(agility30), 1e-12);
        assertEquals(4, parryWindow(StatBlock.ZERO, 0));
        assertEquals(6, parryWindow(new StatBlock(0, 0, 0, 30), 0));
        assertEquals(190, maxResonance(new StatBlock(0, 0, 30, 0), 0));
    }

    @Test
    void chargedAndPlungeMotionValues() {
        assertEquals(2.2, chargedMv(2.2, 3.0, 12, 12, 24), 1e-12);
        assertEquals(2.6, chargedMv(2.2, 3.0, 18, 12, 24), 1e-12);
        assertEquals(3.0, chargedMv(2.2, 3.0, 40, 12, 24), 1e-12);
        assertEquals(1.9, plungeMv(1.4, 0.1, 2.4, 5), 1e-12);
        assertEquals(2.4, plungeMv(1.4, 0.1, 2.4, 30), 1e-12);
    }

    @Test
    void xpCurveMatchesGdd() {
        assertEquals(100, xpToNext(1));
        assertEquals(1631, xpToNext(10));
        assertEquals(17_200, xpToNext(49));
        int toTen = 0;
        for (int level = 1; level < 10; level++) {
            toTen += xpToNext(level);
        }
        assertEquals(6_002, toTen);
    }

    @Test
    void meridianComboDpsIsIronSwordTier() {
        double damage = 5.0 * (1.0 + 1.0 + 1.6);
        double seconds = (3 + 2 + 5 + 3 + 2 + 6 + 5 + 2 + 9) / 20.0;
        assertEquals(9.73, damage / seconds, 0.01);
    }

    @Test
    void reforgeStepsMultiplyBase() {
        assertEquals(5.0 * Math.pow(1.15, 3), hitDamage(5.0, 3, 1.0, 1.0, 1, 1, 1), 1e-9);
        assertEquals(5.0 * 1.6 * 1.5, hitDamage(5.0, 0, 1.6, 1.0, 1.5, 1, 1), 1e-9);
    }
}
