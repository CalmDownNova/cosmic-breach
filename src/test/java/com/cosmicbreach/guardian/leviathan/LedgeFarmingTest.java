package com.cosmicbreach.guardian.leviathan;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Breach Dive's preference for a player who farms the Leviathan from one spot. */
class LedgeFarmingTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final Vec3 LEDGE = new Vec3(10, 64, 10);

    @Test
    void aPlayerHittingFromOneSpotForFourSecondsIsAFarmer() {
        LedgeFarming f = new LedgeFarming();
        f.hit(A, 1000, LEDGE);
        assertFalse(f.farming(A, 1000, LEDGE), "one hit is not a stay");
        f.hit(A, 1040, LEDGE.add(1, 0, 0));
        assertFalse(f.farming(A, 1040, LEDGE), "two seconds is not enough");
        f.hit(A, 1080, LEDGE.add(-1, 0, 1));
        assertTrue(f.farming(A, 1080, LEDGE), "four seconds from within three blocks");
        assertTrue(f.farming(A, 1400, LEDGE), "it only reaches the ledge once a lap, so a farmer stays one between passes");
        assertFalse(f.farming(A, 1080 + LedgeFarming.RECENT + 1, LEDGE), "not once they stop");
    }

    @Test
    void movingAboutOrLeavingTheSpotIsNotFarming() {
        LedgeFarming f = new LedgeFarming();
        for (int i = 0; i <= 10; i++) {
            f.hit(A, 1000 + i * 20, LEDGE.add(i * 2.0, 0, 0));      // walks along as it swings
        }
        assertFalse(f.farming(A, 1200, LEDGE.add(20, 0, 0)));
        f.hit(B, 1000, LEDGE);
        f.hit(B, 1100, LEDGE);
        assertTrue(f.farming(B, 1100, LEDGE));
        assertFalse(f.farming(B, 1100, LEDGE.add(8, 0, 0)), "now standing somewhere else");
        f.hit(B, 1120, LEDGE.add(9, 0, 0));
        assertFalse(f.farming(B, 1120, LEDGE.add(9, 0, 0)), "a new spot starts a new stay");
    }

    @Test
    void theFarmerIsPreferredAndWithoutOneThereIsNone() {
        LedgeFarming f = new LedgeFarming();
        f.hit(A, 1000, LEDGE);
        f.hit(A, 1100, LEDGE);
        f.hit(B, 1090, LEDGE.add(30, 0, 0));
        Map<UUID, Vec3> at = Map.of(A, LEDGE, B, LEDGE.add(30, 0, 0));
        assertEquals(A, f.farmer(List.of(B, A), 1100, at::get));
        f.clear();
        assertNull(f.farmer(List.of(A, B), 1100, at::get));
    }
}
