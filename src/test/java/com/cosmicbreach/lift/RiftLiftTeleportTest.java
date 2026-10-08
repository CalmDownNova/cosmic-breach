package com.cosmicbreach.lift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.leviathan.RiftLayout;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** A ride ends when the player is moved from outside (a teleport), on both sides: nothing keeps steering them to the old column. */
class RiftLiftTeleportTest {
    @Test
    void aMoveFasterThanAnyFallOrRideIsATeleport() {
        Vec3 a = new Vec3(100, 80, 100);
        assertFalse(RiftLift.teleported(a, a.add(0, -3.92, 0)), "terminal fall in one tick");
        assertFalse(RiftLift.teleported(a, a.add(0.6, 0.7, 0.6)), "the lift's own speeds");
        assertFalse(RiftLift.teleported(a, a.add(4, -4, 4)));
        assertTrue(RiftLift.teleported(a, a.add(11, 0, 0)), "eleven blocks in a tick: behind a boulder");
        assertTrue(RiftLift.teleported(a, a.add(0, 30, 0)));
        assertFalse(RiftLift.teleported(null, a), "no earlier position, nothing to compare");
    }

    @Test
    void aRideDoesNotContinueAfterAJump() {
        RiftLayout l = new RiftLayout(400, RiftLayout.CENTRE_Y, -900, 1234L);
        RiftLayout.Platform p = l.platforms().get(0);
        Vec3 deep = new Vec3(p.x() + 3, RiftLift.standY(p) - 10, p.z() + 3);
        RiftLift.Ride ride = RiftLift.start(l, deep);
        assertTrue(ride != null, "a fall under the platform is caught");
        RiftLift.Step moving = RiftLift.step(l, ride, deep, Vec3.ZERO, false, false);
        assertTrue(moving.ride() != null, "and carried while it falls on");
        // the same tick as a teleport of 20 blocks: the helper both sides call first
        RiftLift.Step after = RiftLift.stepAfterMove(l, ride, deep, deep.add(20, 0, 0), Vec3.ZERO, false, false);
        assertNull(after.ride(), "a teleport ends the ride");
        assertNull(after.velocity(), "and steers nothing");
        RiftLift.Step walking = RiftLift.stepAfterMove(l, ride, deep, deep.add(0.1, -0.5, 0.1), new Vec3(0.1, -0.5, 0.1), false, false);
        assertEquals(moving.ride() != null, walking.ride() != null, "an ordinary tick goes on as before");
    }
}
