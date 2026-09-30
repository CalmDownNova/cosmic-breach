package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The attacker's stats in the server's damage (GDD sections 3.3 and 3.4), with Meridian's grades (Power B, Agility C). */
class StatDamageTest {
    private static final ResourceLocation L1 = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/l1");
    private static final WeaponDef MERIDIAN = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B, Stat.AGILITY, Grade.C),
            3.5, List.of(L1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);

    private static MoveDef move(MoveKind kind, double mv) {
        return new MoveDef(kind, new MoveDef.Timing(3, 2, 5), new MoveDef.Hit(mv, 0, 0, 6, 6, 2, 1, 0),
                new HitShape.Arc(3.5, 110, 2.5), Optional.empty(), L1, 0.0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void powerThirtyScalesThroughTheBGrade() {
        StatBlock power30 = new StatBlock(30, 0, 0, 0);
        assertEquals(6.375, HitResolver.damage(MERIDIAN, move(MoveKind.LIGHT, 1.0), 1.0, false, false, power30), 1e-9,
                "5 x (1 + 0.55 x 0.5)");
        assertEquals(6.375 * 1.8, HitResolver.damage(MERIDIAN, move(MoveKind.LIGHT, 1.0), 1.0, true, false, power30), 1e-9,
                "and the crit multiplier is 1.8 at Power 30");
    }

    @Test
    void agilityScalesThroughTheCGrade() {
        assertEquals(5.75, HitResolver.damage(MERIDIAN, move(MoveKind.LIGHT, 1.0), 1.0, false, false,
                new StatBlock(0, 30, 0, 0)), 1e-9, "5 x (1 + 0.30 x 0.5)");
    }

    @Test
    void gradesAdd() {
        assertEquals(5.0 * (1 + 0.55 * 0.5 + 0.30 * 0.4), HitResolver.damage(MERIDIAN, move(MoveKind.LIGHT, 1.0), 1.0,
                false, false, new StatBlock(30, 20, 0, 0)), 1e-9);
    }

    @Test
    void arcaneOnlyReachesAbilities() {
        StatBlock arcane30 = new StatBlock(0, 0, 30, 0);
        assertEquals(5.0, HitResolver.damage(MERIDIAN, move(MoveKind.LIGHT, 1.0), 1.0, false, false, arcane30), 1e-9,
                "Meridian has no Arcane grade");
        assertEquals(7.0 * 1.275, HitResolver.damage(MERIDIAN, move(MoveKind.ABILITY, 1.4), 1.4, false, false, arcane30), 1e-9,
                "Zenith gets Arcane at grade B");
    }

    @Test
    void zeroStatsAreTheOldNumbers() {
        MoveDef l1 = move(MoveKind.LIGHT, 1.0);
        assertEquals(HitResolver.damage(MERIDIAN, l1, 1.0, true, true),
                HitResolver.damage(MERIDIAN, l1, 1.0, true, true, StatBlock.ZERO), 1e-12);
    }
}
