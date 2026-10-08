package com.cosmicbreach.shrine;

import com.cosmicbreach.lift.AscentCurrent;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the current beside a shrine stands (1.1 design section 5), in a made-up world: an island (radius 9, rock from
 * Y 330 to 340, the shrine 3 in from its rim) over a gap (Y 300 to 319) and an asteroid below (radius 4, Y 250).
 */
class ShrineCurrentsTest {
    private static final BlockPos SHRINE = new BlockPos(6, 341, 0);
    private static final Vec3 LANDING = new Vec3(5.5, 341.0, 1.5);
    /** The spots beside the shrine, in the order a player is set down at them: in front of it, then to its two sides. */
    private static final List<Vec3> LANDINGS = List.of(LANDING, new Vec3(6.5, 341.0, 1.5), new Vec3(6.5, 341.0, -0.5));

    private static boolean island(int x, int y, int z) {
        return y >= 330 && y <= 340 && Math.hypot(x + 0.5, z + 0.5) <= 9.0;
    }

    private static boolean asteroid(int x, int y, int z) {
        return y == 250 && Math.hypot(x + 0.5 - 13.0, z + 0.5) <= 4.0;
    }

    @Test
    void itStandsOffTheIslandOverTheAsteroidWithAClearWayToTheShrine() {
        ShrineCurrents.Solid world = (x, y, z) -> island(x, y, z) || asteroid(x, y, z);
        Optional<AscentCurrent.Current> c = ShrineCurrents.find(world, SHRINE, LANDINGS, 300, 200, Double.NaN, Double.NaN, 0.0);
        assertTrue(c.isPresent());
        AscentCurrent.Current cur = c.get();
        assertEquals(251.0, cur.bottom(), 1e-9, "its foot stands on the asteroid");
        assertEquals(341.0, cur.top(), 1e-9);
        assertTrue(Math.hypot(cur.x(), cur.z()) > 10.0, "off the island with room");
        double d = Math.hypot(cur.x() - (SHRINE.getX() + 0.5), cur.z() - (SHRINE.getZ() + 0.5));
        assertTrue(d >= ShrineCurrents.NEAR && d <= ShrineCurrents.FAR + 1.0, "beside the shrine: " + d);
        assertTrue(LANDINGS.stream().anyMatch(l -> l.x == cur.landX() && l.z == cur.landZ()), "it lands at one of the spots beside the shrine");
        assertTrue(cur.landingClear(), "the landing is never inside the current");
    }

    @Test
    void whenTheOnlyOpenAirIsBehindTheShrineItLandsBesideItNotInFrontOfIt() {
        // the shrine faces the island's middle (west), the drop is behind it (east): a rider could not cross over the shrine to its front
        ShrineCurrents.Solid world = (x, y, z) -> island(x, y, z) || asteroid(x, y, z);
        Optional<AscentCurrent.Current> c = ShrineCurrents.find(world, SHRINE, List.of(new Vec3(5.5, 341.0, 0.5), new Vec3(6.5, 341.0, 1.5)), 300, 200,
                Double.NaN, Double.NaN, 0.0);
        assertTrue(c.isPresent());
        assertEquals(6.5, c.get().landX(), 1e-9, "the side spot, not the one in front of the shrine");
        assertEquals(1.5, c.get().landZ(), 1e-9);
        assertTrue(c.get().landingClear());
    }

    @Test
    void theLandingIsNeverInsideTheCurrentEvenWhenTheNearestColumnWouldBe() {
        // open sky all round: three blocks west of the shrine would be the nearest column, and it is within two and a half of the landing
        Optional<AscentCurrent.Current> c = ShrineCurrents.find((x, y, z) -> false, SHRINE, LANDINGS, 300, 200, Double.NaN, Double.NaN, 0.0);
        assertTrue(c.isPresent());
        assertTrue(c.get().landingClear(), "landing " + LANDING + " and current " + c.get());
    }

    @Test
    void withNothingBelowItReachesDownToItsLowest() {
        Optional<AscentCurrent.Current> c = ShrineCurrents.find(ShrineCurrentsTest::island, SHRINE, LANDINGS, 300, 200, Double.NaN, Double.NaN, 0.0);
        assertTrue(c.isPresent());
        assertEquals(200.0, c.get().bottom(), 1e-9);
    }

    @Test
    void itStaysOutOfAKeptOutSpace() {
        ShrineCurrents.Solid world = (x, y, z) -> island(x, y, z) || asteroid(x, y, z);
        Optional<AscentCurrent.Current> c = ShrineCurrents.find(world, SHRINE, LANDINGS, 300, 200, 14.0, 0.0, 6.0);
        assertTrue(c.isPresent());
        assertTrue(Math.hypot(c.get().x() - 14.0, c.get().z()) >= 6.0);
    }

    @Test
    void aColumnBlockedAboveTheGapIsNeverChosen() {
        ShrineCurrents.Solid roofed = (x, y, z) -> island(x, y, z) || y == 310;
        assertTrue(ShrineCurrents.find(roofed, SHRINE, LANDINGS, 300, 200, Double.NaN, Double.NaN, 0.0).isEmpty());
    }

    @Test
    void aLowCeilingFourBlocksOverTheLandingStopsTheCrossingAndOneBlockOffTheShrinesFloorIsMeasuredFromTheLanding() {
        // a rider crossing is up to 4.3 above the floor: a roof at 4 over the landing's floor is in the way
        ShrineCurrents.Solid roofed = (x, y, z) -> island(x, y, z) || (y == 345 && Math.hypot(x - 6.0, z) <= 20.0);
        assertTrue(ShrineCurrents.find(roofed, SHRINE, LANDINGS, 300, 200, Double.NaN, Double.NaN, 0.0).isEmpty());
        // and a landing one block above the shrine's own floor is measured from its own height: the same roof at 345 is 3 over it, in the way too
        List<Vec3> raised = List.of(new Vec3(5.5, 342.0, 1.5), new Vec3(6.5, 342.0, 1.5));
        ShrineCurrents.Solid roof346 = (x, y, z) -> island(x, y, z) || (y == 346 && Math.hypot(x - 6.0, z) <= 20.0);
        assertTrue(ShrineCurrents.find(roof346, SHRINE, raised, 300, 200, Double.NaN, Double.NaN, 0.0).isEmpty(), "4 over a landing at 342");
    }

    @Test
    void aColumnWhoseWayAcrossToTheLandingIsRoofedIsNeverChosen() {
        // a ceiling over the whole upper level, 2 above the floor: nobody could be carried across to the shrine
        ShrineCurrents.Solid ceiling = (x, y, z) -> island(x, y, z) || (y == 343 && Math.hypot(x - 6.0, z) <= 20.0);
        assertTrue(ShrineCurrents.find(ceiling, SHRINE, LANDINGS, 300, 200, Double.NaN, Double.NaN, 0.0).isEmpty());
    }
}
