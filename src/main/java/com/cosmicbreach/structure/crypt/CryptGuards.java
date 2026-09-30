package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.CosmicBreach;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The crypt's hunters: the Hollow Stalker (GDD 7.1) comes from its registry id, so the crypt needs nothing from the
 * task that adds it. Until {@code cosmicbreach:hollow_stalker} is registered, every call here does nothing.
 */
public final class CryptGuards {
    public static final ResourceLocation HOLLOW_STALKER = CosmicBreach.id("hollow_stalker");

    private CryptGuards() {
    }

    public static boolean stalkerRegistered() {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(HOLLOW_STALKER);
    }

    /** A Hollow Stalker at {@code at}, if its entity is registered; empty until then. */
    public static Optional<Entity> stalker(ServerLevel level, Vec3 at) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(HOLLOW_STALKER);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Entity e = type.get().create(level);
        if (e == null) {
            return Optional.empty();
        }
        e.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0f, 0.0f);
        if (e instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        return level.addFreshEntity(e) ? Optional.of(e) : Optional.empty();
    }
}
