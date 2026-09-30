package com.cosmicbreach.progression;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every number in GDD section 3.3's attribute table, as the allocation screen and the game compute them. */
class DerivedStatsTest {
    private static final Map<Stat, Grade> MERIDIAN = Map.of(Stat.POWER, Grade.B, Stat.AGILITY, Grade.C);

    @Test
    void atZero() {
        DerivedStats d = DerivedStats.of(StatBlock.ZERO);
        assertEquals(0.05, d.critChance(), 1e-12);
        assertEquals(1.5, d.critMultiplier(), 1e-12);
        assertEquals(2, d.dashCharges());
        assertEquals(2.0, d.dashRechargeSeconds(), 1e-12);
        assertEquals(3, d.dashIframes());
        assertEquals(0.0, d.moveSpeedBonus(), 1e-12);
        assertEquals(100, d.maxResonance());
        assertEquals(0.0, d.haste(), 1e-12);
        assertEquals(1.0, d.cooldownFactor(), 1e-12);
        assertEquals(0.0, d.bonusHealth(), 1e-12);
        assertEquals(0.0, d.damageReduction(), 1e-12);
        assertEquals(4, d.parryWindow());
        assertEquals(10.0, d.poise(), 1e-12);
    }

    @Test
    void powerThirty() {
        DerivedStats d = DerivedStats.of(new StatBlock(30, 0, 0, 0));
        assertEquals(1.8, d.critMultiplier(), 1e-12, "crit multiplier 1.8x");
        assertEquals(0.275, DerivedStats.gradeBonus(MERIDIAN, Stat.POWER, new StatBlock(30, 0, 0, 0)), 1e-12,
                "Meridian's B grade: 0.55 x S(30)");
    }

    @Test
    void agilityThirty() {
        StatBlock s = new StatBlock(0, 30, 0, 0);
        DerivedStats d = DerivedStats.of(s);
        assertEquals(0.23, d.critChance(), 1e-12, "23% crit chance");
        assertEquals(1.25, d.dashRechargeSeconds(), 1e-12, "1.25 s recharge");
        assertEquals(3, d.dashCharges(), "3 charges");
        assertEquals(0.12, d.moveSpeedBonus(), 1e-12, "+12% speed");
        assertEquals(6, d.dashIframes(), "6 i-frame ticks");
        assertEquals(0.15, DerivedStats.gradeBonus(MERIDIAN, Stat.AGILITY, s), 1e-12, "Meridian's C grade: 0.30 x S(30)");
    }

    @Test
    void arcaneThirty() {
        StatBlock s = new StatBlock(0, 0, 30, 0);
        DerivedStats d = DerivedStats.of(s);
        assertEquals(190, d.maxResonance(), "190 Resonance");
        assertEquals(45.0, d.haste(), 1e-12);
        assertEquals(0.69, d.cooldownFactor(), 0.005, "cooldowns x0.69");
        assertEquals(0.275, DerivedStats.abilityArcaneBonus(MERIDIAN, s), 1e-12, "abilities: Arcane at grade B");
        assertEquals(0.5, DerivedStats.abilityArcaneBonus(Map.of(Stat.ARCANE, Grade.S), s), 1e-12,
                "a weapon's own Arcane grade replaces the B");
    }

    @Test
    void resilienceThirty() {
        DerivedStats d = DerivedStats.of(new StatBlock(0, 0, 0, 30));
        assertEquals(12.0, d.bonusHealth(), 1e-12, "+12 health (6 hearts)");
        assertEquals(0.15, d.damageReduction(), 1e-12, "15% reduction");
        assertEquals(25.0, d.poise(), 1e-12, "poise 25");
        assertEquals(6, d.parryWindow(), "6-tick parry window");
    }

    @Test
    void perPointSteps() {
        assertEquals(2, DerivedStats.of(new StatBlock(0, 19, 0, 0)).dashCharges());
        assertEquals(3, DerivedStats.of(new StatBlock(0, 20, 0, 0)).dashCharges(), "the third charge at 20");
        assertEquals(3, DerivedStats.of(new StatBlock(0, 9, 0, 0)).dashIframes());
        assertEquals(4, DerivedStats.of(new StatBlock(0, 10, 0, 0)).dashIframes(), "+1 i-frame per 10 points");
        assertEquals(4, DerivedStats.of(new StatBlock(0, 0, 0, 14)).parryWindow());
        assertEquals(5, DerivedStats.of(new StatBlock(0, 0, 0, 15)).parryWindow(), "+1 parry tick per 15 points");
        assertEquals(1.51, DerivedStats.of(new StatBlock(1, 0, 0, 0)).critMultiplier(), 1e-12, "+0.01 per Power");
        assertEquals(0.056, DerivedStats.of(new StatBlock(0, 1, 0, 0)).critChance(), 1e-12, "+0.6% per Agility");
        assertEquals(0.004, DerivedStats.of(new StatBlock(0, 1, 0, 0)).moveSpeedBonus(), 1e-12, "+0.4% per Agility");
        assertEquals(103, DerivedStats.of(new StatBlock(0, 0, 1, 0)).maxResonance(), "+3 per Arcane");
        assertEquals(1.5, DerivedStats.of(new StatBlock(0, 0, 1, 0)).haste(), 1e-12, "+1.5 Haste per Arcane");
        assertEquals(0.4, DerivedStats.of(new StatBlock(0, 0, 0, 1)).bonusHealth(), 1e-12, "+0.4 health per Resilience");
        assertEquals(0.005, DerivedStats.of(new StatBlock(0, 0, 0, 1)).damageReduction(), 1e-12, "+0.5% per Resilience");
        assertEquals(10.5, DerivedStats.of(new StatBlock(0, 0, 0, 1)).poise(), 1e-12, "+0.5 poise per Resilience");
    }

    @Test
    void gearTakesItToForty() {
        DerivedStats d = DerivedStats.of(new StatBlock(40, 40, 40, 40));
        assertEquals(0.29, d.critChance(), 1e-12);
        assertEquals(1.9, d.critMultiplier(), 1e-12);
        assertEquals(7, d.dashIframes());
        assertEquals(40.0 / 1.8 / 20.0, d.dashRechargeSeconds(), 1e-12);
        assertEquals(220, d.maxResonance());
        assertEquals(16.0, d.bonusHealth(), 1e-12);
        assertEquals(0.20, d.damageReduction(), 1e-12);
    }

    @Test
    void reductionComesOffTheDamageLeftAfterArmor() {
        assertEquals(8.5f, DerivedStats.reduce(10f, new StatBlock(0, 0, 0, 30)), 1e-6f);
        assertEquals(10f, DerivedStats.reduce(10f, StatBlock.ZERO), 0f);
    }

    @Test
    void attributeValuesBecomeWholePoints() {
        assertEquals(12, ProgressionStats.wholePoints(12.0));
        assertEquals(12, ProgressionStats.wholePoints(12.7), "gear adds whole points; fractions round down");
        assertEquals(13, ProgressionStats.wholePoints(12.9999999999), "float error doesn't lose a point");
        assertEquals(40, ProgressionStats.wholePoints(55.0), "the effective cap");
        assertEquals(0, ProgressionStats.wholePoints(-3.0));
        assertEquals(0, ProgressionStats.wholePoints(Double.NaN));
    }
}
