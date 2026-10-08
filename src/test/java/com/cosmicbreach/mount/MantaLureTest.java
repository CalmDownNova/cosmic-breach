package com.cosmicbreach.mount;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a wild stingray hovers when a player holds the taming item: just past the rock's nearest edge (1.1 design 4). */
class MantaLureTest {
    /** A flat round asteroid of radius 6 whose top block is at y 99 (players stand at y 100). */
    private static final MantaLure.Solid DISC = (x, y, z) -> y <= 99 && y >= 94 && Math.hypot(x + 0.5, z + 0.5) <= 6.0;

    private static double reach(Vec3 spot, double px, double pz) {
        // from the player's eye to the near edge of a stingray's box (1.9 wide, 0.8 tall, its feet at spot.y)
        double eyeY = 100 + 1.62;
        double dx = Math.max(0, Math.hypot(spot.x - px, spot.z - pz) - 0.95);
        double dy = Math.max(0, Math.max(spot.y - eyeY, eyeY - (spot.y + 0.8)));
        return Math.hypot(dx, dy);
    }

    @Test
    void fromTheMiddleItPicksTheNearestEdge() {
        Vec3 spot = MantaLure.edgeSpot(DISC, 0, 100, 0);
        assertNotNull(spot);
        double fromAxis = Math.hypot(spot.x, spot.z);
        assertTrue(fromAxis > 6.0 && fromAxis < 9.0, "just past the rim, at " + fromAxis);
    }

    @Test
    void atTheEdgeItIsWithinReach() {
        Vec3 spot = MantaLure.edgeSpot(DISC, 5, 100, 0);
        assertNotNull(spot);
        assertTrue(reach(spot, 5.5, 0.5) < 3.0, "reach " + reach(spot, 5.5, 0.5));
        assertTrue(spot.x > 6.0, "past the edge, over open air");
    }

    @Test
    void noEdgeNearAndNoRoomMeanNoSpot() {
        MantaLure.Solid plain = (x, y, z) -> y <= 99;
        assertNull(MantaLure.edgeSpot(plain, 0, 100, 0), "no edge within eight blocks");
        MantaLure.Solid walled = (x, y, z) -> y <= 99 && Math.hypot(x + 0.5, z + 0.5) <= 6.0 || y >= 100 && y <= 102 && Math.abs(x) >= 7;
        Vec3 spot = MantaLure.edgeSpot(walled, 0, 100, 0);
        assertTrue(spot == null || Math.abs(spot.x) < 7.0, "never inside a wall");
    }

    @Test
    void theSpotsComeNearestTheirPlayerFirstAndTheFirstIsTheEdgeSpot() {
        // a player near the east rim: the east spot is a block or two away and the ones round the rim are farther, and the
        // stingray takes the first of them whose way is open (so one behind a wall goes round to another)
        List<Vec3> spots = MantaLure.edgeSpots(DISC, 5, 100, 0);
        assertTrue(spots.size() > 4, "an edge in several directions: " + spots.size());
        assertEquals(MantaLure.edgeSpot(DISC, 5, 100, 0), spots.get(0), "the nearest is the one edgeSpot gives");
        for (int i = 1; i < spots.size(); i++) {
            double before = Math.hypot(spots.get(i - 1).x - 5.5, spots.get(i - 1).z - 0.5);
            double after = Math.hypot(spots.get(i).x - 5.5, spots.get(i).z - 0.5);
            assertTrue(before <= after + 1e-9, "nearest first at " + i + ": " + before + " then " + after);
        }
        Vec3 last = spots.get(spots.size() - 1);
        double nearest = Math.hypot(spots.get(0).x - 5.5, spots.get(0).z - 0.5);
        double farthest = Math.hypot(last.x - 5.5, last.z - 0.5);
        assertTrue(nearest < 3.0 && farthest > 5.0, "from " + nearest + " to " + farthest + " blocks away");
    }

    @Test
    void fromTheMiddleThereIsASpotInEveryDirection() {
        assertEquals(MantaLure.DIRECTIONS, MantaLure.edgeSpots(DISC, 0, 100, 0).size());
    }

    @Test
    void noEdgeMeansNoSpotsAtAll() {
        assertTrue(MantaLure.edgeSpots((x, y, z) -> y <= 99, 0, 100, 0).isEmpty());
    }
}
