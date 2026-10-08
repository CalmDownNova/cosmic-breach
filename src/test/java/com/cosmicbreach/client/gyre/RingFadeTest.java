package com.cosmicbreach.client.gyre;

import com.cosmicbreach.client.guardian.TelegraphDraw;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The flyers' blade rings: a pixel minimum only up close, then they scale like geometry and fade out (1.1 design 8). */
class RingFadeTest {
    @Test
    void upCloseTheStrokeKeepsItsPixelWidth() {
        assertEquals(Math.max(0.045, 4.0 * 1.4 / 720.0 * 6.0), RingFade.width(4.0, 6.0, 0.045), 1e-12);
        assertEquals(0.045, RingFade.width(1.0, 6.0, 0.045), 1e-12, "the floor right at the eye");
    }

    /** Within 16 blocks the rings keep the stroke every other warning line has: the one rule, not a copy of its formula. */
    @Test
    void withinSixteenBlocksTheStrokeIsExactlyTheWarningLinesScreenSpaceRule() {
        for (double distance : new double[] {0.5, 1.0, 4.0, 10.0, 16.0}) {
            for (double pixels : new double[] {6.0, 10.0}) {
                assertEquals(TelegraphDraw.screenWidth(new Vec3(distance, 0.0, 0.0), pixels, 0.045), RingFade.width(distance, pixels, 0.045), 1e-12,
                        distance + " blocks at " + pixels + " px");
            }
        }
    }

    @Test
    void pastSixteenBlocksTheStrokeStopsGrowingSoTheRingsKeepTheirHoles() {
        double at16 = RingFade.width(16.0, 6.0, 0.045);
        assertEquals(at16, RingFade.width(60.0, 6.0, 0.045), 1e-12);
        assertEquals(at16, RingFade.width(95.0, 6.0, 0.045), 1e-12);
        assertTrue(at16 * 6.0 < 1.5, "core stroke under a sixth of the rest radius 1.5: " + at16);
    }

    @Test
    void theRingsFadeBetweenFortyAndFiftySixBlocks() {
        assertEquals(1.0f, RingFade.alpha(10.0), 1e-6f);
        assertEquals(1.0f, RingFade.alpha(40.0), 1e-6f);
        assertEquals(0.5f, RingFade.alpha(48.0), 1e-6f);
        assertEquals(0.0f, RingFade.alpha(56.0), 1e-6f);
        assertEquals(0.0f, RingFade.alpha(90.0), 1e-6f);
        assertTrue(RingFade.alpha(44.0) > RingFade.alpha(52.0));
    }
}
