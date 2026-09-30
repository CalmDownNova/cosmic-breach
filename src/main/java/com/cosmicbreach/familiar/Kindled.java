package com.cosmicbreach.familiar;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Kindled (GDD 8.2): +10% damage for {@value FamiliarRules#KINDLED_TICKS} ticks, given by an Emberwisp when its owner
 * dodges perfectly or parries. The bonus is a Buffs term of the hit formula ({@link FamiliarCombat}), so it multiplies
 * with every other.
 */
public class Kindled extends MobEffect {
    public static final int COLOR = 0xFFA63A;

    public Kindled() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
