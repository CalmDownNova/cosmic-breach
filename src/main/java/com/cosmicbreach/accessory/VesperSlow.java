package com.cosmicbreach.accessory;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Vector3f;

/**
 * The Hourglass of Vesper's slow (GDD 5.2): 70% off walking and flying speed while it lasts. It slows the enemy, not
 * time, so it is safe in multiplayer. Its motes are dusk-gold sand.
 */
public class VesperSlow extends MobEffect {
    public static final int COLOR = 0xE8C27A;

    public VesperSlow() {
        super(MobEffectCategory.HARMFUL, COLOR, new DustParticleOptions(new Vector3f(0.95f, 0.78f, 0.45f), 0.9f));
        addAttributeModifier(Attributes.MOVEMENT_SPEED, CosmicBreach.id("vesper_slow"), -AccessoryRules.HOURGLASS_SLOW,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FLYING_SPEED, CosmicBreach.id("vesper_slow_flying"), -AccessoryRules.HOURGLASS_SLOW,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
