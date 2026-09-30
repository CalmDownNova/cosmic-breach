package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.GuardianRegistry;
import com.cosmicbreach.registry.ModCreativeTab;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything the Thalassine Leviathan registers (Thalassine Leviathan design v1): the Rift's bell (the altar, a
 * {@link com.cosmicbreach.guardian.GuardianAltarBlock} sharing the guardians' altar block entity), the coil's
 * walkable back and the bridges that grow out to it during a Moorage (both vanish on their own when no Moorage holds
 * them), the Halo of Nine (a plain item until the Curios task), the Leviathan, its shed scales, the Leviathan Rift
 * structure and piece, and the sounds ({@code tools/sound/leviathan.py}).
 */
public final class LeviathanRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, CosmicBreach.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, CosmicBreach.MOD_ID);

    private static BlockBehaviour.Properties lair(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3_600_000.0f).noLootTable().sound(sound)
                .pushReaction(PushReaction.BLOCK);
    }

    // ------------------------------------------------------------------ blocks

    public static final DeferredBlock<RiftBellBlock> RIFT_BELL = BLOCKS.registerBlock("rift_bell", RiftBellBlock::new,
            lair(MapColor.ICE, SoundType.AMETHYST).noOcclusion().lightLevel(s -> 15));
    /**
     * The Rift's singing crystal: Rimeglass that glows (the central asteroid's geode breaking through, the platforms' rims
     * and undersides, the posts round the bell). Taken from the Rift it falls silent and drops plain Rimeglass.
     */
    public static final DeferredBlock<Block> SINGING_RIMEGLASS = BLOCKS.registerBlock("singing_rimeglass", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(0.5f).sound(SoundType.AMETHYST).lightLevel(s -> 13)
                    .emissiveRendering((s, l, p) -> true));
    public static final DeferredBlock<MoorageBlock> LEVIATHAN_COIL = BLOCKS.registerBlock("leviathan_coil", MoorageBlock::coil,
            lair(MapColor.COLOR_BLUE, SoundType.MOSS).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isValidSpawn((s, l, p, t) -> false));
    public static final DeferredBlock<MoorageBlock> RIFT_BRIDGE = BLOCKS.registerBlock("rift_bridge", MoorageBlock::bridge,
            lair(MapColor.WOOD, SoundType.WOOD).isValidSpawn((s, l, p, t) -> false));

    // ------------------------------------------------------------------ items

    /** The Halo of Nine, a charm (what it does: {@code accessory/}). */
    public static final DeferredItem<com.cosmicbreach.accessory.AccessoryItem> HALO_OF_NINE = ITEMS.registerItem("halo_of_nine",
            p -> new com.cosmicbreach.accessory.AccessoryItem(p, com.cosmicbreach.accessory.Accessory.HALO_OF_NINE),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
    public static final DeferredItem<BlockItem> RIFT_BELL_ITEM = ITEMS.registerSimpleBlockItem(RIFT_BELL);
    public static final DeferredItem<BlockItem> SINGING_RIMEGLASS_ITEM = ITEMS.registerSimpleBlockItem(SINGING_RIMEGLASS);

    // ------------------------------------------------------------------ entities

    /** The Leviathan: its own hit box is its head; never saved (its bell raises a fresh one when the lair loads). */
    public static final DeferredHolder<EntityType<?>, EntityType<ThalassineLeviathan>> THALASSINE_LEVIATHAN = ENTITY_TYPES.register(
            "thalassine_leviathan", () -> EntityType.Builder.of(ThalassineLeviathan::new, MobCategory.MONSTER)
                    .sized(LeviathanMoves.PART_WIDTH[0], LeviathanMoves.PART_HEIGHT[0])
                    .eyeHeight(LeviathanMoves.PART_HEIGHT[0] * 0.6f)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .noSave()
                    .build(CosmicBreach.id("thalassine_leviathan").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<ShedScale>> SHED_SCALE = ENTITY_TYPES.register("shed_scale",
            () -> EntityType.Builder.<ShedScale>of(ShedScale::new, MobCategory.MISC)
                    .sized(0.9f, 0.9f)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .noSave()
                    .build(CosmicBreach.id("shed_scale").toString()));

    // ------------------------------------------------------------------ the lair's structure

    public static final Supplier<StructureType<LeviathanRiftStructure>> LEVIATHAN_RIFT_TYPE = STRUCTURE_TYPES.register("leviathan_rift",
            () -> () -> LeviathanRiftStructure.CODEC);
    public static final Supplier<StructurePieceType> LEVIATHAN_RIFT_PIECE = STRUCTURE_PIECES.register("leviathan_rift",
            () -> (StructurePieceType.ContextlessType) LeviathanRiftPiece::new);

    // ------------------------------------------------------------------ sounds (tools/sound/leviathan.py)

    public static final DeferredHolder<SoundEvent, SoundEvent> SONG = sound("leviathan/song");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWELL = sound("leviathan/swell");
    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKEN = sound("leviathan/awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVE = sound("leviathan/dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULL = sound("leviathan/pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE = sound("leviathan/bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICK_TELL = sound("leviathan/flick_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLINT = sound("leviathan/glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICK = sound("leviathan/flick");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROOP = sound("leviathan/droop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHED = sound("leviathan/shed");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_POP = sound("leviathan/scale_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> COIL = sound("leviathan/coil");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIPPLE = sound("leviathan/ripple");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHUDDER = sound("leviathan/shudder");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEAR_FREE = sound("leviathan/tear_free");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK = sound("leviathan/break");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("leviathan/hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLAND_HIT = sound("leviathan/gland_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("leviathan/death");
    public static final DeferredHolder<SoundEvent, SoundEvent> PEARL = sound("leviathan/pearl");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL = sound("leviathan/bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> GROW = sound("leviathan/grow");
    /** The boss loop, 100 BPM round the whale song, retriggered on the Vesper clock's bars by the client. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_LEVIATHAN = sound("music/leviathan");

    private LeviathanRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        SOUNDS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(THALASSINE_LEVIATHAN.get(), ThalassineLeviathan.createAttributes().build());
            event.put(SHED_SCALE.get(), ShedScale.createAttributes().build());
        });
        // the bell is a guardian altar: it shares the altar's block entity
        modBus.addListener(BlockEntityTypeAddBlocksEvent.class, event -> event.modify(GuardianRegistry.ALTAR_ENTITY.get(), RIFT_BELL.get()));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(ModCreativeTab.COSMIC_BREACH.getKey())) {
                event.accept(HALO_OF_NINE.get());
            }
        });
    }

    /** The lair blocks' tags, for the data run: the bell, the coil and the grown bridges are wither- and dragon-proof. */
    public static void blockTags(BiConsumer<TagKey<Block>, Block[]> tag) {
        Block[] lair = {RIFT_BELL.get(), LEVIATHAN_COIL.get(), RIFT_BRIDGE.get()};
        tag.accept(BlockTags.WITHER_IMMUNE, lair);
        tag.accept(BlockTags.DRAGON_IMMUNE, lair);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicBreach.id(name)));
    }
}
