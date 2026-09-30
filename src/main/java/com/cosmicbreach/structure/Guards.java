package com.cosmicbreach.structure;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.entity.shardling.Shardling;
import com.cosmicbreach.entity.shardling.ShardlingPack;
import com.cosmicbreach.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The structures' guards: a Shardling pack sharing one brain (the Reliquary's halls, the Warden Eye), and the
 * Gyre Knight, which comes from its registry id so the Observatory needs nothing from the task that adds it.
 */
public final class Guards {
    /** The elite of the Drift Belt (GDD 7.1), registered by a later task. */
    public static final net.minecraft.resources.ResourceLocation GYRE_KNIGHT = CosmicBreach.id("gyre_knight");

    private Guards() {
    }

    /** {@code count} Shardlings round {@code at}, facing {@code toward}, one pack. */
    public static List<Entity> shardlings(ServerLevel level, Vec3 at, Vec3 toward, int count) {
        ShardlingPack pack = new ShardlingPack();
        List<Entity> spawned = new ArrayList<>();
        float yaw = (float) (Mth.atan2(toward.z - at.z, toward.x - at.x) * Mth.RAD_TO_DEG) - 90.0f;
        for (int i = 0; i < count; i++) {
            Shardling shardling = ModEntities.SHARDLING.get().create(level);
            if (shardling == null) {
                continue;
            }
            double angle = count == 1 ? 0.0 : i * Math.PI * 2.0 / count;
            double spread = count == 1 ? 0.0 : 1.2;
            shardling.moveTo(at.x + Math.cos(angle) * spread, at.y, at.z + Math.sin(angle) * spread, yaw, 0.0f);
            shardling.setYHeadRot(yaw);
            shardling.setYBodyRot(yaw);
            shardling.joinPack(pack);
            shardling.setPersistenceRequired();
            if (level.addFreshEntity(shardling)) {
                spawned.add(shardling);
            }
        }
        return spawned;
    }

    /** True once a Gyre Knight exists to be spawned. */
    public static boolean knightRegistered() {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(GYRE_KNIGHT);
    }

    /** A Gyre Knight at {@code at}, if its entity is registered; empty until then. */
    public static Optional<Entity> gyreKnight(ServerLevel level, Vec3 at) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(GYRE_KNIGHT);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Entity knight = type.get().create(level);
        if (knight == null) {
            return Optional.empty();
        }
        knight.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0f, 0.0f);
        if (knight instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        return level.addFreshEntity(knight) ? Optional.of(knight) : Optional.empty();
    }
}
