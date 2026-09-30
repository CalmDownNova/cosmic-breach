package com.cosmicbreach.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.net.ModNetworking;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The celestial mounts (GDD 8.1): the Lumen Stag and the Drift Manta, their gear, the Resonance Chime, their sounds
 * ({@code tools/sound/mounts.py}), the inventory menu, natural spawns (biome modifiers in
 * {@code data/cosmicbreach/neoforge/biome_modifier/}: herds on the Sunfield Terraces, lone mantas in the Drift Belt's
 * air), Phase Blink's server side and the debug commands ({@link MountCommands}). Client side:
 * {@code client.mount.MountsClient}. Forge recipes are plain JSON in {@code data/cosmicbreach/recipe/forge/}.
 */
public final class Mounts {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CosmicBreach.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<LumenStag>> LUMEN_STAG = ENTITY_TYPES.register("lumen_stag",
            () -> EntityType.Builder.of(LumenStag::new, MobCategory.CREATURE)
                    .sized(1.25f, 2.05f)
                    .eyeHeight(1.95f)
                    .passengerAttachments(1.42f)
                    .clientTrackingRange(10)
                    .build(CosmicBreach.id("lumen_stag").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<DriftManta>> DRIFT_MANTA = ENTITY_TYPES.register("drift_manta",
            () -> EntityType.Builder.of(DriftManta::new, MobCategory.AMBIENT)
                    .sized(1.9f, 0.8f)
                    .eyeHeight(0.55f)
                    .passengerAttachments(0.62f)
                    .clientTrackingRange(10)
                    .build(CosmicBreach.id("drift_manta").toString()));

    public static final DeferredItem<MountGearItem> ASTRAL_SADDLE = gear(MountGear.ASTRAL_SADDLE, Rarity.UNCOMMON);
    public static final DeferredItem<MountGearItem> STARSTEEL_BARDING = gear(MountGear.STARSTEEL_BARDING, Rarity.UNCOMMON);
    public static final DeferredItem<MountGearItem> NEBULITE_BARDING = gear(MountGear.NEBULITE_BARDING, Rarity.RARE);
    public static final DeferredItem<MountGearItem> COMET_BRIDLE = gear(MountGear.COMET_BRIDLE, Rarity.RARE);
    public static final DeferredItem<MountGearItem> HALO_REINS = gear(MountGear.HALO_REINS, Rarity.RARE);
    public static final DeferredItem<MountGearItem> DRIFT_HARNESS = gear(MountGear.DRIFT_HARNESS, Rarity.UNCOMMON);
    public static final DeferredItem<MountGearItem> NEBULA_REINS = gear(MountGear.NEBULA_REINS, Rarity.RARE);
    public static final DeferredItem<MountGearItem> GALE_FINS = gear(MountGear.GALE_FINS, Rarity.RARE);
    public static final DeferredItem<ResonanceChimeItem> RESONANCE_CHIME = ITEMS.registerItem("resonance_chime", ResonanceChimeItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<DeferredSpawnEggItem> LUMEN_STAG_EGG = ITEMS.registerItem("lumen_stag_spawn_egg",
            p -> new DeferredSpawnEggItem(LUMEN_STAG, 0xF4F1E8, 0x7FE3E0, p), new Item.Properties());
    public static final DeferredItem<DeferredSpawnEggItem> DRIFT_MANTA_EGG = ITEMS.registerItem("drift_manta_spawn_egg",
            p -> new DeferredSpawnEggItem(DRIFT_MANTA, 0xDCE6EE, 0x6FD8F0, p), new Item.Properties());

    public static final DeferredHolder<MenuType<?>, MenuType<MountMenu>> MENU = MENUS.register("celestial_mount",
            () -> IMenuTypeExtension.create(MountMenu::fromNetwork));

    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_HOOF = sound("mount/stag_hoof");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_CALL = sound("mount/stag_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAG_HURT = sound("mount/stag_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANTA_NOTE = sound("mount/manta_note");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANTA_HURT = sound("mount/manta_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANTA_SOUR = sound("mount/manta_sour");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME = sound("mount/chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE_BLINK = sound("mount/phase_blink");
    public static final DeferredHolder<SoundEvent, SoundEvent> TAMED = sound("mount/tamed");

    /** A natural manta spawn attempt that passes the position rule starts one only one time in this many. */
    public static final int MANTA_RARITY = 4;
    public static final double MANTA_SPACING = 48.0;

    private Mounts() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(LUMEN_STAG.get(), LumenStag.createAttributes().build());
            event.put(DRIFT_MANTA.get(), DriftManta.createAttributes().build());
        });
        modBus.addListener(RegisterSpawnPlacementsEvent.class, event -> {
            event.register(LUMEN_STAG.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mounts::canStagSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            event.register(DRIFT_MANTA.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
                    Mounts::canMantaSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        });
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                for (DeferredItem<?> item : List.of(ASTRAL_SADDLE, STARSTEEL_BARDING, NEBULITE_BARDING, COMET_BRIDLE, HALO_REINS,
                        DRIFT_HARNESS, NEBULA_REINS, GALE_FINS, RESONANCE_CHIME, LUMEN_STAG_EGG, DRIFT_MANTA_EGG)) {
                    event.accept(item.get());
                }
            }
        });
        modBus.addListener(RegisterPayloadHandlersEvent.class, Mounts::registerPayloads);
        game.addListener(LivingIncomingDamageEvent.class, Mounts::onIncomingDamage);
        game.addListener(RegisterCommandsEvent.class, event -> MountCommands.register(event.getDispatcher()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        registrar.playToServer(MountActionPayload.TYPE, MountActionPayload.STREAM_CODEC, Mounts::onAction);
        registrar.playToClient(MountFxPayload.TYPE, MountFxPayload.STREAM_CODEC,
                (payload, context) -> com.cosmicbreach.client.mount.MountsClient.fx(payload, context));
    }

    // ------------------------------------------------------------------ spawning

    /**
     * Herds on the Sunfield Terraces' grass and stone (any grass elsewhere), not in the dark, and (spawning on their
     * own) never at an island's rim: ground within {@value StagRules#CLIFF} blocks below all round, three blocks out.
     */
    public static boolean canStagSpawn(EntityType<LumenStag> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                       RandomSource random) {
        var ground = level.getBlockState(pos.below());
        boolean grass = ground.is(ModBlocks.GLIMMER_GRASS.get()) || ground.is(ModBlocks.STARFALL_STONE.get())
                || ground.is(BlockTags.ANIMALS_SPAWNABLE_ON);
        if (!grass || reason == MobSpawnType.NATURAL && level.getRawBrightness(pos, 0) <= 8) {
            return false;
        }
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                if (!groundBelow(level, pos.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean groundBelow(ServerLevelAccessor level, BlockPos at) {
        for (int i = -2; i <= StagRules.CLIFF; i++) {
            if (!level.getBlockState(at.below(i)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Natural manta spawns: in the Drift's open air (three blocks clear up and down), one attempt in
     * {@value #MANTA_RARITY}, never within {@value #MANTA_SPACING} blocks of another. Other reasons always may.
     */
    public static boolean canMantaSpawn(EntityType<DriftManta> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                        RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        if (Layer.at(pos.getY()) != Layer.DRIFT || pos.getY() < Layer.DRIFT.bandMinY + 6 || pos.getY() > Layer.DRIFT.bandMaxY - 6) {
            return false;
        }
        for (int dy = -3; dy <= 3; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) {
                return false;
            }
        }
        return random.nextInt(MANTA_RARITY) == 0
                && level.getEntitiesOfClass(DriftManta.class, new AABB(pos).inflate(MANTA_SPACING)).isEmpty();
    }

    // ------------------------------------------------------------------ the Chime

    /** A Chime use (server): the manta calling with this player answers it, or the nearest calm wild one starts. */
    public static boolean chime(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<DriftManta> near = level.getEntitiesOfClass(DriftManta.class, player.getBoundingBox().inflate(DriftManta.CALL_LEASH),
                m -> m.isAlive() && !m.isTamed());
        for (DriftManta m : near) {
            if (m.callerId() == player.getId() && m.callPhase() != MantaCall.Phase.IDLE && m.callPhase() != MantaCall.Phase.RETREAT) {
                return m.onChime(player);
            }
        }
        near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));
        for (DriftManta m : near) {
            if (m.callPhase() == MantaCall.Phase.IDLE && m.callerId() < 0 && m.onChime(player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Phase Blink, server side

    private static void onAction(MountActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        if (payload.action() != MountActionPayload.BLINK || !(player.getVehicle() instanceof DriftManta manta)
                || manta.getControllingPassenger() != player) {
            return;
        }
        long now = manta.level().getGameTime();
        int cooldown = MantaRules.blinkCooldown(manta.wears(MountGear.NEBULA_REINS));
        Vec3 to = payload.to();
        boolean ready = now - manta.blinkAt() >= cooldown - 2; // two ticks for the clocks' skew
        boolean near = to.distanceTo(manta.position()) <= MantaRules.BLINK_DISTANCE + 1.5;
        if (!ready || !near || !manta.isAlive()) {
            player.connection.send(new ClientboundMoveVehiclePacket(manta)); // back where the server has it
            return;
        }
        manta.blinkAccepted(now);
        Vec3 from = payload.from();
        manta.level().playSound(null, from.x, from.y, from.z, PHASE_BLINK.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        ModNetworking.sendToTrackers(manta, new MountFxPayload(MountFxPayload.BLINK, manta.getId(), from, to));
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 0.4, to.z, 12, 0.8, 0.3, 0.8, 0.02);
    }

    /** Phase Blink's shield: nothing hurts the manta or its rider for its six ticks (the void and /kill still do). */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        DriftManta manta = target instanceof DriftManta m ? m : target.getVehicle() instanceof DriftManta m ? m : null;
        if (manta != null && manta.shielded()) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The nearest living mount of {@code type} to {@code player} within 64 blocks, or null. */
    public static <T extends CelestialMount> T nearest(ServerPlayer player, Class<T> type) {
        return player.serverLevel().getEntitiesOfClass(type, player.getBoundingBox().inflate(64.0), CelestialMount::isAlive).stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElse(null);
    }

    /** A fresh herd id for stags spawned together by a command. */
    public static UUID newHerd() {
        return UUID.randomUUID();
    }

    private static DeferredItem<MountGearItem> gear(MountGear gear, Rarity rarity) {
        Item.Properties props = new Item.Properties().stacksTo(1).rarity(rarity);
        if (gear.armor() > 0) {
            props = props.attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ARMOR, new AttributeModifier(CosmicBreach.id(gear.id()), gear.armor(), AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.BODY)
                    .build());
        }
        return ITEMS.registerItem(gear.id(), p -> new MountGearItem(p, gear), props);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }

    /** True inside the Drift of Aetheria at {@code y}. */
    public static boolean inDrift(net.minecraft.world.level.Level level, double y) {
        return AetheriaWorld.is(level) && Layer.at(y) == Layer.DRIFT;
    }
}
