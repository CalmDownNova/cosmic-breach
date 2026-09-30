package com.cosmicbreach.guardian.unsung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.unsung.NaveLayout.Kind;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Silent Nave's shape (Unsung design v1): the choir floor the fight expects, the altar, the windows, the dark. */
class NaveLayoutTest {
    private static Map<Kind, Integer> count(NaveLayout l) {
        Map<Kind, Integer> n = new EnumMap<>(Kind.class);
        int[] b = l.bounds();
        for (int x = b[0]; x <= b[3]; x++) {
            for (int z = b[2]; z <= b[5]; z++) {
                for (int y = b[1]; y <= b[4]; y++) {
                    n.merge(l.kind(x, y, z), 1, Integer::sum);
                }
            }
        }
        return n;
    }

    @Test
    void theChoirFloorIsWhereTheFightExpectsIt() {
        for (int facing = 0; facing < 4; facing++) {
            NaveLayout l = new NaveLayout(200, -60, 100, facing, 7L);
            ChoirArena a = l.arena();
            int circles = 0;
            for (int x = a.x() - 20; x <= a.x() + 20; x++) {
                for (int z = a.z() - 20; z <= a.z() + 20; z++) {
                    Kind floor = l.kind(x, a.floorY() - 1, z);
                    if (a.circleColumn(x, z) >= 0) {
                        assertEquals(Kind.CIRCLE, floor, "a circle of silence inlaid at " + x + " " + z);
                        circles++;
                    } else if (a.floorColumn(x, z)) {
                        assertTrue(floor == Kind.FLOOR || floor == Kind.FLOOR_BAND, "the choir floor at " + x + " " + z + ": " + floor);
                    }
                    if (a.daisColumn(x, z)) {
                        Kind dais = l.kind(x, a.floorY(), z);
                        assertTrue(dais == Kind.DAIS || dais == Kind.DAIS_EDGE, "the dais");
                    } else if (a.stepColumn(x, z)) {
                        assertEquals(Kind.STEP, l.kind(x, a.floorY(), z), "its step");
                    } else if (a.floorColumn(x, z)) {
                        for (int h = 0; h < 8; h++) {
                            assertEquals(Kind.AIR, l.kind(x, a.floorY() + h, z), "open over the floor");
                        }
                    }
                }
            }
            assertTrue(circles >= 8 * 12, "eight circles of silence: " + circles + " blocks");
        }
    }

    @Test
    void theHymnalStandsAtTheApsesEntranceOnTheNavesAxis() {
        NaveLayout l = new NaveLayout(0, 0, 90, 1, 3L);
        int[] a = l.altar();
        assertEquals(Kind.ALTAR, l.kind(a[0], a[1], a[2]));
        double[] uv = l.local(a[0], a[2]);
        assertTrue(Math.abs(uv[1]) < 0.6, "on the axis");
        assertTrue(uv[0] > ChoirArena.FLOOR_RADIUS && uv[0] < ChoirArena.FLOOR_RADIUS + 4.0, "just past the choir floor, at the arch");
        // the way in: the nave's floor runs from the door to the apse
        for (double u = NaveLayout.NAVE_START + 6.0; u < NaveLayout.NAVE_END; u += 1.0) {
            int[] w = l.world(u, 0.0);
            Kind k = l.kind(w[0], l.floorY() - 1, w[1]);
            assertTrue(k == Kind.FLOOR || k == Kind.FLOOR_BAND, "the nave's floor at u " + u + ": " + k);
        }
        int[] door = l.world(NaveLayout.NAVE_END + 1.0, 0.0);
        assertEquals(Kind.AIR, l.kind(door[0], l.floorY() + 1, door[1]), "the door is open");
    }

    @Test
    void lichenLightsTheWindowsAndTheApseHasNoOtherLight() {
        NaveLayout l = new NaveLayout(64, 64, 110, 2, 11L);
        Map<Kind, Integer> n = count(l);
        assertTrue(n.getOrDefault(Kind.LICHEN_WINDOW_MAGENTA, 0) > 20 && n.getOrDefault(Kind.LICHEN_WINDOW_TEAL, 0) > 20,
                "the apse's windows, lit from inside, both colours: " + n);
        assertTrue(n.getOrDefault(Kind.NAVE_WINDOW_MAGENTA, 0) + n.getOrDefault(Kind.NAVE_WINDOW_TEAL, 0) > 40, "the nave's lit windows");
        assertTrue(n.getOrDefault(Kind.COLUMN, 0) > 40, "the nave's columns");
        assertTrue(n.getOrDefault(Kind.KEEL, 0) > 1000, "a keel of basalt under it");
        assertTrue(l.lichen().size() >= 16, "Neon Lichen on the nave's windows");
        // lichen windows only in the apse's wall
        ChoirArena a = l.arena();
        int[] b = l.bounds();
        for (int x = b[0]; x <= b[3]; x++) {
            for (int z = b[2]; z <= b[5]; z++) {
                for (int y = a.floorY(); y <= a.floorY() + 12; y++) {
                    Kind k = l.kind(x, y, z);
                    if (k == Kind.LICHEN_WINDOW_MAGENTA || k == Kind.LICHEN_WINDOW_TEAL) {
                        double d = a.distance(x + 0.5, z + 0.5);
                        assertTrue(d > ChoirArena.FLOOR_RADIUS && d <= NaveLayout.APSE_WALL_OUT + 0.5, "in the apse wall, where the fight dims them");
                    }
                }
            }
        }
    }

    @Test
    void itFitsUnderTheShearBand() {
        NaveLayout l = new NaveLayout(0, 0, SilentNaveStructure.MAX_FLOOR, 0, 1L);
        for (int x = -3; x <= 3; x++) {
            assertEquals(Kind.KEEP, l.kind(x, l.floorY() + NaveLayout.ROOF_MAX + 1, 0), "nothing above the dome");
        }
    }

    @Test
    void atTheHighestFloorTheTallestBlockStaysUnder144() {
        for (int facing = 0; facing < 4; facing++) {
            NaveLayout l = new NaveLayout(8, -8, SilentNaveStructure.MAX_FLOOR, facing, 3L);
            int[] b = l.bounds();
            int tallest = Integer.MIN_VALUE;
            for (int x = b[0]; x <= b[3]; x++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    for (int y = b[1]; y <= b[4] + 8; y++) {
                        Kind k = l.kind(x, y, z);
                        if (k != Kind.KEEP && k != Kind.AIR) {
                            tallest = Math.max(tallest, y);
                        }
                    }
                }
            }
            assertTrue(tallest < 144, "the tallest block, out of the Shear band: Y " + tallest);
            // the spires shortened, not gone: each tower still rises over the eaves and ends in a post at the cap
            for (int side = -1; side <= 1; side += 2) {
                int[] t = l.world((NaveLayout.TOWER_U0 + NaveLayout.TOWER_U1) / 2.0, side * (NaveLayout.TOWER_V0 + NaveLayout.TOWER_V1) / 2.0);
                assertEquals(Kind.PINNACLE, l.kind(t[0], NaveLayout.TOP_Y, t[1]), "a spire's post at Y " + NaveLayout.TOP_Y);
                assertEquals(Kind.SPIRE, l.kind(t[0], l.floorY() + l.towerTop() + 1, t[1]), "the spire's foot");
                assertTrue(l.towerTop() > NaveLayout.gable(NaveLayout.NAVE_WALL), "the tower over the eaves");
            }
        }
        // a lower floor keeps the spires at full height
        NaveLayout low = new NaveLayout(8, -8, SilentNaveStructure.MIN_FLOOR, 0, 3L);
        assertEquals(NaveLayout.SPIRE_TOP, low.heightCap());
        assertEquals(NaveLayout.TOWER_TOP, low.towerTop());
    }

    @Test
    void fromOutsideItIsACathedral() {
        for (int facing = 0; facing < 4; facing++) {
            NaveLayout l = new NaveLayout(40, -20, 100, facing, 5L);
            Map<Kind, Integer> n = count(l);
            // a steep gabled roof with a ridge along the nave
            int ridge = 0;
            for (double u = 21.5; u <= NaveLayout.NAVE_END; u += 1.0) {
                for (double v = -0.5; v <= 0.5; v += 1.0) {
                    int[] w = l.world(u, v);
                    ridge += l.kind(w[0], l.floorY() + NaveLayout.GABLE_TOP, w[1]) == Kind.RIDGE ? 1 : 0;
                }
            }
            assertTrue(ridge >= 30, "the ridge, but for a hole or two where the roof fell in: " + ridge);
            int[] eave = l.world(30.5, NaveLayout.NAVE_WALL);
            assertEquals(Kind.ROOF_SLOPE, l.kind(eave[0], l.floorY() + NaveLayout.gable(NaveLayout.NAVE_WALL), eave[1]), "the slope");
            // two west towers, each crowned with a spire's post
            for (int side = -1; side <= 1; side += 2) {
                double v = side * (NaveLayout.TOWER_V0 + NaveLayout.TOWER_V1) / 2.0;
                int[] t = l.world((NaveLayout.TOWER_U0 + NaveLayout.TOWER_U1) / 2.0, v);
                assertEquals(Kind.PINNACLE, l.kind(t[0], l.floorY() + NaveLayout.SPIRE_TOP, t[1]), "a spire's top");
                assertEquals(Kind.SPIRE, l.kind(t[0], l.floorY() + NaveLayout.TOWER_TOP + 1, t[1]), "the spire's foot");
            }
            // a rose window over the open door
            int rose = 0;
            for (double v = -4.5; v <= 4.5; v += 1.0) {
                for (int h = 6; h <= 16; h++) {
                    int[] w = l.world(NaveLayout.DOOR_WALL - 1.0, v);
                    Kind k = l.kind(w[0], l.floorY() + h, w[1]);
                    if (k == Kind.NAVE_WINDOW_MAGENTA || k == Kind.NAVE_WINDOW_TEAL) {
                        rose++;
                    }
                }
            }
            assertTrue(rose >= 30, "the rose's glass: " + rose);
            // a flying buttress outside each column: a pier with a pinnacle, an arch to the wall
            int[] pier = l.world(24.5, NaveLayout.PIER_OUT);
            assertEquals(Kind.WALL, l.kind(pier[0], l.floorY() + 10, pier[1]), "a pier");
            assertEquals(Kind.PINNACLE, l.kind(pier[0], l.floorY() + 16, pier[1]), "its pinnacle");
            int[] arch = l.world(24.5, NaveLayout.NAVE_WALL + 2.0);
            assertEquals(Kind.RIB, l.kind(arch[0], l.floorY() + 13, arch[1]), "its flying arch");
            Kind under = l.kind(arch[0], l.floorY() + 8, arch[1]);
            assertTrue(under == Kind.KEEP || under == Kind.AIR, "open under the arch: " + under);
            // the windows glow from outside: lit lichen glass through the wall
            assertTrue(n.getOrDefault(Kind.NAVE_WINDOW_MAGENTA, 0) > 20 && n.getOrDefault(Kind.NAVE_WINDOW_TEAL, 0) > 20, "both hues: " + n);
            assertTrue(n.getOrDefault(Kind.PINNACLE, 0) >= 20, "pinnacles round the dome and on the piers");
        }
    }
}
