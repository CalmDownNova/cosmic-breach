package com.cosmicbreach.structure.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.structure.lens.Lens;
import com.cosmicbreach.structure.lens.LensDifficulty;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * The two structures' plans fit their puzzles (GDD 6.1): the Reliquary's 3 to 5 chambers and ramp, its 5 by 5
 * Lens room inside the top chamber with the vault clear of the sockets; the Observatory's 5 to 8 floors, the
 * lifts on opposite sides of each tripwire, the last lift arriving in the walkway round the 7 by 7 grid.
 */
class StructureLayoutTest {
    /** Every socket of an n by n grid centred on the room, in blocks from the centre. */
    private static Set<Long> sockets(int n) {
        Set<Long> out = new HashSet<>();
        int c = (n - 1) / 2;
        for (int p = 0; p < 4 * n; p++) {
            out.add(key(2 * (Lens.portX(n, p) - c), 2 * (Lens.portZ(n, p) - c)));
        }
        return out;
    }

    private static Set<Long> pedestals(int n) {
        Set<Long> out = new HashSet<>();
        int c = (n - 1) / 2;
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                out.add(key(2 * (x - c), 2 * (z - c)));
            }
        }
        return out;
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ (z & 0xFFFFFFFFL);
    }

    private static double farthest(Set<Long> points) {
        double best = 0;
        for (long k : points) {
            best = Math.max(best, Math.hypot((int) (k >> 32), (int) k));
        }
        return best;
    }

    @Test
    void reliquaryPlans() {
        Set<Integer> chambers = new HashSet<>();
        Set<String> difficulties = new HashSet<>();
        for (long seed = 0; seed < 5000; seed++) {
            ReliquaryLayout plan = ReliquaryLayout.of(seed);
            chambers.add(plan.chambers());
            difficulties.add(plan.difficulty().name());
            assertTrue(plan.chambers() >= 3 && plan.chambers() <= 5);
            assertEquals(5, plan.difficulty().size());
            assertEquals(plan.chambers() - 1, plan.lensChamber());
            for (int k = 0; k < plan.chambers() - 2; k++) {
                assertEquals(plan.ramp(k + 1, 0.0), plan.ramp(k, 2 * Math.PI), 1e-9, "the ramp is one climb");
            }
            assertEquals(ReliquaryLayout.STOREY * plan.lensChamber(), plan.ramp(plan.chambers() - 2, 2 * Math.PI), 1e-9,
                    "the ramp ends at the lens room's floor");
            assertTrue(plan.height() <= 62, "the spire stays well under the Reach's ceiling from its highest island");
            for (int c = 1; c < plan.chambers() - 1; c++) {
                assertTrue(plan.packs()[c] >= 3 && plan.packs()[c] <= 5, "a pack of 3 to 5 in each guard hall");
            }
        }
        assertEquals(Set.of(3, 4, 5), chambers);
        assertEquals(Set.of(LensDifficulty.EASY_5.name(), LensDifficulty.MEDIUM_5.name()), difficulties);
        // the 5 by 5 room fits the top chamber with a walkway round its sockets, and the vault misses them
        Set<Long> sockets = sockets(5);
        assertTrue(farthest(sockets) + 1.0 <= ReliquaryLayout.CHAMBER, "sockets " + farthest(sockets));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            long vault = key(d.getStepX() * 8, d.getStepZ() * 8);
            assertTrue(!sockets.contains(vault) && !pedestals(5).contains(vault));
            assertTrue(8 <= ReliquaryLayout.CHAMBER);
        }
    }

    @Test
    void observatoryPlans() {
        Set<Integer> floors = new HashSet<>();
        Set<String> difficulties = new HashSet<>();
        Set<Long> sockets = sockets(7);
        Set<Long> pedestals = pedestals(7);
        assertTrue(farthest(sockets) + 1.0 <= ObservatoryLayout.CHAMBER_INSIDE, "the 7 by 7 room fits the telescope chamber");
        for (long seed = 0; seed < 3000; seed++) {
            for (int radius = 9; radius <= 16; radius += 7) {
                for (int headroom : new int[] {radius + 60, radius + 80, 200}) {
                    ObservatoryLayout plan = ObservatoryLayout.of(seed, radius, headroom);
                    floors.add(plan.floors());
                    difficulties.add(plan.difficulty().name());
                    assertTrue(plan.floors() >= 5 && plan.floors() <= 8);
                    assertEquals(7, plan.difficulty().size());
                    if (headroom >= radius + 20 + 7 * 5) {
                        assertTrue(plan.height() <= headroom, "the dome fits under the Shear band: " + plan);
                    }
                    for (int k = 0; k < plan.floors() - 1; k++) {
                        if (k > 0) {
                            assertEquals(plan.liftSide(k - 1).getOpposite(), plan.liftSide(k),
                                    "each floor's lifts are on opposite sides of its tripwire");
                        }
                        Direction side = plan.liftSide(k);
                        Direction across = plan.lift().getClockWise();
                        assertNotEquals(side.getAxis(), across.getAxis(), "the tripwire runs across the lift axis");
                        assertTrue(plan.liftRadius(k) <= ObservatoryLayout.TOWER_INSIDE - 0.5);
                    }
                    Direction last = plan.liftSide(plan.floors() - 2);
                    int ax = plan.liftX(plan.floors() - 2);
                    int az = plan.liftZ(plan.floors() - 2);
                    long arrival = key(ax, az);
                    assertTrue(!sockets.contains(arrival) && !pedestals.contains(arrival), "the last lift arrives in the walkway");
                    assertTrue(Math.hypot(ax, az) <= ObservatoryLayout.TOWER_INSIDE - 0.5, "the last lift rises inside the tower");
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        assertTrue(!pedestals.contains(key(ax + d.getStepX(), az + d.getStepZ())),
                                "the last lift's column is not beside a pedestal (a player turning it would stand in the updraft)");
                    }
                    long vault = key(-last.getStepX() * 11, -last.getStepZ() * 11);
                    assertTrue(!sockets.contains(vault) && 11 <= ObservatoryLayout.CHAMBER_INSIDE);
                }
            }
        }
        assertEquals(Set.of(5, 6, 7, 8), floors);
        assertEquals(Set.of(LensDifficulty.MEDIUM_7.name(), LensDifficulty.HARD_7.name()), difficulties);
    }
}
