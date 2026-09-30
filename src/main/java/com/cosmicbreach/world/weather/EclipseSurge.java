package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Eclipse Surge (GDD 2.5), in the Deep every 60 to 90 minutes (half as often once the Heliarch has
 * fallen): a 20 s warning (Vesper stops pulsing, the sky and the sky light dim, a swell rises into silence),
 * then 120 s in which the Hollow grow stronger and Arcane abilities come cheap.
 *
 * <p>For other tasks, either side:
 * <ul>
 *   <li>{@link #active}: the flag. Hollow mobs deal {@link #hollowDamageMultiplier} (x1.25) and spawn
 *       {@link #hollowSpawnMultiplier} (x1.5) as often; Stalkers shadow-step {@link #stalkerStepMultiplier} (x2)
 *       as far.</li>
 *   <li>Weapon abilities (all Arcane) cost {@link #ARCANE_COST} (x0.75) for players in the Deep; the combat
 *       machine gets it every tick on both sides ({@link #abilityCostScale}).</li>
 *   <li>{@link #onHeliarchFirstDeath}: the Heliarch task calls it when the boss first dies; Surges then come
 *       half as often (saved with the world).</li>
 * </ul>
 */
public final class EclipseSurge {
    public static final double HOLLOW_DAMAGE = 1.25;
    public static final double HOLLOW_SPAWNS = 1.5;
    public static final double STALKER_STEP = 2.0;
    public static final double ARCANE_COST = 0.75;

    private EclipseSurge() {
    }

    /** True while a Surge grips the Deep (either side). */
    public static boolean active(Level level) {
        return CosmicWeather.is(level, Layer.DEEP, WeatherKind.SURGE, Phase.ACTIVE);
    }

    /** True during a Surge's warning (either side). */
    public static boolean warning(Level level) {
        return CosmicWeather.is(level, Layer.DEEP, WeatherKind.SURGE, Phase.WARNING);
    }

    public static double hollowDamageMultiplier(Level level) {
        return active(level) ? HOLLOW_DAMAGE : 1.0;
    }

    public static double hollowSpawnMultiplier(Level level) {
        return active(level) ? HOLLOW_SPAWNS : 1.0;
    }

    public static double stalkerStepMultiplier(Level level) {
        return active(level) ? STALKER_STEP : 1.0;
    }

    /** 0.75 for a player in the Deep during a Surge, else 1 (either side). */
    public static double abilityCostScale(Player player) {
        Level level = player.level();
        return AetheriaWorld.is(level) && Layer.at(player.getY()) == Layer.DEEP && active(level) ? ARCANE_COST : 1.0;
    }

    /** The Heliarch died for the first time: Eclipse Surges come half as often from now on. Server side. */
    public static void onHeliarchFirstDeath(MinecraftServer server) {
        ServerLevel aetheria = server.getLevel(AetheriaWorld.LEVEL);
        if (aetheria != null) {
            WeatherData data = WeatherData.get(aetheria);
            data.schedule().heliarchFell();
            data.setDirty();
        }
    }

    /** True once the Heliarch has fallen in this world. Server side. */
    public static boolean heliarchFallen(MinecraftServer server) {
        ServerLevel aetheria = server.getLevel(AetheriaWorld.LEVEL);
        return aetheria != null && WeatherData.get(aetheria).schedule().heliarchFallen();
    }
}
