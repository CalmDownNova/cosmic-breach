package com.cosmicbreach.combat.core;

import java.util.Map;

/** Every number formula from GDD section 3.4, in one place. Pure functions. */
public final class CombatMath {
    /** S(p) = p / (p + 30): half the benefit at 30 points. */
    public static final double STAT_HALF_POINT = 30.0;
    /** Each reforge above a weapon's unlock tier multiplies its base by this. */
    public static final double TIER_STEP = 1.15;

    private CombatMath() {
    }

    public static double statCurve(double points) {
        return points <= 0 ? 0.0 : points / (points + STAT_HALF_POINT);
    }

    /** 1 + the sum of grade coefficient x S(points) over the weapon's graded stats. */
    public static double scaling(Map<Stat, Grade> grades, StatBlock stats) {
        double sum = 1.0;
        for (Map.Entry<Stat, Grade> e : grades.entrySet()) {
            sum += e.getValue().coefficient * statCurve(stats.get(e.getKey()));
        }
        return sum;
    }

    /** Abilities add Arcane at grade B when the weapon has no Arcane grade of its own. */
    public static double abilityScaling(Map<Stat, Grade> grades, StatBlock stats) {
        double s = scaling(grades, stats);
        if (!grades.containsKey(Stat.ARCANE)) {
            s += Grade.B.coefficient * statCurve(stats.arcane());
        }
        return s;
    }

    public static double tierMultiplier(int stepsAboveUnlock) {
        return Math.pow(TIER_STEP, Math.max(0, stepsAboveUnlock));
    }

    public static double hitDamage(double base, int tierSteps, double mv, double scaling,
                                   double crit, double riposte, double buffs) {
        return base * tierMultiplier(tierSteps) * mv * scaling * crit * riposte * buffs;
    }

    public static double critChance(StatBlock s, double gear) {
        return Math.min(0.5, 0.05 + 0.006 * s.agility() + gear);
    }

    public static double critMultiplier(StatBlock s, double gear) {
        return Math.min(2.5, 1.5 + 0.01 * s.power() + gear);
    }

    public static double haste(StatBlock s, double gear) {
        return 1.5 * s.arcane() + gear;
    }

    /** base x 100 / (100 + haste): 45 haste gives x0.69, and it never reaches zero. */
    public static double cooldownTicks(double baseTicks, double haste) {
        return baseTicks * 100.0 / (100.0 + Math.max(0.0, haste));
    }

    public static int maxResonance(StatBlock s, int gear) {
        return 100 + 3 * s.arcane() + gear;
    }

    public static int dashIframes(StatBlock s) {
        return 3 + s.agility() / 10;
    }

    public static int maxDashCharges(StatBlock s, int gear) {
        return 2 + (s.agility() >= 20 ? 1 : 0) + gear;
    }

    public static double dashRechargeTicks(StatBlock s) {
        return 40.0 / (1.0 + 0.02 * s.agility());
    }

    public static int parryWindow(StatBlock s, int gear) {
        return 4 + s.resilience() / 15 + gear;
    }

    public static double maxHealthBonus(StatBlock s) {
        return 0.4 * s.resilience();
    }

    public static double damageReduction(StatBlock s) {
        return 0.005 * s.resilience();
    }

    public static double playerPoise(StatBlock s, double gear) {
        return 10 + 0.5 * s.resilience() + gear;
    }

    /** Move speed bonus as a fraction of base speed: +0.4% per Agility point (GDD section 3.3). */
    public static double moveSpeedBonus(StatBlock s) {
        return 0.004 * s.agility();
    }

    /** Motion value of a charged move released after {@code held} ticks, linear from min to full. */
    public static double chargedMv(double mvMin, double mvMax, int held, int min, int full) {
        if (full <= min) {
            return mvMax;
        }
        double t = Math.max(0.0, Math.min(1.0, (held - min) / (double) (full - min)));
        return mvMin + (mvMax - mvMin) * t;
    }

    /** Motion value of a plunge that fell {@code fallBlocks}: base + perBlock x fall, capped. */
    public static double plungeMv(double base, double perBlock, double cap, double fallBlocks) {
        return Math.min(cap, base + perBlock * Math.max(0.0, fallBlocks));
    }

    /** Attunement XP to go from level L to L+1 (GDD section 3.2). */
    public static int xpToNext(int level) {
        return (int) Math.round(50 * Math.pow(level, 1.5) + 50);
    }
}
