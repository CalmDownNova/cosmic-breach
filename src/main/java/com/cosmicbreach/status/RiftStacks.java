package com.cosmicbreach.status;

/**
 * The Rift status's numbers (GDD 8.2), pure: each stack takes {@value #ARMOR_PER_STACK} of the target's armor for
 * {@value #TICKS} ticks, up to {@value #MAX} stacks. A new stack adds one (up to the cap) and starts the
 * {@value #TICKS} ticks again for them all. Hollow Stalkers apply it (the Rend), and the Heliarch's Tendril Lash.
 */
public final class RiftStacks {
    public static final int MAX = 3;
    public static final int TICKS = 100;
    public static final double ARMOR_PER_STACK = 0.15;

    private RiftStacks() {
    }

    /** The stacks after one more lands on {@code stacks}. */
    public static int after(int stacks) {
        return Math.min(MAX, Math.max(0, stacks) + 1);
    }

    /** The share of armor left at {@code stacks} stacks. */
    public static double armorScale(int stacks) {
        return 1.0 - ARMOR_PER_STACK * Math.max(0, Math.min(MAX, stacks));
    }
}
