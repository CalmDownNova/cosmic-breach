package com.cosmicbreach.structure.sanctum;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.structure.vault.VaultBlock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything the Breach Sanctum registers (W7, GDD 6.1 and 7.3): its stone (darkened ivory and tarnished gilt, the
 * last embers of Solenne, Rift Glass lit from within, voidscorched stone), the Choir Pillars, the Gate, the Throne
 * Seal and the two Eclipse Locks, the Regent's Throne, the wings' vault; the Dying Star Heart and the two charms
 * (plain items until the accessories task); Voidsick; the sounds ({@code tools/sound/sanctum.py}); the structure, its
 * piece and its one-per-world placement. Every block of the Sanctum is unbreakable in survival (the Gate must be the
 * way in) and drops nothing. Textures and models come from {@code tools/art/gen_sanctum.py}; names are in
 * {@code assets/cosmicbreach_sanctum/lang/en_us.json}.
 */
public final class SanctumRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, CosmicBreach.MOD_ID);

    public static final ResourceKey<Structure> BREACH_SANCTUM = ResourceKey.create(Registries.STRUCTURE, CosmicBreach.id("breach_sanctum"));
    public static final ResourceKey<LootTable> LENS_VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, CosmicBreach.id("vaults/sanctum_lens"));
    public static final ResourceKey<LootTable> CHOIR_VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, CosmicBreach.id("vaults/sanctum_choir"));

    // ------------------------------------------------------------------ blocks

    private static BlockBehaviour.Properties sanctum(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable().sound(sound)
                .pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never);
    }

    public static final DeferredBlock<Block> SANCTUM_IVORY = block("sanctum_ivory", Block::new,
            sanctum(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));
    public static final DeferredBlock<Block> SANCTUM_IVORY_BRICKS = block("sanctum_ivory_bricks", Block::new,
            sanctum(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));
    public static final DeferredBlock<StairBlock> SANCTUM_IVORY_STAIRS = block("sanctum_ivory_stairs",
            p -> new StairBlock(SANCTUM_IVORY.get().defaultBlockState(), p), sanctum(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));
    public static final DeferredBlock<SlabBlock> SANCTUM_IVORY_SLAB = block("sanctum_ivory_slab", SlabBlock::new,
            sanctum(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));
    public static final DeferredBlock<Block> SANCTUM_GILT = block("sanctum_gilt", Block::new,
            sanctum(MapColor.GOLD, SoundType.METAL));
    public static final DeferredBlock<Block> SANCTUM_EMBER = block("sanctum_ember", Block::new,
            sanctum(MapColor.COLOR_ORANGE, SoundType.AMETHYST).lightLevel(s -> 15).emissiveRendering((s, l, p) -> true));
    public static final DeferredBlock<Block> SANCTUM_RIFT_LAMP = block("sanctum_rift_lamp", Block::new,
            sanctum(MapColor.COLOR_PURPLE, SoundType.GLASS).lightLevel(s -> 13));
    public static final DeferredBlock<Block> SANCTUM_UMBRAL = block("sanctum_umbral", Block::new,
            sanctum(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<RotatedPillarBlock> CHOIR_PILLAR = block("choir_pillar", RotatedPillarBlock::new,
            sanctum(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));
    public static final DeferredBlock<SanctumGateBlock> SANCTUM_GATE = block("sanctum_gate", SanctumGateBlock::new,
            sanctum(MapColor.GOLD, SoundType.METAL).noOcclusion().dynamicShape().isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false).lightLevel(s -> s.getValue(SanctumGateBlock.OPEN) ? 11 : 4));
    public static final DeferredBlock<Block> THRONE_SEAL = block("throne_seal", Block::new,
            sanctum(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES).lightLevel(s -> 3));
    public static final DeferredBlock<EclipseLockBlock> ECLIPSE_LOCK = block("eclipse_lock", EclipseLockBlock::new,
            sanctum(MapColor.COLOR_BLACK, SoundType.METAL).lightLevel(s -> s.getValue(EclipseLockBlock.LIT) ? 15 : 2));
    public static final DeferredBlock<SanctumThroneBlock> SANCTUM_THRONE = block("sanctum_throne", SanctumThroneBlock::new,
            sanctum(MapColor.GOLD, SoundType.CALCITE).noOcclusion().lightLevel(s -> s.getValue(SanctumThroneBlock.HEART) ? 15 : 7));
    public static final DeferredBlock<VaultBlock> SANCTUM_VAULT = block("sanctum_vault", p -> new VaultBlock(p, 3),
            sanctum(MapColor.GOLD, SoundType.VAULT).noOcclusion().lightLevel(s -> s.getValue(VaultBlock.READY) ? 12 : 4));

    // ------------------------------------------------------------------ items

    public static final DeferredItem<DyingStarHeartItem> DYING_STAR_HEART = ITEMS.registerItem("dying_star_heart", DyingStarHeartItem::new,
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant());
    /** The Event Horizon Lens, a charm (what it does: {@code accessory/}). */
    public static final DeferredItem<com.cosmicbreach.accessory.AccessoryItem> EVENT_HORIZON_LENS = ITEMS.registerItem("event_horizon_lens",
            p -> new com.cosmicbreach.accessory.AccessoryItem(p, com.cosmicbreach.accessory.Accessory.EVENT_HORIZON_LENS),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
    /** The Hourglass of Vesper, a charm (what it does: {@code accessory/}). */
    public static final DeferredItem<com.cosmicbreach.accessory.AccessoryItem> HOURGLASS_OF_VESPER = ITEMS.registerItem("hourglass_of_vesper",
            p -> new com.cosmicbreach.accessory.AccessoryItem(p, com.cosmicbreach.accessory.Accessory.HOURGLASS_OF_VESPER),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));

    // ------------------------------------------------------------------ Voidsick

    public static final DeferredHolder<MobEffect, Voidsick> VOIDSICK = EFFECTS.register("voidsick", Voidsick::new);

    // ------------------------------------------------------------------ sounds (tools/sound/sanctum.py)

    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_OPEN = sound("sanctum/gate_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_REFUSE = sound("sanctum/gate_refuse");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCK_LIT = sound("sanctum/lock_lit");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAIR_OPEN = sound("sanctum/stair_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_HUM = sound("sanctum/throne_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> FALL_RESCUE = sound("sanctum/fall_rescue");

    // ------------------------------------------------------------------ the structure

    public static final DeferredHolder<StructureType<?>, StructureType<SanctumStructure>> SANCTUM_TYPE = STRUCTURE_TYPES.register(
            "breach_sanctum", () -> () -> SanctumStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> SANCTUM_PIECE = STRUCTURE_PIECES.register(
            "breach_sanctum", () -> (StructurePieceType.ContextlessType) SanctumPiece::new);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<SanctumPlacement>> SANCTUM_PLACEMENT =
            PLACEMENTS.register("breach_sanctum", () -> () -> SanctumPlacement.CODEC);

    private SanctumRegistry() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        PLACEMENTS.register(modBus);
    }

    /** Every Sanctum block. */
    public static Block[] blocks() {
        return BLOCKS.getEntries().stream().map(h -> (Block) h.get()).toArray(Block[]::new);
    }

    /** The Sanctum's tags, for the data run: none of it yields to a wither or the dragon. */
    public static void blockTags(BiConsumer<TagKey<Block>, Block[]> tag) {
        tag.accept(BlockTags.WITHER_IMMUNE, blocks());
        tag.accept(BlockTags.DRAGON_IMMUNE, blocks());
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
            BlockBehaviour.Properties properties) {
        return BLOCKS.registerBlock(name, factory, properties);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
