package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
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

/** The server's damage and knockback numbers for Meridian at zero stats (GDD sections 3.4 and 4.2). */
class HitDamageTest {
    private static final ResourceLocation L1 = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "meridian/l1");
    private static final WeaponDef MERIDIAN = new WeaponDef(5.0, Map.of(Stat.POWER, Grade.B, Stat.AGILITY, Grade.C),
            3.5, List.of(L1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1);

    private static MoveDef move(MoveKind kind, double mv, double impact) {
        return new MoveDef(kind, new MoveDef.Timing(3, 2, 5), new MoveDef.Hit(mv, 0, 0, impact, 6, 2, 1, 0),
                new HitShape.Arc(3.5, 110, 2.5), Optional.empty(), L1, 0.0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void risingCutDealsTheBaseDamage() {
        assertEquals(5.0, HitResolver.damage(MERIDIAN, move(MoveKind.LIGHT, 1.0, 6), 1.0, false, false), 1e-9);
    }

    @Test
    void eachReforgeAboveTheUnlockTierAddsFifteenPercent() {
        MoveDef l1 = move(MoveKind.LIGHT, 1.0, 6);
        assertEquals(5.0 * 1.15, HitResolver.damage(MERIDIAN, l1, 1.0, false, false, com.cosmicbreach.combat.core.StatBlock.ZERO,
                1.0, 1), 1e-9, "Meridian reforged to T2");
        assertEquals(5.0 * Math.pow(1.15, 3), HitResolver.damage(MERIDIAN, l1, 1.0, false, false,
                com.cosmicbreach.combat.core.StatBlock.ZERO, 1.0, 3), 1e-9, "to T4");
        assertEquals(5.0, HitResolver.damage(MERIDIAN, l1, 1.0, false, false, com.cosmicbreach.combat.core.StatBlock.ZERO,
                1.0, 0), 1e-9);
    }

    @Test
    void critAndRiposteMultiply() {
        MoveDef l1 = move(MoveKind.LIGHT, 1.0, 6);
        assertEquals(7.5, HitResolver.damage(MERIDIAN, l1, 1.0, true, false), 1e-9, "crit x1.5 at zero Power");
        assertEquals(7.5, HitResolver.damage(MERIDIAN, l1, 1.0, false, true), 1e-9, "riposte x1.5");
        assertEquals(11.25, HitResolver.damage(MERIDIAN, l1, 1.0, true, true), 1e-9);
    }

    @Test
    void theUsesMotionValueCounts() {
        MoveDef line = move(MoveKind.CHARGED, 2.2, 25);
        assertEquals(11.0, HitResolver.damage(MERIDIAN, line, 2.2, false, false), 1e-9);
        assertEquals(15.0, HitResolver.damage(MERIDIAN, line, 3.0, false, false), 1e-9, "a full charge");
        assertEquals(6.0, HitResolver.damage(MERIDIAN, line, 1.2, false, false), 1e-9, "the ground wave");
        double plunge = CombatMath.plungeMv(1.4, 0.1, 2.4, 6.0);
        assertEquals(10.0, HitResolver.damage(MERIDIAN, move(MoveKind.PLUNGE, 1.4, 20), plunge, false, false), 1e-9);
    }

    @Test
    void abilitiesUseAbilityScalingWhichIsFlatAtZeroStats() {
        assertEquals(7.0, HitResolver.damage(MERIDIAN, move(MoveKind.ABILITY, 1.4, 18), 1.4, false, false), 1e-9);
    }

    @Test
    void knockbackGrowsWithImpact() {
        assertEquals(0.52, HitResolver.knockback(6), 1e-9);
        assertEquals(0.90, HitResolver.knockback(25), 1e-9);
    }
}
