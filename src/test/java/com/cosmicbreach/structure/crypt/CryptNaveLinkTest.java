package com.cosmicbreach.structure.crypt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.unsung.NaveLayout;
import com.cosmicbreach.guardian.unsung.SilentNaveStructure;
import com.cosmicbreach.structure.sanctum.SanctumSite;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/**
 * A crypt and the Silent Nave may share a pillar (a chance kept on purpose), but the nave, built after the crypt, must
 * never bury the crypt's rooms or seal its way in, and the crypt must not seal the nave's door. The walk is searched
 * over a block model of both structures, and checked against the same model with the rule switched off, to show the
 * test can fail.
 */
class CryptNaveLinkTest {
    private static final int PILLAR = 0;
    private static final int FLOOR_Y = 100;
    private static final double PLATFORM_R = 12.0;

    /** A nave and a crypt on one pillar the way worldgen sets them: the nave's door at the pillar's middle. */
    private record Scene(NaveLayout nave, BlockPos origin, CryptLayout plan, CryptNaveLink link) {
        int roofY() {
            return origin.getY() + 8;
        }

        boolean inBox(int x, int z) {
            return x >= origin.getX() && x < origin.getX() + CryptLayout.SIZE && z >= origin.getZ() && z < origin.getZ() + CryptLayout.SIZE;
        }

        /** True if the block is solid (a body cannot stand in it), with the rule on (the nave's builder) or off (as it was). */
        boolean solid(int x, int y, int z, boolean rule) {
            NaveLayout.Kind k = nave.kind(x, y, z);
            if (rule) {
                if (link.spares(x, y, z)) {
                    return true; // the crypt's roof and rooms
                }
                if (link.carves(x, y, z)) {
                    return false;
                }
                int[] g = link.gatehouseBox();
                boolean inGateVolume = x >= g[0] && x <= g[3] && z >= g[2] && z <= g[5] && y >= g[1] && y <= g[4];
                if (inGateVolume && k == NaveLayout.Kind.KEEP) {
                    return false; // the gatehouse is not built; its room is cleared
                }
            }
            if (k == NaveLayout.Kind.AIR) {
                return false;
            }
            if (k != NaveLayout.Kind.KEEP) {
                return true;
            }
            boolean onPlatform = Math.hypot(x + 0.5 - PILLAR, z + 0.5 - PILLAR) <= PLATFORM_R;
            return y <= roofY() && (inBox(x, z) || onPlatform || rule && link.laneFloor(x, z));
        }

        boolean stand(int x, int y, int z, boolean rule) {
            return !solid(x, y, z, rule) && !solid(x, y + 1, z, rule) && solid(x, y - 1, z, rule);
        }

        /** Walks from the nave's landing to the top of the crypt's stair; true if there is a way. */
        boolean reachesStair(boolean rule) {
            BlockPos top = link.top();
            return walk(rule, new int[] {top.getX(), top.getZ()});
        }

        /** Walks from the nave's landing in through its door to the far end of the hall (the arch to the choir). */
        boolean reachesChoirArch(boolean rule) {
            return walk(rule, nave.world(NaveLayout.NAVE_START + 3.0, 2.0));
        }

        boolean walk(boolean rule, int[] goal) {
            int[] start = nave.world(NaveLayout.DOOR_WALL + 1.5, 0.0);
            int y0 = roofY() + 1;
            if (!stand(start[0], y0, start[1], rule) || !stand(goal[0], y0, goal[1], rule)) {
                return false;
            }
            int[] b = nave.bounds();
            int minX = Math.min(b[0], origin.getX()) - 2;
            int maxX = Math.max(b[3], origin.getX() + CryptLayout.SIZE) + 2;
            int minZ = Math.min(b[2], origin.getZ()) - 2;
            int maxZ = Math.max(b[5], origin.getZ() + CryptLayout.SIZE) + 2;
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[] {start[0], y0, start[1]});
            seen.add(key(start[0], y0, start[1]));
            while (!queue.isEmpty()) {
                int[] p = queue.poll();
                if (p[0] == goal[0] && p[2] == goal[1]) {
                    return true;
                }
                for (int s = 0; s < 4; s++) {
                    int nx = p[0] + CryptLayout.DX[s];
                    int nz = p[2] + CryptLayout.DZ[s];
                    if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                        continue;
                    }
                    for (int dy = 1; dy >= -3; dy--) {
                        int ny = p[1] + dy;
                        if (dy == 1 && solid(p[0], p[1] + 2, p[2], rule)) {
                            continue; // no headroom to step up
                        }
                        if (stand(nx, ny, nz, rule) && seen.add(key(nx, ny, nz))) {
                            queue.add(new int[] {nx, ny, nz});
                            break;
                        }
                    }
                }
            }
            return false;
        }

        private static long key(int x, int y, int z) {
            return (((long) x + 4096) * 8192L + (z + 4096)) * 512L + y;
        }
    }

    private static Scene scene(int facing, long cryptSeed) {
        int[] a = new NaveLayout(0, 0, 0, facing, 0L).axis();
        NaveLayout nave = new NaveLayout((int) Math.round(PILLAR - a[0] * SilentNaveStructure.DOOR_BACK),
                (int) Math.round(PILLAR - a[1] * SilentNaveStructure.DOOR_BACK), FLOOR_Y, facing, 11L);
        CryptLayout plan = CryptLayout.generate(cryptSeed);
        BlockPos origin = new BlockPos(PILLAR - CryptLayout.SIZE / 2, FLOOR_Y - 1 - 8, PILLAR - CryptLayout.SIZE / 2);
        return new Scene(nave, origin, plan, CryptNaveLink.of(nave, origin, plan));
    }

    @Test
    void theWayInIsAlwaysOpenWhenACryptSharesTheNavesPillar() {
        int sealedWithoutRule = 0;
        int runs = 0;
        int longest = 0;
        for (int facing = 0; facing < 4; facing++) {
            for (long seed = 1; seed <= 60; seed++) {
                Scene s = scene(facing, seed * 7919L);
                runs++;
                String at = " (facing " + facing + ", seed " + seed + ", entrance " + s.plan().entranceX + "," + s.plan().entranceZ + ")";
                assertTrue(CryptNaveLink.overlaps(s.nave(), s.origin()), "the crypt's box meets the nave's" + at);
                assertTrue(s.link().reachesOpen(), "the lane comes into the open" + at);
                assertTrue(s.reachesStair(true), "a way from the nave's landing to the top of the crypt's stair" + at);
                assertTrue(s.reachesChoirArch(true), "the nave's door and hall stay open" + at);
                longest = Math.max(longest, s.link().laneColumns().size() / 3);
                if (!s.reachesStair(false)) {
                    sealedWithoutRule++;
                }
            }
        }
        System.out.printf("crypt under nave: %d runs, %d sealed without the rule, longest lane %d%n", runs, sealedWithoutRule, longest);
        assertTrue(sealedWithoutRule > 0, "the model must be able to show a sealed entrance, or the test proves nothing");
    }

    @Test
    void theNaveLeavesTheCryptsRoomsAndStairAlone() {
        Scene s = scene(1, 12345L);
        int[] b = s.nave().bounds();
        int spared = 0;
        int minY = s.origin().getY() - 8 * (s.plan().levels - 1) - 4;
        for (int x = s.origin().getX(); x < s.origin().getX() + CryptLayout.SIZE; x++) {
            for (int z = s.origin().getZ(); z < s.origin().getZ() + CryptLayout.SIZE; z++) {
                for (int y = Math.max(b[1], minY); y < s.roofY(); y++) {
                    assertTrue(s.link().spares(x, y, z), "the crypt's volume is spared at " + x + " " + y + " " + z);
                    spared++;
                }
            }
        }
        assertTrue(spared > 0);
        BlockPos top = s.link().top();
        assertTrue(s.link().spares(top.getX(), s.roofY(), top.getZ()), "the stair's top step is spared");
        assertTrue(s.link().carves(top.getX(), s.roofY() + 1, top.getZ()), "air over the stair's top");
        assertTrue(s.link().carves(top.getX(), s.roofY() + CryptNaveLink.HEAD, top.getZ()), "the lane is " + CryptNaveLink.HEAD + " high");
        assertFalse(s.link().carves(top.getX(), s.roofY() + CryptNaveLink.HEAD + 1, top.getZ()), "and no higher");
        assertFalse(s.link().spares(s.origin().getX() - 5, s.roofY() - 1, s.origin().getZ() - 5), "outside the crypt is the nave's");
    }

    @Test
    void noDeepStructureSitsOnTheSanctumsPlatform() {
        int n = 0;
        int near = 0;
        for (long salt = 1; salt <= 3; salt++) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt * 0x9E3779B97F4A7C15L);
            SanctumSite sanctum = SanctumSite.of(t);
            for (int rx = -10; rx < 10; rx++) {
                for (int rz = -10; rz < 10; rz++) {
                    int x = rx * 640 + 320;
                    int z = rz * 640 + 320;
                    Optional<HollowCryptStructure.Site> crypt = HollowCryptStructure.site(t, x, z);
                    if (crypt.isPresent()) {
                        n++;
                        double d = Math.hypot(crypt.get().gate().getX() - sanctum.endX, crypt.get().gate().getZ() - sanctum.endZ);
                        assertTrue(d > 12.0, "a crypt's gate is " + d + " blocks from the sanctum's causeway end");
                        if (d < 80.0) {
                            near++;
                        }
                    }
                    Optional<NaveLayout> nave = SilentNaveStructure.site(t, salt, new ChunkPos(x >> 4, z >> 4));
                    if (nave.isPresent()) {
                        n++;
                        int[] door = nave.get().world(NaveLayout.DOOR_WALL - 0.5, 0.0);
                        assertTrue(Math.hypot(door[0] - sanctum.endX, door[1] - sanctum.endZ) > 8.0, "a nave's door is clear of the sanctum's causeway end");
                    }
                }
            }
        }
        System.out.printf("deep structure sites checked against the sanctum: %d (%d within 80 blocks)%n", n, near);
        assertTrue(n > 0);
    }
}
