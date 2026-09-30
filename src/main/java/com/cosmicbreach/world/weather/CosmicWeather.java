package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.AetheriaGravity;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Aetheria's cosmic weather (GDD 2.5, W3b): one event per layer at a time, scheduled per layer by the server
 * ({@link WeatherScheduler}, saved in {@link WeatherData}, intervals from {@link WeatherConfig}), synced to
 * clients so skies, sounds and rules match. The events: {@link SolarFlare} (Reach), {@link MeteorShower}
 * (Reach and Drift), {@link GravityTide} (Drift), {@link EclipseSurge} (Deep); plus the Drift's standing
 * currents ({@link DriftCurrents}). Commands: {@code /cosmicbreach weather <flare|shower|tide|surge> [layer]},
 * {@code weather clear [layer]}, {@code weather status}.
 *
 * <p>For other tasks, on either side: {@link #get} (a layer's weather), {@link SolarFlare#exposed} (Star Eggs
 * hatch in a Flare), {@link EclipseSurge#active} and its multipliers (Hollow mobs, Stalkers), and on the
 * server {@link EclipseSurge#onHeliarchFirstDeath} (the boss task calls it once).
 */
public final class CosmicWeather {
    private static volatile LayerWeather[] clientLayers = {LayerWeather.CALM, LayerWeather.CALM, LayerWeather.CALM};
    private static volatile long clientSalt;
    private static volatile boolean clientSynced;

    private CosmicWeather() {
    }

    public static void register(IEventBus modBus, IEventBus game, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, WeatherConfig.SPEC, WeatherConfig.FILE);
        WeatherSounds.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, WeatherNet::register);
        AetheriaGravity.setModifier(GravityTide::modify);
        game.addListener(LevelTickEvent.Post.class, WeatherScheduler::onLevelTick);
        game.addListener(EntityTickEvent.Pre.class, WeatherPush::onEntityTickPre);
        game.addListener(EntityTickEvent.Post.class, SolarFlare::onEntityTickPost);
        game.addListener(LivingIncomingDamageEvent.class, SolarFlare::onIncomingDamage);
        game.addListener(LivingFallEvent.class, GravityTide::onFall);
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> WeatherScheduler.arrived(event.getEntity()));
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> WeatherScheduler.arrived(event.getEntity()));
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> WeatherScheduler.arrived(event.getEntity()));
        game.addListener(ServerStoppedEvent.class, event -> WeatherScheduler.reset());
        game.addListener(RegisterCommandsEvent.class, event -> WeatherCommands.register(event.getDispatcher()));
    }

    /** {@code layer}'s weather on this side: the server's schedule, or what the server last sent this client. */
    public static LayerWeather get(Level level, Layer layer) {
        if (!AetheriaWorld.is(level)) {
            return LayerWeather.CALM;
        }
        if (level.isClientSide()) {
            return clientLayers[layer.ordinal()];
        }
        return level instanceof ServerLevel server ? WeatherData.get(server).snapshot(layer) : LayerWeather.CALM;
    }

    /** True if {@code layer} is in {@code kind}'s {@code phase} (either side). */
    public static boolean is(Level level, Layer layer, WeatherKind kind, Phase phase) {
        return get(level, layer).is(kind, phase);
    }

    /** True once this side knows the weather: always on the server; on a client, after the first sync. */
    public static boolean synced(Level level) {
        return !level.isClientSide() || clientSynced;
    }

    /** The Drift currents' salt on this side (the server makes it from the seed; clients get it synced). */
    public static long currentSalt(Level level) {
        if (level.isClientSide()) {
            return clientSalt;
        }
        return level instanceof ServerLevel server ? DriftCurrents.salt(server.getSeed()) : 0L;
    }

    /** The multiplier on the Resonance {@code player} earns: 2 in a Solar Flare over the Reach. Either side. */
    public static double resonanceGain(Player player) {
        return SolarFlare.resonanceGain(player);
    }

    /** The multiplier on {@code player}'s ability costs: 0.75 in an Eclipse Surge in the Deep. Either side. */
    public static double abilityCostScale(Player player) {
        return EclipseSurge.abilityCostScale(player);
    }

    /** Client only: what the server last sent for {@code layer}, whatever level the client is in. */
    public static LayerWeather client(Layer layer) {
        return clientLayers[layer.ordinal()];
    }

    /** Client only: the server's latest weather (called on the client thread by the sync handler). */
    public static void acceptClient(WeatherNet.Sync sync) {
        clientLayers = sync.layers().toArray(new LayerWeather[0]);
        clientSalt = sync.currentSalt();
        clientSynced = true;
    }

    /** Client only: forget the server's weather (on leaving a server). */
    public static void resetClient() {
        clientLayers = new LayerWeather[] {LayerWeather.CALM, LayerWeather.CALM, LayerWeather.CALM};
        clientSalt = 0L;
        clientSynced = false;
    }
}
