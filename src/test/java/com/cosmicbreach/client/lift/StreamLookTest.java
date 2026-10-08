package com.cosmicbreach.client.lift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The air vents' look (1.1 design sections 5 and 6): pure rules that {@link StreamRenderer} draws by. A vent has to be found by
 * sight from across the arena, so its screen widths are fixed in pixels at distance, it keeps climbing past its top as a fading
 * plume (seen over the platform's edge from the far side), and a stream within a block of the eye is never drawn in full.
 */
class StreamLookTest {
    @Test
    void aStreamShowsInFullUpToItsTopAndFadesToNothingOverItsPlume() {
        assertEquals(1.0f, StreamLook.fadeAbove(10.0, 20.0, 8.0), 1e-6);
        assertEquals(1.0f, StreamLook.fadeAbove(20.0, 20.0, 8.0), 1e-6, "at the top");
        assertEquals(0.5f, StreamLook.fadeAbove(24.0, 20.0, 8.0), 1e-6, "halfway up the plume");
        assertEquals(0.0f, StreamLook.fadeAbove(28.0, 20.0, 8.0), 1e-6, "at its end");
        assertEquals(0.0f, StreamLook.fadeAbove(40.0, 20.0, 8.0), 1e-6, "past it");
    }

    @Test
    void aStreamWithNoPlumeIsCutOffAtItsTop() {
        assertEquals(1.0f, StreamLook.fadeAbove(20.0, 20.0, 0.0), 1e-6);
        assertEquals(0.0f, StreamLook.fadeAbove(20.5, 20.0, 0.0), 1e-6);
    }

    @Test
    void aStreamFadesOutOverTheLastStretchOfItsRangeInsteadOfCuttingOff() {
        // a column keeps its width in pixels at any distance, so at the edge of its range it would pop in at full strength
        assertEquals(1.0f, StreamLook.rangeFade(10.0, 192.0), 1e-6, "close");
        assertEquals(1.0f, StreamLook.rangeFade(192.0 - StreamLook.RANGE_FADE, 192.0), 1e-6, "where the fade begins");
        assertEquals(0.5f, StreamLook.rangeFade(192.0 - StreamLook.RANGE_FADE / 2.0, 192.0), 1e-6, "halfway through it");
        assertEquals(0.0f, StreamLook.rangeFade(192.0, 192.0), 1e-6, "at the end of its range");
        assertEquals(0.0f, StreamLook.rangeFade(250.0, 192.0), 1e-6, "past it");
        double last = 2.0;
        for (double d = 150.0; d <= 200.0; d += 2.0) {
            assertTrue(StreamLook.rangeFade(d, 192.0) <= last + 1e-12, "it only ever dims with distance");
            last = StreamLook.rangeFade(d, 192.0);
        }
    }

    @Test
    void theColumnKeepsItsPixelsFromFarAndItsBlocksUpClose() {
        // 20 pixels of a 720 line screen at 70 degrees is 0.039 of the distance in blocks; never under the column's own width
        double far = StreamLook.width(70.0, 20.0, 1.2);
        double near = StreamLook.width(10.0, 20.0, 1.2);
        assertEquals(70.0 * 1.4 / 720.0 * 20.0, far, 1e-9, "far off the pixels rule");
        assertEquals(1.2, near, 1e-9, "close in the world width rule");
        assertTrue(far > near * 2.0, "so it reads from across the arena");
    }

    @Test
    void theLayersNestFromAnOutlineThroughABodyToABrightCore() {
        assertTrue(StreamLook.OUTLINE_PX > StreamLook.BODY_PX && StreamLook.BODY_PX > StreamLook.CORE_PX, "widest outside");
        assertTrue(StreamLook.BODY_PX >= 18.0, "a body at least 18 pixels across from far off, not a thin line");
        assertTrue(StreamLook.OUTLINE_ALPHA < StreamLook.BODY_ALPHA, "the dark rim is the faintest layer");
    }

    @Test
    void aRidersTrailIsFullAtItsHeadAndGoneAfterItsLastTick() {
        assertEquals(1.0f, StreamLook.trailFade(0.0), 1e-6, "at the rider");
        assertEquals(0.5f, StreamLook.trailFade(StreamLook.TRAIL_TICKS / 2.0), 1e-6, "halfway back");
        assertEquals(0.0f, StreamLook.trailFade(StreamLook.TRAIL_TICKS), 1e-6, "at its end");
        assertEquals(0.0f, StreamLook.trailFade(StreamLook.TRAIL_TICKS + 15.0), 1e-6, "past it");
        double last = Double.MAX_VALUE;
        for (double age = 0.0; age <= StreamLook.TRAIL_TICKS; age += 1.0) {
            assertTrue(StreamLook.trailTaper(age) <= last + 1e-12, "it only narrows toward the tail");
            last = StreamLook.trailTaper(age);
        }
        assertEquals(1.0, StreamLook.trailTaper(0.0), 1e-9, "full width at the rider");
        assertEquals(0.25, StreamLook.trailTaper(StreamLook.TRAIL_TICKS), 1e-9, "a quarter at the tail");
    }

    @Test
    void aTrailEasesInUnderTheRidersFeetInsteadOfBeingCutFlat() {
        assertEquals(0.0f, StreamLook.headRamp(0.0), 1e-6, "nothing at the rider");
        assertEquals(0.5f, StreamLook.headRamp(StreamLook.HEAD_BLOCKS / 2.0), 1e-6, "half way along the head");
        assertEquals(1.0f, StreamLook.headRamp(StreamLook.HEAD_BLOCKS), 1e-6, "all of it from the head's length back");
        assertEquals(1.0f, StreamLook.headRamp(12.0), 1e-6, "and on down the trail");
        float last = -1.0f;
        for (double d = 0.0; d <= StreamLook.HEAD_BLOCKS; d += 0.1) {
            assertTrue(StreamLook.headRamp(d) >= last - 1e-6, "it only ever strengthens away from the rider");
            last = StreamLook.headRamp(d);
        }
        assertTrue(StreamLook.headRamp(0.1) < 0.05f, "and eases in gently, not in a step");
        assertTrue(StreamLook.headWidth(0.0) > 0.0 && StreamLook.headWidth(0.0) < 0.5, "it narrows to a point at the rider");
        assertEquals(1.0, StreamLook.headWidth(StreamLook.HEAD_BLOCKS), 1e-9, "full width where the head ends");
    }

    @Test
    void aTrailLastsLongEnoughToShowTenBlocksOfARiseAndStaysSeenFromAcrossTheArena() {
        // a rider climbs 0.7 a tick: the trail is at least ten blocks long behind them
        assertTrue(StreamLook.TRAIL_TICKS * 0.7 >= 10.0, "ten blocks of rise");
        assertTrue(StreamLook.TRAIL_PX >= 18.0, "wide enough on the screen from far off");
    }

    @Test
    void theCurrentsAreGoldAndTheVentsStayCyan() {
        assertEquals(StreamLook.CURRENT_BODY, StreamLook.bodyColor(LiftClient.CURRENT, 0x38D6F0));
        assertEquals(0x38D6F0, StreamLook.bodyColor(LiftClient.VENT, 0x38D6F0));
        int red = StreamLook.CURRENT_BODY >> 16 & 0xFF;
        int blue = StreamLook.CURRENT_BODY & 0xFF;
        assertTrue(red > blue + 100, "warm, not cyan");
    }

    @Test
    void theShimmerStaysWithinAFewPercentOfSteady() {
        for (double t = 0.0; t < 200.0; t += 0.7) {
            float s = StreamLook.shimmer(t, 1.3);
            assertTrue(s >= 0.85f && s <= 1.15f, "shimmer " + s + " at " + t);
        }
    }
}
