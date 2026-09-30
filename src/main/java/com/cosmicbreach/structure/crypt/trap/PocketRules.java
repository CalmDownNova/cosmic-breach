package com.cosmicbreach.structure.crypt.trap;

/**
 * The Void Pocket under a Void Rift (GDD 6.4): a sealed room. A fall into it wakes three Hollow Stalkers (once that
 * creature exists; until then, nobody), and its way out opens when they are all dead or after {@link #OPEN_AFTER}
 * ticks, whichever comes first. With nobody inside for {@link #RESEAL_TICKS} ticks it seals again, ready for the
 * next fall. It costs health and time, never the run. Pure.
 */
public final class PocketRules {
    public static final int STALKERS = 3;
    public static final int OPEN_AFTER = 45 * 20;
    public static final int RESEAL_TICKS = 200;

    private PocketRules() {
    }

    /** True if a pocket woken at {@code since} opens at {@code now}, with {@code alive} of its Stalkers alive. */
    public static boolean opens(long since, long now, int spawned, int alive) {
        return since >= 0 && (now - since >= OPEN_AFTER || spawned > 0 && alive == 0);
    }
}
