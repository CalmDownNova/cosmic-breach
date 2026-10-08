package com.cosmicbreach.client.lift;

/**
 * How the air vents look (1.1 design sections 5 and 6), as plain numbers and rules so they can be checked without a screen:
 * {@link StreamRenderer} draws by them. A vent has to be found by sight from the far side of the arena, in daylight and at dusk,
 * so each of its layers keeps a width in pixels from any distance (never under the column's own width in blocks), and it
 * climbs past its top as a plume that fades out, which shows over a platform's edge from across the arena.
 */
final class StreamLook {
    /** The dark rim, the cyan body, the additive halo and the white-hot core: pixels on a 720 line screen at 70 degrees, kept from any distance. */
    static final double OUTLINE_PX = 54.0;
    static final double BODY_PX = 36.0;
    static final double GLOW_PX = 26.0;
    static final double CORE_PX = 8.0;
    /** The climbing streaks, and the beacon at the stream's top. */
    static final double STREAK_PX = 18.0;
    static final double BEACON_PX = 56.0;

    /** Each layer's alpha at the middle of its band (the sides fade to nothing). */
    static final float OUTLINE_ALPHA = 0.34f;
    static final float BODY_ALPHA = 0.5f;
    static final float GLOW_ALPHA = 0.55f;
    static final float CORE_ALPHA = 0.75f;
    static final float STREAK_ALPHA = 0.85f;

    /**
     * The trail behind a player the rescue lift carries (seen by everyone in the arena): it follows their last
     * {@value #TRAIL_TICKS} ticks, {@value #TRAIL_PX} pixels wide at the rider (never under {@value #TRAIL_MIN} blocks) and
     * narrowing to a quarter of that at its tail.
     */
    static final int TRAIL_TICKS = 20;
    static final double TRAIL_PX = 24.0;
    static final double TRAIL_MIN = 0.9;
    static final float TRAIL_RIM_ALPHA = 0.22f;
    static final float TRAIL_BODY_ALPHA = 0.55f;
    static final float TRAIL_GLOW_ALPHA = 0.6f;
    static final float TRAIL_CORE_ALPHA = 0.85f;

    /** A trail eases in over this many blocks back from the rider, starting at their feet, so it is never cut flat across their body. */
    static final double HEAD_BLOCKS = 1.6;

    /** A stream far from the eye fades out over the last this many blocks of its range, so it never pops in at full strength at the edge. */
    static final double RANGE_FADE = 30.0;

    private StreamLook() {
    }

    /**
     * World width that shows about {@code pixels} tall at {@code distance} blocks on a 720 line screen at a 70 degree field of
     * view (the rule of {@code TelegraphDraw.screenWidth}); never under {@code min}, the layer's own width in blocks.
     */
    static double width(double distance, double pixels, double min) {
        return Math.max(min, distance * 1.4 / 720.0 * pixels);
    }

    /** How much of a stream shows at height {@code y}: all of it up to its top, then a fade to nothing over its plume (none: cut off). */
    static float fadeAbove(double y, double top, double rise) {
        if (y <= top) {
            return 1.0f;
        }
        if (rise <= 0.0) {
            return 0.0f;
        }
        return (float) Math.max(0.0, 1.0 - (y - top) / rise);
    }

    /** How much of a stream shows {@code distance} blocks away when it is drawn within {@code range}: all of it, then a fade to nothing over the last {@value #RANGE_FADE} blocks. */
    static float rangeFade(double distance, double range) {
        return (float) Math.max(0.0, Math.min(1.0, (range - distance) / RANGE_FADE));
    }

    /** How much of a trail shows {@code distance} blocks back along it from the rider: nothing at the rider, all of it from {@value #HEAD_BLOCKS} blocks (a smooth ramp). */
    static float headRamp(double distance) {
        double t = Math.max(0.0, Math.min(1.0, distance / HEAD_BLOCKS));
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** A trail's width that far back from the rider, as a share of its width further down: a point at the rider, widening to the full width where the head ends. */
    static double headWidth(double distance) {
        return 0.2 + 0.8 * headRamp(distance);
    }

    /** How strongly a point of a rider's trail {@code age} ticks old shows: all of it at the rider, nothing at the trail's end. */
    static float trailFade(double age) {
        return (float) Math.max(0.0, 1.0 - age / TRAIL_TICKS);
    }

    /** A trail's width {@code age} ticks back, as a share of its width at the rider: a quarter at its tail. */
    static double trailTaper(double age) {
        return 1.0 - 0.75 * Math.min(1.0, age / TRAIL_TICKS);
    }

    /** The body of a stream whose glow is {@code glow}: the rising currents' warm gold (a deeper gold under it), else {@code vent}, the cyan of the air vents. */
    static int bodyColor(int glow, int vent) {
        return glow == LiftClient.CURRENT ? CURRENT_BODY : vent;
    }

    /** The deeper gold under a current's pale glow. */
    static final int CURRENT_BODY = 0xE39A1E;

    /** A slow shimmer of brightness, about 1 plus or minus 0.12, so a still stream breathes. */
    static float shimmer(double time, double phase) {
        return (float) (1.0 + 0.12 * Math.sin(time * 0.21 + phase));
    }
}
