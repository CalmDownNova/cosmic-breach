package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.guardian.Telegraphs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arena's layout, the fists' flight paths, the telegraph shapes and the attack timings. */
class ColossusGeometryTest {
    private static final CrownArena ARENA = new CrownArena(100, 450, -40);

    @Test
    void sixCrystalsStandFourteenBlocksOutSixtyDegreesApart() {
        for (int k = 0; k < 6; k++) {
            Vec3 p = ARENA.crystalPoint(k);
            double d = ARENA.distance(p.x, p.z);
            assertTrue(Math.abs(d - 14.0) < 0.2, "crystal " + k + " at " + d);
            Vec3 next = ARENA.crystalPoint(k + 1 == 6 ? 0 : k + 1);
            double a0 = Math.atan2(p.z - ARENA.z(), p.x - ARENA.x());
            double a1 = Math.atan2(next.z - ARENA.z(), next.x - ARENA.x());
            double step = ((Math.toDegrees(a1 - a0) % 360.0) + 360.0) % 360.0;
            assertTrue(Math.abs(step - 60.0) < 1.5, "clockwise 60 degrees, got " + step);
            assertEquals(1.1, p.y - 450, 1e-9, "beams run at chest height");
        }
        assertEquals(new BlockPos(113, 450, -41), ARENA.crystalBase(0), "crystal 0 due east: the 2 by 2 round the corner (114, -40)");
    }

    @Test
    void everyCrystalBlockBelongsToOneCrystal() {
        for (int k = 0; k < 6; k++) {
            BlockPos base = ARENA.crystalBase(k);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    for (int dy = 0; dy < 4; dy++) {
                        assertEquals(k, ARENA.crystalAt(base.offset(dx, dy, dz)));
                    }
                }
            }
            assertEquals(-1, ARENA.crystalAt(base.offset(0, 4, 0)), "4 blocks tall");
        }
        assertEquals(-1, ARENA.crystalAt(ARENA.centreBlock()));
    }

    @Test
    void theArenaHoldsTheFloorAndTheWallButNotTheSky() {
        assertTrue(ARENA.contains(100 + 18.5, 451, -40));
        assertFalse(ARENA.contains(100 + 19.5, 451, -40));
        assertFalse(ARENA.contains(100, 470, -40));
    }

    @Test
    void theWristsHangEitherSideOfTheBody() {
        Vec3 centre = ARENA.centre();
        Vec3 right = ColossusMoves.wrist(centre, 0f, true);
        Vec3 left = ColossusMoves.wrist(centre, 0f, false);
        assertTrue(right.x < centre.x && left.x > centre.x, "facing south, the right arm is on the west side");
        assertEquals(ColossusMoves.WRIST_HEIGHT, right.y - centre.y, 1e-9);
    }

    @Test
    void aSlammingFistRisesOverItsRingAndLandsOnTickTwentyFour() {
        Vec3 wrist = ColossusMoves.wrist(ARENA.centre(), 0f, true);
        Vec3 ring = ARENA.centre().add(4, 0, 12);
        for (int t = ColossusMoves.SLAM_RISE; t < ColossusMoves.SLAM_GLINT; t++) {
            Vec3 p = ColossusMoves.slamFist(t, wrist, ring);
            assertEquals(ring.x, p.x, 1e-9);
            assertEquals(ring.z, p.z, 1e-9);
            assertTrue(p.y - ring.y >= ColossusMoves.HOVER - 1e-9, "hovering over the ring at tick " + t);
        }
        Vec3 landed = ColossusMoves.slamFist(ColossusMoves.SLAM_TELL, wrist, ring);
        assertEquals(ColossusMoves.FIST_REST, landed.y - ring.y, 1e-9);
        assertTrue(ColossusMoves.slamFist(23, wrist, ring).y - ring.y > 1.0, "still in the air a tick before");
        assertEquals(wrist, ColossusMoves.returningFist(ColossusMoves.FIST_RETURN, landed, wrist));
    }

    @Test
    void telegraphsAreNeverShorterThanTwelveTicks() {
        assertTrue(ColossusMoves.SLAM_TELL >= ColossusMoves.MIN_TELEGRAPH);
        assertTrue(ColossusMoves.SLAM_TELL - ColossusMoves.SLAM_TRACK >= ColossusMoves.MIN_TELEGRAPH, "the ring holds still 12 ticks");
        assertTrue(ColossusMoves.SWEEP_TELL >= ColossusMoves.MIN_TELEGRAPH);
        assertTrue(ColossusMoves.REFRACTION_CHARGE >= ColossusMoves.MIN_TELEGRAPH);
        assertTrue(ColossusMoves.BURST_TELL >= ColossusMoves.MIN_TELEGRAPH);
        assertEquals(4, ColossusMoves.SLAM_TELL - ColossusMoves.SLAM_GLINT, "the glint 4 ticks before the fist lands");
        assertEquals(36, ColossusMoves.DOUBLE_SLAM_DELAY + ColossusMoves.SLAM_TELL, "the double slam's second fist lands on tick 36");
    }

    @Test
    void theSweepCrossesTwoHundredDegreesAfterItsTell() {
        assertEquals(-100.0, ColossusMoves.sweepAngle(10), 1e-9);
        assertEquals(-100.0, ColossusMoves.sweepAngle(30), 1e-9);
        assertEquals(100.0, ColossusMoves.sweepAngle(36), 1e-9);
        assertEquals(30, ColossusMoves.sweepTickAt(-100));
        assertEquals(33, ColossusMoves.sweepTickAt(0));
        assertEquals(35, ColossusMoves.sweepTickAt(100));
        Vec3 fist = ColossusMoves.sweepFist(33, Vec3.ZERO, ARENA.centre(), 0f);
        assertEquals(ColossusMoves.SWEEP_FIST_RADIUS, ARENA.distance(fist.x, fist.z), 1e-6);
    }

    @Test
    void theRingCatchesAnyoneReachingIntoIt() {
        Vec3 ring = new Vec3(0, 64, 0);
        assertTrue(Telegraphs.inCircle(player(2.9, 64, 0), ring, 3.0), "the box's edge is inside");
        assertFalse(Telegraphs.inCircle(player(3.4, 64, 0), ring, 3.0));
        assertFalse(Telegraphs.inCircle(player(0, 67, 0), ring, 3.0), "high above it");
    }

    @Test
    void theBandIsTwoHundredDegreesInFrontFromThreeToNine() {
        Vec3 centre = new Vec3(0, 64, 0);
        assertTrue(Telegraphs.inBand(player(0, 64, 6), centre, 3, 9, 0f, 200), "straight ahead (south)");
        assertTrue(Telegraphs.inBand(player(6, 64, 0), centre, 3, 9, 0f, 200), "east: 90 degrees to the left");
        assertFalse(Telegraphs.inBand(player(0, 64, -6), centre, 3, 9, 0f, 200), "behind it");
        assertFalse(Telegraphs.inBand(player(0, 64, 1.5), centre, 3, 9, 0f, 200), "inside 3");
        assertFalse(Telegraphs.inBand(player(0, 64, 10), centre, 3, 9, 0f, 200), "outside 9");
        assertEquals(90.0, Telegraphs.angleFrom(centre, new Vec3(-6, 64, 0), 0f), 1e-9, "west is clockwise of south");
    }

    @Test
    void aBeamTouchesThePlayersItCrosses() {
        Vec3 a = new Vec3(-10, 65.1, 0);
        Vec3 b = new Vec3(10, 65.1, 0);
        assertTrue(Telegraphs.onLine(player(0, 64, 0.5), a, b, 0.45));
        assertFalse(Telegraphs.onLine(player(0, 64, 1.5), a, b, 0.45));
        Vec3 out = Telegraphs.outOfLine(new Vec3(3, 64, 0.2), a, b);
        assertTrue(out.z > 0.99, "pushed out sideways");
    }

    private static AABB player(double x, double y, double z) {
        return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }
}
