package com.cosmicbreach.guardian.heliarch;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;

/**
 * What the Heliarch says (the recorded takes, shaped by {@code tools/sound/heliarch.py} into its hollow, regal voice),
 * heard by everyone in the fight at once, with the words as the subtitle. {@code lengthTicks} is how long each line's
 * sound lasts (its .ogg, rounded up; a test checks them), so the client keeps the subtitle up for all of it.
 */
public enum HeliarchLine {
    INTRO(HeliarchRegistry.VOICE_INTRO::get, 161, "Another singer. I poured a sun into the dark for you. It was not enough."),
    HOLLOWING(HeliarchRegistry.VOICE_HOLLOWING::get, 124, "The Hollow wants the rest of me. Let it have you instead."),
    NOVA(HeliarchRegistry.VOICE_NOVA::get, 60, "Burn with me!"),
    COLLAPSE(HeliarchRegistry.VOICE_COLLAPSE::get, 98, "Everything falls. Everything always falls."),
    DEATH(HeliarchRegistry.VOICE_DEATH::get, 103, "Ah... I can hear them again. Sing."),
    PLAYER_DOWN(HeliarchRegistry.VOICE_PLAYER_DOWN::get, 52, "Silence.");

    private final Supplier<SoundEvent> sound;
    private final int lengthTicks;
    private final String words;

    HeliarchLine(Supplier<SoundEvent> sound, int lengthTicks, String words) {
        this.sound = sound;
        this.lengthTicks = lengthTicks;
        this.words = words;
    }

    public SoundEvent sound() {
        return sound.get();
    }

    public int lengthTicks() {
        return lengthTicks;
    }

    /** The subtitle: the spoken words. */
    public String words() {
        return words;
    }

    /** Its name in files: {@code intro}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String subtitleKey() {
        return "subtitles.cosmicbreach.heliarch.voice_" + id();
    }
}
