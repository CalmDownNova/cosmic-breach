package com.cosmicbreach.progression;

import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.core.StatBlock;

import java.util.Map;

/**
 * Every number the four attributes give (GDD sections 3.3 and 3.4), from effective points. The
 * allocation screen shows these, and the game uses the same {@link CombatMath} formulas.
 *
 * @param critChance          chance of a crit, 0 to 0.5
 * @param critMultiplier      damage multiplier of a crit
 * @param dashCharges         dash charges
 * @param dashRechargeSeconds seconds for one charge to come back
 * @param dashIframes         invulnerable ticks of a dash
 * @param moveSpeedBonus      extra move speed as a fraction (0.12 is +12%)
 * @param maxResonance        Resonance cap
 * @param haste               Haste
 * @param cooldownFactor      what cooldowns are multiplied by
 * @param bonusHealth         extra max health (2 is one heart)
 * @param damageReduction     share of damage taken away after armor
 * @param parryWindow         active parry ticks
 * @param poise               Impact it takes to stagger the player
 */
public record DerivedStats(double critChance, double critMultiplier, int dashCharges, double dashRechargeSeconds,
                           int dashIframes, double moveSpeedBonus, int maxResonance, double haste, double cooldownFactor,
                           double bonusHealth, double damageReduction, int parryWindow, double poise) {

    public static DerivedStats of(StatBlock s) {
        double haste = CombatMath.haste(s, 0);
        return new DerivedStats(
                CombatMath.critChance(s, 0),
                CombatMath.critMultiplier(s, 0),
                CombatMath.maxDashCharges(s, 0),
                CombatMath.dashRechargeTicks(s) / 20.0,
                CombatMath.dashIframes(s),
                CombatMath.moveSpeedBonus(s),
                CombatMath.maxResonance(s, 0),
                haste,
                CombatMath.cooldownTicks(1.0, haste),
                CombatMath.maxHealthBonus(s),
                CombatMath.damageReduction(s),
                CombatMath.parryWindow(s, 0),
                CombatMath.playerPoise(s, 0));
    }

    /**
     * How much a weapon's grade in {@code stat} adds to its damage at these points, as a fraction of
     * the zero-stat damage (0.275 is +27.5%): grade coefficient x S(points), 0 without a grade.
     */
    public static double gradeBonus(Map<Stat, Grade> grades, Stat stat, StatBlock s) {
        Grade grade = grades.get(stat);
        return grade == null ? 0.0 : grade.coefficient * CombatMath.statCurve(s.get(stat));
    }

    /**
     * What Arcane adds to a weapon's abilities: its own Arcane grade if it has one, else grade B
     * (GDD section 3.4), as a fraction of the zero-stat damage.
     */
    public static double abilityArcaneBonus(Map<Stat, Grade> grades, StatBlock s) {
        Grade grade = grades.getOrDefault(Stat.ARCANE, Grade.B);
        return grade.coefficient * CombatMath.statCurve(s.arcane());
    }

    /** Damage taken after armor, with the Resilience reduction. */
    public static float reduce(float damageAfterArmor, StatBlock s) {
        double reduction = CombatMath.damageReduction(s);
        return reduction <= 0 ? damageAfterArmor : (float) (damageAfterArmor * (1.0 - reduction));
    }
}
