package com.cosmicbreach.client.fx;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailMathTest {
    private static final double EPS = 1e-9;

    @Test
    void theTrailRunsFromTheEndOfTheWindUpThroughTheFollowThrough() {
        assertArrayEquals(new double[] {2, 6}, TrailMath.window(3, 2), EPS, "L1: startup 3, active 2");
        assertArrayEquals(new double[] {4, 8}, TrailMath.window(5, 2), EPS, "L3");
        assertArrayEquals(new double[] {0, 2}, TrailMath.window(0, 0), EPS, "no startup, no active ticks: still a tick");
    }

    @Test
    void itIsBrightestAtTheBladeAndGoneAtItsLength() {
        assertEquals(1.0, TrailMath.ageStrength(0, 2.4), EPS);
        assertEquals(0.0, TrailMath.ageStrength(2.4, 2.4), EPS);
        assertEquals(0.0, TrailMath.ageStrength(5, 2.4), EPS);
        double previous = 2.0;
        for (double age = 0; age < 2.4; age += 0.1) {
            double s = TrailMath.ageStrength(age, 2.4);
            assertTrue(s < previous, "fades steadily behind the blade");
            previous = s;
        }
    }

    @Test
    void aSwingThatJustBeganStillFadesToNothingAtItsStart() {
        assertEquals(2.4, TrailMath.fadeLength(2.4, 9.0, 4.0), EPS, "long under way: the full length");
        assertEquals(1.0, TrailMath.fadeLength(2.4, 5.0, 4.0), EPS, "a tick in: only that tick to fade over");
        assertTrue(TrailMath.fadeLength(2.4, 4.0, 4.0) > 0, "never zero");
        double length = TrailMath.fadeLength(2.4, 5.0, 4.0);
        assertEquals(0.0, TrailMath.ageStrength(5.0 - 4.0, length), EPS, "the swing's first pose shows nothing");
    }

    @Test
    void theCurveKeepsItsEndsAndStaysRoundThroughSparseSamples() {
        List<Vec3> arc = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            double a = Math.toRadians(-60 + 30 * i);
            arc.add(new Vec3(Math.sin(a), 0, Math.cos(a)));
        }
        TrailMath.Curve curve = TrailMath.smooth(arc, 0.05);
        List<Vec3> points = curve.points();
        assertEquals(arc.get(0), points.get(0));
        assertEquals(arc.get(4), points.get(points.size() - 1));
        assertTrue(points.size() > 20, "about one point per 5 cm");
        for (Vec3 p : points) {
            assertEquals(1.0, p.length(), 0.02, "stays on the circle between the samples: " + p);
        }
        double previous = -1;
        for (double at : curve.at()) {
            assertTrue(at > previous, "the parameter only goes forward");
            previous = at;
        }
        assertEquals(4.0, curve.at().get(curve.at().size() - 1), EPS);
    }

    @Test
    void aStraightStrokeIsBowedIntoACrescentMeetingTheBlade() {
        List<TrailMath.Point2> chop = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            chop.add(new TrailMath.Point2(0.05, 0.9 - 0.13 * i)); // straight down the middle, tail first
        }
        double[] bow = TrailMath.bow(chop);
        assertEquals(0.0, bow[0], EPS, "the tail stays");
        assertEquals(0.0, bow[10], EPS, "the stroke still meets the blade");
        double chord = 1.3;
        assertEquals(TrailMath.MIN_BOW * chord, bow[5], 1e-6, "the middle bows by MIN_BOW of the chord");
        // Downward, the chord's left normal is +x: a chop bows to the right of the screen, like ")".
        assertTrue(bow[5] > 0);
    }

    @Test
    void aStrokeAlreadyCurvedEnoughIsLeftAlone() {
        List<TrailMath.Point2> sweep = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            double a = Math.toRadians(-70 + 140 * i / 12.0);
            sweep.add(new TrailMath.Point2(Math.sin(a), Math.cos(a) - 0.5));
        }
        double[] bow = TrailMath.bow(sweep);
        for (double b : bow) {
            assertEquals(0.0, b, EPS);
        }
    }

    @Test
    void aSlightlyCurvedStrokeIsBowedTheWayItAlreadyLeans() {
        List<TrailMath.Point2> stroke = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            stroke.add(new TrailMath.Point2(-0.05 * Math.sin(Math.PI * t), -1 + 2 * t)); // up, leaning left a little
        }
        double[] bow = TrailMath.bow(stroke);
        // Upward, the chord's left normal is -x: bowing further left is a positive push.
        assertTrue(bow[5] > 0, "leans left, bowed further left");
        assertTrue(bow[5] < TrailMath.MIN_BOW * 2, "only up to the minimum bow");
    }

    @Test
    void theTrailEndsInsideTheViewUnlessTheBladeLeftIt() {
        assertEquals(1.0, TrailMath.edgeFade(0.3, 0.2), EPS);
        assertEquals(1.0, TrailMath.edgeFade(TrailMath.EDGE_FADE_FROM, 0.2), EPS);
        assertEquals(0.0, TrailMath.edgeFade(TrailMath.EDGE_FADE_FROM + TrailMath.EDGE_FADE_OVER, 0.2), EPS);
        assertEquals(0.0, TrailMath.edgeFade(1.2, 0.2), EPS, "past the edge: nothing");
        double mid = TrailMath.edgeFade(TrailMath.EDGE_FADE_FROM + TrailMath.EDGE_FADE_OVER / 2, 0.2);
        assertEquals(0.5, mid, 1e-9);
        // A blade near the edge keeps itself and a little beyond before the fade.
        assertEquals(1.0, TrailMath.edgeFade(0.85, 0.83), EPS);
        assertTrue(TrailMath.edgeFade(0.9, 0.83) < 1.0);
        // A blade that left the view: the trail follows it out.
        assertEquals(1.0, TrailMath.edgeFade(1.5, 1.8), EPS);
    }
}
