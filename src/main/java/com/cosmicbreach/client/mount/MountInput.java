package com.cosmicbreach.client.mount;

import com.cosmicbreach.mount.DriftManta;
import com.cosmicbreach.mount.LumenStag;
import com.cosmicbreach.mount.MountActionPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The dash key while riding (GDD 8.1: "the dash key triggers the mount's own move instead"): the Lumen Stag makes its
 * next jump, the Drift Manta its Phase Blink. The rider's client moves the mount, so both happen here at once; the
 * blink then tells the server, which checks it and starts the cooldown and the shield.
 */
public final class MountInput {
    private MountInput() {
    }

    /** The dash key was pressed by a rider. */
    public static void dashKey(LocalPlayer player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LumenStag stag && stag.getControllingPassenger() == player) {
            stag.requestDashJump();
        } else if (vehicle instanceof DriftManta manta && manta.getControllingPassenger() == player) {
            Vec3 from = manta.blinkLocally(player);
            if (from == null) {
                player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.4f, 0.6f); // not ready yet
                return;
            }
            PacketDistributor.sendToServer(new MountActionPayload(MountActionPayload.BLINK, from, manta.position()));
            MountFx.blink(manta, from, manta.position());
        }
    }
}
