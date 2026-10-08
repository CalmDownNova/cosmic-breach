package com.cosmicbreach.lift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.leviathan.RiftLayout;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Items dropped into the boss 2 arena's bowl come back onto a platform (playtest 3). */
class RiftItemReturnTest {
    private static RiftLayout rift(int n) {
        return new RiftLayout(400 + 331 * n, RiftLayout.CENTRE_Y, -900 + 517 * n, 1234L + 977L * n);
    }

    private static Vec3 beside(RiftLayout l, RiftLayout.Platform p, double out, double y) {
        double r = RiftLayout.PLATFORM_RING + p.radius() + out;
        return new Vec3(l.centre().x + r * Math.cos(p.angle()), y, l.centre().z + r * Math.sin(p.angle()));
    }

    @Test
    void anItemOnTheBowlComesBackOntoItsPlatform() {
        for (int n = 0; n < 8; n++) {
            RiftLayout l = rift(n);
            for (RiftLayout.Platform p : l.platforms()) {
                Vec3 at = beside(l, p, 2.0, l.bowlSurface(RiftLayout.PLATFORM_RING + p.radius() + 2.0) + 0.1);
                Vec3 to = RiftItemReturn.returnTo(l, at);
                assertNotNull(to, "rift " + n + " platform " + p.index());
                // set on that platform, at its standing height, within its top
                assertEquals(RiftLift.standY(p) + 0.25, to.y, 1e-9);
                assertTrue(Math.hypot(to.x - p.x(), to.z - p.z()) < p.radius(), "on the platform's top");
                // and from there it is never sent back again
                assertNull(RiftItemReturn.returnTo(l, to));
            }
        }
    }

    @Test
    void aFallingItemIsCaughtAtTheLiftsLine() {
        RiftLayout l = rift(1);
        RiftLayout.Platform p = l.platform(3);
        double line = RiftLift.standY(p) - RiftLift.CATCH_BELOW;
        assertNull(RiftItemReturn.returnTo(l, beside(l, p, 2.0, line + 0.5)));
        assertNotNull(RiftItemReturn.returnTo(l, beside(l, p, 2.0, line - 0.5)));
    }

    @Test
    void anItemOnTheBowlsHighRimOrUnderTheCoreComesBackToo() {
        RiftLayout l = rift(2);
        RiftLayout.Platform p = l.platform(0);
        // near the shell the bowl rises to only a dozen blocks under the platforms: still under the lift's line
        double r = RiftLayout.RX - 5.0;
        assertNotNull(RiftItemReturn.returnTo(l, beside(l, p, r - RiftLayout.PLATFORM_RING - p.radius(), l.bowlSurface(r) + 0.1)));
        // under the central asteroid
        assertNotNull(RiftItemReturn.returnTo(l, l.centre().add(0, -RiftLayout.CORE_RADIUS - 3, 0)));
    }

    @Test
    void anItemOutsideTheRiftStays() {
        RiftLayout l = rift(2);
        assertNull(RiftItemReturn.returnTo(l, new Vec3(l.centre().x + RiftLayout.RX + 20, 150, l.centre().z)));
    }
}
