package com.cosmicbreach.guardian.leviathan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Leviathan's movement: the orbit, the recorded path its body follows, and the paths it swims off the orbit. */
class LeviathanPathTest {
    private static final Vec3 C = new Vec3(100.5, 229.5, -40.5);

    private static double radius(Vec3 p) {
        return Math.hypot(p.x - C.x, p.z - C.z);
    }

    @Test
    void theOrbitCirclesTheCoreRisingAndFallingFourBlocks() {
        double wave = 0.7;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int k = 0; k < 360; k++) {
            Vec3 p = LeviathanOrbit.point(C, Math.toRadians(k), wave);
            assertEquals(LeviathanMoves.ORBIT_RADIUS, radius(p), 1e-9);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        assertEquals(C.y - LeviathanMoves.WAVE, minY, 0.01);
        assertEquals(C.y + LeviathanMoves.WAVE, maxY, 0.01);
        double lap = 2 * Math.PI * LeviathanMoves.ORBIT_RADIUS / LeviathanMoves.SPEED;
        assertEquals(395.0, lap, 1.0, "a lap takes about 20 s at 0.35 blocks a tick");
        assertEquals(2 * Math.PI, LeviathanOrbit.advance(0, 2 * Math.PI * LeviathanMoves.ORBIT_RADIUS), 1e-9);
        assertEquals(Math.toRadians(90), LeviathanOrbit.ahead(Math.toRadians(350), Math.toRadians(80)), 1e-9);
    }

    @Test
    void theTrailKeepsEachSegmentAFixedDistanceOfPathBehindTheHead() {
        PathTrail trail = new PathTrail(480, 0.25);
        Vec3 head = null;
        for (int i = 0; i <= 200; i++) {
            head = new Vec3(i * 0.35, 5, 0);
            trail.record(head);
        }
        for (double d : LeviathanMoves.FOLLOW) {
            Vec3 p = trail.behind(head, d);
            assertEquals(head.x - d, p.x, 1e-6);
            assertEquals(5.0, p.y, 1e-9);
        }
        // stalling adds nothing and moves nothing
        Vec3 before = trail.behind(head, 13.0);
        for (int i = 0; i < 50; i++) {
            trail.record(head);
        }
        assertEquals(before.x, trail.behind(head, 13.0).x, 1e-9);
        // past the oldest point it carries on straight
        PathTrail shortTrail = new PathTrail(8, 0.25);
        shortTrail.record(new Vec3(0, 0, 0));
        shortTrail.record(new Vec3(1, 0, 0));
        assertEquals(-4.0, shortTrail.behind(new Vec3(1, 0, 0), 5.0).x, 1e-9);
    }

    @Test
    void theBodyFollowsTheOrbitsCurveExactly() {
        PathTrail trail = new PathTrail(480, 0.25);
        double a = 0;
        Vec3 head = LeviathanOrbit.point(C, a, 0.3);
        for (int tick = 0; tick < 400; tick++) {
            a = LeviathanOrbit.advance(a, LeviathanMoves.SPEED);
            head = LeviathanOrbit.point(C, a, 0.3);
            trail.record(head);
        }
        for (double d : LeviathanMoves.FOLLOW) {
            Vec3 p = trail.behind(head, d);
            assertEquals(LeviathanMoves.ORBIT_RADIUS, radius(p), 0.15, "on the orbit " + d + " behind");
            double behind = LeviathanOrbit.ahead(LeviathanOrbit.angleOf(C, p), a) * LeviathanMoves.ORBIT_RADIUS;
            assertTrue(behind <= d + 0.05 && behind > d - 1.2, "about " + d + " blocks of path behind, a little less of flat arc (the wave): " + behind);
        }
    }

    @Test
    void theDiveLeavesTheOrbitThroughTheTargetAndLoopsBackIntoIt() {
        double wave = 1.1;
        double start = Math.toRadians(20);
        Vec3 target = new Vec3(C.x + 31 * Math.cos(Math.toRadians(95)), C.y + 3, C.z + 31 * Math.sin(Math.toRadians(95)));
        LeviathanPaths.Swim dive = LeviathanPaths.dive(C, wave, start, target, RiftLayout.PLATFORM_RING + 6 + 3.5);
        Polyline path = dive.path();
        assertEquals(0.0, path.start().distanceTo(LeviathanOrbit.point(C, start, wave)), 1e-6, "it starts on the orbit");
        assertTrue(dive.endsOnOrbit());
        assertEquals(0.0, path.end().distanceTo(LeviathanOrbit.point(C, dive.endAngle(), wave)), 1e-6, "and ends on it");
        double nearest = Double.MAX_VALUE;
        Vec3 through = target.add(0, LeviathanPaths.DIVE_OVER, 0);
        for (double s = 0; s <= path.length(); s += 0.25) {
            Vec3 p = path.at(s);
            nearest = Math.min(nearest, p.distanceTo(through));
            if (Math.abs(p.y - C.y) < 14) {
                assertTrue(radius(p) > 14.0, "never through the central asteroid: " + p);
            }
        }
        assertTrue(nearest < 0.3, "its head passes through where the target stands: " + nearest);
        // over the platform (out to its rim, 6 wide here) it stays level, its body clear of the rock underfoot
        for (double s = 0; s <= path.length(); s += 0.25) {
            Vec3 p = path.at(s);
            double a = Math.atan2(p.z - C.z, p.x - C.x);
            if (radius(p) > RiftLayout.PLATFORM_RING - 6.0 && radius(p) < RiftLayout.PLATFORM_RING + 6.0 && Math.abs(a - Math.toRadians(95)) < 0.2) {
                assertTrue(p.y - LeviathanMoves.PART_HEIGHT[0] / 2.0 >= target.y - 0.01, "over the platform, clear of its top: " + p);
            }
        }
        assertTrue(path.length() > 50 && path.length() < 160, "a dive of " + path.length());
        assertTrue(LeviathanPaths.diveWindow(Math.toRadians(75)));
        assertTrue(!LeviathanPaths.diveWindow(Math.toRadians(10)) && !LeviathanPaths.diveWindow(Math.toRadians(200)));
    }

    @Test
    void theMoorageCoilsTheBodyRoundTheCoreWithALevelBack() {
        double a = Math.toRadians(40);
        double mid = Math.toRadians(40 + 150);
        Vec3 head = LeviathanOrbit.point(C, a, 0.0);
        LeviathanPaths.Swim swim = LeviathanPaths.moorage(C, 0.0, head, a, mid);
        assertTrue(!swim.endsOnOrbit());
        Polyline path = swim.path();
        PathTrail trail = new PathTrail(480, 0.25);
        for (double s = 0; s <= path.length(); s += LeviathanMoves.SWIM_IN_SPEED) {
            trail.record(path.at(s));
        }
        Vec3 end = path.end();
        trail.record(end);
        double top = Math.floor(C.y + LeviathanMoves.COIL_TOP);
        for (double s = 0; s <= LeviathanMoves.FOLLOW[3]; s += 1.0) {
            Vec3 p = trail.behind(end, s);
            assertEquals(LeviathanMoves.COIL_RADIUS, radius(p), 0.35, "on the coil " + s + " behind");
            assertEquals(top, p.y + LeviathanPaths.halfHeight(s), 0.3, "its back is level " + s + " behind");
        }
        assertEquals(LeviathanPaths.coilHeadAngle(a, mid), LeviathanOrbit.angleOf(C, end) + Math.round((LeviathanPaths.coilHeadAngle(a, mid)
                - LeviathanOrbit.angleOf(C, end)) / (2 * Math.PI)) * 2 * Math.PI, 0.2);
        double midAngle = LeviathanOrbit.angleOf(C, trail.behind(end, LeviathanMoves.FOLLOW[1]));
        assertEquals(0.0, Math.sin((midAngle - mid) / 2.0), 0.12, "the coil's middle lies at the angle asked for");
    }

    @Test
    void theMoorageSwimsOnRoundItsOrbitToCoilBesideItsTargetsPlatform() {
        double a = Math.toRadians(10);
        for (int deg = 0; deg < 360; deg += 45) {
            double mid = Math.toRadians(deg);
            Vec3 head = LeviathanOrbit.point(C, a, 1.0);
            LeviathanPaths.Swim swim = LeviathanPaths.moorage(C, 1.0, head, a, mid);
            Polyline path = swim.path();
            PathTrail trail = new PathTrail(480, 0.25);
            for (double s = 0; s <= path.length(); s += LeviathanMoves.SWIM_IN_SPEED) {
                trail.record(path.at(s));
            }
            trail.record(path.end());
            double midAngle = LeviathanOrbit.angleOf(C, trail.behind(path.end(), LeviathanMoves.FOLLOW[1]));
            assertEquals(0.0, Math.sin((midAngle - mid) / 2.0), 0.12, "coil middle for " + deg);
            double start = LeviathanPaths.coilStart(a, mid);
            assertTrue(start >= a + 0.9 && start < a + 0.9 + 2 * Math.PI, "joins the coil ahead, within a lap");
            for (double s = 0; s <= path.length(); s += 0.5) {
                double r = radius(path.at(s));
                assertTrue(r > RiftLayout.CORE_RADIUS + 1.5 && r < RiftLayout.CLEAR_RADIUS, "clear of the core and the platforms: " + r);
            }
        }
    }

    @Test
    void itRisesFromItsSleepIntoTheOrbitClearOfTheCore() {
        RiftLayout layout = new RiftLayout(100, 229, -41, 77L);
        Vec3 c = layout.centre();
        double sleep = layout.entranceAngle() + Math.PI;
        Vec3 head = LeviathanPaths.polar(c, sleep, ThalassineLeviathan.SLEEP_RADIUS, layout.bowlSurface(ThalassineLeviathan.SLEEP_RADIUS) + 2.3);
        LeviathanPaths.Swim rise = LeviathanPaths.rise(c, 0.4, head, sleep);
        assertEquals(0.0, rise.path().start().distanceTo(head), 1e-6);
        assertEquals(0.0, rise.path().end().distanceTo(LeviathanOrbit.point(c, rise.endAngle(), 0.4)), 1e-6);
        for (double s = 0; s <= rise.path().length(); s += 0.5) {
            Vec3 p = rise.path().at(s);
            double d = p.distanceTo(c);
            assertTrue(d > RiftLayout.CORE_RADIUS + 3.0, "clear of the core at " + p);
        }
        double speed = rise.path().length() / (LeviathanMoves.INTRO - 30.0);
        assertTrue(speed > 0.4 && speed < 1.0, "it rises at " + speed);
    }

    @Test
    void theSinkEndsOnTheBowlAndTheDroopSinksTheHeadToThePlatforms() {
        Vec3 head = new Vec3(C.x + 22, C.y + 2, C.z);
        LeviathanPaths.Swim sink = LeviathanPaths.sink(head, new Vec3(0, 0, 1), C.y - 55);
        assertEquals(C.y - 55, sink.path().end().y, 0.5);
        LeviathanPaths.Swim droop = LeviathanPaths.droop(C, head, 0.0, (int) C.y - 3);
        assertEquals(C.y - 3 + 1.2, droop.path().end().y, 0.3);
        List<Double> ys = new ArrayList<>();
        for (double s = 0; s <= droop.path().length(); s += 1) {
            ys.add(droop.path().at(s).y);
        }
        assertTrue(ys.get(ys.size() - 1) < ys.get(0), "nose-down");
    }
}
