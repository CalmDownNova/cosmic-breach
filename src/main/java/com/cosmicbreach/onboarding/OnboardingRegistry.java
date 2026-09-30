package com.cosmicbreach.onboarding;

import com.cosmicbreach.CosmicBreach;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Everything the way in registers (W4, GDD 1.3): the Starfall Shard (block and item), the Breach, the Codex
 * and its torn page, their block entities, sounds ({@code tools/sound/onboarding.py}), the per-player
 * {@link OnboardingState} and the Fallen Rift structure. The Breach Frame itself is W1's
 * ({@code ModBlocks.BREACH_FRAME}), made a {@link BreachFrameBlock}.
 */
public final class OnboardingRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, CosmicBreach.MOD_ID);

    // ------------------------------------------------------------------ blocks

    /** The fallen star: glows (light 15), breaks by hand at once, drops a Starfall Shard. */
    public static final DeferredBlock<StarfallShardBlock> STARFALL_SHARD_BLOCK = BLOCKS.register("starfall_shard",
            () -> new StarfallShardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).instabreak()
                    .lightLevel(state -> 15).emissiveRendering((state, level, pos) -> true).noOcclusion()
                    .sound(SoundType.AMETHYST_CLUSTER).pushReaction(PushReaction.DESTROY).isValidSpawn(Blocks::never)));

    /** The open ring's middle: a hole with Aetheria's sky under it. Only a broken frame closes it. */
    public static final DeferredBlock<BreachBlock> BREACH = BLOCKS.register("breach",
            () -> new BreachBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).noCollission()
                    .strength(-1.0f, 3_600_000.0f).noLootTable().lightLevel(state -> 11).noOcclusion()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never).sound(SoundType.AMETHYST)));

    // ------------------------------------------------------------------ items

    public static final DeferredItem<StarfallShardItem> STARFALL_SHARD = ITEMS.registerItem("starfall_shard",
            StarfallShardItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<CodexItem> STARFALL_CODEX = ITEMS.registerItem("starfall_codex",
            CodexItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<TornPageItem> TORN_CODEX_PAGE = ITEMS.registerItem("torn_codex_page",
            TornPageItem::new, new Item.Properties().stacksTo(16));

    // ------------------------------------------------------------------ block entities

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarfallShardBlockEntity>> STARFALL_SHARD_ENTITY =
            BLOCK_ENTITIES.register("starfall_shard",
                    () -> BlockEntityType.Builder.of(StarfallShardBlockEntity::new, STARFALL_SHARD_BLOCK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BreachBlockEntity>> BREACH_ENTITY =
            BLOCK_ENTITIES.register("breach", () -> BlockEntityType.Builder.of(BreachBlockEntity::new, BREACH.get()).build(null));

    // ------------------------------------------------------------------ sounds

    /** The streak across the sky (played by each client along the streak's path). */
    public static final DeferredHolder<SoundEvent, SoundEvent> STARFALL_STREAK = sound("onboarding/starfall_streak");
    /** The impact: heard to 128 blocks (sounds.json gives it the matching attenuation distance). */
    public static final DeferredHolder<SoundEvent, SoundEvent> STARFALL_BOOM = SOUNDS.register("onboarding/starfall_boom",
            () -> SoundEvent.createFixedRangeEvent(CosmicBreach.id("onboarding/starfall_boom"), 128.0f));
    /** The shard humming where it lies. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD_HUM = sound("onboarding/shard_hum");
    /** A ring opening. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_ACTIVATE = sound("onboarding/ring_activate");
    /** The wind rising out of an open Breach. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BREACH_WIND = sound("onboarding/breach_wind");
    /** Falling up. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FALL_UP = sound("onboarding/fall_up");

    // ------------------------------------------------------------------ player state

    public static final Supplier<AttachmentType<OnboardingState>> STATE = ATTACHMENTS.register("onboarding",
            () -> AttachmentType.builder(() -> OnboardingState.NEW).serialize(OnboardingState.CODEC).copyOnDeath().build());

    // ------------------------------------------------------------------ the Fallen Rift

    public static final DeferredHolder<StructureType<?>, StructureType<FallenRiftStructure>> FALLEN_RIFT =
            STRUCTURE_TYPES.register("fallen_rift", () -> () -> FallenRiftStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> FALLEN_RIFT_PIECE =
            STRUCTURE_PIECES.register("fallen_rift", () -> (StructurePieceType.ContextlessType) FallenRiftPiece::new);

    private OnboardingRegistry() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
