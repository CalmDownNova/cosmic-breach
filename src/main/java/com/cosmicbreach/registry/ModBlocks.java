package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.block.HaloMossBlock;
import com.cosmicbreach.block.HaloMossPlantBlock;
import com.cosmicbreach.block.NeonLichenBlock;
import com.cosmicbreach.block.StarbloomBlock;
import com.cosmicbreach.block.StarbloomCropBlock;
import com.cosmicbreach.block.StrippableLogBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Every block of Aetheria's three layers (GDD 2.2) and the Breach Frame (GDD 1.3), with their items and
 * the "Cosmic Breach Blocks" creative tab. Models, loot, tags, recipes and names come from the data run
 * ({@code datagen/}); textures from {@code tools/art/gen_blocks_*.py}.
 *
 * <p>Tool tiers (tags in {@code ModBlockTagsProvider}): the Reach's ores need a stone pickaxe, Nebulite
 * an iron one, Eclipsium a diamond one. Driftwood is petrified: an axe block, but it does not burn.
 *
 * <p>The tab lists the block items in registration order, so fields are declared in display order.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CosmicBreach.MOD_ID);

    /** A building block with its slab, stairs and (optionally) wall, named after {@code stem}. */
    public record StoneSet(DeferredBlock<Block> base, DeferredBlock<SlabBlock> slab, DeferredBlock<StairBlock> stairs,
            @Nullable DeferredBlock<WallBlock> wall) {}

    private static final List<StoneSet> SETS = new ArrayList<>();

    /** Driftwood's door, trapdoor, button and plate rules and sounds (wood defaults). */
    public static final BlockSetType DRIFTWOOD_SET_TYPE = BlockSetType.register(new BlockSetType(CosmicBreach.MOD_ID + ":driftwood"));
    /** Driftwood's fence gate sounds. Deliberately not registered as a WoodType: there are no Driftwood signs. */
    public static final WoodType DRIFTWOOD_WOOD_TYPE = new WoodType(CosmicBreach.MOD_ID + ":driftwood", DRIFTWOOD_SET_TYPE);

    // ------------------------------------------------------------------ the Upper Reach (T1)

    public static final DeferredBlock<Block> STARFALL_STONE = block("starfall_stone", Block::new,
            stone(MapColor.QUARTZ, 1.5f, 6f, SoundType.CALCITE));
    public static final StoneSet STARFALL_STONE_SET = set(STARFALL_STONE, "starfall_stone", true);
    public static final DeferredBlock<Block> POLISHED_STARFALL_STONE = block("polished_starfall_stone", Block::new,
            stone(MapColor.QUARTZ, 1.5f, 6f, SoundType.CALCITE));
    public static final StoneSet POLISHED_STARFALL_STONE_SET = set(POLISHED_STARFALL_STONE, "polished_starfall_stone", true);
    public static final DeferredBlock<Block> STARFALL_STONE_BRICKS = block("starfall_stone_bricks", Block::new,
            stone(MapColor.QUARTZ, 1.5f, 6f, SoundType.CALCITE));
    public static final StoneSet STARFALL_STONE_BRICK_SET = set(STARFALL_STONE_BRICKS, "starfall_stone_brick", true);

    /** Pale gold grass over Starfall Stone. It does not spread; without Silk Touch it drops Starfall Stone. */
    public static final DeferredBlock<Block> GLIMMER_GRASS = block("glimmer_grass", Block::new,
            stone(MapColor.GOLD, 1.0f, 6f, SoundType.GRASS));
    /** Turquoise crystal: amethyst's sounds and chime, a faint light. */
    public static final DeferredBlock<AmethystBlock> SPIRE_QUARTZ = block("spire_quartz", AmethystBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(1.5f).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().lightLevel(state -> 3));
    public static final DeferredBlock<Block> STARSTEEL_ORE = block("starsteel_ore", Block::new,
            stone(MapColor.QUARTZ, 3f, 3f, SoundType.CALCITE));
    public static final DeferredBlock<Block> STARSTEEL_BLOCK = block("starsteel_block", Block::new,
            metal(MapColor.SAND, 5f, 6f, SoundType.METAL));
    /** Left by Meteor Showers (GDD 2.5): raw Starsteel, 5% a Heartstone; itself only with Silk Touch. */
    public static final DeferredBlock<Block> METEORITE = block("meteorite", Block::new,
            stone(MapColor.TERRACOTTA_BLACK, 4f, 9f, SoundType.ANCIENT_DEBRIS).lightLevel(state -> 3));
    /** The tip of a hanging strand; the strand's body is {@link #HALO_MOSS_PLANT} (no item). */
    public static final DeferredBlock<HaloMossBlock> HALO_MOSS = block("halo_moss", HaloMossBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).randomTicks().noCollission().instabreak()
                    .sound(SoundType.WEEPING_VINES).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<HaloMossPlantBlock> HALO_MOSS_PLANT = BLOCKS.registerBlock("halo_moss_plant",
            HaloMossPlantBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollission().instabreak()
                    .sound(SoundType.WEEPING_VINES).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<StarbloomBlock> STARBLOOM = block("starbloom",
            p -> new StarbloomBlock(MobEffects.SLOW_FALLING, 5f, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FlowerPotBlock> POTTED_STARBLOOM = BLOCKS.registerBlock("potted_starbloom",
            p -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, STARBLOOM, p),
            BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY));
    /** The crop (age 0 to 3); planted from {@link #STARBLOOM_SEEDS}, so it has no block item of its own. */
    public static final DeferredBlock<StarbloomCropBlock> STARBLOOM_CROP = BLOCKS.registerBlock("starbloom_crop",
            StarbloomCropBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks()
                    .instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<ItemNameBlockItem> STARBLOOM_SEEDS = ITEMS.registerItem("starbloom_seeds",
            p -> new ItemNameBlockItem(STARBLOOM_CROP.get(), p));

    // ------------------------------------------------------------------ the Drift (T2)

    public static final DeferredBlock<Block> DRIFTSTONE = block("driftstone", Block::new,
            stone(MapColor.TERRACOTTA_LIGHT_BLUE, 1.8f, 6f, SoundType.TUFF));
    public static final StoneSet DRIFTSTONE_SET = set(DRIFTSTONE, "driftstone", true);
    public static final DeferredBlock<Block> DRIFTSTONE_BRICKS = block("driftstone_bricks", Block::new,
            stone(MapColor.TERRACOTTA_LIGHT_BLUE, 1.8f, 6f, SoundType.TUFF_BRICKS));
    public static final StoneSet DRIFTSTONE_BRICK_SET = set(DRIFTSTONE_BRICKS, "driftstone_brick", true);
    public static final DeferredBlock<Block> NEBULITE_ORE = block("nebulite_ore", Block::new,
            stone(MapColor.TERRACOTTA_LIGHT_BLUE, 3.5f, 3f, SoundType.TUFF));
    public static final DeferredBlock<Block> NEBULITE_BLOCK = block("nebulite_block", Block::new,
            metal(MapColor.COLOR_PURPLE, 5f, 6f, SoundType.METAL));

    public static final DeferredBlock<StrippableLogBlock> DRIFTWOOD_LOG = block("driftwood_log",
            p -> new StrippableLogBlock(ModBlocks.STRIPPED_DRIFTWOOD_LOG, p), wood(2f, 2f));
    public static final DeferredBlock<StrippableLogBlock> DRIFTWOOD_WOOD = block("driftwood_wood",
            p -> new StrippableLogBlock(ModBlocks.STRIPPED_DRIFTWOOD_WOOD, p), wood(2f, 2f));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_DRIFTWOOD_LOG = block("stripped_driftwood_log",
            RotatedPillarBlock::new, wood(2f, 2f));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_DRIFTWOOD_WOOD = block("stripped_driftwood_wood",
            RotatedPillarBlock::new, wood(2f, 2f));
    public static final DeferredBlock<Block> DRIFTWOOD_PLANKS = block("driftwood_planks", Block::new, wood(2f, 3f));
    public static final DeferredBlock<StairBlock> DRIFTWOOD_STAIRS = block("driftwood_stairs",
            p -> new StairBlock(DRIFTWOOD_PLANKS.get().defaultBlockState(), p), wood(2f, 3f));
    public static final DeferredBlock<SlabBlock> DRIFTWOOD_SLAB = block("driftwood_slab", SlabBlock::new, wood(2f, 3f));
    public static final DeferredBlock<FenceBlock> DRIFTWOOD_FENCE = block("driftwood_fence", FenceBlock::new,
            wood(2f, 3f).forceSolidOn());
    public static final DeferredBlock<FenceGateBlock> DRIFTWOOD_FENCE_GATE = block("driftwood_fence_gate",
            p -> new FenceGateBlock(DRIFTWOOD_WOOD_TYPE, p), wood(2f, 3f).forceSolidOn());
    public static final DeferredBlock<DoorBlock> DRIFTWOOD_DOOR = BLOCKS.registerBlock("driftwood_door",
            p -> new DoorBlock(DRIFTWOOD_SET_TYPE, p), wood(3f, 3f).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<DoubleHighBlockItem> DRIFTWOOD_DOOR_ITEM = ITEMS.registerItem("driftwood_door",
            p -> new DoubleHighBlockItem(DRIFTWOOD_DOOR.get(), p));
    public static final DeferredBlock<TrapDoorBlock> DRIFTWOOD_TRAPDOOR = block("driftwood_trapdoor",
            p -> new TrapDoorBlock(DRIFTWOOD_SET_TYPE, p), wood(3f, 3f).noOcclusion().isValidSpawn(Blocks::never));
    public static final DeferredBlock<PressurePlateBlock> DRIFTWOOD_PRESSURE_PLATE = block("driftwood_pressure_plate",
            p -> new PressurePlateBlock(DRIFTWOOD_SET_TYPE, p),
            wood(0.5f, 0.5f).forceSolidOn().noCollission().pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<ButtonBlock> DRIFTWOOD_BUTTON = block("driftwood_button",
            p -> new ButtonBlock(DRIFTWOOD_SET_TYPE, 30, p),
            BlockBehaviour.Properties.of().noCollission().strength(0.5f).pushReaction(PushReaction.DESTROY));

    /** Translucent ice-like crystal. Needs a pickaxe; drops itself. */
    public static final DeferredBlock<TransparentBlock> RIMEGLASS = block("rimeglass", TransparentBlock::new,
            glass(MapColor.ICE, 0.5f));

    // ------------------------------------------------------------------ the Deep (T3)

    /** Columnar basalt (a pillar: it takes the axis it is placed along). */
    public static final DeferredBlock<RotatedPillarBlock> UMBRAL_BASALT = block("umbral_basalt", RotatedPillarBlock::new,
            stone(MapColor.COLOR_BLACK, 2.5f, 6f, SoundType.BASALT));
    public static final DeferredBlock<Block> POLISHED_UMBRAL_BASALT = block("polished_umbral_basalt", Block::new,
            stone(MapColor.COLOR_BLACK, 2.5f, 6f, SoundType.POLISHED_DEEPSLATE));
    public static final StoneSet POLISHED_UMBRAL_BASALT_SET = set(POLISHED_UMBRAL_BASALT, "polished_umbral_basalt", false);
    public static final DeferredBlock<Block> UMBRAL_BASALT_BRICKS = block("umbral_basalt_bricks", Block::new,
            stone(MapColor.COLOR_BLACK, 2.5f, 6f, SoundType.DEEPSLATE_BRICKS));
    public static final StoneSet UMBRAL_BASALT_BRICK_SET = set(UMBRAL_BASALT_BRICKS, "umbral_basalt_brick", true);
    /** Dark glass: like tinted glass it lets no light through. Needs a pickaxe; drops itself. */
    public static final DeferredBlock<TintedGlassBlock> RIFT_GLASS = block("rift_glass", TintedGlassBlock::new,
            glass(MapColor.COLOR_PURPLE, 0.5f));
    public static final DeferredBlock<Block> ECLIPSIUM_ORE = block("eclipsium_ore", Block::new,
            stone(MapColor.COLOR_BLACK, 4.5f, 3f, SoundType.BASALT));
    public static final DeferredBlock<Block> ECLIPSIUM_BLOCK = block("eclipsium_block", Block::new,
            metal(MapColor.COLOR_BLACK, 6f, 12f, SoundType.NETHERITE_BLOCK));
    public static final DeferredBlock<NeonLichenBlock> MAGENTA_NEON_LICHEN = block("magenta_neon_lichen", NeonLichenBlock::new,
            lichen(MapColor.COLOR_MAGENTA));
    public static final DeferredBlock<NeonLichenBlock> TEAL_NEON_LICHEN = block("teal_neon_lichen", NeonLichenBlock::new,
            lichen(MapColor.COLOR_CYAN));

    // ------------------------------------------------------------------ the way in (GDD 1.3)

    /** One piece of a Breach Ring; lit while its ring is open (the ring's logic is W4's, {@code onboarding/}). */
    public static final DeferredBlock<Block> BREACH_FRAME = block("breach_frame", com.cosmicbreach.onboarding.BreachFrameBlock::new,
            stone(MapColor.COLOR_ORANGE, 2f, 6f, SoundType.COPPER));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS_TAB = TABS.register("cosmic_breach_blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cosmicbreach.blocks"))
                    .icon(() -> new ItemStack(STARFALL_STONE_BRICKS.get()))
                    .withTabsBefore(ModCreativeTab.COSMIC_BREACH.getKey())
                    .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(
                () -> ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(STARBLOOM.getId(), POTTED_STARBLOOM)));
    }

    /** Every slab, stairs and wall set, for the data run. */
    public static List<StoneSet> stoneSets() {
        return Collections.unmodifiableList(SETS);
    }

    // ------------------------------------------------------------------ helpers

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
            BlockBehaviour.Properties properties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    private static StoneSet set(DeferredBlock<Block> base, String stem, boolean wall) {
        DeferredBlock<SlabBlock> slab = BLOCKS.register(stem + "_slab",
                () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(base.get())));
        ITEMS.registerSimpleBlockItem(slab);
        DeferredBlock<StairBlock> stairs = BLOCKS.register(stem + "_stairs",
                () -> new StairBlock(base.get().defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(base.get())));
        ITEMS.registerSimpleBlockItem(stairs);
        DeferredBlock<WallBlock> w = null;
        if (wall) {
            w = BLOCKS.register(stem + "_wall",
                    () -> new WallBlock(BlockBehaviour.Properties.ofFullCopy(base.get()).forceSolidOn()));
            ITEMS.registerSimpleBlockItem(w);
        }
        StoneSet set = new StoneSet(base, slab, stairs, w);
        SETS.add(set);
        return set;
    }

    private static BlockBehaviour.Properties stone(MapColor colour, float hardness, float resistance, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(hardness, resistance).sound(sound);
    }

    private static BlockBehaviour.Properties metal(MapColor colour, float hardness, float resistance, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .requiresCorrectToolForDrops().strength(hardness, resistance).sound(sound);
    }

    /** Petrified wood: wood's feel and tools, but not ignited by lava and not flammable. */
    private static BlockBehaviour.Properties wood(float hardness, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.BASS)
                .strength(hardness, resistance).sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties glass(MapColor colour, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.HAT).strength(hardness)
                .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion().isValidSpawn(Blocks::never)
                .isRedstoneConductor(ModBlocks::never).isSuffocating(ModBlocks::never).isViewBlocking(ModBlocks::never);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private static BlockBehaviour.Properties lichen(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).replaceable().noCollission().strength(0.2f)
                .sound(SoundType.GLOW_LICHEN).lightLevel(GlowLichenBlock.emission(7)).ignitedByLava()
                .emissiveRendering((state, level, pos) -> true) // bioluminescent: drawn at full brightness, even in the Deep's dark
                .pushReaction(PushReaction.DESTROY);
    }
}
