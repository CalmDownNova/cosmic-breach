package com.cosmicbreach.combat.core;

/**
 * The balance rule for enemy damage (F1): every guardian design table gives a hit's damage before armor and what it
 * should come to against that layer's reference gear, and the second number is vanilla's armor formula applied to
 * the first ({@link #afterArmor}), then the player's Resilience reduction. Vanilla's formula cuts small hits hard
 * (armor 20 takes about 70% off a 10-point hit, 60% off a 24-point one), so a design's after-armor column must be
 * reached by choosing the damage before armor with {@link #beforeArmorFor}, never by assuming a flat share.
 * Pure; the numbers match {@code CombatRules.getDamageAfterAbsorb} in vanilla 1.21.1 for damage without enchantments.
 */
public final class ArmorMath {
    private ArmorMath() {
    }

    /** Vanilla's damage after armor: {@code damage x (1 - clamp(max(armor / 5, armor - 4 damage / (toughness + 8)), 0, 20) / 25)}. */
    public static double afterArmor(double damage, double armor, double toughness) {
        double effective = Math.max(armor / 5.0, armor - 4.0 * damage / (toughness + 8.0));
        double clamped = Math.max(0.0, Math.min(20.0, effective));
        return damage * (1.0 - clamped / 25.0);
    }

    /** After armor, then a flat reduction share (Resilience's 0.5% a point). */
    public static double taken(double damage, double armor, double toughness, double reduction) {
        return afterArmor(damage, armor, toughness) * (1.0 - reduction);
    }

    /**
     * The damage before armor that lands as {@code target} against this gear and reduction (bisection; {@link #taken}
     * only grows with damage).
     */
    public static double beforeArmorFor(double target, double armor, double toughness, double reduction) {
        double lo = 0.0;
        double hi = 1000.0;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2.0;
            if (taken(mid, armor, toughness, reduction) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0;
    }
}
