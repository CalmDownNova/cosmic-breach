package com.cosmicbreach.guardian;

/**
 * Guardian health scaling (GDD 7.3, used by every guardian): {@code base x (1 + 0.6 x (players - 1))}, counting
 * the players in the arena when it wakes, at least one and at most {@value #MAX_PLAYERS}. Pure.
 *
 * <p>Vanilla caps the MAX_HEALTH attribute at {@value #VANILLA_CAP}, under a four-player Prism Colossus (1,176) and
 * every Hollow Heliarch. A guardian whose scaled health can pass it keeps its health in a {@link Pool}:
 * <ul>
 *   <li>its MAX_HEALTH attribute is {@link Pool#MIRROR} and never changes (a change would make vanilla clamp the
 *       health to it);</li>
 *   <li>on the server its {@code getHealth()} answers from the pool, so {@code getHealth()}, {@code setHealth()},
 *       {@code heal()} and vanilla's own damage all work in real health;</li>
 *   <li>its {@code setHealth()} puts the value into the pool and keeps the pool's share of {@link Pool#MIRROR} in
 *       vanilla's synced health, so clients see the right fraction ({@code getHealth()} over {@code getMaxHealth()});</li>
 *   <li>{@code getMaxHealth()} is final in vanilla and answers the mirror's {@link Pool#MIRROR}: guardian code reads
 *       {@link Pool#max()} and {@link Pool#fraction()} instead.</li>
 * </ul>
 */
public final class GuardianHealth {
    public static final int MAX_PLAYERS = 4;
    public static final double PER_EXTRA_PLAYER = 0.6;
    /** Vanilla's ceiling on the MAX_HEALTH attribute. */
    public static final double VANILLA_CAP = 1024.0;

    private GuardianHealth() {
    }

    /** The multiplier for {@code players} players (1.0 solo, 2.8 for four or more). */
    public static double factor(int players) {
        int n = Math.max(1, Math.min(MAX_PLAYERS, players));
        return 1.0 + PER_EXTRA_PLAYER * (n - 1);
    }

    /** {@code base} health scaled for {@code players} players. */
    public static double scaled(double base, int players) {
        return base * factor(players);
    }

    /** True if {@code base} scaled for a full arena passes vanilla's cap: that guardian needs a {@link Pool}. */
    public static boolean needsPool(double base) {
        return scaled(base, MAX_PLAYERS) > VANILLA_CAP;
    }

    /** A guardian's health past vanilla's cap, in real units (see the class notes). */
    public static final class Pool {
        /** The pooled guardian's MAX_HEALTH attribute: vanilla's health carries the pool's share of this. */
        public static final float MIRROR = 1000f;
        /** The least mirror while any health is left, so a client never sees a living guardian at zero. */
        public static final float MIRROR_FLOOR = 0.01f;

        private double max;
        private double left;

        public Pool(double max) {
            reset(max);
        }

        /** A full pool of {@code base} health scaled for {@code players} players. */
        public static Pool scaled(double base, int players) {
            return new Pool(GuardianHealth.scaled(base, players));
        }

        /** A new fight: {@code max} health, all of it left. */
        public void reset(double max) {
            this.max = Math.max(1e-6, max);
            this.left = this.max;
        }

        /** Sets the health left, kept between zero and the maximum. */
        public void set(double health) {
            left = Math.max(0.0, Math.min(max, health));
        }

        public double max() {
            return max;
        }

        public double left() {
            return left;
        }

        public double fraction() {
            return left / max;
        }

        /** True if this pool holds more than a MAX_HEALTH attribute could. */
        public boolean pastVanillaCap() {
            return max > VANILLA_CAP;
        }

        /** What vanilla's health holds: the pool's share of {@link #MIRROR}, never zero while any is left. */
        public float mirror() {
            if (left <= 0.0) {
                return 0f;
            }
            return Math.max(MIRROR_FLOOR, (float) (MIRROR * left / max));
        }
    }
}
