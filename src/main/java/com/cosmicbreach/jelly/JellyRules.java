package com.cosmicbreach.jelly;

/**
 * The drift jelly's rules and numbers (1.2 design section 7, Lane C), pure so they are tested without a world. What
 * carries them out: {@link DriftJelly} (movement, sting), {@link JellyBounce} (the bounce), {@link JellyBloom} (the swarm
 * event), {@link Jellies} (spawning).
 *
 * <ul>
 *   <li><b>Size</b>: the bell is the hitbox, 3 blocks wide and 2.25 high; its tendrils hang 4 to 5 blocks under it and are
 *       model only, the sting reaches 4 blocks below.</li>
 *   <li><b>Bounce</b>: a landing on the bell top returns 1.3 times the impact speed (a slime block returns exactly 1.0), at
 *       least 0.95 (about five blocks of height) and at most 1.6 (about 14 blocks) for an ordinary fall, never less than 1.05
 *       times the impact below 2.6; standing on it without sneaking bounces at the least, sneaking on landing stops dead.
 *       Skim, the effect it gives, adds 30 percent to air acceleration for 4 s.</li>
 *   <li><b>Sting</b>: 2 damage (a heart), once a second to each target in the volume under the bell, no knockback.</li>
 *   <li><b>Spawns</b>: hovering 2 to 5 blocks over ground always accepted; over a void rarely.</li>
 *   <li><b>Bloom</b>: 15 to 25 jellies rise from under the layer over 40 percent of a 4 to 7 minute life, drift for 30
 *       percent, then sink away for the last 30.</li>
 * </ul>
 */
public final class JellyRules {
    // ------------------------------------------------------------------ size
    public static final float WIDTH = 3.0f;
    public static final float HEIGHT = 2.25f;
    public static final float EYE_HEIGHT = 1.6f;
    /** How far under the bell the shortest tendril hangs (the model's 64 units); the sting reaches this far. */
    public static final double TENDRIL_HANG = 4.0;
    public static final double STING_HALF_WIDTH = 1.3;

    // ------------------------------------------------------------------ health, speed
    public static final double HEALTH = 24.0;
    public static final double SPEED = 0.05;
    public static final int XP = 6;
    /** Blocks per tick while it drifts and while it flees, how long a flight lasts, and how it eases toward the speed it wants. */
    public static final double WANDER_SPEED = 0.035;
    public static final double FLEE_SPEED = 0.10;
    public static final int FLEE_TICKS = 140;
    public static final double EASE = 0.06;

    // ------------------------------------------------------------------ bounce and skim
    /** Vanilla's gravity and drag, for the height of a launch. */
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    public static final double BOUNCE_FACTOR = 1.3;
    public static final double BOUNCE_MIN = 0.95;
    public static final double BOUNCE_MAX = 2.6;
    /** The speed bounces on bounces settle at (about 14 blocks high), and the least stronger than a slime block's a long fall stays. */
    public static final double CHAIN_CAP = 1.6;
    public static final double SLIME_PLUS = 1.05;
    public static final int BOUNCE_COOLDOWN = 8;
    public static final int SKIM_TICKS = 80;
    public static final float SKIM_BONUS = 0.30f;
    /** A player's air acceleration in blocks per tick squared, vanilla's. */
    public static final float AIR_ACCEL = 0.02f;

    /** The speed (blocks a tick) a fall of this many blocks arrives at, ignoring drag. */
    public static double impactSpeed(double fallDistance) {
        return Math.sqrt(2.0 * GRAVITY * Math.max(0.0, fallDistance));
    }

    /** What a slime block returns: the impact speed, whole. */
    public static double slimeLaunch(double fallDistance) {
        return impactSpeed(fallDistance);
    }

    /**
     * The jelly's launch speed for a fall. 1.3 times the impact, no less than {@link #BOUNCE_MIN}, and no more than
     * {@link #CHAIN_CAP} or 1.05 times the impact (whichever is more), nor {@link #BOUNCE_MAX}: a landing is always
     * stronger than a slime block's (up to a fall of about 38 blocks), yet keeping on landing settles at a bounce of
     * about 14 blocks instead of growing each time. Sneaking on landing is a soft stop: no launch at all ({@link JellyBounce}).
     */
    public static double launchSpeed(double fallDistance) {
        double impact = impactSpeed(fallDistance);
        double cap = Math.min(BOUNCE_MAX, Math.max(CHAIN_CAP, SLIME_PLUS * impact));
        return Math.max(BOUNCE_MIN, Math.min(cap, BOUNCE_FACTOR * impact));
    }

    /** How high, in blocks, a launch of this speed (blocks a tick) carries, under vanilla's gravity and drag. */
    public static double apexHeight(double speed) {
        double y = 0.0;
        double v = speed;
        for (int i = 0; i < 400 && v > 0; i++) {
            y += v;
            v = (v - GRAVITY) * DRAG;
        }
        return y;
    }

    /**
     * True if a player's feet at ({@code dx}, {@code dz}) from the bell's middle and height {@code feetY} are landing on
     * the bell of a jelly standing at {@code jellyY}.
     */
    public static boolean landsOnBell(double dx, double dz, double feetY, double jellyY) {
        double top = jellyY + HEIGHT;
        double reach = WIDTH / 2.0 + 0.3;
        return Math.abs(dx) <= reach && Math.abs(dz) <= reach && feetY >= top - 0.5 && feetY <= top + 0.6;
    }

    /** The extra blocks per tick squared of air acceleration Skim gives (zero without it). */
    public static float extraAirAccel(boolean skim) {
        return skim ? AIR_ACCEL * SKIM_BONUS : 0.0f;
    }

    // ------------------------------------------------------------------ sting
    public static final float STING_DAMAGE = 2.0f;
    public static final int STING_PERIOD = 10;
    public static final int STING_COOLDOWN = 20;

    /** True if a point {@code dx}, {@code dy}, {@code dz} from the bell's bottom middle is in the tendrils' volume. */
    public static boolean stingsAt(double dx, double dy, double dz) {
        return Math.abs(dx) <= STING_HALF_WIDTH && Math.abs(dz) <= STING_HALF_WIDTH && dy <= 0.0 && dy >= -TENDRIL_HANG;
    }

    /** True if a target last stung at {@code last} (a tick count) may be stung again at {@code now}. */
    public static boolean stingReady(int now, int last) {
        return now - last >= STING_COOLDOWN;
    }

    // ------------------------------------------------------------------ hovering
    /** The sentinel for no ground within the probe's reach. */
    public static final int UNKNOWN_GROUND = -1;
    public static final double HOVER_MIN = 2.0;
    public static final double HOVER_MAX = 5.0;
    /** How far down it looks for ground. */
    public static final int PROBE_DEPTH = 9;

    /** The vertical speed (blocks a tick) it wants with ground this many blocks below its feet: none in the band, nor with no ground. */
    public static double hoverVelocity(double height) {
        if (height < 0.0) {
            return 0.0;
        }
        if (height < HOVER_MIN) {
            return Math.min(0.05, 0.02 + 0.01 * (HOVER_MIN - height));
        }
        if (height > HOVER_MAX) {
            return -Math.min(0.04, 0.015 + 0.005 * (height - HOVER_MAX));
        }
        return 0.0;
    }

    // ------------------------------------------------------------------ spawning
    public static final int AREA_CAP = 3;
    public static final double AREA_RADIUS = 48.0;
    /**
     * The spawner picks a height at random under the top of the world, so nearly every position it tries is in empty air over
     * a void; the few that hover 2 to 5 blocks over ground are all taken. A void's chance is small enough that in the open
     * Spans most jellies still hang over ground (measured by the spawn scenario), and four times that where the dense tag says.
     */
    private static final double VOID_CHANCE = 0.002;
    private static final double DENSE_FACTOR = 4.0;

    /** The chance a spawn attempt with ground this many blocks below ({@link #UNKNOWN_GROUND} for none within reach) goes ahead. */
    public static double spawnChance(int groundDepth, boolean dense) {
        if (groundDepth >= 2 && groundDepth <= 5) {
            return 1.0;
        }
        if (groundDepth == 1) {
            return 0.2;
        }
        if (groundDepth == 0) {
            return 0.0;
        }
        if (groundDepth > 5 && groundDepth <= PROBE_DEPTH) {
            return 0.25;
        }
        return dense ? VOID_CHANCE * DENSE_FACTOR : VOID_CHANCE;
    }

    /** True if this many natural jellies are already near. */
    public static boolean crowded(int near) {
        return near >= AREA_CAP;
    }

    // ------------------------------------------------------------------ the bloom
    public enum BloomPhase { RISE, DRIFT, SINK, GONE }

    public static final double BLOOM_RISE = 0.04;
    public static final double BLOOM_SINK = 0.05;

    /** A bloom's size from a roll in [0, 1): 15 to 25. */
    public static int bloomCount(double roll) {
        return 15 + (int) Math.min(10, Math.floor(roll * 11.0));
    }

    /** One jelly's bloom life in ticks from a roll in [0, 1): four to seven minutes. */
    public static int bloomLife(double roll) {
        return 4800 + (int) (roll * 3600.0);
    }

    /** Ticks to the next bloom from a roll in [0, 1): twenty to sixty minutes. */
    public static long bloomDelay(double roll) {
        return 24000L + (long) (roll * 48000.0);
    }

    /** Where in its life a bloom jelly is: rising for 40 percent, drifting to 70, sinking to the end. */
    public static BloomPhase bloomPhase(long elapsed, int life) {
        if (elapsed < life * 2L / 5L) {
            return BloomPhase.RISE;
        }
        if (elapsed < life * 7L / 10L) {
            return BloomPhase.DRIFT;
        }
        return elapsed < life ? BloomPhase.SINK : BloomPhase.GONE;
    }

    /** The vertical speed (blocks a tick) of a phase. */
    public static double bloomVertical(BloomPhase phase) {
        return switch (phase) {
            case RISE -> BLOOM_RISE;
            case SINK -> -BLOOM_SINK;
            default -> 0.0;
        };
    }

    private JellyRules() {
    }
}
