package com.cosmicbreach.gear.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The orbit rings never dip into the anvil's base (the max-tier screenshot of 2026-10-08). */
class ForgeRingGeometryTest {
    private static final double FLOOR = ForgeRingGeometry.BASE_TOP + ForgeRingGeometry.CLEARANCE;

    @Test
    void everyTierKeepsEveryRingAboveTheBase() {
        for (int tier = 1; tier <= ForgeRingGeometry.TIERS; tier++) {
            for (int i = 0; i < ForgeRingGeometry.ringsFor(tier); i++) {
                ForgeRingGeometry.Ring ring = ForgeRingGeometry.ring(i);
                double lowest = Double.MAX_VALUE;
                for (int s = 0; s < 3600; s++) {
                    // every angle, and every stage of growing out (scale 0 to 1)
                    for (double scale : new double[]{0.25, 0.5, 1.0}) {
                        lowest = Math.min(lowest, ring.point(Math.PI * 2 * s / 3600.0, scale)[1]);
                    }
                }
                assertTrue(lowest >= FLOOR - 1e-9, "tier " + tier + " ring " + (i + 1) + " dips to " + lowest + " (needs " + FLOOR + ")");
            }
        }
    }

    @Test
    void sampledLowestPointMatchesTheClosedForm() {
        for (int i = 0; i < ForgeRingGeometry.TIERS; i++) {
            ForgeRingGeometry.Ring ring = ForgeRingGeometry.ring(i);
            double lowest = Double.MAX_VALUE;
            for (int s = 0; s < 7200; s++) {
                lowest = Math.min(lowest, ring.point(Math.PI * 2 * s / 7200.0, 1.0)[1]);
            }
            assertEquals(ring.lowestY(), lowest, 1e-3);
        }
    }

    @Test
    void liftedRingsStayUnderTheCentreCeilingAndKeepSomeTilt() {
        for (int i = 0; i < ForgeRingGeometry.TIERS; i++) {
            ForgeRingGeometry.Ring ring = ForgeRingGeometry.ring(i);
            assertTrue(ring.centreY() <= ForgeRingGeometry.MAX_CENTRE_Y + 1e-9, "ring " + (i + 1) + " centre " + ring.centreY());
            assertTrue(Math.abs(ring.tiltX()) > 5, "ring " + (i + 1) + " keeps a visible tilt");
        }
    }

    @Test
    void ringCountFollowsTheTierAndIsClamped() {
        assertEquals(0, ForgeRingGeometry.ringsFor(0));
        assertEquals(4, ForgeRingGeometry.ringsFor(9));
    }
}
