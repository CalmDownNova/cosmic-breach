package com.cosmicbreach.client.fx;

/**
 * The particle budget's rules (GDD section 11), pure so they are tested: at most {@value #LIMIT} of
 * our particles alive; full rate within {@value #FULL_RATE_DISTANCE} blocks of the camera, half out
 * to {@value #HALF_RATE_DISTANCE}, none beyond except for the effects that must always read (a parry,
 * a big impact), which keep half; and vanilla's particle setting scales everything: All 100%,
 * Decreased 40%, Minimal 10%.
 */
public final class FxRates {
    public static final int LIMIT = 1500;
    public static final double FULL_RATE_DISTANCE = 16.0;
    public static final double HALF_RATE_DISTANCE = 48.0;
    public static final double ALL = 1.0;
    public static final double DECREASED = 0.4;
    public static final double MINIMAL = 0.1;

    private FxRates() {
    }

    /** The share of an effect's particles to spawn this far from the camera. */
    public static double distanceFactor(double distance, boolean important) {
        if (distance <= FULL_RATE_DISTANCE) {
            return 1.0;
        }
        if (distance <= HALF_RATE_DISTANCE || important) {
            return 0.5;
        }
        return 0.0;
    }

    /**
     * How many of {@code wanted} particles to spawn at {@code rate}, with {@code room} left under the
     * limit. The fraction rounds up with probability equal to itself ({@code roll} in [0, 1)), so small
     * effects still show up now and then at low rates; {@code atLeastOne} keeps one of an effect that
     * must read whenever its rate isn't zero.
     */
    public static int count(int wanted, double rate, int room, double roll, boolean atLeastOne) {
        if (wanted <= 0 || rate <= 0.0 || room <= 0) {
            return 0;
        }
        double exact = wanted * rate;
        int whole = (int) Math.floor(exact);
        if (roll < exact - whole) {
            whole++;
        }
        if (atLeastOne && whole == 0) {
            whole = 1;
        }
        return Math.min(whole, room);
    }
}
