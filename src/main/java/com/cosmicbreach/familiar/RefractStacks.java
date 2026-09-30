package com.cosmicbreach.familiar;

/**
 * The Refract status's numbers (GDD 8.2), pure: the Prism Moth puts a stack on its owner's target every 2 s, up to
 * {@value #MAX}; each new stack starts the {@value #TICKS} ticks again for them all. At {@value #MAX} stacks the next
 * ability hit on that target consumes them for +30% damage. Fewer stacks do nothing and stay.
 */
public final class RefractStacks {
    public static final int MAX = 3;
    public static final int TICKS = 160;
    public static final double BONUS = 0.30;

    private RefractStacks() {
    }

    /** The stacks after one more lands on {@code stacks}. */
    public static int after(int stacks) {
        return Math.min(MAX, Math.max(0, stacks) + 1);
    }

    /** True if an ability hit on a target with {@code stacks} consumes them. */
    public static boolean consumes(int stacks) {
        return stacks >= MAX;
    }

    /** The damage multiplier an ability hit on a target with {@code stacks} gets: x1.3 at three, x1 below. */
    public static double multiplier(int stacks) {
        return consumes(stacks) ? 1.0 + BONUS : 1.0;
    }
}
