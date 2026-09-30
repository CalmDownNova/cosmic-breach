package com.cosmicbreach.world.weather;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The Solar Flare (GDD 2.5), over the Reach every 25 to 35 minutes: a 15 s warning (Solenne brightens, the
 * sky's edge whitens, a hum rises), then 90 s in which anything under open sky takes Scorch, 1 fire damage a
 * second ({@link #SCORCH}: fire resistance and fire immunity protect, armor doesn't); any block overhead is
 * shade. Meanwhile players in the Reach earn Resonance twice as fast, solar damage players deal
 * ({@link #SOLAR}, every fire type) is 25% stronger, and Shardlings (which love light, and never burn: tag
 * {@link #SOLAR_IMMUNE}) run 30% faster in the open.
 *
 * <p>For other tasks, either side: {@link #active}, {@link #exposed} (Star Eggs hatch when exposed to a Flare)
 * and {@link #openSky}.
 */
public final class SolarFlare {
    public static final ResourceKey<DamageType> SCORCH = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("scorch"));
    /** Damage the Flare boosts when a player deals it: {@code #minecraft:is_fire} and anything else solar. */
    public static final TagKey<DamageType> SOLAR = TagKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("solar"));
    /** Creatures Scorch never touches (the Shardling). */
    public static final TagKey<EntityType<?>> SOLAR_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, CosmicBreach.id("solar_immune"));
    public static final ResourceLocation SHARDLING_SPEED_ID = CosmicBreach.id("solar_flare_speed");

    public static final float SCORCH_DAMAGE = 1.0f;
    public static final int SCORCH_INTERVAL_TICKS = 20;
    public static final double RESONANCE_GAIN = 2.0;
    public static final float SOLAR_DAMAGE_MULTIPLIER = 1.25f;
    public static final double SHARDLING_SPEED_BONUS = 0.30;

    private SolarFlare() {
    }

    /** True while a Flare burns over the Reach (either side). */
    public static boolean active(Level level) {
        return CosmicWeather.is(level, Layer.REACH, WeatherKind.FLARE, Phase.ACTIVE);
    }

    /** True during a Flare's warning (either side). */
    public static boolean warning(Level level) {
        return CosmicWeather.is(level, Layer.REACH, WeatherKind.FLARE, Phase.WARNING);
    }

    /** True if nothing that blocks movement (any solid block, glass, leaves, water) is above {@code pos}. */
    public static boolean openSky(Level level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1;
    }

    /** True if {@code pos} is in the Reach under open sky while a Flare burns (either side). */
    public static boolean exposed(Level level, BlockPos pos) {
        return AetheriaWorld.is(level) && Layer.at(pos.getY()) == Layer.REACH && active(level) && openSky(level, pos);
    }

    /** An entity is exposed when its eyes are. */
    public static boolean exposed(Entity entity) {
        return exposed(entity.level(), BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()));
    }

    /** 2 for a player in the Reach during a Flare, else 1 (either side). */
    public static double resonanceGain(Player player) {
        Level level = player.level();
        return AetheriaWorld.is(level) && Layer.at(player.getY()) == Layer.REACH && active(level) ? RESONANCE_GAIN : 1.0;
    }

    public static DamageSource scorch(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SCORCH));
    }

    // ------------------------------------------------------------------ server rules

    /** Scorch once a second: everything living, exposed and not immune. */
    static void tick(ServerLevel level) {
        if (!active(level) || level.getGameTime() % SCORCH_INTERVAL_TICKS != 0) {
            return;
        }
        DamageSource source = scorch(level);
        List<LivingEntity> burning = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living && living.isAlive() && !living.getType().is(SOLAR_IMMUNE)
                    && !(living instanceof Player player && player.isSpectator()) && exposed(living)) {
                burning.add(living);
            }
        }
        // hurt after the walk: a death drops loot, which adds entities
        for (LivingEntity living : burning) {
            living.hurt(source, SCORCH_DAMAGE);
        }
    }

    /** Solar damage a player deals in the Reach during a Flare is 25% stronger. */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide() || !AetheriaWorld.is(level) || !(event.getSource().getEntity() instanceof Player)
                || !event.getSource().is(SOLAR) || Layer.at(victim.getY()) != Layer.REACH || !active(level)) {
            return;
        }
        event.setAmount(event.getAmount() * SOLAR_DAMAGE_MULTIPLIER);
    }

    /** Light-loving mobs ({@link #SOLAR_IMMUNE}) run faster while exposed to a Flare. Server side. */
    static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !mob.getType().is(SOLAR_IMMUNE)) {
            return;
        }
        boolean wanted = mob.isAlive() && exposed(mob);
        setSpeedBonus(mob, wanted);
    }

    /** The Flare changed: drop the speed bonus from everything once it ends (the tick keeps it right while it runs). */
    static void weatherChanged(ServerLevel level) {
        if (active(level)) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.getType().is(SOLAR_IMMUNE)) {
                setSpeedBonus(mob, false);
            }
        }
    }

    private static void setSpeedBonus(Mob mob, boolean on) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean has = speed.hasModifier(SHARDLING_SPEED_ID);
        if (on && !has) {
            speed.addTransientModifier(new AttributeModifier(SHARDLING_SPEED_ID, SHARDLING_SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on && has) {
            speed.removeModifier(SHARDLING_SPEED_ID);
        }
    }
}
