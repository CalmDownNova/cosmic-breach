package com.cosmicbreach.client.weather;

import com.cosmicbreach.client.sky.SkyState;
import com.cosmicbreach.client.sky.SkyWeather;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.CosmicWeather;
import com.cosmicbreach.world.weather.LayerWeather;
import com.cosmicbreach.world.weather.WeatherKind;
import com.cosmicbreach.world.weather.WeatherNet;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import com.cosmicbreach.world.weather.WeatherSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Cosmic weather on the client (W3b): takes the server's weather, eases the sky's hooks towards it every
 * tick ({@link SkyWeather}: Solenne's flare, Vesper's silence, the Surge's dimming, each weighted by how much
 * of the sky is that event's layer), plays the warning sounds and shows a line over the hotbar when an event
 * in the player's layer begins, and runs the other visuals: {@link SkyStreaks} (the Meteor Shower's red
 * streaks), {@link MeteorFx} (circles, meteors, impacts), {@link DustStreams} (the Drift's currents and the
 * Tide) and {@link WeatherOverlay} (the glow of a Flare on an exposed player, the dark of a Surge).
 */
public final class WeatherClient {
    /** Cues fire only for a phase that began this recently (not for one joined halfway). */
    private static final int FRESH_TICKS = 40;
    private static final RandomSource RANDOM = RandomSource.create();

    private static float flare;
    private static float silence;
    private static float dim;
    /** The warning's throb: its phase in turns, and its size this tick. */
    private static double throbPhase;
    private static float throb;

    private WeatherClient() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(RegisterGuiLayersEvent.class, WeatherOverlay::register);
        SkyWeather.addLayer(SkyStreaks::render);
        game.addListener(ClientTickEvent.Post.class, event -> tick());
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> reset());
    }

    /** The server's weather arrived (client thread). */
    public static void onSync(WeatherNet.Sync sync) {
        LayerWeather[] before = new LayerWeather[3];
        for (Layer layer : Layer.values()) {
            before[layer.ordinal()] = CosmicWeather.client(layer);
        }
        CosmicWeather.acceptClient(sync);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !AetheriaWorld.is(mc.level)) {
            return;
        }
        Layer here = Layer.at(mc.player.getY());
        LayerWeather was = before[here.ordinal()];
        LayerWeather now = CosmicWeather.client(here);
        if (was.kind() == now.kind() && was.phase() == now.phase()) {
            return;
        }
        long time = mc.level.getGameTime();
        if (now.phase() != Phase.IDLE && time - now.phaseStart() <= FRESH_TICKS) {
            begin(mc, now.kind(), now.phase());
        } else if (now.phase() == Phase.IDLE && was.phase() == Phase.ACTIVE) {
            message(mc, was.kind(), "ended");
        }
        if (now.phase() == Phase.IDLE && was.phase() == Phase.WARNING) {
            stopWarning(mc); // cleared during its warning: the hum goes with it
        }
    }

    /** The warning sound playing now (a Flare's hum, a Surge's swell), so a clear or leaving Aetheria can stop it. */
    private static SoundInstance warning;

    private static void stopWarning(Minecraft mc) {
        if (warning != null) {
            mc.getSoundManager().stop(warning);
            warning = null;
        }
    }

    private static void begin(Minecraft mc, WeatherKind kind, Phase phase) {
        if (phase == Phase.WARNING) {
            message(mc, kind, "warning");
            stopWarning(mc);
            switch (kind) {
                case FLARE -> warning = play(mc, WeatherSounds.FLARE_HUM.get(), 0.9f);
                case SURGE -> warning = play(mc, WeatherSounds.SURGE_SWELL.get(), 1.0f);
                default -> {
                }
            }
        } else {
            message(mc, kind, "active");
            if (kind == WeatherKind.TIDE) {
                play(mc, WeatherSounds.TIDE_DROP.get(), 1.0f);
            }
        }
    }

    private static void message(Minecraft mc, WeatherKind kind, String stage) {
        int colour = switch (kind) {
            case FLARE -> 0xFFD27A;
            case SHOWER -> 0xFF7A5C;
            case TIDE -> 0xA6DCFF;
            case SURGE -> 0xC3A6FF;
        };
        mc.gui.setOverlayMessage(Component.translatable("weather.cosmicbreach." + kind.id() + "." + stage)
                .withStyle(style -> style.withColor(TextColor.fromRgb(colour))), false);
    }

    /** A sound heard everywhere at once (no position): the sky itself. */
    private static SoundInstance play(Minecraft mc, SoundEvent sound, float volume) {
        SoundInstance instance = new SimpleSoundInstance(sound.getLocation(), SoundSource.WEATHER, volume, 1.0f, RANDOM,
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        mc.getSoundManager().play(instance);
        return instance;
    }

    /** True while a weather warning's sound is held to be stopped (for tests: the test client plays no sound). */
    public static boolean warningHeld() {
        return warning != null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean here = mc.level != null && mc.player != null && AetheriaWorld.is(mc.level);
        if (!here) {
            stopWarning(mc); // left Aetheria: its sky's warnings stay behind
        }
        float targetFlare = 0f;
        float targetSilence = 0f;
        float targetDim = 0f;
        if (here) {
            long time = mc.level.getGameTime();
            LayerWeather reach = CosmicWeather.client(Layer.REACH);
            LayerWeather deep = CosmicWeather.client(Layer.DEEP);
            if (reach.is(WeatherKind.FLARE, Phase.WARNING)) {
                double p = reach.progress(time);
                targetFlare = (float) (0.3 + 0.7 * p);
                // Solenne throbs, faster and harder as the warning runs out (like the hum's beating)
                throbPhase += (0.6 + 2.4 * p * p) / 20.0;
                throb = (float) (0.2 * (0.35 + 0.65 * p) * (0.5 + 0.5 * Math.sin(throbPhase * Math.PI * 2.0)));
            } else if (reach.is(WeatherKind.FLARE, Phase.ACTIVE)) {
                targetFlare = (float) (0.92 + 0.08 * Math.sin(time * 0.21));
            }
            if (deep.is(WeatherKind.SURGE, Phase.WARNING)) {
                double p = deep.progress(time);
                targetSilence = (float) Math.min(1.0, p / 0.4);
                targetDim = (float) p;
            } else if (deep.is(WeatherKind.SURGE, Phase.ACTIVE)) {
                targetSilence = 1f;
                targetDim = 1f;
            }
        }
        if (targetFlare == 0f || !CosmicWeather.client(Layer.REACH).is(WeatherKind.FLARE, Phase.WARNING)) {
            throb = Math.max(0f, throb - 0.02f);
        }
        flare = approach(flare, targetFlare, 0.05f, 0.01f);
        silence = approach(silence, targetSilence, 0.05f, 0.01f);
        dim = approach(dim, targetDim, 0.02f, 0.01f);
        double[] weights = SkyState.frame().layers;
        SkyWeather.setSolenneFlare(Math.min(1f, flare + throb) * (float) weights[0]);
        SkyWeather.setVesperSilence(silence * (float) weights[2]);
        SkyWeather.setSkyDim(dim * (float) weights[2]);
        if (here && !mc.isPaused()) {
            SkyStreaks.tick(mc);
            DustStreams.tick(mc);
            WeatherOverlay.tick(mc);
        }
    }

    private static float approach(float value, float target, float up, float down) {
        if (target > value) {
            return Math.min(target, value + up);
        }
        return Math.max(target, value - down);
    }

    /** Eased hook values (for checks): flare, silence, dim. */
    public static float[] hooks() {
        return new float[] {flare, silence, dim};
    }

    /** For checks: sky streaks drawn in the last frame. */
    public static int streaksDrawn() {
        return SkyStreaks.drawn();
    }

    /** For checks: meteors (circles) on their way or landing. */
    public static int meteorsLive() {
        return MeteorFx.live();
    }

    /** For checks: dust motes spawned in the last tick. */
    public static int dustSpawned() {
        return DustStreams.spawned();
    }

    /** For checks: the exposure glow on the local player's screen, 0 to 1. */
    public static float exposureGlow() {
        return WeatherOverlay.exposure();
    }

    private static void reset() {
        CosmicWeather.resetClient();
        flare = 0f;
        silence = 0f;
        dim = 0f;
        SkyWeather.setSolenneFlare(0f);
        SkyWeather.setVesperSilence(0f);
        SkyWeather.setSkyDim(0f);
        SkyStreaks.clear();
        WeatherOverlay.clear();
        throb = 0f;
    }
}
