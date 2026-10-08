package com.cosmicbreach.lift;

import com.cosmicbreach.guardian.leviathan.RiftLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss 2 arena's rescue lift (1.1 design section 6): a fall off any platform, inner or outer rim, or a jump from the
 * bowl ends standing on a platform in under 8 seconds, with no hard grind against the Rift's rock on the way. Each ride is
 * run tick by tick the way the client runs it ({@link LiftSim}: the Drift's gravity and drag, a player's box that stops at
 * the Rift's real blocks) in several Rifts built from different seeds, since every world's Rifts differ in their platforms'
 * heights, sizes, boulders, rim crystals, bowl rubble and the rock rings in it.
 */
class RiftLiftTest {
    /** The Drift's jump (0.42 x 1.3). */
    private static final double JUMP = 0.546;
    private static final int EIGHT_SECONDS = 160;
    /** A rescue's own time from its catch (the fall before it, 6 blocks in a Tide's gravity, is another 30 ticks or so). */
    private static final int SEVEN_SECONDS = 140;

    /** Rift number {@code n}: a different seed, centre and so entrance, wave, platform sizes and tops each. */
    private static RiftLayout rift(int n) {
        return new RiftLayout(400 + 331 * n, RiftLayout.CENTRE_Y, -900 + 517 * n, 1234L + 977L * n);
    }

    private static List<LiftSim> sims(int count) {
        List<LiftSim> out = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            out.add(new LiftSim(rift(n)));
        }
        return out;
    }

    private static String describe(int n, String what, LiftSim.Result r) {
        return String.format(Locale.ROOT, "rift %d, %s: %d ticks (on platform %d, ground %b), %d ticks pressed against rock, ended %s", n, what, r.ticks(), r.platform(),
                r.ground(), r.blocked(), r.feet());
    }

    private static Vec3 outward(RiftLayout.Platform p, double sign) {
        return new Vec3(Math.cos(p.angle()), 0, Math.sin(p.angle())).scale(sign);
    }

    /** Falls all round every platform's rim (walking off, still or at a walk) in eight Rifts; {@code inner} picks the half of the rim that faces the core. */
    private static int fallsOffTheRim(boolean inner) {
        int caught = 0;
        List<LiftSim> sims = sims(8);
        for (int n = 0; n < sims.size(); n++) {
            LiftSim sim = sims.get(n);
            for (RiftLayout.Platform p : sim.layout.platforms()) {
                for (int k = 0; k < 16; k++) {
                    double angle = p.angle() + k * Math.PI / 8.0;
                    boolean towardCore = Math.cos(angle - p.angle()) < 0;
                    Vec3 start = towardCore == inner ? sim.firstStepOff(p, angle) : null;
                    if (start == null) {
                        continue; // not this half of the rim, or a walkway runs on from here: nobody falls off at this angle
                    }
                    for (double speed : new double[] {0.0, 0.12}) {
                        Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                        LiftSim.Result r = sim.ride(start, dir.scale(speed), false, 400);
                        String what = "platform " + p.index() + ", ray " + k + ", speed " + speed;
                        if (r.lastRide() == null && r.ground()) {
                            // stepped down onto a walkway (they slope between platforms of different heights) and never fell far
                            assertTrue(r.feet().y >= RiftLift.standY(p) - RiftLift.CATCH_BELOW, describe(n, what, r));
                            continue;
                        }
                        caught++;
                        assertTrue(r.landed() && r.ticks() < EIGHT_SECONDS, "under 8 s: " + describe(n, what, r));
                        assertEquals(r.lastRide().platform(), r.platform(), "back on its own platform: " + describe(n, what, r));
                        assertTrue(r.blocked() <= 12, "never ground against rock: " + describe(n, what, r));
                    }
                }
            }
        }
        return caught;
    }

    @Test
    void aFallOffAnyInnerRimEndsStandingOnThatPlatformInUnderEightSeconds() {
        assertTrue(fallsOffTheRim(true) > 200, "the test really catches falls");
    }

    @Test
    void aFallOffAnyOuterRimEndsStandingOnItToo() {
        assertTrue(fallsOffTheRim(false) > 200, "the test really catches falls");
    }

    /**
     * Steps off the way in (the walkway from platform 0 out to the shell's gap, 3 wide, no rails) at {@code distance} blocks
     * from the axis: a block clear of its planks to one side of its middle line, at the walkway's height, standing still.
     * Null if the spot is not one a player could fall from (something solid in the box, or planks still under it).
     */
    private static Vec3 offTheWayIn(LiftSim sim, double distance, int side) {
        RiftLayout l = sim.layout;
        double e = l.entranceAngle();
        Vec3 c = l.centre();
        double across = 2.4 * side;
        Vec3 at = new Vec3(c.x + distance * Math.cos(e) - Math.sin(e) * across, RiftLift.standY(l.platform(0)), c.z + distance * Math.sin(e) + Math.cos(e) * across);
        return sim.free(at) && !sim.supported(at) ? at : null;
    }

    @Test
    void aFallOffTheWayInFromAnywhereAlongItEndsOnAPlatformInUnderEightSeconds() {
        int tried = 0;
        int worst = 0;
        List<LiftSim> sims = sims(12);
        for (int n = 0; n < sims.size(); n++) {
            LiftSim sim = sims.get(n);
            for (double distance : new double[] {40, 48, 56, 64, 72, 76}) {
                for (int side : new int[] {-1, 1}) {
                    Vec3 start = offTheWayIn(sim, distance, side);
                    if (start == null) {
                        continue;
                    }
                    LiftSim.Result r = sim.ride(start, Vec3.ZERO, false, 400);
                    String what = String.format(Locale.ROOT, "off the way in %.0f blocks out, side %d", distance, side);
                    tried++;
                    assertNotNull(r.lastRide(), "caught: " + describe(n, what, r));
                    assertTrue(r.landed() && r.ticks() < EIGHT_SECONDS, "under 8 s: " + describe(n, what, r));
                    assertTrue(r.blocked() <= 12, "never ground against rock: " + describe(n, what, r));
                    worst = Math.max(worst, r.ticks());
                }
            }
        }
        assertTrue(tried >= 100, "the test really steps off the way in: " + tried);
        assertTrue(worst < 150, "and even the longest fall is not a long wait: " + worst + " ticks");
    }

    /** A jump from {@code spot}: the first tick carries them up by the jump's speed, and the lift first looks at them there, in the air. */
    private static LiftSim.Result jump(LiftSim sim, Vec3 spot) {
        Vec3 up = sim.move(spot, new Vec3(0, JUMP, 0)).feet();
        return sim.ride(up, new Vec3(0, (JUMP - sim.gravity) * 0.98, 0), false, 500);
    }

    /** The bowl's floor in the open: standing spots from the axis out to the platforms' ring and past it, all round. */
    private static List<Vec3> bowlSpots(LiftSim sim, double[] radii, int angles) {
        List<Vec3> out = new ArrayList<>();
        Vec3 c = sim.layout.centre();
        for (double r : radii) {
            for (int k = 0; k < angles; k++) {
                double a = k * 2.0 * Math.PI / angles + 0.05;
                double x = c.x + r * Math.cos(a);
                double z = c.z + r * Math.sin(a);
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                for (int y = (int) Math.ceil(sim.layout.bowlSurface(r)) + 14; y >= (int) Math.floor(sim.layout.bowlSurface(r)) - 6; y--) {
                    if (sim.solid(bx, y, bz) && !sim.solid(bx, y + 1, bz) && !sim.solid(bx, y + 2, bz)) {
                        out.add(new Vec3(x, y + 1.0, z));
                        break;
                    }
                }
            }
        }
        return out;
    }

    @Test
    void aJumpFromTheBowlFloorEndsOnAPlatformInUnderEightSeconds() {
        int taken = 0;
        int declined = 0;
        int stuck = 0;
        List<LiftSim> sims = sims(8);
        for (int n = 0; n < sims.size(); n++) {
            LiftSim sim = sims.get(n);
            for (Vec3 spot : bowlSpots(sim, new double[] {8, 12, 16, 20, 24, 28, 32, 36, 40}, 16)) {
                LiftSim.Result r = jump(sim, spot);
                String what = String.format(Locale.ROOT, "jump from %.0f %.0f %.0f", spot.x - sim.layout.centre().x, spot.y, spot.z - sim.layout.centre().z);
                if (r.lastRide() == null) {
                    declined++; // walled in under an overhang: the lift leaves them alone (see aPlayerWalledIntoAPocketIsLeftAlone)
                    continue;
                }
                taken++;
                if (!r.landed() || r.blocked() > 40) {
                    stuck++;
                    continue;
                }
                assertTrue(r.ticks() < EIGHT_SECONDS, "under 8 s: " + describe(n, what, r));
                assertEquals(r.lastRide().platform(), r.platform(), "on its own platform: " + describe(n, what, r));
            }
        }
        assertTrue(taken > 900, "most jumps are taken: " + taken);
        assertTrue(declined <= taken / 50, "walled-in spots are rare: " + declined + " of " + (taken + declined));
        assertTrue(stuck <= taken / 200, "a ride that is taken almost never sticks: " + stuck + " of " + taken);
    }

    @Test
    void overTheLandingARiderIsLoweredOntoItAndNeverLetGoInTheAir() {
        RiftLayout l = rift(0);
        for (RiftLayout.Platform p : l.platforms()) {
            for (boolean outer : new boolean[] {false, true}) {
                RiftLift.Ride ride = new RiftLift.Ride(p.index(), outer, 1);
                Vec3 land = RiftLift.landing(l, ride);
                double stand = RiftLift.standY(p);
                String where = "platform " + p.index() + (outer ? " outer" : " inner");
                // carried over the landing at the carry height: lowered, and still on the lift
                RiftLift.Step over = RiftLift.step(l, ride, new Vec3(land.x, stand + RiftLift.CLEARANCE, land.z), Vec3.ZERO, false, false);
                assertNotNull(over.ride(), where + ": still on the lift over the landing");
                assertTrue(over.velocity().y < -0.1, where + ": going down");
                // every height from just over the platform to the carry height, a little off the landing: steered back to it and going down, never let go
                for (double up = 0.05; up <= RiftLift.CLEARANCE; up += 0.25) {
                    for (double off : new double[] {0.0, 0.5, 1.0}) {
                        Vec3 at = new Vec3(land.x + off, stand + up, land.z);
                        RiftLift.Step lowering = RiftLift.step(l, ride, at, new Vec3(0, -0.2, 0), false, false);
                        String what = String.format(Locale.ROOT, "%s, %.2f up and %.1f to one side", where, up, off);
                        assertNotNull(lowering.ride(), what + ": still on the lift");
                        assertTrue(lowering.velocity().y < -0.1, what + ": going down");
                        assertTrue(off == 0.0 || lowering.velocity().x < 0.0, what + ": steered back toward the landing");
                    }
                }
                // standing on it, the ride is over
                assertNull(RiftLift.step(l, ride, new Vec3(land.x, stand, land.z), Vec3.ZERO, true, false).ride(), where + ": standing");
            }
        }
    }

    /**
     * The Drift's weather reaches inside the arenas (nothing excludes them): a Gravity Tide's gravity of 0.15 and a flow of 0.11 a tick
     * (a Tide's current of 0.06 inside a standing current's tube of 0.05) moves the player on top of the lift's own movement. Every walk
     * off every platform's inner rim, with the flow toward the core, across it, away from it and at the angles between, ends standing
     * on the platform they were caught for, caught once and in under 8 seconds: a rider let go in the air over a landing 1.1 in from
     * the inner rim was carried off the edge by the flow, fell, was caught again and looped until the weather stopped.
     */
    @Test
    void aRescueInTheDriftsWeatherSetsThePlayerDownOnTheirPlatformAtTheFirstTry() {
        int checked = 0;
        int uncaught = 0;
        for (int n = 0; n < 6; n++) {
            RiftLayout l = rift(n);
            for (RiftLayout.Platform p : l.platforms()) {
                for (double degrees : new double[] {0.0, 22.5, -22.5, 45.0, -45.0, 90.0, 180.0}) {
                    double heading = p.angle() + Math.PI + Math.toRadians(degrees); // 0 degrees: toward the core
                    LiftSim sim = new LiftSim(l, LiftSim.TIDE_GRAVITY, new Vec3(Math.cos(heading), 0, Math.sin(heading)).scale(0.11));
                    for (int k = 0; k < 16; k++) {
                        double angle = p.angle() + k * Math.PI / 8.0;
                        Vec3 start = Math.cos(angle - p.angle()) < 0 ? sim.firstStepOff(p, angle) : null;
                        if (start == null) {
                            continue; // not the half of the rim that faces the core, or a walkway runs on from here
                        }
                        LiftSim.Result r = sim.ride(start, Vec3.ZERO, false, 500);
                        String what = String.format(Locale.ROOT, "rift %d, platform %d, ray %d, flow %.1f degrees off the core: %d ticks (caught at %d), %d catches, ended on platform %d (ground %b) at %s",
                                n, p.index(), k, degrees, r.ticks(), r.caughtAt(), r.catches(), r.platform(), r.ground(), r.feet());
                        if (r.lastRide() == null) {
                            uncaught++; // the flow carried them back onto the platform's walkway before the line: nothing to lift
                            continue;
                        }
                        checked++;
                        // from the catch: the flow can carry a player along a platform's edge, or down a walkway, for a while before they fall
                        assertTrue(r.landed() && r.ticks() - r.caughtAt() < SEVEN_SECONDS, "under 7 s from the catch: " + what);
                        assertEquals(1, r.catches(), "caught once, set down at the first try: " + what);
                        assertEquals(r.lastRide().platform(), r.platform(), "on the platform they were caught for: " + what);
                    }
                }
            }
        }
        assertTrue(checked > 1500, "the test really rescues players in the weather: " + checked + " (" + uncaught + " never fell as far as the line)");
    }

    @Test
    void aJumpFromTheBowlInTheDriftsWeatherEndsOnAPlatformAtTheFirstTryToo() {
        int taken = 0;
        for (int n = 0; n < 3; n++) {
            RiftLayout l = rift(n);
            for (double degrees : new double[] {0.0, 135.0, 270.0}) {
                double heading = Math.toRadians(degrees + 30.0 * n);
                LiftSim sim = new LiftSim(l, LiftSim.TIDE_GRAVITY, new Vec3(Math.cos(heading), 0, Math.sin(heading)).scale(0.11));
                for (Vec3 spot : bowlSpots(sim, new double[] {12, 20, 28}, 8)) {
                    LiftSim.Result r = jump(sim, spot);
                    if (r.lastRide() == null) {
                        continue; // walled in under an overhang: left alone
                    }
                    taken++;
                    String what = String.format(Locale.ROOT, "rift %d, flow at %.0f degrees, jump from %.0f %.0f %.0f: %d ticks, %d catches", n, Math.toDegrees(heading),
                            spot.x - l.centre().x, spot.y, spot.z - l.centre().z, r.ticks(), r.catches());
                    if (r.blocked() > 40) {
                        continue; // pinned against a rock ring on the way: the weather-free test allows these too
                    }
                    assertTrue(r.landed() && r.ticks() < EIGHT_SECONDS, "under 8 s: " + what);
                    assertEquals(1, r.catches(), "set down at the first try: " + what);
                    assertEquals(r.lastRide().platform(), r.platform(), "on its own platform: " + what);
                }
            }
        }
        assertTrue(taken > 150, "the test really lifts jumps in the weather: " + taken);
    }

    @Test
    void anyoneThrownOrDroppedBelowTheLineWhoIsInOpenAirIsLiftedToo() {
        int landed = 0;
        int tried = 0;
        List<LiftSim> sims = sims(6);
        for (int n = 0; n < sims.size(); n++) {
            LiftSim sim = sims.get(n);
            RiftLayout l = sim.layout;
            Vec3 c = l.centre();
            Random rnd = new Random(n * 7919L + 1);
            for (int i = 0; i < 150; i++) {
                // anywhere inside, from a block over the bowl to the line, with a throw of up to a block a tick any way
                double r = 14 + rnd.nextDouble() * 56;
                double a = rnd.nextDouble() * 2 * Math.PI;
                double y = l.bowlSurface(r) + 6 + rnd.nextDouble() * (RiftLayout.CENTRE_Y - 14 - l.bowlSurface(r));
                Vec3 at = new Vec3(c.x + r * Math.cos(a), y, c.z + r * Math.sin(a));
                if (!l.inside(at) || !sim.free(at)) {
                    continue;
                }
                double speed = rnd.nextDouble();
                double heading = rnd.nextDouble() * 2 * Math.PI;
                Vec3 v = new Vec3(Math.cos(heading) * speed, (rnd.nextDouble() - 0.5) * 1.2, Math.sin(heading) * speed);
                LiftSim.Result r2 = sim.ride(at, v, false, 700);
                if (r2.lastRide() == null && r2.ground()) {
                    continue; // came down on rock of the shell below the line: they are standing, nothing to lift
                }
                tried++;
                if (r2.landed() && r2.ticks() < 400) {
                    landed++;
                }
            }
        }
        assertTrue(tried > 500, "the test really throws players about: " + tried);
        assertTrue(landed >= tried * 97 / 100, "all but a few end on a platform within 20 s: " + landed + " of " + tried);
    }

    @Test
    void itLeavesAloneWhoeverStandsIsAboveTheLineOrIsOutsideTheSphere() {
        RiftLayout l = rift(0);
        RiftLayout.Platform p = l.platform(1);
        Vec3 onTop = new Vec3(p.x(), RiftLift.standY(p), p.z());
        assertNull(RiftLift.step(l, null, onTop, Vec3.ZERO, true, false).velocity(), "standing");
        Vec3 justBelow = new Vec3(p.x(), RiftLift.standY(p), p.z()).add(outward(p, -1).scale(p.radius() + 0.6)).add(0, -3.0, 0);
        assertNull(RiftLift.step(l, null, justBelow, new Vec3(0, -0.3, 0), false, false).ride(), "three below: not yet");
        Vec3 far = new Vec3(l.centre().x + 200, 230, l.centre().z);
        assertNull(RiftLift.step(l, null, far, new Vec3(0, -0.5, 0), false, false).ride(), "outside the sphere");
        Vec3 deep = justBelow.add(0, -12.0, 0);
        assertNotNull(RiftLift.step(l, null, deep, new Vec3(0, -0.5, 0), false, false).ride(), "twelve below: caught");
        assertNull(RiftLift.step(l, null, deep, new Vec3(0, -0.5, 0), true, false).ride(), "on the ground (the bowl's floor) it waits for a jump");
    }

    @Test
    void sneakingSinksInsteadOfRising() {
        RiftLayout l = rift(0);
        RiftLayout.Platform p = l.platform(2);
        Vec3 deep = new Vec3(p.x(), RiftLift.standY(p), p.z()).add(outward(p, -1).scale(p.radius() + 0.6)).add(0, -12.0, 0);
        RiftLift.Step s = RiftLift.step(l, null, deep, new Vec3(0, -0.6, 0), false, true);
        assertNotNull(s.ride());
        assertEquals(RiftLift.SINK, s.velocity().y, 1e-9);
        RiftLift.Step again = RiftLift.step(l, s.ride(), deep, s.velocity(), false, true);
        assertEquals(RiftLift.SINK, again.velocity().y, 1e-9, "and keeps sinking while they hold it");
    }

    @Test
    void theColumnsStandInOpenAirClearOfTheCoreTheCoilAndTheBridgeLines() {
        for (RiftLayout.Platform p : rift(0).platforms()) {
            for (boolean outer : new boolean[] {false, true}) {
                for (int side : new int[] {-1, 1}) {
                    RiftLayout l = rift(0);
                    Vec3 col = RiftLift.column(l, new RiftLift.Ride(p.index(), outer, side));
                    double r = RiftLift.radius(l, col);
                    if (!outer) {
                        assertTrue(r > 17.5 && r < RiftLayout.CLEAR_RADIUS - 1.0, "inner column at radius " + r);
                    } else {
                        assertTrue(r > RiftLayout.PLATFORM_RING + p.radius() + 1.0, "outer column at radius " + r);
                    }
                    assertTrue(l.inside(col), "inside the sphere");
                }
            }
        }
    }

    @Test
    void everyColumnIsOpenAirFromTheLineUpAndEveryLandingIsClearSolidGroundInAnyRift() {
        int columns = 0;
        for (LiftSim sim : sims(40)) {
            RiftLayout l = sim.layout;
            for (RiftLayout.Platform p : l.platforms()) {
                for (boolean outer : new boolean[] {false, true}) {
                    for (int side : new int[] {-1, 1}) {
                        RiftLift.Ride ride = new RiftLift.Ride(p.index(), outer, side);
                        Vec3 col = RiftLift.column(l, ride);
                        for (double y = RiftLift.standY(p) - RiftLift.DEEP_BELOW; y <= RiftLift.standY(p) + RiftLift.CLEARANCE; y += 0.5) {
                            assertTrue(sim.free(new Vec3(col.x, y, col.z)), String.format(Locale.ROOT, "platform %d %s column %d is open at %.1f in the Rift at %d",
                                    p.index(), outer ? "outer" : "inner", side, y, l.x()));
                        }
                        Vec3 land = RiftLift.landing(l, ride);
                        assertTrue(sim.free(land), String.format(Locale.ROOT, "platform %d %s landing %d is clear in the Rift at %d", p.index(), outer ? "outer" : "inner", side, l.x()));
                        assertTrue(sim.supported(land), String.format(Locale.ROOT, "platform %d %s landing %d has the platform under it in the Rift at %d", p.index(),
                                outer ? "outer" : "inner", side, l.x()));
                        columns++;
                    }
                }
            }
        }
        assertEquals(40 * 8 * 4, columns);
    }

    @Test
    void aPlayerWalledIntoAPocketOfTheBowlIsLeftAloneRatherThanHeldAgainstTheRock() {
        // somewhere under a rock ring or overhang in the bowl, no way up is clear: the lift does not take them
        int walledIn = 0;
        int free = 0;
        for (LiftSim sim : sims(30)) {
            for (Vec3 spot : bowlSpots(sim, new double[] {8, 12, 16, 20, 24}, 16)) {
                Vec3 jumping = sim.move(spot, new Vec3(0, JUMP, 0)).feet();
                RiftLift.Step s = RiftLift.step(sim.layout, null, jumping, new Vec3(0, JUMP, 0), false, false);
                if (RiftLift.start(sim.layout, jumping) == null) {
                    walledIn++;
                    assertNull(s.ride(), "no ride");
                    assertNull(s.velocity(), "and no push");
                } else {
                    free++;
                    assertNotNull(s.ride());
                    assertNotNull(s.velocity());
                }
            }
        }
        assertTrue(walledIn > 0, "the bowl really has pockets, and the test found one");
        assertTrue(free > 20 * walledIn, "but most of it is open");
    }

    @Test
    void theHeldRiseKeepsAPlayersHeadOutOfAPlatformsLowerFlank() {
        RiftLayout l = rift(0);
        RiftLayout.Platform p = l.platform(3);
        Vec3 mid = new Vec3(p.x(), RiftLift.standY(p), p.z());
        assertTrue(RiftLift.underBody(p, mid.add(0, -9.0, 0)), "right under its middle, a few blocks short of its lowest rock");
        assertTrue(RiftLift.underBody(p, mid.add(outward(p, 1).scale(p.radius() - 1.0)).add(0, -8.0, 0)), "under its rim, where it is thinnest but still there at 8 below");
        assertFalse(RiftLift.underBody(p, mid.add(outward(p, 1).scale(p.radius() + 3.0)).add(0, -8.0, 0)), "three blocks out past its rim");
        assertFalse(RiftLift.underBody(p, mid.add(0, -30.0, 0)), "far under it, below its lowest rock");
        assertFalse(RiftLift.underBody(p, mid.add(0, 0.5, 0)), "level with its top");
    }

    @Test
    void theSimStopsAPlayerOnTheBowlAndAtAPlatformsRim() {
        LiftSim sim = sims(1).get(0);
        // dropped over the bowl with nothing to hold them, they come to rest on the floor, and the box stops at a block
        Vec3 spot = bowlSpots(sim, new double[] {24}, 4).get(0);
        LiftSim.Move fall = sim.move(spot.add(0, 3.0, 0), new Vec3(0, -5.0, 0));
        assertTrue(fall.onGround() && fall.hitY(), "stopped by the floor");
        assertTrue(fall.feet().y >= spot.y - 1e-6 && fall.feet().y < spot.y + 3.0, "standing on it");
        // and walking off a platform's rim leaves nothing under them within a block or two of its nominal edge
        RiftLayout.Platform p = sim.layout.platform(2);
        Vec3 edge = null;
        for (int k = 0; k < 16 && edge == null; k++) {
            edge = sim.firstStepOff(p, p.angle() + k * Math.PI / 8.0);
        }
        assertNotNull(edge);
        assertFalse(sim.supported(edge), "past the rim there is nothing under them");
        assertTrue(sim.free(edge), "and the box is clear of every block");
        assertTrue(Math.hypot(edge.x - p.x(), edge.z - p.z()) <= p.radius() + 2.0, "within a block or two of its nominal rim");
    }
}
