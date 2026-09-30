package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the Binary Edges added to a hit: the modifiers weapons register (the Mark's crit chance, the single
 * blade's damage), the damage formula's Buffs term, and the push of hits spread over one move.
 */
class HitModifiersTest {
    private static final ResourceLocation L1 = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "edges/l1");
    private static final WeaponDef EDGES = new WeaponDef(3.8, Map.of(Stat.AGILITY, Grade.A, Stat.POWER, Grade.D), 2.8,
            List.of(L1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 2);
    private static final MoveDef HOOK = new MoveDef(MoveKind.LIGHT, new MoveDef.Timing(2, 1, 3),
            new MoveDef.Hit(1.0, 0, 0, 3, 2, 1, 1, 0), new HitShape.Arc(2.8, 90, 2.5), Optional.empty(), L1, 0.0,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    @AfterEach
    void forget() {
        HitModifiers.clearForTests();
    }

    @Test
    void withNoModifierNothingChanges() {
        assertEquals(0.0, HitModifiers.critChanceBonus(null, null));
        assertEquals(1.0, HitModifiers.damageMultiplier(null, null));
        assertEquals(0.05, HitResolver.critChance(null, StatBlock.ZERO, null), 1e-9);
    }

    @Test
    void bonusesAddAndMultipliersMultiply() {
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double critChanceBonus(net.minecraft.server.level.ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
                return 0.20; // Marked
            }

            @Override
            public double damageMultiplier(net.minecraft.server.level.ServerPlayer attacker, com.cosmicbreach.combat.core.MoveInstance move) {
                return 0.6; // one blade
            }
        });
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double critChanceBonus(net.minecraft.server.level.ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
                return 0.05;
            }
        });
        assertEquals(0.25, HitModifiers.critChanceBonus(null, null), 1e-9);
        assertEquals(0.6, HitModifiers.damageMultiplier(null, null), 1e-9);
        assertEquals(0.30, HitResolver.critChance(null, StatBlock.ZERO, null), 1e-9, "5% base + 20% Mark + 5%");
    }

    @Test
    void aTargetAwareModifierSeesTheTargetAndTheOthersStillCount() {
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double damageMultiplier(net.minecraft.server.level.ServerPlayer attacker, com.cosmicbreach.combat.core.MoveInstance move) {
                return 0.6; // one blade: no say about the target
            }
        });
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double damageMultiplier(net.minecraft.server.level.ServerPlayer attacker, com.cosmicbreach.combat.core.MoveInstance move,
                                           net.minecraft.world.entity.LivingEntity target) {
                return target == null ? 1.0 : 1.1; // an Aligned target takes +10%
            }
        });
        assertEquals(0.6, HitModifiers.damageMultiplier(null, null), 1e-9, "the old question ignores the target's modifier");
        assertEquals(0.6, HitModifiers.damageMultiplier(null, null, null), 1e-9, "no target: no Alignment");
    }

    @Test
    void theMarkRaisesTheCritChanceUnderItsCap() {
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double critChanceBonus(net.minecraft.server.level.ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
                return 0.20;
            }
        });
        assertEquals(0.25, HitResolver.critChance(null, StatBlock.ZERO, null), 1e-9, "Marked at zero Agility");
        StatBlock agile = new StatBlock(0, 40, 0, 0);
        assertEquals(CombatMath.critChance(agile, 0.2), HitResolver.critChance(null, agile, null), 1e-9);
        assertEquals(0.49, HitResolver.critChance(null, agile, null), 1e-9, "5% + 40 x 0.6% + 20%");
        HitModifiers.register(new HitModifiers.Modifier() {
            @Override
            public double critChanceBonus(net.minecraft.server.level.ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
                return 0.20;
            }
        });
        assertEquals(0.5, HitResolver.critChance(null, agile, null), 1e-9, "capped at 50%");
    }

    @Test
    void buffsMultiplyTheHit() {
        assertEquals(3.8, HitResolver.damage(EDGES, HOOK, 1.0, false, false, StatBlock.ZERO), 1e-9);
        assertEquals(3.8 * 0.6, HitResolver.damage(EDGES, HOOK, 1.0, false, false, StatBlock.ZERO, 0.6), 1e-9,
                "the right blade alone at 60%");
        assertEquals(3.8 * 0.6 * 1.5, HitResolver.damage(EDGES, HOOK, 1.0, true, false, StatBlock.ZERO, 0.6), 1e-9,
                "and it still crits");
    }

    @Test
    void spreadHitsPushTogetherAsFarAsOneHitOfTheirImpact() {
        assertEquals(HitResolver.knockback(3.0), HitResolver.knockback(3.0, 1), 1e-9, "a single hit as before");
        double gyre = HitResolver.knockback(2.5, 4);
        assertEquals(HitResolver.knockback(10.0), 4 * gyre, 1e-9, "the Gyre's four hits push like one of Impact 10");
        assertEquals(0.15, gyre, 1e-9);
        assertEquals(HitResolver.knockback(18.0) / 6, HitResolver.knockback(3.0, 6), 1e-9, "the Orbit's six");
    }
}
