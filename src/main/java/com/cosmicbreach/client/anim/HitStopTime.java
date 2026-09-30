package com.cosmicbreach.client.anim;

/**
 * Game time in, animation time out, for hit-stop: a hold keeps the animation still for some ticks,
 * then it plays at {@value #CATCH_UP_SPEED} times speed until it has made up every tick it lost, then
 * at normal speed again. So a move's animation still ends on the move's last tick, however many hits
 * held it. The bookkeeping is exact in fractions of a tick, however the time arrives (frames or ticks).
 * Pure: no game or library access, so it is unit-tested.
 */
final class HitStopTime {
    /** How fast the animation plays while it catches up after a hold. */
    static final double CATCH_UP_SPEED = 2.0;
    private static final double EPSILON = 1e-9;

    /** Game ticks still to hold still. */
    private double holdLeft;
    /** Ticks the animation is behind the game. */
    private double lag;

    /** Holds still for {@code ticks} ticks from now. Hits in a row don't add up: the longer hold wins. */
    void hold(int ticks) {
        if (ticks > 0) {
            holdLeft = Math.max(holdLeft, ticks);
        }
    }

    /** Back to normal speed with nothing to make up (a new animation started). */
    void reset() {
        holdLeft = 0;
        lag = 0;
    }

    /** The animation time that passes while {@code real} ticks of game time pass (0 for none or less). */
    double advance(double real) {
        if (!(real > 0)) {
            return 0;
        }
        if (holdLeft > 0) {
            double held = Math.min(real, holdLeft);
            holdLeft -= held;
            lag += held;
            real -= held;
            if (holdLeft < EPSILON) {
                holdLeft = 0;
            }
        }
        double out = 0;
        if (real > 0 && lag > 0) {
            double catching = Math.min(real, lag / (CATCH_UP_SPEED - 1.0));
            lag -= catching * (CATCH_UP_SPEED - 1.0);
            out += catching * CATCH_UP_SPEED;
            real -= catching;
            if (lag < EPSILON) {
                lag = 0;
            }
        }
        return out + real;
    }

    boolean isHolding() {
        return holdLeft > 0;
    }

    /** Ticks the animation is behind the game right now (a hold still to come is not lag yet). */
    double lag() {
        return lag;
    }
}
