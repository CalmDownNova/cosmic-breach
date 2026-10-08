package com.cosmicbreach.entity.gyre;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.entity.gyre.GyreModes.Mode;
import com.cosmicbreach.progression.KillRewards;
import com.cosmicbreach.progression.XpSource;
import com.cosmicbreach.registry.ModCreativeTab;
import com.cosmicbreach.world.Layer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * The Gyre Knight's registrations (GDD 7.1): the entity ({@code cosmicbreach:gyre_knight}, which also wakes the Gyre
 * Observatory's Knight post), the Gravity Loop (a plain item until the Curios task), its sounds
 * ({@code tools/sound/gyre.py}), 600 Attunement XP a kill, and its natural spawns: rare, in the Drift Belt's air near
 * rock (the biome modifier {@code data/cosmicbreach/neoforge/biome_modifier/gyre_knight_spawns.json} adds it to the
 * biome, {@link #canSpawn} keeps it rare). Debug: {@code /cosmicbreach debug gyre spawn|mode <mode>|hold <ticks>|cooldowns|divenext|info}.
 */
public final class GyreKnights {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);

    /** A natural spawn attempt that passes the position rules still starts a Knight only one time in this many. */
    public static final int RARITY = 6;
    /** No natural Knight within this many blocks of another. */
    public static final double SPACING = 64.0;

    public static final DeferredHolder<EntityType<?>, EntityType<GyreKnight>> GYRE_KNIGHT = ENTITY_TYPES.register("gyre_knight",
            () -> EntityType.Builder.of(GyreKnight::new, MobCategory.MONSTER)
                    .sized(GyreKnight.WIDTH, GyreKnight.HEIGHT)
                    .eyeHeight(1.9f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(CosmicBreach.id("gyre_knight").toString()));

    /** The Gravity Loop, a ring (what it does: {@code accessory/}). */
    public static final DeferredItem<com.cosmicbreach.accessory.AccessoryItem> GRAVITY_LOOP = ITEMS.registerItem("gravity_loop",
            p -> new com.cosmicbreach.accessory.AccessoryItem(p, com.cosmicbreach.accessory.Accessory.GRAVITY_LOOP),
            new Item.Properties().rarity(Rarity.RARE).stacksTo(1));

    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = sound("gyre/hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHINE = sound("gyre/whine");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWEEP = sound("gyre/sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_TELL = sound("gyre/lance_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_FIRE = sound("gyre/lance_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_HIT = sound("gyre/lance_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECALL_TELL = sound("gyre/recall_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECALL = sound("gyre/recall");
    /** A dive's tell (1.1): the rings ring up and a bright call cuts in, about a second, ending as it drops. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVE_TELL = sound("gyre/dive_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_BREAK = sound("gyre/blade_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT = sound("gyre/deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> STUN = sound("gyre/stun");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("gyre/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("gyre/death");

    private GyreKnights() {
    }

    public static void register(IEventBus modBus, IEventBus game) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> event.put(GYRE_KNIGHT.get(), GyreKnight.createAttributes().build()));
        modBus.addListener(RegisterSpawnPlacementsEvent.class, event -> event.register(GYRE_KNIGHT.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING, GyreKnights::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(GRAVITY_LOOP.get());
            }
        });
        KillRewards.register(GYRE_KNIGHT, XpSource.ELITE.at(XpSource.LayerTier.DRIFT));
        game.addListener(RegisterCommandsEvent.class, event -> registerCommands(event.getDispatcher()));
    }

    /**
     * Natural spawns: in the Drift's air (Y 175 to 285) with rock within 12 blocks below, clear headroom, on one attempt
     * in {@value #RARITY}, never within {@value #SPACING} blocks of another Knight, not on Peaceful. Other reasons (the
     * Observatory's post, eggs, commands) always may.
     */
    public static boolean canSpawn(EntityType<GyreKnight> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        return placeRule(pos.getY(), level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.above(2)).isAir(), rockBelow(level, pos), GyreKnight.rare(random))
                && level.getEntitiesOfClass(GyreKnight.class, new AABB(pos).inflate(SPACING)).isEmpty();
    }

    /** The position part of the natural spawn rule, pure. */
    public static boolean placeRule(int y, boolean clear, boolean rockBelow, boolean lucky) {
        return y >= 175 && y <= 285 && Layer.at(y) == Layer.DRIFT && clear && rockBelow && lucky;
    }

    private static boolean rockBelow(ServerLevelAccessor level, BlockPos pos) {
        for (int d = 1; d <= 12; d++) {
            if (!level.getBlockState(pos.below(d)).isAir()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ debug

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var modeNode = Commands.literal("mode");
        for (Mode m : Mode.values()) {
            modeNode.then(Commands.literal(m.name().toLowerCase(Locale.ROOT)).executes(c -> forceMode(c, m)));
        }
        dispatcher.register(Commands.literal("cosmicbreach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("gyre")
                                .then(Commands.literal("spawn").executes(GyreKnights::spawn))
                                .then(Commands.literal("info").executes(GyreKnights::info))
                                .then(Commands.literal("hold").then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> {
                                            GyreKnight k = nearest(c.getSource());
                                            if (k != null) {
                                                k.holdModes(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "ticks"));
                                            }
                                            return k == null ? 0 : 1;
                                        })))
                                .then(Commands.literal("cooldowns").executes(c -> {
                                    GyreKnight k = nearest(c.getSource());
                                    if (k != null) {
                                        k.clearCooldowns();
                                    }
                                    return k == null ? 0 : 1;
                                }))
                                .then(Commands.literal("divenext").executes(c -> {
                                    GyreKnight k = nearest(c.getSource());
                                    if (k != null) {
                                        k.diveNext();
                                    }
                                    return k == null ? 0 : 1;
                                }))
                                .then(modeNode))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 at = player.position().add(Vec3.directionFromRotation(0f, player.getYRot()).scale(8.0)).add(0, 6.0, 0);
        GyreKnight k = GYRE_KNIGHT.get().create(player.serverLevel());
        if (k == null) {
            return 0;
        }
        k.moveTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
        k.setPersistenceRequired();
        player.serverLevel().addFreshEntity(k);
        return 1;
    }

    private static @Nullable GyreKnight nearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GyreKnight best = null;
        double bestD = 96.0 * 96.0;
        for (GyreKnight k : player.serverLevel().getEntitiesOfClass(GyreKnight.class, player.getBoundingBox().inflate(96.0))) {
            double d = k.distanceToSqr(player);
            if (d < bestD) {
                best = k;
                bestD = d;
            }
        }
        if (best == null) {
            source.sendFailure(Component.translatable("commands.cosmicbreach.debug.gyre.none"));
        }
        return best;
    }

    private static int forceMode(CommandContext<CommandSourceStack> context, Mode mode) throws CommandSyntaxException {
        GyreKnight k = nearest(context.getSource());
        if (k == null) {
            return 0;
        }
        k.forceMode(mode);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GyreKnight k = nearest(context.getSource());
        if (k == null) {
            return 0;
        }
        String text = String.format(Locale.ROOT, "mode %s health %.1f/%.1f exposed %s blades %s %s %s counts %s modes %s", k.mode(),
                k.getHealth(), k.getMaxHealth(), k.coreExposed(), k.blade(0), k.blade(1), k.blade(2), Arrays.toString(k.counts()),
                Arrays.toString(k.modesSeen()));
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
