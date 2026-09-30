package com.cosmicbreach.guardian.leviathan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Leviathan Rift's layout: its sphere fits the Drift, its orbit is clear, its platforms, bridges, bowl and climbs. */
class RiftLayoutTest {
    private static final RiftLayout L = new RiftLayout(400, RiftLayout.CENTRE_Y, -900, 1234L);

    private static RiftLayout.Kind kind(int x, int y, int z) {
        return L.kind(L.slice(x, z, x, z), x, y, z);
    }

    private static boolean solid(RiftLayout.Kind k) {
        return k != RiftLayout.Kind.AIR && k != RiftLayout.Kind.KEEP && k != RiftLayout.Kind.UPDRAFT && k != RiftLayout.Kind.UPDRAFT_TOP;
    }

    @Test
    void theSphereFitsInsideTheDriftsBand() {
        int[] b = L.bounds();
        assertTrue(RiftLayout.CENTRE_Y - RiftLayout.RY >= 162 && RiftLayout.CENTRE_Y + RiftLayout.RY <= 298);
        assertTrue(b[4] - b[1] < 160);
        assertEquals(160, 2 * RiftLayout.RX, "160 blocks across");
    }

    @Test
    void eightPlatformsRingTheCoreWithinSixBlocksOfItsEquator() {
        for (RiftLayout.Platform p : L.platforms()) {
            double r = Math.hypot(p.x() - L.centre().x, p.z() - L.centre().z);
            assertEquals(RiftLayout.PLATFORM_RING, r, 1e-6);
            assertTrue(Math.abs(p.top() - L.y()) <= 6, "top " + p.top());
            double orbitY = LeviathanOrbit.point(L.centre(), p.angle(), L.wavePhase()).y;
            assertEquals(orbitY, p.top() + RiftLayout.SWING_BAND, 0.51, "platform " + p.index() + " rides the orbit's wave");
            // its inner rim: rock from just outside the orbit's widest flank, so a player at the rim can swing at the body going by
            double rim = Double.NaN;
            for (double out = 23.0; out <= 29.0; out += 0.25) {
                int x = (int) Math.floor(L.centre().x + out * Math.cos(p.angle()));
                int z = (int) Math.floor(L.centre().z + out * Math.sin(p.angle()));
                if (solid(kind(x, p.top(), z))) {
                    rim = out;
                    break;
                }
            }
            assertTrue(rim >= RiftLayout.CLEAR_RADIUS - 1.0 && rim <= 26.8, "platform " + p.index() + " rim at " + rim);
            assertTrue(p.radius() >= 5 && p.radius() <= 6, "10 to 12 across");
            assertTrue(solid(kind((int) Math.floor(p.x()), p.top(), (int) Math.floor(p.z()))), "its top is rock");
        }
        BlockPos bell = L.bell();
        assertEquals(RiftLayout.Kind.BELL, kind(bell.getX(), bell.getY(), bell.getZ()));
        assertEquals(L.platform(0).top() + 1, bell.getY(), "the bell stands on platform 0");
    }

    @Test
    void theLairGlowsItsCoreBristlingWithSingingCrystalThePlatformsLitBelowAndTheBellsPosts() {
        Vec3 c = L.centre();
        int coreCrystal = 0;
        for (int dx = -19; dx <= 19; dx++) {
            for (int dy = -19; dy <= 19; dy++) {
                for (int dz = -19; dz <= 19; dz++) {
                    if (kind(L.x() + dx, L.y() + dy, L.z() + dz) == RiftLayout.Kind.SINGING_RIMEGLASS) {
                        coreCrystal++;
                        double lat = Math.abs(dy + 0.5) / Math.sqrt((dx + 0.5) * (dx + 0.5) + (dy + 0.5) * (dy + 0.5) + (dz + 0.5) * (dz + 0.5));
                        assertTrue(lat > 0.18, "no singing crystal at the equator, where the coil lies: " + dx + " " + dy + " " + dz);
                    }
                }
            }
        }
        assertTrue(coreCrystal > 150, "the core bristles with singing crystal: " + coreCrystal);
        for (RiftLayout.Platform p : L.platforms()) {
            int below = 0;
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    for (int y = p.top() - 12; y < p.top() - 2; y++) {
                        if (kind((int) Math.floor(p.x()) + dx, y, (int) Math.floor(p.z()) + dz) == RiftLayout.Kind.SINGING_RIMEGLASS) {
                            below++;
                        }
                    }
                }
            }
            assertTrue(below >= 2, "singing crystal hangs under platform " + p.index());
        }
        for (Vec3 post : L.bellPosts()) {
            int x = (int) Math.floor(post.x);
            int z = (int) Math.floor(post.z);
            assertEquals(RiftLayout.Kind.SINGING_RIMEGLASS, kind(x, (int) Math.floor(post.y), z), "a lit post by the bell");
            assertEquals(RiftLayout.Kind.SINGING_RIMEGLASS, kind(x, (int) Math.floor(post.y) + 1, z), "two blocks tall");
        }
        assertTrue(c != null);
    }

    @Test
    void theOrbitAndTheSpaceRoundTheCoreAreOpenAir() {
        Vec3 c = L.centre();
        int solidCount = 0;
        for (int a = 0; a < 360; a += 3) {
            for (double r = 18.5; r <= 24.5; r += 1.5) {
                for (int dy = -7; dy <= 7; dy += 2) {
                    int x = (int) Math.floor(c.x + r * Math.cos(Math.toRadians(a)));
                    int z = (int) Math.floor(c.z + r * Math.sin(Math.toRadians(a)));
                    if (solid(kind(x, L.y() + dy, z))) {
                        solidCount++;
                    }
                }
            }
        }
        assertEquals(0, solidCount, "nothing in the orbit's band");
        assertTrue(solid(kind(L.x(), L.y(), L.z())), "the central asteroid");
        assertEquals(RiftLayout.Kind.RIMEGLASS, kind(L.x(), L.y(), L.z()), "its geode core");
    }

    @Test
    void theBowlCatchesADropFromEveryPlatformAboutFiftyBlocksDown() {
        for (RiftLayout.Platform p : L.platforms()) {
            double a = p.angle();
            int x = (int) Math.floor(L.centre().x + (RiftLayout.PLATFORM_RING + p.radius() + 1.5) * Math.cos(a));
            int z = (int) Math.floor(L.centre().z + (RiftLayout.PLATFORM_RING + p.radius() + 1.5) * Math.sin(a));
            int y = p.top();
            int fell = 0;
            while (!solid(kind(x, y - 1, z)) && fell < 80) {
                y--;
                fell++;
            }
            assertTrue(fell > 35 && fell < 70, "a drop off platform " + p.index() + " lands after " + fell);
        }
    }

    @Test
    void bridgesJoinNeighboursAndTheWalkwayReachesTheLedge() {
        for (int k = 0; k < RiftLayout.PLATFORMS; k++) {
            RiftLayout.Platform p = L.platform(k);
            RiftLayout.Platform q = L.platform(k + 1);
            for (double f = 0.2; f <= 0.8; f += 0.1) {
                double x = p.x() + (q.x() - p.x()) * f;
                double z = p.z() + (q.z() - p.z()) * f;
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                boolean found = false;
                for (int y = Math.min(p.top(), q.top()) - 2; y <= Math.max(p.top(), q.top()) + 1; y++) {
                    RiftLayout.Kind kk = kind(bx, y, bz);
                    found |= kk == RiftLayout.Kind.PLANKS || kk == RiftLayout.Kind.SLAB;
                }
                assertTrue(found, "a bridge from " + k + " to " + (k + 1) + " at " + f);
            }
        }
        Vec3 ledge = L.ledgeTop();
        assertTrue(solid(kind((int) Math.floor(ledge.x), (int) Math.floor(ledge.y) - 1, (int) Math.floor(ledge.z))), "the ledge outside");
    }

    @Test
    void twoUpdraftsRiseFromTheBowlToPlatforms() {
        assertEquals(2, L.updrafts().size());
        for (RiftLayout.Updraft u : L.updrafts()) {
            assertEquals(RiftLayout.Kind.UPDRAFT_TOP, kind(u.x(), u.top(), u.z()));
            assertEquals(RiftLayout.Kind.UPDRAFT, kind(u.x(), u.bottom(), u.z()));
            assertTrue(solid(kind(u.x(), u.bottom() - 1, u.z())) || solid(kind(u.x(), u.bottom() - 2, u.z())), "it starts on the bowl");
            assertTrue(u.top() - u.bottom() > 40);
            assertNotEquals(0, Math.abs(u.stepX()) + Math.abs(u.stepZ()));
        }
    }

    @Test
    void theNaturalKnightSpawnRuleIsRareAndOnlyInTheDriftsAirNearRock() {
        assertTrue(com.cosmicbreach.entity.gyre.GyreKnights.placeRule(230, true, true, true));
        assertTrue(!com.cosmicbreach.entity.gyre.GyreKnights.placeRule(330, true, true, true), "not in the Reach");
        assertTrue(!com.cosmicbreach.entity.gyre.GyreKnights.placeRule(230, false, true, true), "needs headroom");
        assertTrue(!com.cosmicbreach.entity.gyre.GyreKnights.placeRule(230, true, false, true), "near rock");
        assertTrue(!com.cosmicbreach.entity.gyre.GyreKnights.placeRule(230, true, true, false), "one attempt in six");
    }
}
