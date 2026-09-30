package com.cosmicbreach.familiar;

/**
 * What a Familiar Lantern shows, pure: {@link #LIT} with its familiar resting inside, {@link #OUT} while the familiar
 * is summoned (the lantern empty), {@link #DARK} for {@value FamiliarRules#DARK_TICKS} ticks after the familiar died
 * (it can't be summoned until it relights).
 */
public enum LanternState {
    LIT,
    OUT,
    DARK;

    /** The state at game time {@code now} for a lantern dark until {@code darkUntil}, its familiar {@code out} or not. */
    public static LanternState of(long darkUntil, boolean out, long now) {
        if (now < darkUntil) {
            return DARK;
        }
        return out ? OUT : LIT;
    }

    /** Whole seconds until a lantern dark until {@code darkUntil} relights (0 once lit), rounded up. */
    public static int secondsLeft(long darkUntil, long now) {
        long ticks = darkUntil - now;
        return ticks <= 0 ? 0 : (int) ((ticks + 19) / 20);
    }

    /** When a lantern whose familiar died at {@code now} relights. */
    public static long darkUntil(long now) {
        return now + FamiliarRules.DARK_TICKS;
    }
}
