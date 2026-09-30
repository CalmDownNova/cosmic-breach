package com.cosmicbreach.gear;

import com.cosmicbreach.world.weather.SolarFlare;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Scorch (GDD 8.2): 1 fire damage a second while it lasts (4 s from its sources). A detonation on a
 * Scorched enemy flares for +50% (the Meteor Call checks for it). It burns with the Solar Flare's own
 * damage type ({@link SolarFlare#SCORCH}), so fire resistance and fire immunity stop it and armor doesn't.
 */
public class Scorch extends MobEffect {
    public static final int TICKS = 80;
    public static final int COLOR = 0xFF7A26;

    public Scorch() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(SolarFlare.scorch(entity.level()), SolarFlare.SCORCH_DAMAGE);
        return true;
    }
}
