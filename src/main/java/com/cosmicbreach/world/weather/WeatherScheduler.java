package com.cosmicbreach.world.weather;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Runs the weather on the server: once a tick in Aetheria, while at least one player is there, the
 * {@link WeatherSchedule} moves on; every change goes to the players in Aetheria as a {@link WeatherNet.Sync};
 * then each event's rules run ({@link SolarFlare#tick}, {@link MeteorShower#tick}).
 */
public final class WeatherScheduler {
    /** Changes seen, newest last (for checks): "layer kind phase at gameTime". */
    private static final List<String> LOG = new ArrayList<>();
    private static long tideEndedAt = Long.MIN_VALUE;

    private WeatherScheduler() {
    }

    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !AetheriaWorld.is(level) || level.players().isEmpty()) {
            return;
        }
        WeatherData data = WeatherData.get(level);
        LayerWeather driftBefore = data.snapshot(Layer.DRIFT);
        int changed = data.schedule().tick(WeatherConfig::intervalTicks, level.getRandom(), WeatherConfig.enabled());
        data.setDirty();
        data.refresh(level.getGameTime());
        if (changed != 0) {
            changed(level, data, changed, driftBefore);
        }
        SolarFlare.tick(level);
        MeteorShower.tick(level, data);
    }

    /** Starts {@code kind} in {@code layer} now (its warning first). */
    public static void start(ServerLevel level, Layer layer, WeatherKind kind) {
        WeatherData data = WeatherData.get(level);
        LayerWeather driftBefore = data.snapshot(Layer.DRIFT);
        float heading = (float) (level.getRandom().nextDouble() * Math.PI * 2.0);
        data.schedule().start(layer, kind, heading, WeatherConfig::intervalTicks, level.getRandom());
        data.setDirty();
        data.refresh(level.getGameTime());
        changed(level, data, 1 << layer.ordinal(), driftBefore);
    }

    /** Ends {@code layer}'s event now. True if it had one. */
    public static boolean clear(ServerLevel level, Layer layer) {
        WeatherData data = WeatherData.get(level);
        LayerWeather driftBefore = data.snapshot(Layer.DRIFT);
        if (!data.schedule().clear(layer)) {
            return false;
        }
        data.setDirty();
        data.refresh(level.getGameTime());
        changed(level, data, 1 << layer.ordinal(), driftBefore);
        return true;
    }

    /** Jumps {@code layer}'s warning to its end (tests and impatient admins). True if it was warning. */
    public static boolean skipWarning(ServerLevel level, Layer layer) {
        WeatherData data = WeatherData.get(level);
        boolean skipped = data.schedule().skipWarning(layer);
        data.setDirty();
        return skipped;
    }

    private static void changed(ServerLevel level, WeatherData data, int layers, LayerWeather driftBefore) {
        long time = level.getGameTime();
        for (Layer layer : Layer.values()) {
            if ((layers & (1 << layer.ordinal())) != 0) {
                LayerWeather w = data.snapshot(layer);
                String entry = layer.name().toLowerCase(Locale.ROOT) + " "
                        + (w.kind() == null ? "calm" : w.kind().id() + " " + w.phase().name().toLowerCase(Locale.ROOT))
                        + " at " + time;
                LOG.add(entry);
                if (LOG.size() > 64) {
                    LOG.remove(0);
                }
                CosmicBreach.LOGGER.debug("[cosmicbreach] weather: {}", entry);
            }
        }
        if (driftBefore.is(WeatherKind.TIDE, Phase.ACTIVE) && !data.snapshot(Layer.DRIFT).is(WeatherKind.TIDE, Phase.ACTIVE)) {
            tideEndedAt = time;
        }
        SolarFlare.weatherChanged(level);
        MeteorShower.weatherChanged(level, data);
        PacketDistributor.sendToPlayersInDimension(level, sync(level, data));
    }

    /** A player arrived in Aetheria (or logged in or respawned there): send them the weather. */
    static void arrived(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel level && AetheriaWorld.is(level)) {
            WeatherData data = WeatherData.get(level);
            data.refresh(level.getGameTime());
            PacketDistributor.sendToPlayer(serverPlayer, sync(level, data));
        }
    }

    private static WeatherNet.Sync sync(ServerLevel level, WeatherData data) {
        List<LayerWeather> layers = new ArrayList<>(3);
        for (Layer layer : Layer.values()) {
            layers.add(data.snapshot(layer));
        }
        return new WeatherNet.Sync(DriftCurrents.salt(level.getSeed()), layers);
    }

    /** Game time the last Gravity Tide ended (fall damage stays off a while after). */
    static long tideEndedAt() {
        return tideEndedAt;
    }

    /** The recent weather changes, oldest first (for checks and {@code weather status}). */
    public static List<String> log() {
        return List.copyOf(LOG);
    }

    static void reset() {
        LOG.clear();
        tideEndedAt = Long.MIN_VALUE;
        MeteorShower.reset();
    }
}
