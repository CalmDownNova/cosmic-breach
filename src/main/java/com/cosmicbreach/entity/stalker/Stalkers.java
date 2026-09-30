package com.cosmicbreach.entity.stalker;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.progression.KillRewards;
import com.cosmicbreach.progression.XpSource;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.status.Statuses;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.light.AbilityLight;
import com.cosmicbreach.world.light.TempLights;
import com.cosmicbreach.world.weather.EclipseSurge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * The Hollow Stalker's registrations (GDD 7.1): the entity {@code cosmicbreach:hollow_stalker} (which also wakes the
 * Hollow Crypt's Stalker spots and Void Pockets, see {@code CryptGuards}), the Mask Shard trophy block, its sounds
 * ({@code tools/sound/stalker.py}; the whispers are the committed takes in {@code tools/sound/eleven/stalker/}), the
 * Grasp's damage type {@code cosmicbreach:hollow_grasp} (through armor), the Rift status, 180 Attunement XP a kill, and
 * its natural spawns: in the Rift Abyss, in the dark (the biome modifier
 * {@code data/cosmicbreach/neoforge/biome_modifier/hollow_stalker_spawns.json}; {@link #canSpawn} keeps them to dark
 * ground and makes them half again as common in an Eclipse Surge). Also the light that answers them: {@link TempLights}
 * and every weapon ability's light ({@link AbilityLight}).
 * Debug: {@code /cosmicbreach debug stalker spawn [distance]|info|ready|rend}.
 */
public final class Stalkers {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    public static final ResourceKey<DamageType> GRASP_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("hollow_grasp"));

    public static final DeferredHolder<EntityType<?>, EntityType<HollowStalker>> HOLLOW_STALKER = ENTITY_TYPES.register("hollow_stalker",
            () -> EntityType.Builder.of(HollowStalker::new, MobCategory.MONSTER)
                    .sized(StalkerRules.WIDTH, StalkerRules.HEIGHT)
                    .eyeHeight((float) StalkerRules.MASK_Y)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(CosmicBreach.id("hollow_stalker").toString()));

    public static final DeferredBlock<MaskShardBlock> MASK_SHARD = BLOCKS.register("mask_shard", () -> new MaskShardBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.0f).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(s -> 3).pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> MASK_SHARD_ITEM = ITEMS.registerSimpleBlockItem(MASK_SHARD,
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER = sound("stalker/whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRASP_WHISPER = sound("stalker/grasp_whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> REND_TELL = sound("stalker/rend_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLINT = sound("stalker/glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> REND = sound("stalker/rend");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRASP = sound("stalker/grasp");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEK = sound("stalker/shriek");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = sound("stalker/step");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("stalker/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("stalker/death");

    private Stalkers() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ENTITY_TYPES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        Statuses.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> event.put(HOLLOW_STALKER.get(), HollowStalker.createAttributes().build()));
        modBus.addListener(RegisterSpawnPlacementsEvent.class, event -> event.register(HOLLOW_STALKER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Stalkers::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(MASK_SHARD_ITEM.get());
            }
        });
        KillRewards.register(HOLLOW_STALKER, XpSource.TRASH_MOB.at(XpSource.LayerTier.DEEP));
        game.addListener(RegisterCommandsEvent.class, event -> registerCommands(event.getDispatcher()));
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer p) {
                StalkerGrasp.forget(p);
            }
        });
        TempLights.register(game);
        AbilityLight.register();
        StalkerGrasp.register();
    }

    /** The Grasp's damage: through armor, from the Stalker. */
    public static DamageSource graspDamage(Entity stalker) {
        Holder<DamageType> type = stalker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(GRASP_DAMAGE);
        return new DamageSource(type, stalker);
    }

    /**
     * Natural spawns: in the Deep (any layer outside Aetheria), on ground, where it is dark to a Stalker and block light
     * is at most {@value StalkerRules#SPAWN_BLOCK_LIGHT}, one attempt in two (three in four in an Eclipse Surge), fewer than
     * {@value StalkerRules#SPAWN_NEIGHBOURS} others within {@value StalkerRules#SPAWN_SPACING} blocks, not on Peaceful.
     * Other reasons (the crypt, commands) always may.
     */
    public static boolean canSpawn(EntityType<HollowStalker> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                   RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        Level l = level.getLevel();
        boolean aetheria = AetheriaWorld.is(l);
        if (aetheria && Layer.at(pos.getY()) != Layer.DEEP) {
            return false;
        }
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int light = StalkerRules.effectiveLight(block, level.getBrightness(LightLayer.SKY, pos), l.getSkyDarken(),
                com.cosmicbreach.world.light.DeepLight.skyScale(l, pos.getY()));
        boolean ground = level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type);
        if (!StalkerRules.spawnRule(ground, light, block, random.nextFloat(), EclipseSurge.hollowSpawnMultiplier(l))) {
            return false;
        }
        // an ambusher, not a swarm: the dark is nearly everywhere in the Deep, so no more than a couple share a stretch of it
        return level.getEntitiesOfClass(HollowStalker.class, new net.minecraft.world.phys.AABB(pos).inflate(StalkerRules.SPAWN_SPACING))
                .size() < StalkerRules.SPAWN_NEIGHBOURS;
    }

    // ------------------------------------------------------------------ debug

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("stalker")
                                .then(Commands.literal("spawn").executes(c -> spawn(c, 8))
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "distance")))))
                                .then(Commands.literal("info").executes(Stalkers::info))
                                .then(Commands.literal("ready").executes(c -> {
                                    HollowStalker s = nearest(c.getSource());
                                    if (s != null) {
                                        s.readyNow();
                                    }
                                    return s == null ? 0 : 1;
                                }))
                                .then(Commands.literal("rend").executes(c -> {
                                    HollowStalker s = nearest(c.getSource());
                                    if (s != null) {
                                        s.rendNow();
                                    }
                                    return s == null ? 0 : 1;
                                })))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 at = player.position().add(Vec3.directionFromRotation(0f, player.getYRot()).scale(distance));
        HollowStalker s = HOLLOW_STALKER.get().create(player.serverLevel());
        if (s == null) {
            return 0;
        }
        s.moveTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
        s.setPersistenceRequired();
        player.serverLevel().addFreshEntity(s);
        return 1;
    }

    static @Nullable HollowStalker nearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HollowStalker best = null;
        double bestD = 64.0 * 64.0;
        for (HollowStalker s : player.serverLevel().getEntitiesOfClass(HollowStalker.class, player.getBoundingBox().inflate(64.0))) {
            double d = s.distanceToSqr(player);
            if (d < bestD) {
                best = s;
                bestD = d;
            }
        }
        if (best == null) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.stalker.none"));
        }
        return best;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        HollowStalker s = nearest(context.getSource());
        if (s == null) {
            return 0;
        }
        String text = String.format(Locale.ROOT, "state %s health %.1f/%.1f light %d counts %s", s.serverState(), s.getHealth(),
                s.getMaxHealth(), StalkerLight.block(s.level(), s.blockPosition()), Arrays.toString(s.counts()));
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }

    /** Block tags for the data run (vanilla tag files are written only by ModBlockTagsProvider). */
    public static void blockTags(java.util.function.BiConsumer<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>,
            net.minecraft.world.level.block.Block[]> tag) {
        tag.accept(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE, new net.minecraft.world.level.block.Block[] {MASK_SHARD.get()});
    }
}
