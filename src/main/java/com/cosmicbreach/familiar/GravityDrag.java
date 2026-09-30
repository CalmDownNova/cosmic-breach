package com.cosmicbreach.familiar;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Gravikin's pull on enemies next to it (GDD 8.2): 20% slower while they stay within
 * {@value FamiliarRules#SLOW_RADIUS} blocks of it (put on for {@value FamiliarRules#SLOW_TICKS} ticks and refreshed).
 */
public class GravityDrag extends MobEffect {
    public static final int COLOR = 0x6E7CC8;

    public GravityDrag() {
        super(MobEffectCategory.HARMFUL, COLOR);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, CosmicBreach.id("gravity_drag"), -FamiliarRules.SLOW,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
