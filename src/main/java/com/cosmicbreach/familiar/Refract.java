package com.cosmicbreach.familiar;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Refract (GDD 8.2), a light status the Prism Moth primes: stacks to {@value RefractStacks#MAX} (the amplifier plus
 * one), each new stack restarting its {@value RefractStacks#TICKS} ticks; at three, the next ability hit on it
 * consumes them for +30% ({@link FamiliarCombat}). It does nothing by itself. No swirl of potion particles: the client
 * draws a prism shard over the target for each stack instead.
 */
public class Refract extends MobEffect {
    public static final int COLOR = 0xBFEFFF;

    public Refract() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /** One more stack on {@code target} (server side); returns the stacks it has now. */
    public static int addStack(LivingEntity target) {
        int stacks = RefractStacks.after(stacks(target));
        target.removeEffect(FamiliarRegistry.REFRACT); // a stronger instance over a weaker one keeps the old time otherwise
        target.addEffect(new MobEffectInstance(FamiliarRegistry.REFRACT, RefractStacks.TICKS, stacks - 1, false, false, true));
        return stacks;
    }

    /** The stacks of Refract on {@code target} now (0 for none). */
    public static int stacks(LivingEntity target) {
        MobEffectInstance current = target.getEffect(FamiliarRegistry.REFRACT);
        return current == null ? 0 : current.getAmplifier() + 1;
    }

    /** Takes every stack off {@code target}; returns how many there were. */
    public static int consume(LivingEntity target) {
        int n = stacks(target);
        if (n > 0) {
            target.removeEffect(FamiliarRegistry.REFRACT);
        }
        return n;
    }
}
