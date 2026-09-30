package com.cosmicbreach.client.guardian;

import com.cosmicbreach.world.VesperClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The boss loop while a guardian is awake (Prism Colossus design v1, "Look and sound"): an 8-bar phrase at 100 BPM,
 * started on a bar line of the Vesper clock and started again every {@value #PHRASE_BEATS} beats, so it stays on the
 * pulsar's beat however the frames fall; each phrase rings a little past its end into the next. Other music stops
 * while it plays; it fades out when the fight ends. When the bar asks for another loop (a new phase), the playing
 * phrase fades and the new loop starts on the next bar line. Client thread.
 */
public final class GuardianMusic {
    /** Beats in one phrase of the loop (8 bars of 4). */
    public static final int PHRASE_BEATS = 32;
    private static final int FADE_TICKS = 40;

    private static @Nullable Phrase playing;
    private static @Nullable SoundEvent current;
    private static long nextStartBeat = Long.MIN_VALUE;
    private static boolean active;
    private static int phrases;
    /** Ticks the loop stays pulled down, the last {@value #DUCK_RELEASE} of them coming back up. */
    private static int duckLeft;
    private static final int DUCK_RELEASE = 16;
    private static final float DUCKED = 0.2f;

    private GuardianMusic() {
    }

    /** A phrase of the loop, as music, everywhere at once; it can fade out. */
    private static final class Phrase extends AbstractTickableSoundInstance {
        private int fading = -1;

        Phrase(SoundEvent sound) {
            super(sound, SoundSource.MUSIC, RandomSource.create());
            this.looping = false;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 1.0f;
        }

        void fadeOut() {
            if (fading < 0) {
                fading = FADE_TICKS;
            }
        }

        @Override
        public void tick() {
            float fade = 1f;
            if (fading >= 0) {
                fading--;
                fade = Math.max(0f, fading / (float) FADE_TICKS);
                if (fading <= 0) {
                    stop();
                }
            }
            volume = fade * duckFactor();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /**
     * Pulls the loop down for {@code ticks} ticks, then brings it back over {@value #DUCK_RELEASE}: a peak of the
     * fight (the Shatter) sounds alone for a moment.
     */
    public static void duck(int ticks) {
        duckLeft = Math.max(duckLeft, ticks + DUCK_RELEASE);
    }

    private static float duckFactor() {
        if (duckLeft <= 0) {
            return 1f;
        }
        if (duckLeft > DUCK_RELEASE) {
            return DUCKED;
        }
        return DUCKED + (1f - DUCKED) * (1f - duckLeft / (float) DUCK_RELEASE);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (duckLeft > 0) {
            duckLeft--;
        }
        if (mc.level == null || mc.player == null) {
            stopAll();
            return;
        }
        ResourceLocation id = GuardianBarHud.musicWanted();
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
        boolean want = sound != null;
        long t = mc.level.getGameTime();
        long beat = VesperClock.beat(t);
        if (want && !active) {
            active = true;
            mc.getMusicManager().stopPlaying();
            // the next bar line
            nextStartBeat = (beat / VesperClock.BEATS_PER_BAR + 1) * VesperClock.BEATS_PER_BAR;
        }
        if (!want && active) {
            active = false;
            current = null;
            if (playing != null) {
                playing.fadeOut();
            }
            nextStartBeat = Long.MIN_VALUE;
        }
        if (active && want && current != null && sound != current) {
            // another loop is wanted (the next phase's): this one fades, the new one starts on the next bar line
            if (playing != null) {
                playing.fadeOut();
            }
            current = sound;
            nextStartBeat = (beat / VesperClock.BEATS_PER_BAR + 1) * VesperClock.BEATS_PER_BAR;
        }
        if (active && VesperClock.isBeat(t) && beat >= nextStartBeat) {
            current = sound;
            Phrase phrase = new Phrase(sound);
            mc.getSoundManager().play(phrase);
            playing = phrase;
            phrases++;
            nextStartBeat = beat + PHRASE_BEATS;
        }
    }

    /** While the loop plays, no other music starts. */
    static void onSelectMusic(SelectMusicEvent event) {
        if (active) {
            event.overrideMusic(null);
        }
    }

    private static void stopAll() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
        active = false;
        current = null;
        nextStartBeat = Long.MIN_VALUE;
    }

    /** The loop playing now, or null (for checks). */
    public static @Nullable SoundEvent current() {
        return current;
    }

    /** True while the loop is on (for checks). */
    public static boolean active() {
        return active;
    }

    /** Phrases started so far (for checks). */
    public static int phrasesStarted() {
        return phrases;
    }
}
