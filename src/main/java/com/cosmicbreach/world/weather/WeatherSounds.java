package com.cosmicbreach.world.weather;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Cosmic weather's sounds, synthesized by {@code tools/sound/weather.py} (subtitles in
 * {@code assets/cosmicbreach_weather/lang}). All play in the Weather volume.
 */
public final class WeatherSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    /** The Solar Flare's warning: a hum rising for 15 s into a white shimmer. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_HUM = sound("weather/flare_hum");
    /** A meteor's whistle, 2 s from its impact (played at the circle). */
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_WHISTLE = sound("weather/meteor_whistle");
    /** A meteor's impact. */
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_IMPACT = sound("weather/meteor_impact");
    /** The Gravity Tide's start: a sub-bass drop as the Drift lets go. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_DROP = sound("weather/tide_drop");
    /** The Eclipse Surge's warning: a dark swell that ends in silence as Vesper stops. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SURGE_SWELL = sound("weather/surge_swell");

    private WeatherSounds() {
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
