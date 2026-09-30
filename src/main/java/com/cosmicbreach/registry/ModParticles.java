package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The combat particles. Their sprites are in {@code assets/cosmicbreach/particles/*.json}; the client's
 * {@code FxParticles} registers their providers. The effects spawn them directly on the client with
 * their own colour, size and motion, so a type only picks the sprite and the defaults.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, CosmicBreach.MOD_ID);

    /** A four-point star glint: hit flashes, crits, parries, perfect dodges, motes. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STAR_GLINT = register("star_glint");
    /** A thin spark drawn as a streak along its motion. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = register("spark");
    /** A tumbling crystal shard (three sprites). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHARD = register("shard");
    /** A thin ring, flat on the ground or facing the camera. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RING = register("ring");

    private ModParticles() {
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
