package com.cosmicbreach.world.gen;

import com.cosmicbreach.guardian.unsung.SilentNaveStructure;
import com.cosmicbreach.structure.crypt.HollowCryptStructure;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * How often a crypt start and a boss arena start find a site, per zone, over random start chunks (the arena's counted
 * only where its biome lets it stand). Runs only with {@code CB_BENCH} set; prints one line per salt. The structure
 * sets' spacings are set from these rates (see the layer 3 zone plan).
 */
class SiteRateProbe {
    @Test
    void run() {
        Assumptions.assumeTrue(System.getenv("CB_BENCH") != null);
        for (long salt : new long[] {20260927L, 7L, -918273645L}) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            Random r = new Random(salt);
            int crypt = 0, nave = 0, n = 3000;
            int[] zn = new int[4], zc = new int[4], zv = new int[4];
            for (int i = 0; i < n; i++) {
                int cx = (int) ((r.nextDouble() * 2 - 1) * 1500);
                int cz = (int) ((r.nextDouble() * 2 - 1) * 1500);
                if (Math.hypot(cx * 16, cz * 16) < 400) { i--; continue; }
                int zone = t.zones.zoneAt(cx * 16 + 8, cz * 16 + 8);
                zn[zone]++;
                if (HollowCryptStructure.site(t, cx * 16 + 8, cz * 16 + 8).isPresent()) { crypt++; zc[zone]++; }
                var nv = SilentNaveStructure.site(t, salt, new ChunkPos(cx, cz));
                if (nv.isPresent() && t.zones.zoneAt(nv.get().cx(), nv.get().cz()) != DeepZones.GARDENS) { nave++; zv[zone]++; }
            }
            System.out.println("SITE_RATE salt " + salt + " crypt " + crypt / (double) n + " nave " + nave / (double) n + " per zone crypt " + java.util.Arrays.toString(new double[] {zc[0] / (double) zn[0], zc[1] / (double) zn[1], zc[2] / (double) zn[2], zc[3] / (double) zn[3]})
                    + " nave " + java.util.Arrays.toString(new double[] {zv[0] / (double) zn[0], zv[1] / (double) zn[1], zv[2] / (double) zn[2], zv[3] / (double) zn[3]}));
        }
    }
}
