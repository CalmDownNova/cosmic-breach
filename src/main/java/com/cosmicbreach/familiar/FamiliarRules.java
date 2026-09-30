package com.cosmicbreach.familiar;

import java.util.List;

/**
 * Every number of the combat familiars (GDD 8.2), pure. Ticks throughout (20 a second).
 *
 * <p>Scaling: health {@value #HEALTH_BASE} + {@value #HEALTH_PER_RESILIENCE} x the owner's Resilience (the Gravikin
 * x{@value #GRAVIKIN_HEALTH}), damage {@value #DAMAGE_BASE} + {@value #DAMAGE_PER_ARCANE} x the owner's Arcane, a hit
 * every {@value #ATTACK_INTERVAL} ticks. The lantern: dark for {@value #DARK_TICKS} ticks after a death; a dismissed
 * familiar heals in its lantern, whole again after {@value #REST_TICKS}. Beyond {@value #TELEPORT_RANGE} blocks it
 * teleports back.
 */
public final class FamiliarRules {
    // ------------------------------------------------------------------ scaling
    public static final double HEALTH_BASE = 10.0;
    public static final double HEALTH_PER_RESILIENCE = 0.5;
    public static final double GRAVIKIN_HEALTH = 1.5;
    public static final double DAMAGE_BASE = 2.0;
    public static final double DAMAGE_PER_ARCANE = 0.1;

    // ------------------------------------------------------------------ the lantern
    /** A dead familiar's lantern stays dark this long (60 s). */
    public static final int DARK_TICKS = 1200;
    /** A dismissed familiar is whole again after this long in its lantern (it heals evenly meanwhile). */
    public static final int REST_TICKS = 1200;
    /** The familiar key held this many ticks summons or dismisses; a shorter press is a tap, which cycles the mode. */
    public static final int HOLD_TICKS = 8;

    // ------------------------------------------------------------------ following and fighting
    /** Farther than this from its owner, it teleports back. */
    public static final double TELEPORT_RANGE = 16.0;
    /** It never fights anything farther than this from its owner. */
    public static final double TARGET_RANGE = 16.0;
    /** One hit every this many ticks. */
    public static final int ATTACK_INTERVAL = 30;
    /** It strikes from this near its target's middle. */
    public static final double ATTACK_REACH = 1.5;
    /** "An enemy you hit recently", and "what hurt you lately": within this many ticks. */
    public static final int RECENT_TICKS = 100;
    /** In Guard it only fights what is this near its owner. */
    public static final double GUARD_RADIUS = 6.0;
    /** In Attack it also seeks out monsters this near its owner. */
    public static final double SEEK_RADIUS = 8.0;
    /** Health it regains every {@value #REGEN_EVERY} ticks once it has gone {@value #REGEN_AFTER} ticks unhurt. */
    public static final float REGEN = 1.0f;
    public static final int REGEN_EVERY = 40;
    public static final int REGEN_AFTER = 100;

    // ------------------------------------------------------------------ Emberwisp
    /** Scorch on its owner's recent target every 3 s. */
    public static final int SCORCH_EVERY = 60;
    /** Kindled: +10% damage for 60 ticks after a perfect dodge or a parry. */
    public static final int KINDLED_TICKS = 60;
    public static final double KINDLED_BONUS = 0.10;

    // ------------------------------------------------------------------ Gravikin
    /** A taunt every 20 s: enemies within 8 blocks target it for 80 ticks; a boss takes 150 threat for its owner. */
    public static final int TAUNT_EVERY = 400;
    public static final int TAUNT_TICKS = 80;
    public static final double TAUNT_RADIUS = 8.0;
    public static final double TAUNT_THREAT = 150.0;
    /** Enemies next to it (within this of its middle) move 20% slower. */
    public static final double SLOW = 0.20;
    public static final double SLOW_RADIUS = 2.5;
    /** The slow is put on for this long and refreshed every {@value #SLOW_REFRESH} ticks while they stay near. */
    public static final int SLOW_TICKS = 10;
    public static final int SLOW_REFRESH = 5;

    // ------------------------------------------------------------------ Prism Moth
    /** A Refract stack on its owner's target every 2 s. */
    public static final int REFRACT_EVERY = 40;
    /** One negative status taken off its owner every 30 s. */
    public static final int CLEANSE_EVERY = 600;

    // ------------------------------------------------------------------ hatching
    /** A Star Egg hatches after 10 minutes of loaded time in a Brazier of Solenne (at once under a Solar Flare). */
    public static final int HATCH_TICKS = 12000;

    private FamiliarRules() {
    }

    /** Max health of a {@code kind} whose owner has {@code resilience} points: (10 + 0.5 x Resilience), x1.5 for the Gravikin. */
    public static double maxHealth(FamiliarKind kind, int resilience) {
        return (HEALTH_BASE + HEALTH_PER_RESILIENCE * Math.max(0, resilience)) * kind.healthScale();
    }

    /** One hit's damage for an owner with {@code arcane} points: 2 + 0.1 x Arcane. */
    public static double damage(int arcane) {
        return DAMAGE_BASE + DAMAGE_PER_ARCANE * Math.max(0, arcane);
    }

    /** The damage multiplier Kindled gives (1 without it). */
    public static double kindledMultiplier(boolean kindled) {
        return kindled ? 1.0 + KINDLED_BONUS : 1.0;
    }

    /** The movement speed multiplier the Gravikin's slow leaves an enemy next to it. */
    public static double slowedSpeed() {
        return 1.0 - SLOW;
    }

    /**
     * The share of health a familiar comes out with: what it went in with ({@code health}, 0 to 1) plus what it healed
     * over {@code restedTicks} in its lantern (whole after {@value #REST_TICKS}).
     */
    public static float restored(float health, long restedTicks) {
        double h = Math.max(0.0, Math.min(1.0, health)) + Math.max(0L, restedTicks) / (double) REST_TICKS;
        return (float) Math.min(1.0, h);
    }

    /** True once a Star Egg has spent {@code hatchTicks} of loaded time in a brazier. */
    public static boolean hatched(int progress, int hatchTicks) {
        return progress >= hatchTicks;
    }

    /** A status on the owner the cleanse may take: whether it is Rift, and how long it has left (-1 for no end). */
    public record Status(boolean rift, int duration) {
    }

    /**
     * Which of the owner's negative statuses the Prism Moth takes: Rift first (the GDD names it), else the one with the
     * most time left (no end counts as the most). -1 if there are none.
     */
    public static int pickCleanse(List<Status> statuses) {
        int best = -1;
        long bestKey = Long.MIN_VALUE;
        for (int i = 0; i < statuses.size(); i++) {
            Status s = statuses.get(i);
            long key = s.rift() ? Long.MAX_VALUE : s.duration() < 0 ? Long.MAX_VALUE - 1 : s.duration();
            if (best < 0 || key > bestKey) {
                best = i;
                bestKey = key;
            }
        }
        return best;
    }

    /** What the taunt knows about a creature near the Gravikin. */
    public record Near(boolean boss, boolean enemy, boolean onUs, double distance) {
    }

    /**
     * Whether the taunt pulls this creature: within {@value #TAUNT_RADIUS} blocks, not a boss (bosses take threat
     * instead), and in Attack any monster or anything after the owner or the Gravikin; in Guard only what is after
     * them ({@code onUs}); in Passive nothing.
     */
    public static boolean taunts(FamiliarMode mode, Near near) {
        if (!mode.fights() || near.boss() || near.distance() > TAUNT_RADIUS) {
            return false;
        }
        return near.onUs() || (mode == FamiliarMode.ATTACK && near.enemy());
    }

    /** Whether a boss this far away takes the taunt's threat: within {@value #TAUNT_RADIUS}, and not in Passive. */
    public static boolean tauntsBoss(FamiliarMode mode, double distance) {
        return mode.fights() && distance <= TAUNT_RADIUS;
    }
}
