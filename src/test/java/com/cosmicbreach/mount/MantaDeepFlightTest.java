package com.cosmicbreach.mount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.ShearBand;
import org.junit.jupiter.api.Test;

/** The manta flies in the Deep too (1.2 design section 5), under a soft ceiling below the lower Shear band. */
class MantaDeepFlightTest {
    /** Flies straight up for {@code ticks} from height {@code y0} (jump held), returning the height reached. */
    private static double climb(Layer layer, double y0, int ticks, boolean fins) {
        double[] v = {0, 0, 0};
        double y = y0;
        for (int i = 0; i < ticks; i++) {
            Layer here = Layer.at(y);
            v = MantaRules.step(v, MantaRules.target(0, 0, true, 0f, 0f, here, y, false, fins), MantaRules.flies(here));
            y += v[1];
        }
        return y;
    }

    @Test
    void theDeepAndTheDriftFlyTheReachDoesNot() {
        assertTrue(MantaRules.flies(Layer.DEEP));
        assertTrue(MantaRules.flies(Layer.DRIFT));
        assertFalse(MantaRules.flies(Layer.REACH));
    }

    @Test
    void inTheDeepJumpClimbsAndLookingUpClimbs() {
        double[] up = MantaRules.target(0, 0, true, 0f, 0f, Layer.DEEP, 60.0, false, false);
        assertEquals(MantaRules.VERTICAL, up[1], 1e-9);
        double[] look = MantaRules.target(1, 0, false, 0f, -60f, Layer.DEEP, 60.0, false, false);
        assertEquals(MantaRules.VERTICAL, look[1], 1e-9);
        double[] dive = MantaRules.target(1, 0, false, 0f, 60f, Layer.DEEP, 60.0, false, false);
        assertEquals(-MantaRules.VERTICAL, dive[1], 1e-9);
    }

    @Test
    void theClimbStopsAtTheCeilingUnderTheShearBand() {
        assertEquals(ShearBand.B.minY - 1, MantaRules.DEEP_CEILING);
        for (boolean fins : new boolean[]{false, true}) {
            double top = climb(Layer.DEEP, 60.0, 2000, fins);
            assertTrue(top <= MantaRules.DEEP_CEILING + 0.3, "reached " + top);
            assertTrue(top > MantaRules.DEEP_CEILING - 1.0, "it should get close to the ceiling, reached " + top);
            assertTrue(Layer.at(top) == Layer.DEEP && ShearBand.at(top) == null, "never inside the band");
        }
    }

    @Test
    void theClimbEasesOffOverTheLastBlocks() {
        assertEquals(1.0, MantaRules.climbScale(Layer.DEEP, MantaRules.DEEP_CEILING - MantaRules.CEILING_RAMP), 1e-9);
        assertEquals(0.5, MantaRules.climbScale(Layer.DEEP, MantaRules.DEEP_CEILING - MantaRules.CEILING_RAMP / 2), 1e-9);
        assertEquals(0.0, MantaRules.climbScale(Layer.DEEP, MantaRules.DEEP_CEILING), 1e-9);
        assertEquals(0.0, MantaRules.climbScale(Layer.DEEP, 150), 1e-9);
        assertEquals(1.0, MantaRules.climbScale(Layer.DRIFT, 299), 1e-9, "the Drift is unchanged");
    }

    @Test
    void aboveTheCeilingItSettlesBackAndCanStillDive() {
        double[] jump = MantaRules.target(0, 0, true, 0f, 0f, Layer.DEEP, 150.0, false, false);
        assertTrue(jump[1] < 0.0, "carried into the band it sinks back: " + jump[1]);
        double[] dive = MantaRules.target(1, 0, false, 0f, 60f, Layer.DEEP, 150.0, false, false);
        assertEquals(-MantaRules.VERTICAL, dive[1], 1e-9);
        assertTrue(MantaRules.aboveCeiling(Layer.DEEP, 150) && !MantaRules.aboveCeiling(Layer.DEEP, 100));
    }

    @Test
    void levelFlightIsFullSpeedUnderTheCeilingToo() {
        double[] fwd = MantaRules.target(1, 0, false, 0f, 0f, Layer.DEEP, 143.9, false, false);
        assertEquals(0.35, Math.hypot(fwd[0], fwd[2]), 1e-9);
        assertEquals(0.0, fwd[1], 1e-9);
    }

    @Test
    void theReachStillOnlyGlides() {
        double[] glide = MantaRules.target(1, 0, true, 0f, -80f, Layer.REACH, 400.0, false, false);
        assertTrue(glide[1] < 0.0);
        assertTrue(MantaRules.step(new double[]{0, 0.3, 0}, glide, MantaRules.flies(Layer.REACH))[1] <= 0.0, "carried momentum cannot lift it");
    }

    @Test
    void aBlinkNeverEndsAboveTheCeiling() {
        double[] up = MantaRules.blinkDirection(0f, -60f, true);
        double[] cut = MantaRules.clampBlink(up, Layer.DEEP, MantaRules.DEEP_CEILING - 2);
        assertTrue(cut[1] * MantaRules.BLINK_DISTANCE <= 2.0 + 1e-9);
        double[] none = MantaRules.clampBlink(up, Layer.DEEP, MantaRules.DEEP_CEILING + 3);
        assertEquals(0.0, none[1], 1e-9);
        assertEquals(up[1], MantaRules.clampBlink(up, Layer.DEEP, 60.0)[1], 1e-9, "far below the ceiling the blink is whole");
        assertEquals(up[1], MantaRules.clampBlink(up, Layer.DRIFT, 250.0)[1], 1e-9);
    }

    @Test
    void parkingKeepsItsOneOneOneRules() {
        // a manta left below the Drift stays where it was left, and one that is not in the Drift is not a safe spot
        assertFalse(MountCareRules.needsRescue(true, false, true, MountGear.Kind.MANTA, 100.0, true));
        assertTrue(MountCareRules.needsRescue(true, false, true, MountGear.Kind.MANTA, 100.0, false));
        assertTrue(MountCareRules.ridBelow(MountGear.Kind.MANTA, 100.0));
        assertFalse(MountCareRules.safeHere(MountGear.Kind.MANTA, 100.0, false, false));
    }
}
