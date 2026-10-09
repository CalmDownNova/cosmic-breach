package com.cosmicbreach.jelly;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.progression.KillRewards;
import com.cosmicbreach.progression.XpSource;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.Layer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The drift jelly's registrations (1.2 design section 7): the entity {@code cosmicbreach:drift_jelly}, drift gel and
 * candied gel, the Skim effect, the sting's damage type, the spawn placement and the debug commands.
 *
 * <p>Natural spawns: the biome modifier {@code data/cosmicbreach/neoforge/biome_modifier/drift_jelly_spawns.json} adds
 * the jelly to every biome in the tag {@code cosmicbreach:drift_jelly_spawns} (the layer 3 zones), and
 * {@code drift_jelly_dense.json} adds it again to the biomes of {@code cosmicbreach:drift_jelly_dense} (where it hangs over
 * voids as stepping stones). {@link #canSpawn} holds it to layer 3 and chooses where in the air: {@link JellyRules#spawnChance}.
 * Debug: {@code /cosmicbreach debug jelly spawn [distance]|bloom [count]|clear|info}.
 */
public final class Jellies {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);

    public static final ResourceKey<DamageType> STING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CosmicBreach.id("drift_sting"));
    /** The biomes it spawns in, and the ones where it also hangs over voids. */
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME, CosmicBreach.id("drift_jelly_spawns"));
    public static final TagKey<Biome> DENSE_BIOMES = TagKey.create(Registries.BIOME, CosmicBreach.id("drift_jelly_dense"));

    /** Ambient, like the Drift Manta: it never takes up a monster's room and is never a night's problem. */
    public static final DeferredHolder<EntityType<?>, EntityType<DriftJelly>> DRIFT_JELLY = ENTITY_TYPES.register("drift_jelly",
            () -> EntityType.Builder.of(DriftJelly::new, MobCategory.AMBIENT)
                    .sized(JellyRules.WIDTH, JellyRules.HEIGHT)
                    .eyeHeight(JellyRules.EYE_HEIGHT)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .fireImmune()
                    .build(CosmicBreach.id("drift_jelly").toString()));

    public static final DeferredHolder<MobEffect, MobEffect> SKIM = EFFECTS.register("skim", SkimEffect::new);

    /** Raw: a little food, and thirty seconds of night vision. */
    public static final FoodProperties DRIFT_GEL_FOOD = new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f)
            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false, true), 1.0f).build();
    /** Candied: more food, and the glow is cooked out. */
    public static final FoodProperties CANDIED_GEL_FOOD = new FoodProperties.Builder().nutrition(6).saturationModifier(0.6f).build();

    public static final DeferredItem<GelItem> DRIFT_GEL = ITEMS.registerItem("drift_gel",
            p -> new GelItem(p.food(DRIFT_GEL_FOOD), "drift_gel"));
    public static final DeferredItem<GelItem> CANDIED_GEL = ITEMS.registerItem("candied_gel",
            p -> new GelItem(p.food(CANDIED_GEL_FOOD), "candied_gel"));
    public static final DeferredItem<DeferredSpawnEggItem> DRIFT_JELLY_EGG = ITEMS.registerItem("drift_jelly_spawn_egg",
            p -> new DeferredSpawnEggItem(DRIFT_JELLY, 0x2C1A70, 0xEE3EE6, p), new Item.Properties());

    private Jellies() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> event.put(DRIFT_JELLY.get(), DriftJelly.createAttributes().build()));
        modBus.addListener(RegisterSpawnPlacementsEvent.class, event -> event.register(DRIFT_JELLY.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING, Jellies::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                ITEMS.getEntries().forEach(item -> event.accept(item.get()));
            }
        });
        KillRewards.register(DRIFT_JELLY, XpSource.TRASH_MOB.at(XpSource.LayerTier.DEEP));
        game.addListener(RegisterCommandsEvent.class, event -> registerCommands(event.getDispatcher()));
        JellyBounce.register(game);
        JellyBloom.register(game);
    }

    // ------------------------------------------------------------------ spawning

    /**
     * Natural spawns: layer 3 of Aetheria only, at least 8 blocks off its floor, with the bell's room free; then
     * {@link JellyRules#spawnChance} by the ground below (2 to 5 blocks: always; a void: rarely, more in the dense
     * biomes), and fewer than {@value JellyRules#AREA_CAP} natural jellies within {@code 48} blocks. Other reasons (eggs,
     * commands, a bloom) always may.
     */
    public static boolean canSpawn(EntityType<DriftJelly> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        Level l = level.getLevel();
        if (!AetheriaWorld.is(l) || Layer.at(pos.getY()) != Layer.DEEP || pos.getY() < 8) {
            return false;
        }
        boolean dense = level.getBiome(pos).is(DENSE_BIOMES);
        if (random.nextDouble() >= JellyRules.spawnChance(DriftJelly.groundDepth(level, pos), dense)) {
            return false;
        }
        int near = 0;
        for (DriftJelly j : level.getEntitiesOfClass(DriftJelly.class, new AABB(pos).inflate(JellyRules.AREA_RADIUS))) {
            if (!j.inBloom()) {
                near++;
            }
        }
        return !JellyRules.crowded(near);
    }

    // ------------------------------------------------------------------ debug

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("jelly")
                                .then(Commands.literal("spawn").executes(c -> spawn(c, 8))
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "distance")))))
                                .then(Commands.literal("bloom").executes(c -> bloom(c, JellyRules.bloomCount(c.getSource().getLevel().random.nextDouble())))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 60))
                                                .executes(c -> bloom(c, IntegerArgumentType.getInteger(c, "count")))))
                                .then(Commands.literal("clear").executes(Jellies::clear))
                                .then(Commands.literal("info").executes(Jellies::info)))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 at = player.position().add(Vec3.directionFromRotation(0f, player.getYRot()).scale(distance));
        DriftJelly jelly = DRIFT_JELLY.get().create(player.serverLevel());
        if (jelly == null) {
            return 0;
        }
        jelly.moveTo(at.x, at.y + 3.0, at.z, player.getYRot() + 180f, 0f);
        jelly.setPersistenceRequired();
        player.serverLevel().addFreshEntity(jelly);
        return 1;
    }

    private static int bloom(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int n = JellyBloom.start(player.serverLevel(), player.position(), count).size();
        context.getSource().sendSuccess(() -> Component.literal("A bloom of " + n + " drift jellies."), false);
        return n;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int n = 0;
        for (DriftJelly j : player.serverLevel().getEntities(DRIFT_JELLY.get(), e -> true)) {
            j.discard();
            n++;
        }
        return n;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        List<? extends DriftJelly> all = level.getEntities(DRIFT_JELLY.get(), e -> true);
        long bloom = all.stream().filter(DriftJelly::inBloom).count();
        double nanos = all.stream().mapToDouble(DriftJelly::meanStepNanos).average().orElse(0.0);
        String text = String.format(java.util.Locale.ROOT, "%d drift jellies (%d in a bloom), mean tick %.1f microseconds, next bloom in %d ticks", all.size(),
                bloom, nanos / 1000.0, JellyBloom.nextIn(level));
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return all.size();
    }

    // ------------------------------------------------------------------ items

    /** A gel. Its tooltip (what it is, where it comes from, what it is for) comes from the mod's tooltip system and lang file. */
    public static final class GelItem extends Item {
        public GelItem(Properties properties, String id) {
            super(properties.rarity(Rarity.COMMON));
        }
    }

    /** Skim (+30 percent air acceleration; {@link JellyBounce#onPlayerTick} does the work). */
    public static final class SkimEffect extends MobEffect {
        public SkimEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x38E8DA);
        }
    }
}
