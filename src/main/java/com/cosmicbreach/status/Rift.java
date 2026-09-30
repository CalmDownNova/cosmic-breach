package com.cosmicbreach.status;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Rift (GDD 8.2), a void status: -15% armor per stack for 100 ticks, up to 3 stacks ({@link RiftStacks}). The stack
 * count is the effect's amplifier plus one, and the armor modifier scales with it (vanilla multiplies an effect's
 * modifier by amplifier + 1). {@link #addStack} is the one way in.
 */
public class Rift extends MobEffect {
    public static final int COLOR = 0x9A2BD6;

    public Rift() {
        super(MobEffectCategory.HARMFUL, COLOR);
        addAttributeModifier(Attributes.ARMOR, CosmicBreach.id("rift"), -RiftStacks.ARMOR_PER_STACK,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** One more stack of Rift on {@code target} (server side); returns the stacks it has now. */
    public static int addStack(LivingEntity target) {
        MobEffectInstance current = target.getEffect(Statuses.RIFT);
        int stacks = RiftStacks.after(current == null ? 0 : current.getAmplifier() + 1);
        target.removeEffect(Statuses.RIFT); // replacing a weaker instance with a stronger one keeps the old duration otherwise
        target.addEffect(new MobEffectInstance(Statuses.RIFT, RiftStacks.TICKS, stacks - 1, false, true, true));
        return stacks;
    }

    /** The stacks of Rift on {@code target} now (0 for none). */
    public static int stacks(LivingEntity target) {
        MobEffectInstance current = target.getEffect(Statuses.RIFT);
        return current == null ? 0 : current.getAmplifier() + 1;
    }
}
