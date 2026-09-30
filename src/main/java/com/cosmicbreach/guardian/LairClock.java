package com.cosmicbreach.guardian;

/**
 * A guardian lair's cooldown and the rule for waking its guardian (GDD 3.5, "Guardian respawns"), pure. After a
 * kill the lair rests for {@value #COOLDOWN_TICKS} ticks (20 minutes). Once rested it is armed: the guardian wakes
 * when a player without its attunement enters the arena, or when anyone uses a Guardian Echo on its altar. A fight
 * that resets (everyone left) costs no cooldown.
 */
public final class LairClock {
    /** 20 minutes. */
    public static final long COOLDOWN_TICKS = 24_000L;

    private long restUntil;

    public LairClock() {
        this(Long.MIN_VALUE);
    }

    public LairClock(long restUntil) {
        this.restUntil = restUntil;
    }

    /** True once the cooldown is over at {@code now}. */
    public boolean armed(long now) {
        return now >= restUntil;
    }

    /** The guardian died at {@code now}: the lair rests. */
    public void killed(long now) {
        restUntil = now + COOLDOWN_TICKS;
    }

    /** Ticks of rest left at {@code now}. */
    public long remaining(long now) {
        return restUntil == Long.MIN_VALUE ? 0 : Math.max(0, restUntil - now);
    }

    /** Restores a saved rest ({@link Long#MIN_VALUE}: none). */
    public void restore(long restUntil) {
        this.restUntil = restUntil;
    }

    /** Ends the rest at once (debug). */
    public void clear() {
        restUntil = Long.MIN_VALUE;
    }

    public long restUntil() {
        return restUntil;
    }

    /**
     * Whether a dormant guardian wakes: only when armed, and then for an Echo used on the altar or for an
     * unattuned player inside the arena.
     */
    public static boolean wakes(boolean armed, boolean echoUsed, boolean unattunedInside) {
        return armed && (echoUsed || unattunedInside);
    }
}
