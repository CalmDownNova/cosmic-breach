package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import com.cosmicbreach.world.weather.WeatherSchedule.Slot;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The weather saved with the Aetheria level ({@code data/cosmicbreach_weather.dat} in its folder): each
 * layer's event and phase with the ticks spent in it, every slot's countdown, and whether the Heliarch has
 * fallen. Also keeps this tick's {@link LayerWeather} snapshots, which the server's rules read.
 */
public final class WeatherData extends SavedData {
    public static final String NAME = "cosmicbreach_weather";
    private static final SavedData.Factory<WeatherData> FACTORY = new SavedData.Factory<>(WeatherData::new, WeatherData::load);

    private final WeatherSchedule schedule = new WeatherSchedule();
    private final LayerWeather[] snapshots = {LayerWeather.CALM, LayerWeather.CALM, LayerWeather.CALM};

    public static WeatherData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public WeatherSchedule schedule() {
        return schedule;
    }

    public LayerWeather snapshot(Layer layer) {
        return snapshots[layer.ordinal()];
    }

    /** Rebuilds the snapshots for {@code gameTime} (after the schedule changed or ticked). */
    public void refresh(long gameTime) {
        for (Layer layer : Layer.values()) {
            snapshots[layer.ordinal()] = LayerWeather.of(schedule.now(layer), gameTime);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag layers = new CompoundTag();
        for (Layer layer : Layer.values()) {
            WeatherSchedule.Now now = schedule.now(layer);
            CompoundTag t = new CompoundTag();
            t.putString("kind", now.kind() == null ? "" : now.kind().id());
            t.putString("phase", now.phase().name().toLowerCase(Locale.ROOT));
            t.putInt("elapsed", now.elapsed());
            t.putInt("length", now.length());
            t.putFloat("heading", now.heading());
            layers.put(layer.name().toLowerCase(Locale.ROOT), t);
        }
        tag.put("layers", layers);
        CompoundTag next = new CompoundTag();
        for (Slot slot : Slot.values()) {
            next.putInt(slot.id(), schedule.countdown(slot));
        }
        tag.put("next", next);
        tag.putBoolean("heliarch_fallen", schedule.heliarchFallen());
        return tag;
    }

    private static WeatherData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeatherData data = new WeatherData();
        WeatherSchedule s = data.schedule;
        CompoundTag layers = tag.getCompound("layers");
        for (Layer layer : Layer.values()) {
            CompoundTag t = layers.getCompound(layer.name().toLowerCase(Locale.ROOT));
            WeatherKind kind = WeatherKind.byId(t.getString("kind"));
            Phase phase = switch (t.getString("phase")) {
                case "warning" -> Phase.WARNING;
                case "active" -> Phase.ACTIVE;
                default -> Phase.IDLE;
            };
            if (kind != null && kind.canHappenIn(layer)) {
                s.now(layer).set(kind, phase, t.getInt("elapsed"), t.getInt("length"), t.getFloat("heading"));
            }
        }
        CompoundTag next = tag.getCompound("next");
        for (Slot slot : Slot.values()) {
            s.setCountdown(slot, next.contains(slot.id()) ? next.getInt(slot.id()) : -1);
        }
        s.setHeliarchFallen(tag.getBoolean("heliarch_fallen"));
        return data;
    }
}
