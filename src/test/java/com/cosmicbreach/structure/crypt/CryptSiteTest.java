package com.cosmicbreach.structure.crypt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.BreachShape;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Where Hollow Crypts stand (GDD 6.1), read from the terrain model the way worldgen does: on a Rift Abyss pillar's
 * top, clear of the Breach, the whole crypt (its keel included) inside the Deep, and in a good share of the
 * placement regions.
 */
class CryptSiteTest {
    @Test
    void cryptsStandOnDeepPillarsInManyRegions() {
        int n = 0;
        int sites = 0;
        for (long salt = 1; salt <= 3; salt++) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt * 0x9E3779B97F4A7C15L);
            for (int rx = -8; rx < 8; rx++) {
                for (int rz = -8; rz < 8; rz++) {
                    int x = rx * 640 + 320;
                    int z = rz * 640 + 320;
                    n++;
                    Optional<HollowCryptStructure.Site> site = HollowCryptStructure.site(t, x, z);
                    if (site.isEmpty()) {
                        continue;
                    }
                    sites++;
                    HollowCryptStructure.Site s = site.get();
                    int bottom = s.origin().getY() - CryptLayout.STOREY * 2 - 4;
                    assertTrue(Layer.at(s.gate().getY()) == Layer.DEEP && s.gate().getY() + 8 <= Layer.DEEP.rockMaxY + 8,
                            "the gatehouse stands in the Deep: " + s.gate());
                    assertTrue(bottom > 20, "three levels and the keel stay over the void: " + s.origin());
                    assertTrue(Math.hypot(s.gate().getX(), s.gate().getZ()) > BreachShape.DEEP_RADIUS, "clear of the Breach");
                    assertTrue(t.blocksAt(s.gate().getX(), s.gate().getY() - 1, s.gate().getZ()) > 0, "the gatehouse stands on rock");
                }
            }
        }
        System.out.printf("hollow crypt sites in %d of %d regions (%.0f%%)%n", sites, n, 100.0 * sites / n);
        assertTrue(sites > n / 5, "crypt sites in " + sites + " of " + n + " regions");
    }
}
