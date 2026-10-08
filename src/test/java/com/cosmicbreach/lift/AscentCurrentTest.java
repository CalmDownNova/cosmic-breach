package com.cosmicbreach.lift;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rising current (1.1 design section 5, and the planning decision that currents never catch falling players): standing
 * in its foot, or walking or jumping into it, ends standing in front of the shrine above in under 12 seconds; sneaking lets
 * go; whoever drops in from above, however far, lands where they fell and is not carried back up; whoever stands on the upper
 * level beside it is left alone. Simulated with the client's own motion (gravity 0.08, 0.4x in the Drift; drag 0.98 down, 0.91
 * across) over a floor at the foot and the island's ground round the landing.
 */
class AscentCurrentTest {
    private static final AscentCurrent.Current C = new AscentCurrent.Current(100.5, 50.5, 221.0, 341.0, 106.5, 52.5);
    private static final List<AscentCurrent.Current> ALL = List.of(C);
    /** The Drift's jump (0.42 x 1.3). */
    private static final double JUMP = 0.546;

    private static double gravity(double y) {
        return 0.08 * (y >= 160 && y < 300 ? 0.4 : 1.0);
    }

    /** The ground's height under (x, z): the island round the landing, the floor round the foot, else none. */
    private static double ground(AscentCurrent.Current c, double x, double z) {
        if (Math.hypot(x - c.landX(), z - c.landZ()) <= 2.0) {
            return c.top();
        }
        if (Math.hypot(x - c.x(), z - c.z()) <= 3.0) {
            return c.bottom();
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** One simulated player: the client's motion and the step called as the client calls it. */
    private static final class Sim {
        Vec3 feet;
        Vec3 v;
        AscentCurrent.State state = AscentCurrent.State.START;
        boolean onGround;
        boolean sneak;
        boolean rode;
        int ticks;

        final AscentCurrent.Current cur;
        final List<AscentCurrent.Current> all;

        Sim(Vec3 feet, Vec3 v) {
            this(feet, v, C);
        }

        Sim(Vec3 feet, Vec3 v, AscentCurrent.Current cur) {
            this.feet = feet;
            this.v = v;
            this.cur = cur;
            this.all = List.of(cur);
            this.onGround = feet.y <= ground(cur, feet.x, feet.z) + 1e-6;
        }

        /** One tick; {@code jump} makes a grounded player jump after the step, as the client's own movement does. */
        void tick(boolean jump) {
            AscentCurrent.Step s = AscentCurrent.step(all, state, feet, v, onGround, sneak);
            state = s.state();
            rode |= state.ride() != null;
            if (s.velocity() != null) {
                v = s.velocity();
            }
            if (jump && onGround && state.ride() == null) {
                v = new Vec3(v.x, JUMP, v.z);
            }
            Vec3 next = feet.add(v);
            double g = ground(cur, next.x, next.z);
            onGround = next.y <= g;
            if (onGround) {
                next = new Vec3(next.x, g, next.z);
                v = new Vec3(v.x, 0.0, v.z);
            }
            feet = next;
            v = new Vec3(v.x * 0.91, (v.y - gravity(feet.y)) * 0.98, v.z * 0.91);
            ticks++;
        }

        boolean inFrontOfTheShrine() {
            return state.ride() == null && onGround && Math.abs(feet.y - cur.top()) < 1e-6
                    && Math.hypot(feet.x - cur.landX(), feet.z - cur.landZ()) <= 1.0;
        }

        /** Runs until the player stands in front of the shrine above; the tick count, or -1. */
        int untilArrived(int max) {
            for (int t = 0; t < max; t++) {
                tick(false);
                if (inFrontOfTheShrine()) {
                    return ticks;
                }
            }
            return -1;
        }
    }

    @Test
    void standingInItsFootEndsInFrontOfTheShrineAboveInUnderTwelveSeconds() {
        Sim sim = new Sim(new Vec3(C.x(), C.bottom(), C.z()), Vec3.ZERO);
        int ticks = sim.untilArrived(600);
        assertTrue(ticks > 0 && ticks < 240, ticks + " ticks");
    }

    @Test
    void walkingIntoItsFootFromOutsideCarriesYouUp() {
        Sim sim = new Sim(new Vec3(C.x() - 2.5, C.bottom(), C.z()), new Vec3(0.2, 0.0, 0.0));
        int ticks = sim.untilArrived(600);
        assertTrue(sim.rode && ticks > 0 && ticks < 240, ticks + " ticks");
    }

    @Test
    void aJumpIntoItFromTheEdgeOfItsFloorCarriesYouUp() {
        Sim sim = new Sim(new Vec3(C.x() - 2.9, C.bottom(), C.z()), Vec3.ZERO);
        sim.v = new Vec3(0.15, 0.0, 0.0);
        for (int t = 0; t < 40 && !sim.rode; t++) {
            sim.tick(t == 0);
        }
        assertTrue(sim.rode, "a jump that crosses its edge is carried");
        assertTrue(sim.untilArrived(600) > 0);
    }

    @Test
    void droppingInFromTheLevelAboveLandsWhereYouFellAndIsNotCarriedBackUp() {
        // dropped from the level above, down the middle of the column, from 150 blocks over its top
        Sim sim = new Sim(new Vec3(C.x(), C.top() + 150.0, C.z()), Vec3.ZERO);
        for (int t = 0; t < 2000 && !sim.onGround; t++) {
            sim.tick(false);
        }
        assertTrue(sim.onGround && Math.abs(sim.feet.y - C.bottom()) < 1e-6, "landed on the floor at its foot: " + sim.feet);
        assertFalse(sim.rode, "never carried");
        for (int t = 0; t < 200; t++) {
            sim.tick(false);
        }
        assertFalse(sim.rode, "and not carried once landed: standing in its foot after a fall is not walking into it");
        assertEquals(C.bottom(), sim.feet.y, 1e-9);
    }

    @Test
    void aFallOfAnySizeIntoItIsLeftAlone() {
        for (double drop : new double[] {10.0, 40.0, 100.0}) {
            // dropped from this far over the column's top
            Sim sim = new Sim(new Vec3(C.x() + 0.8, C.top() + 3.0 + drop, C.z()), Vec3.ZERO);
            for (int t = 0; t < 2000 && !sim.onGround; t++) {
                sim.tick(false);
            }
            for (int t = 0; t < 30; t++) {
                sim.tick(false);
            }
            assertFalse(sim.rode, "a drop of " + drop + " blocks");
            assertEquals(C.bottom(), sim.feet.y, 1e-9);
        }
    }

    @Test
    void aFastFallIsLeftAloneEvenFromAShortDrop() {
        AscentCurrent.Step s = AscentCurrent.step(ALL, AscentCurrent.State.START, new Vec3(C.x(), 300.0, C.z()), new Vec3(0, -1.2, 0), false, false);
        assertNull(s.state().ride());
        assertNull(s.velocity());
    }

    @Test
    void steppingOffTheRimIntoTheTopOfItIsLeftAloneAndFallsPastIt() {
        // standing on the island's rim at the shrine's height, a block from the catch: one step off and they drop through the column
        Sim sim = new Sim(new Vec3(C.x() - 2.2, C.top(), C.z()), new Vec3(0.25, 0.0, 0.0));
        sim.onGround = false;
        for (int t = 0; t < 400 && !sim.onGround; t++) {
            sim.tick(false);
        }
        assertFalse(sim.rode, "stepping off the island beside it is a fall, not a way in");
        assertEquals(C.bottom(), sim.feet.y, 1e-9, "they fell to the floor at its foot");
    }

    @Test
    void whoeverLandedInItCanStillHopIntoIt() {
        Sim sim = new Sim(new Vec3(C.x(), C.top() + 40.0, C.z()), Vec3.ZERO);
        for (int t = 0; t < 2000 && !sim.onGround; t++) {
            sim.tick(false);
        }
        sim.tick(false);
        assertFalse(sim.rode);
        sim.tick(true); // a jump from where they landed: they chose it
        for (int t = 0; t < 3 && !sim.rode; t++) {
            sim.tick(false);
        }
        assertTrue(sim.rode, "jumping in it is walking into it");
        assertTrue(sim.untilArrived(600) > 0);
    }

    @Test
    void whoeverLandedInItCanStepOutAndBackIn() {
        Sim sim = new Sim(new Vec3(C.x(), C.top() + 40.0, C.z()), Vec3.ZERO);
        for (int t = 0; t < 2000 && !sim.onGround; t++) {
            sim.tick(false);
        }
        sim.feet = new Vec3(C.x() - 2.5, C.bottom(), C.z());
        sim.v = Vec3.ZERO;
        sim.tick(false);
        assertFalse(sim.rode, "outside it");
        sim.v = new Vec3(0.2, 0.0, 0.0);
        assertTrue(sim.untilArrived(600) > 0, "walking back in carries them");
    }

    @Test
    void sneakingLetsGoAndAFallGoesOnUntouched() {
        Sim sim = new Sim(new Vec3(C.x(), C.bottom(), C.z()), Vec3.ZERO);
        for (int t = 0; t < 60; t++) {
            sim.tick(false);
        }
        assertNotNull(sim.state.ride());
        sim.sneak = true;
        AscentCurrent.Step s = AscentCurrent.step(ALL, sim.state, sim.feet, sim.v, false, true);
        assertNull(s.state().ride(), "sneaking lets go");
        assertNull(s.velocity(), "and the fall goes on untouched");
        sim.state = s.state();
        for (int t = 0; t < 200 && !sim.onGround; t++) {
            sim.tick(false);
        }
        assertTrue(sim.onGround && sim.feet.y < C.bottom() + 1e-6, "dropped back to the floor: " + sim.feet);
        assertTrue(sim.state.ride() == null);
    }

    @Test
    void sneakingInItsFootIsNotCarried() {
        Sim sim = new Sim(new Vec3(C.x(), C.bottom(), C.z()), Vec3.ZERO);
        sim.sneak = true;
        for (int t = 0; t < 100; t++) {
            sim.tick(false);
        }
        assertFalse(sim.rode);
        sim.sneak = false;
        sim.tick(false);
        assertTrue(sim.rode, "and carried once they stand up");
    }

    @Test
    void itLeavesAloneWhoeverStandsOnTheLevelAboveOrIsOutOfIt() {
        AscentCurrent.Step above = AscentCurrent.step(ALL, AscentCurrent.State.START, new Vec3(C.x() + 1.0, C.top(), C.z()), Vec3.ZERO, true, false);
        assertNull(above.state().ride(), "on the ground above");
        assertNull(AscentCurrent.step(ALL, AscentCurrent.State.START, new Vec3(C.x() + 3.0, 280.0, C.z()), Vec3.ZERO, false, false).velocity(), "beside it");
        assertNull(AscentCurrent.step(ALL, AscentCurrent.State.START, new Vec3(C.x(), 200.0, C.z()), Vec3.ZERO, false, false).velocity(), "below its foot");
    }

    @Test
    void theLandingIsOutsideTheCatchWithRoomToSpare() {
        assertTrue(Math.hypot(C.landX() - C.x(), C.landZ() - C.z()) > AscentCurrent.CATCH + AscentCurrent.LANDING_ROOM);
        assertTrue(C.landingClear());
        assertFalse(new AscentCurrent.Current(100.5, 50.5, 221.0, 341.0, 101.5, 50.5).landingClear());
    }

    @Test
    void aTeleportForgetsTheDropSoFar() {
        // stood at the shrine's height, then was moved (a command, a respawn) to the floor at the foot of the column: that is no fall
        AscentCurrent.State atTheShrine = AscentCurrent.step(ALL, AscentCurrent.State.START, new Vec3(C.landX(), C.top(), C.landZ()), Vec3.ZERO, true, false).state();
        AscentCurrent.Step moved = AscentCurrent.step(ALL, atTheShrine, new Vec3(C.x(), C.bottom(), C.z()), Vec3.ZERO, true, false);
        assertNotNull(moved.state().ride(), "put in its foot they are carried, however far they were moved");
        // while a real drop of the same height is not
        AscentCurrent.State falling = new AscentCurrent.State(null, C.top(), null, new Vec3(C.x(), C.bottom() + 3.0, C.z()));
        assertNull(AscentCurrent.step(ALL, falling, new Vec3(C.x(), C.bottom() + 2.0, C.z()), new Vec3(0, -0.8, 0), false, false).state().ride());
    }

    @Test
    void aRiderAlwaysArrivesBesideTheShrineHoweverFarTheWayAcrossIs() {
        // the search puts the axis 3 to 14 blocks from the shrine and the landing a little off it: 12, 17 and 25 blocks apart all arrive
        for (double apart : new double[] {5.0, 12.5, 17.0, 25.0}) {
            AscentCurrent.Current far = new AscentCurrent.Current(100.5, 50.5, 221.0, 341.0, 100.5 + apart, 50.5);
            Sim sim = new Sim(new Vec3(far.x(), far.bottom(), far.z()), Vec3.ZERO, far);
            int ticks = sim.untilArrived(900);
            assertTrue(ticks > 0 && ticks < 400, apart + " blocks across: " + ticks + " ticks, ended " + sim.feet);
        }
    }

    @Test
    void aLandingOneBlockOffTheFloorStillArrives() {
        AscentCurrent.Current high = new AscentCurrent.Current(100.5, 50.5, 221.0, 342.0, 106.5, 52.5);
        Sim sim = new Sim(new Vec3(high.x(), high.bottom(), high.z()), Vec3.ZERO, high);
        assertTrue(sim.untilArrived(600) > 0);
    }

    @Test
    void aRiderPushedFarOffIsLetGo() {
        AscentCurrent.State riding = new AscentCurrent.State(C, 300.0, null, new Vec3(C.x() + 30.0, 300.0, C.z() - 30.0));
        AscentCurrent.Step s = AscentCurrent.step(ALL, riding, new Vec3(C.x() + 30.0, 300.0, C.z() - 30.0), Vec3.ZERO, false, false);
        assertNull(s.state().ride());
        assertNull(s.velocity());
    }
}
