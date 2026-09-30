package com.cosmicbreach.client.fx;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The charge's rising choir, looping on a charging player (the file loops seamlessly and is not
 * streamed). It swells in volume and pitch until the charge is full ({@link ChargeRamp}), then holds;
 * on release or cancel it fades out over a couple of ticks rather than cutting off.
 */
final class ChargeLoopSound extends AbstractTickableSoundInstance {
    private static final float RELEASE_KEEP = 0.45f;

    private final Player player;
    private final int rampTicks;
    private int held;
    private boolean releasing;

    /** {@code rampTicks}: ticks from the start of the charge to full charge; {@code sound}: the loop to play. */
    ChargeLoopSound(Player player, int rampTicks, SoundEvent sound) {
        super(sound, SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.rampTicks = Math.max(1, rampTicks);
        this.looping = true;
        this.delay = 0;
        this.volume = ChargeRamp.volume(0.0);
        this.pitch = ChargeRamp.pitch(0.0);
        follow();
    }

    /** The charge ended: fade out. */
    void release() {
        releasing = true;
    }

    @Override
    public void tick() {
        if (player.isRemoved()) {
            stop();
            return;
        }
        follow();
        if (releasing) {
            volume *= RELEASE_KEEP;
            if (volume < 0.03f) {
                stop();
            }
            return;
        }
        held++;
        double progress = Math.min(1.0, held / (double) rampTicks);
        volume = ChargeRamp.volume(progress);
        pitch = ChargeRamp.pitch(progress);
    }

    private void follow() {
        x = player.getX();
        y = player.getY() + player.getBbHeight() * 0.6;
        z = player.getZ();
    }
}
