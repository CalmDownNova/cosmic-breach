package com.cosmicbreach.client.astrolabe;

import com.cosmicbreach.astrolabe.Astrolabes;
import com.cosmicbreach.astrolabe.PocketStar;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The Pocket Star's warm hum (GDD 4.2), looping at the star while it burns: swelling in, guttering out. */
final class StarHum extends AbstractTickableSoundInstance {
    private final PocketStar star;
    private int age;

    StarHum(PocketStar star) {
        super(Astrolabes.HUM.get(), SoundSource.PLAYERS, RandomSource.create());
        this.star = star;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.pitch = 1.0f;
        follow();
    }

    @Override
    public void tick() {
        if (star.isRemoved()) {
            volume *= 0.5f;
            if (volume < 0.02f) {
                stop();
            }
            return;
        }
        age++;
        follow();
        float in = Math.min(1f, age / 8f);
        float left = star.life() - star.tickCount;
        float out = left < 10 ? Math.max(0f, left / 10f) : 1f;
        volume = 0.7f * in * out;
        pitch = star.singular() ? 0.84f : 1.0f;
    }

    private void follow() {
        x = star.getX();
        y = star.getY() + 0.25;
        z = star.getZ();
    }
}
