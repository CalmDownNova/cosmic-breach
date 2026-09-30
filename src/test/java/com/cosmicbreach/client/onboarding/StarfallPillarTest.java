package com.cosmicbreach.client.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The fallen shard's pillar keeps its on-screen width with distance (F1 polish: a thin line at 60 blocks). */
class StarfallPillarTest {
    @Test
    void upCloseThePillarKeepsItsOwnWidth() {
        assertEquals(1.0f, StarfallShardRenderer.widthScale(0.0), 1e-6);
        assertEquals(1.0f, StarfallShardRenderer.widthScale(StarfallShardRenderer.NEAR), 1e-6);
    }

    @Test
    void fartherAwayItWidensInProportion() {
        assertEquals(60.0 / StarfallShardRenderer.NEAR, StarfallShardRenderer.widthScale(60.0), 1e-5);
        assertEquals(2.0f, StarfallShardRenderer.widthScale(2 * StarfallShardRenderer.NEAR), 1e-6);
    }

    @Test
    void itStopsWideningAtTheCap() {
        assertEquals(StarfallShardRenderer.MAX_WIDTH_SCALE, StarfallShardRenderer.widthScale(StarfallShardRenderer.VIEW_DISTANCE), 1e-6);
        assertEquals(StarfallShardRenderer.MAX_WIDTH_SCALE, StarfallShardRenderer.widthScale(1e6), 1e-6);
    }
}
