package com.cosmicbreach.structure.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.structure.crypt.CryptLayout.Kind;
import org.junit.jupiter.api.Test;

/** GDD 6.1's labyrinth: 10,000 crypts, each connected, 12 to 25 rooms over 2 or 3 levels, with every trap. */
class CryptLayoutTest {
    @Test
    void tenThousandCryptsKeepEveryRule() {
        long t0 = System.nanoTime();
        int[] byLevels = new int[4];
        int minRooms = 99;
        int maxRooms = 0;
        int maxAttempts = 0;
        long attempts = 0;
        for (long seed = 0; seed < 10_000; seed++) {
            CryptLayout c = CryptLayout.generate(seed * 104_729L + 3);
            assertNull(c.check(), "seed " + seed);
            assertTrue(c.connected(), "seed " + seed);
            assertTrue(c.levels == 2 || c.levels == 3);
            int rooms = c.rooms();
            assertTrue(rooms >= CryptLayout.MIN_ROOMS && rooms <= CryptLayout.MAX_ROOMS, "rooms " + rooms);
            for (Kind k : new Kind[] {Kind.RIFT, Kind.POCKET, Kind.GRAVITY, Kind.CHUTES, Kind.TRIPWIRE}) {
                assertTrue(c.count(k) >= 1, k + " in seed " + seed);
            }
            assertEquals(1, c.count(Kind.ENTRANCE));
            assertEquals(c.levels - 1, c.count(Kind.STAIR_DOWN));
            byLevels[c.levels]++;
            minRooms = Math.min(minRooms, rooms);
            maxRooms = Math.max(maxRooms, rooms);
            maxAttempts = Math.max(maxAttempts, c.attempts);
            attempts += c.attempts;
        }
        long micros = (System.nanoTime() - t0) / 1000;
        System.out.printf("crypt layouts: 10000 in %d ms (%.0f us each), levels 2: %d, 3: %d, rooms %d to %d, attempts mean %.1f max %d%n",
                micros / 1000, micros / 10_000.0, byLevels[2], byLevels[3], minRooms, maxRooms, attempts / 10_000.0, maxAttempts);
        assertTrue(byLevels[2] > 1000 && byLevels[3] > 1000, "both 2 and 3 levels occur");
    }

    @Test
    void oneSeedOneCrypt() {
        CryptLayout a = CryptLayout.generate(99L);
        CryptLayout b = CryptLayout.generate(99L);
        assertEquals(a.levels, b.levels);
        for (int l = 0; l < a.levels; l++) {
            for (int x = 0; x < CryptLayout.GRID; x++) {
                for (int z = 0; z < CryptLayout.GRID; z++) {
                    assertEquals(a.kind(l, x, z), b.kind(l, x, z));
                    for (int s = 0; s < 4; s++) {
                        assertEquals(a.door(l, x, z, s), b.door(l, x, z, s));
                    }
                }
            }
        }
    }

    @Test
    void theMainPathRunsFromTheEntranceToTheChoirFloorThroughEveryTrap() {
        for (long seed = 0; seed < 500; seed++) {
            CryptLayout c = CryptLayout.generate(seed);
            var path = c.mainPath();
            int[] first = path.get(0);
            assertEquals(0, first[0]);
            assertEquals(Kind.ENTRANCE, c.kind(0, first[1], first[2]));
            int[] last = path.get(path.size() - 1);
            assertEquals(c.bottom(), last[0]);
            boolean besideChoir = false;
            for (int s = 0; s < 4; s++) {
                int nx = last[1] + CryptLayout.DX[s];
                int nz = last[2] + CryptLayout.DZ[s];
                besideChoir |= c.door(last[0], last[1], last[2], s) && c.kind(last[0], nx, nz) == Kind.CHOIR;
            }
            assertTrue(besideChoir, "the path ends at the Choir Floor's door");
            int traps = 0;
            for (int[] p : path) {
                Kind k = c.kind(p[0], p[1], p[2]);
                traps |= k == Kind.RIFT ? 1 : k == Kind.GRAVITY ? 2 : k == Kind.CHUTES ? 4 : k == Kind.TRIPWIRE ? 8 : 0;
            }
            assertEquals(15, traps, "every kind of trap lies on the way to the vault");
        }
    }
}
