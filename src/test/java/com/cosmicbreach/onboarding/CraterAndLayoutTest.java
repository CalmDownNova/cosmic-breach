package com.cosmicbreach.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.onboarding.StarfallCrater.Cell;
import com.cosmicbreach.onboarding.StarfallCrater.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraterAndLayoutTest {
    @Test
    void theStarfallCraterIsASmallLinedBowlWithTheShardInTheMiddle() {
        for (int r = StarfallCrater.MIN_RADIUS; r <= StarfallCrater.MAX_RADIUS; r++) {
            List<Cell> cells = StarfallCrater.cells(r);
            Map<String, Integer> lining = new HashMap<>();
            int shards = 0;
            for (Cell c : cells) {
                assertTrue(Math.abs(c.dx()) <= r && Math.abs(c.dz()) <= r, "cell outside radius " + r);
                if (c.kind() == Kind.LINING) {
                    assertEquals(null, lining.put(c.dx() + "," + c.dz(), c.dy()), "one lining cell per column");
                }
                if (c.kind() == Kind.SHARD) {
                    shards++;
                    assertEquals(0, c.dx());
                    assertEquals(0, c.dz());
                }
            }
            assertEquals(1, shards);
            // every dug cell of a column is above its lining
            for (Cell c : cells) {
                if (c.kind() != Kind.LINING) {
                    assertTrue(c.dy() > lining.get(c.dx() + "," + c.dz()));
                }
            }
            // deepest in the middle: radius 2 digs 1, radius 3 digs 2
            assertEquals(-(r - 1), (int) lining.get("0,0"));
            Cell shard = StarfallCrater.shard(r);
            assertEquals(lining.get("0,0") + 1, shard.dy(), "the shard stands on the lining");
            // the rim is lined at ground level
            assertEquals(0, (int) lining.get(r + ",0"));
        }
    }

    @Test
    void theLandingIsA6By6PlatformRoundA4By4RingAndTheArrivalLandsOnTheBricks() {
        int[] origin = LandingLayout.originFor(300, -120);
        // the arrival column is on the bricks, two blocks from the hole
        assertEquals(LandingLayout.Part.BRICK, LandingLayout.partAt(300 - origin[0], -120 - origin[1]));
        assertEquals(LandingLayout.Part.FRAME, LandingLayout.partAt(300 - origin[0] + 1, -120 - origin[1]));
        assertEquals(LandingLayout.Part.BREACH, LandingLayout.partAt(300 - origin[0] + 2, -120 - origin[1]));
        int frames = 0;
        int bricks = 0;
        int breach = 0;
        for (int dx = -4; dx <= 5; dx++) {
            for (int dz = -4; dz <= 5; dz++) {
                switch (LandingLayout.partAt(dx, dz)) {
                    case FRAME -> frames++;
                    case BRICK -> bricks++;
                    case BREACH -> breach++;
                    default -> {
                    }
                }
            }
        }
        assertEquals(12, frames);
        assertEquals(20, bricks);
        assertEquals(4, breach);
        // the frames are exactly the ring's, the Breach exactly its middle
        for (int[] o : BreachRing.RING) {
            assertEquals(LandingLayout.Part.FRAME, LandingLayout.partAt(o[0], o[1]));
        }
        for (int[] m : BreachRing.MIDDLE) {
            assertEquals(LandingLayout.Part.BREACH, LandingLayout.partAt(m[0], m[1]));
        }
        assertEquals(LandingLayout.Part.NONE, LandingLayout.partAt(4, 0));
        assertEquals(LandingLayout.Part.NONE, LandingLayout.partAt(0, -3));
        int[] back = LandingLayout.arrivalFor(origin[0], origin[1]);
        assertEquals(300, back[0]);
        assertEquals(-120, back[1]);
    }

    @Test
    void aFallenRiftKeepsEightOfTwelveFramesAndARadiusOf8To12() {
        for (long seed = -500; seed < 500; seed++) {
            int r = FallenRiftLayout.radius(seed);
            assertTrue(r >= 8 && r <= 12);
            int present = 0;
            for (boolean b : FallenRiftLayout.framesPresent(seed)) {
                present += b ? 1 : 0;
            }
            assertEquals(8, present);
            // one craft (6 frames) finishes it
            assertTrue(BreachRing.RING.length - present <= 6);
            int[] chest = FallenRiftLayout.chest(seed);
            // the chest lies outside the ring's border and the flat floor round it
            assertFalse(FallenRiftLayout.flat(chest[0], chest[1]));
            assertTrue(Math.max(Math.abs(chest[0] - 0.5), Math.abs(chest[1] - 0.5)) <= 4.0);
        }
        // the floor is flat at the ring's level all round the ring
        for (int[] o : BreachRing.RING) {
            assertEquals(-FallenRiftLayout.depth(10), FallenRiftLayout.floorAt(10, o[0], o[1]));
        }
        // the same seed draws the same rift in every chunk
        assertEquals(FallenRiftLayout.radius(42), FallenRiftLayout.radius(42));
        assertEquals(FallenRiftLayout.noise(9, 1, 2, 3), FallenRiftLayout.noise(9, 1, 2, 3));
    }

    @Test
    void theRiftBowlIsDeepestInTheMiddleWithALipPastTheRim() {
        int r = 10;
        assertEquals(-FallenRiftLayout.depth(r), FallenRiftLayout.floorOffset(r, 0));
        assertEquals(0, FallenRiftLayout.floorOffset(r, r));
        assertEquals(1, FallenRiftLayout.floorOffset(r, r + 0.8));
        assertEquals(Integer.MIN_VALUE, FallenRiftLayout.floorOffset(r, r + 3));
        for (double d = 0; d < r; d += 0.5) {
            assertTrue(FallenRiftLayout.floorOffset(r, d) <= FallenRiftLayout.floorOffset(r, d + 0.5));
        }
    }
}
