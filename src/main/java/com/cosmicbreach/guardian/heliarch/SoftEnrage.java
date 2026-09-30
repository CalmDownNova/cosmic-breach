package com.cosmicbreach.guardian.heliarch;

/**
 * The soft enrage (GDD 7.3): past 8 minutes of total fight time the Heliarch's damage rises 10% every 30 s, with no
 * cap; before that it is x1. Pure.
 */
public final class SoftEnrage {
    private SoftEnrage() {
    }

    /** Steps of +10% at {@code fightTicks} ticks into the fight: 0 before 8 minutes, 1 from 8:00, 2 from 8:30. */
    public static int steps(long fightTicks) {
        if (fightTicks < HeliarchMoves.ENRAGE_AFTER) {
            return 0;
        }
        return (int) ((fightTicks - HeliarchMoves.ENRAGE_AFTER) / HeliarchMoves.ENRAGE_STEP) + 1;
    }

    /** The damage multiplier at {@code fightTicks}. */
    public static double multiplier(long fightTicks) {
        return 1.0 + HeliarchMoves.ENRAGE_PER_STEP * steps(fightTicks);
    }
}
