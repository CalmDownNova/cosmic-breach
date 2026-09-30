package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.entity.edges.ThrownSickle;
import com.cosmicbreach.entity.shardling.ShardFragment;
import com.cosmicbreach.entity.shardling.ShardNeedle;
import com.cosmicbreach.entity.shardling.Shardling;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The entities: the Shardling (GDD section 7.1), its spit needles and the shards it breaks into. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);

    /** A little bigger than a fox. Position updates every 2 ticks, so its short bursts and lunges read smoothly. */
    public static final DeferredHolder<EntityType<?>, EntityType<Shardling>> SHARDLING = ENTITY_TYPES.register("shardling",
            () -> EntityType.Builder.of(Shardling::new, MobCategory.MONSTER)
                    .sized(Shardling.BASE_WIDTH * Shardling.SCALE, Shardling.BASE_HEIGHT * Shardling.SCALE)
                    .eyeHeight(Shardling.BASE_EYE_HEIGHT * Shardling.SCALE)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build(CosmicBreach.id("shardling").toString()));

    /** Shard Spit's needle. Lives under a second and is never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<ShardNeedle>> SHARD_NEEDLE = ENTITY_TYPES.register("shard_needle",
            () -> EntityType.Builder.<ShardNeedle>of(ShardNeedle::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .noSave()
                    .build(CosmicBreach.id("shard_needle").toString()));

    /** One of the three shards a dead Shardling breaks into; bursts 30 ticks later. Never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<ShardFragment>> SHARD_FRAGMENT = ENTITY_TYPES.register("shard_fragment",
            () -> EntityType.Builder.<ShardFragment>of(ShardFragment::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(4)
                    .updateInterval(2)
                    .noSave()
                    .build(CosmicBreach.id("shard_fragment").toString()));

    /** The Binary Edges' thrown left sickle (the Tether). Moves itself on both sides; never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSickle>> THROWN_SICKLE = ENTITY_TYPES.register("thrown_sickle",
            () -> EntityType.Builder.<ThrownSickle>of(ThrownSickle::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .noSave()
                    .build(CosmicBreach.id("thrown_sickle").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class,
                event -> event.put(SHARDLING.get(), Shardling.createAttributes().build()));
    }
}
