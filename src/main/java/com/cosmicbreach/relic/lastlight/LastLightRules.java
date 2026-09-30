package com.cosmicbreach.relic.lastlight;

/**
 * Last Light's numbers and rules (GDD 7.3), pure so they are unit-tested: the Dawnguard stance's automatic parries
 * and what each costs, when a parry's Daybreak fires, and the passive Sunlight (a charge stored per parry, up to three,
 * all spent by a charged attack for +40% each).
 *
 * <ul>
 *   <li><b>Dawnguard</b> (35 Resonance, 9 s): a 30-tick stance, the ability move's active ticks. Every hit with an
 *       attacker (a blow, a shot, a mob's blast) that lands while it holds is parried automatically for
 *       {@value #PARRY_COST} Resonance; without the Resonance to pay, the hit lands. The world's own harm (a fall,
 *       lava, the void) is not an attack and is never parried.</li>
 *   <li><b>Daybreak</b>: each parry's counter-slash, an arc of radius {@value #DAYBREAK_RADIUS} and
 *       {@value #DAYBREAK_ANGLE} degrees round the attacker's side at motion value {@value #DAYBREAK_MV}. Parries in
 *       the same tick share one Daybreak (it already cuts every attacker in the arc).</li>
 *   <li><b>Sunlight</b>: every parry with Last Light in hand, the stance's and the parry key's alike, stores a charge,
 *       up to {@value #MAX_CHARGES}. The next charged attack spends them all: its damage is multiplied by
 *       1 + {@value #CHARGE_BONUS} per charge spent (x2.2 with three).</li>
 * </ul>
 */
public final class LastLightRules {
    /** Dawnguard's stance, in ticks: the ability move's active phase. */
    public static final int STANCE_TICKS = 30;
    /** Resonance each automatic parry costs. */
    public static final double PARRY_COST = 10.0;
    /** A stance parry in these first ticks counts as an early one (the Event Horizon Lens's window, GDD 5.2). */
    public static final int STANCE_EARLY_TICKS = 2;
    public static final double DAYBREAK_RADIUS = 5.0;
    public static final double DAYBREAK_ANGLE = 270.0;
    public static final double DAYBREAK_MV = 2.4;
    /** The most Sunlight charges held at once. */
    public static final int MAX_CHARGES = 3;
    /** A charged attack's damage bonus per Sunlight charge it spends. */
    public static final double CHARGE_BONUS = 0.40;
    /** The Impact a hit counts as when its attacker doesn't say (a vanilla mob's melee), for the parry's poise damage. */
    public static final double DEFAULT_IMPACT = 10.0;

    /** What the stance does with one incoming hit. */
    public enum Outcome {
        /** Parried: cancelled, {@link #PARRY_COST} paid, a Sunlight charge stored, a Daybreak (once a tick). */
        PARRY,
        /** An attack the stance would parry, but the Resonance to pay isn't there: it lands. */
        UNPAID,
        /** Not an attack (the world's harm, or damage that nothing may stop): the stance ignores it. */
        IGNORED
    }

    private LastLightRules() {
    }

    /**
     * The stance's answer to a hit: {@code fromAttacker} if something caused it (an entity, or a projectile or blast
     * one fired), {@code unstoppable} if it bypasses invulnerability ({@code /kill}, the void), {@code resonance} what
     * the player holds.
     */
    public static Outcome answer(boolean fromAttacker, boolean unstoppable, double resonance) {
        if (!fromAttacker || unstoppable) {
            return Outcome.IGNORED;
        }
        return resonance >= PARRY_COST - 1e-9 ? Outcome.PARRY : Outcome.UNPAID;
    }

    /** True if a parry at {@code now} fires a Daybreak: the first parry of its tick does, later ones share it. */
    public static boolean daybreakDue(long lastDaybreak, long now) {
        return now != lastDaybreak;
    }

    /** Sunlight after one more parry: one more charge, never more than {@link #MAX_CHARGES}. */
    public static int store(int charges) {
        return Math.min(MAX_CHARGES, Math.max(0, charges) + 1);
    }

    /** The damage multiplier of a charged attack that spent {@code charges}: +40% each. */
    public static double chargedMultiplier(int charges) {
        return 1.0 + CHARGE_BONUS * Math.max(0, Math.min(MAX_CHARGES, charges));
    }

    /**
     * True while the Dawnguard move (startup {@code startup} ticks, then {@link #STANCE_TICKS} active) holds its
     * stance, {@code elapsed} ticks after it started.
     */
    public static boolean stanceHolds(int startup, int elapsed) {
        return elapsed >= startup && elapsed < startup + STANCE_TICKS;
    }
}
