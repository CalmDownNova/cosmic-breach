package com.cosmicbreach.sandbox;

import com.cosmicbreach.sandbox.ArenaLayout.Kind;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaLayoutTest {
    private static final int R = ArenaLayout.RADIUS;

    @Test
    void aFloorDiscOfRadius22() {
        assertTrue(floor(ArenaLayout.at(0, 0, 0)));
        assertTrue(floor(ArenaLayout.at(R, 0, 0)));
        assertTrue(floor(ArenaLayout.at(0, 0, -R)));
        assertEquals(Kind.KEEP, ArenaLayout.at(R + 1, 0, 0));
        assertEquals(Kind.KEEP, ArenaLayout.at(16, 0, 16), "the corner of the square is outside the disc");
        int floor = 0;
        for (int x = -R - 2; x <= R + 2; x++) {
            for (int z = -R - 2; z <= R + 2; z++) {
                if (floor(ArenaLayout.at(x, 0, z))) {
                    floor++;
                }
            }
        }
        double area = Math.PI * (R + 0.5) * (R + 0.5);
        assertTrue(Math.abs(floor - area) < area * 0.03, floor + " floor blocks for a disc of about " + area);
    }

    @Test
    void mostlyAFieldWithASmoothQuartzPattern() {
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                counts.merge(ArenaLayout.at(x, 0, z), 1, Integer::sum);
            }
        }
        int field = counts.getOrDefault(Kind.FLOOR, 0);
        int quartz = counts.getOrDefault(Kind.PATTERN, 0);
        assertTrue(quartz > 100 && field > 3 * quartz, "field " + field + ", smooth quartz " + quartz);
    }

    @Test
    void theMiddleIsOpenAndTheAirAboveIsCleared() {
        for (int y = 1; y <= ArenaLayout.CLEAR_HEIGHT; y++) {
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    assertEquals(Kind.AIR, ArenaLayout.at(x, y, z), "at " + x + " " + y + " " + z);
                }
            }
        }
        assertEquals(Kind.KEEP, ArenaLayout.at(0, ArenaLayout.CLEAR_HEIGHT + 1, 0));
        assertEquals(Kind.KEEP, ArenaLayout.at(0, -1, 0));
    }

    @Test
    void aRimWithLanternsRoundTheEdge() {
        int lanterns = 0;
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                if (ArenaLayout.at(x, 2, z) == Kind.LANTERN) {
                    lanterns++;
                    assertEquals(Kind.RIM, ArenaLayout.at(x, 1, z), "a lantern stands on the rim");
                }
            }
        }
        assertEquals((int) (360 / ArenaLayout.LANTERN_EVERY), lanterns);
        assertEquals(Kind.RIM, ArenaLayout.at(R, 1, 0));
        assertEquals(Kind.AIR, ArenaLayout.at(R - 1, 1, 0), "only the edge is walled");
    }

    @Test
    void fiveAmethystPillarsForCoverWellAwayFromTheMiddleAndTheEdge() {
        int columns = 0;
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (ArenaLayout.at(x, 1, z) == Kind.PILLAR) {
                    columns++;
                    double r = Math.hypot(x, z);
                    assertTrue(r > 9 && r < 16, "a pillar column " + r + " from the middle");
                }
            }
        }
        assertEquals(ArenaLayout.PILLARS * 4, columns, "five 2 by 2 pillars");
        for (int i = 0; i < ArenaLayout.PILLARS; i++) {
            int[] c = ArenaLayout.pillarCorner(i);
            int h = ArenaLayout.pillarHeight(i);
            assertTrue(h >= 3, "tall enough to hide behind");
            assertEquals(Kind.PILLAR, ArenaLayout.at(c[0], h, c[1]));
            assertEquals(Kind.CLUSTER, ArenaLayout.at(c[0], h + 1, c[1]));
            // Nothing stands on the lines along the axes to the middle, where packs run in.
            for (int x = c[0]; x <= c[0] + 1; x++) {
                for (int z = c[1]; z <= c[1] + 1; z++) {
                    assertTrue(x != 0 && z != 0, "pillar " + i + " has a column on an axis at " + x + " " + z);
                }
            }
        }
    }

    private static boolean floor(Kind kind) {
        return kind == Kind.FLOOR || kind == Kind.PATTERN;
    }
}
