package com.cosmicbreach.mixin.world;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether a living entity holds jump: a rider's jump key, which vanilla keeps in a protected field (the client's own
 * input, and on the server what the rider's input packets say). The celestial mounts read their rider's with it
 * ({@link com.cosmicbreach.mount.CelestialMount#riderJumping}). Read-only access, nothing changed.
 */
@Mixin(LivingEntity.class)
public interface LivingJumpingAccessor {
    @Accessor("jumping")
    boolean cosmicbreach$jumping();
}
