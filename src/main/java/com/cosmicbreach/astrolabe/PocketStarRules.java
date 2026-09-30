package com.cosmicbreach.astrolabe;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Pocket Star and its Supernova (GDD 4.2), and the Singularity, pure.
 *
 * <ul>
 *   <li>The star goes where you aim, up to {@value #RANGE} blocks ({@link #placement}), for {@value #LIFE} ticks. It
 *       pulses every {@value #PULSE_EVERY} ticks ({@link #pulsesAt}: radius {@value #PULSE_RADIUS}, motion value
 *       {@value #PULSE_MV}), swallows enemy projectiles within {@value #SWALLOW_RADIUS} blocks, and lights the ground
 *       around it to {@value #LIGHT}, which Hollow Stalkers won't enter.</li>
 *   <li>The ability's second press is the Supernova: an early detonation, radius {@value #NOVA_RADIUS}, motion value
 *       {@value #NOVA_BASE} + {@value #NOVA_BONUS} x the share of the star's time left ({@link #novaMv}), Impact
 *       {@value #NOVA_IMPACT}.</li>
 *   <li>The Singularity (co-op): a Pocket Star inside a Comet Maul's Gravity Well makes the well pull
 *       {@value #SINGULARITY_PULL} times as hard ({@link #pullScale}); a star that has been inside one is a Singularity
 *       for the rest of its life, and its Supernova deals {@value #SINGULARITY_NOVA} times as much.</li>
 * </ul>
 */
public final class PocketStarRules {
    public static final double RANGE = 16.0;
    public static final int LIFE = 80;
    public static final int PULSE_EVERY = 10;
    public static final double PULSE_RADIUS = 3.0;
    public static final double PULSE_MV = 0.6;
    public static final double PULSE_IMPACT = 4.0;
    public static final double SWALLOW_RADIUS = 4.0;
    public static final int LIGHT = 15;
    /** The star floats this far back from the surface it was aimed at. */
    public static final double BACK_OFF = 0.8;
    public static final double NOVA_RADIUS = 5.0;
    public static final double NOVA_BASE = 1.0;
    public static final double NOVA_BONUS = 3.0;
    public static final double NOVA_IMPACT = 40.0;
    public static final double SINGULARITY_PULL = 2.0;
    public static final double SINGULARITY_NOVA = 1.5;
    /** Ground lights: one under the star and four this far out, so light 12 or more covers a patch about 5 blocks round. */
    public static final int GROUND_LIGHT_SPREAD = 3;

    private PocketStarRules() {
    }

    /** Where the star goes: just short of what the aim hit ({@code hit}), else {@code range} blocks along the aim. */
    public static Vec3 placement(Vec3 eye, Vec3 look, @Nullable Vec3 hit, double range) {
        Vec3 dir = look.normalize();
        if (hit == null) {
            return eye.add(dir.scale(range));
        }
        double d = Math.max(0.0, hit.distanceTo(eye) - BACK_OFF);
        return eye.add(dir.scale(d));
    }

    /** True if the star pulses at age {@code age}: every {@value #PULSE_EVERY} ticks through its life, its last included. */
    public static boolean pulsesAt(int age) {
        return age > 0 && age <= LIFE && age % PULSE_EVERY == 0;
    }

    /** The share of the star's life left at {@code age}, 0 to 1. */
    public static double fractionLeft(int age, int life) {
        if (life <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (life - age) / (double) life));
    }

    /** The Supernova's motion value at {@code age}: base + bonus x the share left, x1.5 for a Singularity. */
    public static double novaMv(int age, int life, double base, double bonus, boolean singular) {
        double mv = base + bonus * fractionLeft(age, life);
        return singular ? mv * SINGULARITY_NOVA : mv;
    }

    /** True if a star at {@code star} is inside a well of {@code radius} round {@code wellCentre}. */
    public static boolean inside(Vec3 star, Vec3 wellCentre, double radius) {
        return star.distanceTo(wellCentre) <= radius;
    }

    /** How hard a Gravity Well pulls: twice as hard with a Pocket Star inside it. */
    public static double pullScale(boolean starInside) {
        return starInside ? SINGULARITY_PULL : 1.0;
    }

    /** The ground lights' offsets from the point under the star, in blocks: the middle and four round it. */
    public static int[][] groundLights() {
        int s = GROUND_LIGHT_SPREAD;
        return new int[][] {{0, 0}, {s, 0}, {-s, 0}, {0, s}, {0, -s}};
    }
}
