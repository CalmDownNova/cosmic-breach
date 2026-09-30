package com.cosmicbreach.client.fx;

import com.cosmicbreach.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Our four particle types' sprites and providers. The effects make particles directly with the
 * factories here (each picks its sprite and sensible defaults) and set them up further; the
 * providers only serve {@code /particle cosmicbreach:...} and anything else that goes through the
 * particle engine by type.
 */
public final class FxParticles {
    private static @Nullable SpriteSet glints;
    private static @Nullable SpriteSet sparks;
    private static @Nullable SpriteSet shards;
    private static @Nullable SpriteSet rings;
    private static final RandomSource RANDOM = RandomSource.create();

    private FxParticles() {
    }

    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.STAR_GLINT.get(), sprites -> {
            glints = sprites;
            return (SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) ->
                    glint(level, new Vec3(x, y, z)).velocity(new Vec3(dx, dy, dz)).size(0.25f, 0.05f).life(10).spin(0.1f);
        });
        event.registerSpriteSet(ModParticles.SPARK.get(), sprites -> {
            sparks = sprites;
            return (SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) ->
                    spark(level, new Vec3(x, y, z)).velocity(new Vec3(dx, dy, dz)).size(0.04f, 0.02f).life(10).drag(0.85f);
        });
        event.registerSpriteSet(ModParticles.SHARD.get(), sprites -> {
            shards = sprites;
            return (SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) ->
                    shard(level, new Vec3(x, y, z)).velocity(new Vec3(dx, dy, dz)).size(0.12f, 0.08f).life(20).gravity(1.0f).spin(0.3f);
        });
        event.registerSpriteSet(ModParticles.RING.get(), sprites -> {
            rings = sprites;
            return (SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) ->
                    ring(level, new Vec3(x, y, z)).size(0.2f, 1.2f).sizeEase(2f).life(8);
        });
    }

    /** True once the sprites are known (always, in game). */
    public static boolean ready() {
        return glints != null && sparks != null && shards != null && rings != null;
    }

    public static FxParticle glint(ClientLevel level, Vec3 at) {
        return new FxParticle(level, at.x, at.y, at.z, glints.get(RANDOM));
    }

    public static FxParticle spark(ClientLevel level, Vec3 at) {
        return new FxParticle(level, at.x, at.y, at.z, sparks.get(RANDOM)).streak(1.6f, 0.08f);
    }

    public static FxParticle shard(ClientLevel level, Vec3 at) {
        return new FxParticle(level, at.x, at.y, at.z, shards.get(RANDOM)).physics();
    }

    public static FxParticle ring(ClientLevel level, Vec3 at) {
        return new FxParticle(level, at.x, at.y, at.z, rings.get(RANDOM));
    }
}
