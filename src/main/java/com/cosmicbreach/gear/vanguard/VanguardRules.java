package com.cosmicbreach.gear.vanguard;

import com.cosmicbreach.combat.core.CombatMath;

/**
 * The Starfall Vanguard's numbers (GDD 5.1), pure. Two pieces, Heavy Landing: fall damage halved, and a
 * landing from {@value #SHOCKWAVE_MIN_FALL} blocks or more releases a shockwave. Four pieces, Meteoric
 * Momentum: the fall damage avoided becomes Heat for the next hit, and charging gains poise. The set
 * ability, Meteor Call, drops a meteor on a marked spot.
 */
public final class VanguardRules {
    // Heavy Landing
    public static final double FALL_DAMAGE_KEPT = 0.5;
    public static final double SHOCKWAVE_MIN_FALL = 4.0;
    public static final double SHOCKWAVE_RADIUS = 3.0;
    public static final double SHOCKWAVE_BASE = 3.0;
    public static final double SHOCKWAVE_PER_BLOCK = 0.5;
    public static final double SHOCKWAVE_MAX = 10.0;
    public static final double SHOCKWAVE_IMPACT = 12.0;

    // Meteoric Momentum
    public static final double HEAT_MAX = 20.0;
    public static final int HEAT_TICKS = 60;
    public static final double CHARGING_POISE = 10.0;

    // Meteor Call
    public static final int METEOR_COOLDOWN = 600;
    public static final double METEOR_RANGE = 24.0;
    public static final double METEOR_RADIUS = 4.0;
    public static final int METEOR_WARNING_TICKS = 30;
    public static final double METEOR_BASE = 12.0;
    /** Power scales the meteor at grade B (0.55), like an ability with no Power grade of its own. */
    public static final double METEOR_POWER_GRADE = 0.55;
    public static final double METEOR_IMPACT = 60.0;
    public static final int CRATER_TICKS = 60;
    /** A detonation on a Scorched enemy flares for +50% (GDD 8.2's status table). */
    public static final double SCORCHED_FLARE = 1.5;

    private VanguardRules() {
    }

    /** The shockwave's damage for a landing from {@code fallBlocks}: 3 + 0.5 a block, at most 10; 0 below 4 blocks. */
    public static double shockwaveDamage(double fallBlocks) {
        if (!(fallBlocks >= SHOCKWAVE_MIN_FALL)) {
            return 0.0;
        }
        return Math.min(SHOCKWAVE_MAX, SHOCKWAVE_BASE + SHOCKWAVE_PER_BLOCK * fallBlocks);
    }

    /** Fall damage after Heavy Landing. */
    public static double heavyLanding(double fallDamage) {
        return Math.max(0.0, fallDamage) * FALL_DAMAGE_KEPT;
    }

    /** Heat after storing {@code avoided} fall damage on top of what is still stored: never above 20. */
    public static double storeHeat(double stored, double avoided) {
        return Math.min(HEAT_MAX, Math.max(0.0, stored) + Math.max(0.0, avoided));
    }

    /**
     * Vanilla's fall damage ({@code LivingEntity.calculateFallDamage}): the blocks past the safe distance,
     * times the event's multiplier and the fall damage attribute, rounded up; never negative.
     */
    public static double vanillaFallDamage(double fallDistance, double multiplier, double safeDistance, double attributeMultiplier) {
        return Math.max(0.0, Math.ceil((fallDistance - safeDistance) * multiplier * attributeMultiplier));
    }

    /** Meteor Call's damage at {@code power}: 12 x (1 + 0.55 x S(Power)); x1.5 on a Scorched target. */
    public static double meteorDamage(int power, boolean scorched) {
        double damage = METEOR_BASE * (1.0 + METEOR_POWER_GRADE * CombatMath.statCurve(power));
        return scorched ? damage * SCORCHED_FLARE : damage;
    }
}
