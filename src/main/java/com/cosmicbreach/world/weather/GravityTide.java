package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.AetheriaGravity;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * The Gravity Tide (GDD 2.5), in the Drift every 20 to 30 minutes: a 10 s warning (the dust streams all
 * swing to one heading), then 45 s of gravity at {@value #GRAVITY}x (through {@link AetheriaGravity}, the same
 * answer on both sides), a current carrying everything {@value #CURRENT} blocks a tick along the heading
 * ({@link WeatherPush}), and no fall damage (nor for {@value #FALL_GRACE_TICKS} ticks after, for those
 * still coming down).
 */
public final class GravityTide {
    public static final double GRAVITY = 0.15;
    public static final double CURRENT = 0.06;
    public static final int FALL_GRACE_TICKS = 200;

    private GravityTide() {
    }

    /** True while a Tide runs in the Drift (either side). */
    public static boolean active(Level level) {
        return CosmicWeather.is(level, Layer.DRIFT, WeatherKind.TIDE, Phase.ACTIVE);
    }

    /** True during a Tide's warning (either side). */
    public static boolean warning(Level level) {
        return CosmicWeather.is(level, Layer.DRIFT, WeatherKind.TIDE, Phase.WARNING);
    }

    /** The Tide's heading in radians while it runs, else NaN (either side). */
    public static double heading(Level level) {
        LayerWeather w = CosmicWeather.get(level, Layer.DRIFT);
        return w.is(WeatherKind.TIDE, Phase.ACTIVE) ? w.heading() : Double.NaN;
    }

    /** {@link AetheriaGravity}'s modifier: the Drift's 0.4x becomes 0.15x while a Tide runs. */
    static double modify(Level level, double y, double multiplier) {
        if (Layer.at(y) != Layer.DRIFT || !active(level)) {
            return multiplier;
        }
        return multiplier * GRAVITY / AetheriaGravity.DRIFT;
    }

    /** No fall damage in the Drift during a Tide and shortly after. Server side. */
    static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide() || !AetheriaWorld.is(level) || Layer.at(entity.getY()) != Layer.DRIFT) {
            return;
        }
        long ended = WeatherScheduler.tideEndedAt();
        boolean grace = ended != Long.MIN_VALUE && level.getGameTime() - ended <= FALL_GRACE_TICKS;
        if (active(level) || grace) {
            event.setCanceled(true);
        }
    }
}
