package com.cosmicbreach.client.anim;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hit-stop's time accounting: holds, the catch-up and the return to normal speed, in any slicing of time. */
class HitStopTimeTest {
    private static final double EPS = 1e-9;

    @Test
    void withoutAHoldAnimationTimeIsGameTime() {
        HitStopTime time = new HitStopTime();
        assertEquals(0.37, time.advance(0.37), EPS);
        assertEquals(1.0, time.advance(1.0), EPS);
        assertEquals(0.0, time.advance(0.0), EPS);
        assertEquals(0.0, time.advance(-0.5), EPS);
    }

    @Test
    void aTwoTickHoldStandsStillThenCatchesUpOverTwoTicks() {
        HitStopTime time = new HitStopTime();
        time.hold(2);
        double animation = 0;
        double[] expected = {0, 0, 2, 4, 5, 6};
        for (int tick = 0; tick < expected.length; tick++) {
            animation += time.advance(1.0);
            assertEquals(expected[tick], animation, EPS, "after game tick " + (tick + 1));
        }
        assertFalse(time.isHolding());
        assertEquals(0.0, time.lag(), EPS);
    }

    @Test
    void theSameTotalsWhateverSlicesTimeArrivesIn() {
        Random random = new Random(7);
        for (int trial = 0; trial < 50; trial++) {
            HitStopTime whole = new HitStopTime();
            HitStopTime sliced = new HitStopTime();
            int hold = 1 + random.nextInt(5);
            whole.hold(hold);
            sliced.hold(hold);
            double a = 0;
            double b = 0;
            for (int tick = 0; tick < 20; tick++) {
                a += whole.advance(1.0);
                double left = 1.0;
                while (left > 1e-12) {
                    double slice = Math.min(left, random.nextDouble() * 0.4);
                    b += sliced.advance(slice);
                    left -= slice;
                }
                assertEquals(a, b, 1e-6, "trial " + trial + " tick " + tick);
            }
            assertEquals(20.0, a, 1e-6, "back in step with the game after " + hold + " ticks of hold");
        }
    }

    @Test
    void holdsInARowTakeTheLongerOneNotTheSum() {
        HitStopTime time = new HitStopTime();
        time.hold(2);
        double animation = time.advance(1.0); // 1 tick held, 1 still to hold
        time.hold(3); // a second hit: now 3 ticks from here
        assertTrue(time.isHolding());
        for (int tick = 0; tick < 3; tick++) {
            animation += time.advance(1.0);
        }
        assertEquals(0.0, animation, EPS);
        assertEquals(4.0, time.lag(), EPS);
        for (int tick = 0; tick < 4; tick++) {
            animation += time.advance(1.0);
        }
        assertEquals(8.0, animation, EPS, "4 ticks of lag made up at double speed over 4 ticks");
        assertEquals(0.0, time.lag(), EPS);
    }

    @Test
    void resetDropsHoldAndLag() {
        HitStopTime time = new HitStopTime();
        time.hold(4);
        time.advance(2.5);
        time.reset();
        assertFalse(time.isHolding());
        assertEquals(0.0, time.lag(), EPS);
        assertEquals(1.0, time.advance(1.0), EPS);
    }

    @Test
    void zeroOrNegativeHoldsDoNothing() {
        HitStopTime time = new HitStopTime();
        time.hold(0);
        time.hold(-3);
        assertFalse(time.isHolding());
        assertEquals(1.0, time.advance(1.0), EPS);
    }
}
