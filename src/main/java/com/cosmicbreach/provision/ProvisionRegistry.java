package com.cosmicbreach.provision;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModMaterials;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The levels' provisions (1.1, the Twilight Forest rule: after the portal nobody has to go home for anything the mod
 * asks for). Food in every level: Halo Berries off Halo Moss and Lumen Venison from the stags (level 1), Manta Fillets
 * (level 2), the Umbral Cap that grows in the Deep's dark (levels 3 and 4); venison and fillets sear in a furnace.
 * Native materials joining the common tags the mod's recipes now take: Starhide (from stags and mantas) is leather,
 * Rime Thread (spun from Rimeglass) and Umbral Silk are string, Starsteel gilds where gold does
 * ({@link #GILDING}). Two pickaxes: Starsteel mines what iron mines (Nebulite), Nebulite what diamond mines (Eclipsium).
 * Starfall Stone, Driftstone and Umbral Basalt make stone tools and furnaces; fallen Driftwood on the Reach
 * ({@link FallenDriftwoodFeature}) gives planks, sticks, a crafting table and fuel.
 */
public final class ProvisionRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicBreach.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicBreach.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, CosmicBreach.MOD_ID);

    /** Gold or what serves for it (Starsteel), for the recipes that gild. */
    public static final TagKey<Item> GILDING = TagKey.create(Registries.ITEM, CosmicBreach.id("gilding"));
    /** Both Neon Lichens: torches and fuel. */
    public static final TagKey<Item> NEON_LICHENS = TagKey.create(Registries.ITEM, CosmicBreach.id("neon_lichens"));

    // ------------------------------------------------------------------ food (nutrition, saturation modifier)

    public static final FoodProperties HALO_BERRIES_FOOD = new FoodProperties.Builder().nutrition(2).saturationModifier(0.4f).fast().build();
    public static final FoodProperties LUMEN_VENISON_FOOD = new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build();
    public static final FoodProperties SEARED_VENISON_FOOD = new FoodProperties.Builder().nutrition(8).saturationModifier(0.8f).build();
    public static final FoodProperties MANTA_FILLET_FOOD = new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).build();
    public static final FoodProperties SEARED_FILLET_FOOD = new FoodProperties.Builder().nutrition(6).saturationModifier(0.8f).build();
    public static final FoodProperties UMBRAL_CAP_FOOD = new FoodProperties.Builder().nutrition(5).saturationModifier(0.6f).build();

    public static final DeferredItem<Item> HALO_BERRIES = food("halo_berries", HALO_BERRIES_FOOD);
    public static final DeferredItem<Item> LUMEN_VENISON = food("lumen_venison", LUMEN_VENISON_FOOD);
    public static final DeferredItem<Item> SEARED_LUMEN_VENISON = food("seared_lumen_venison", SEARED_VENISON_FOOD);
    public static final DeferredItem<Item> MANTA_FILLET = food("manta_fillet", MANTA_FILLET_FOOD);
    public static final DeferredItem<Item> SEARED_MANTA_FILLET = food("seared_manta_fillet", SEARED_FILLET_FOOD);

    /** The Deep's mushroom: eaten raw whenever you are hungry, planted on basalt when you sneak or are not hungry ({@link UmbralCapItem}). */
    public static final DeferredBlock<UmbralCapBlock> UMBRAL_CAP = BLOCKS.registerBlock("umbral_cap", UmbralCapBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission().instabreak().randomTicks()
                    .sound(SoundType.FUNGUS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<UmbralCapItem> UMBRAL_CAP_ITEM = ITEMS.registerItem("umbral_cap",
            p -> new UmbralCapItem(UMBRAL_CAP.get(), p.food(UMBRAL_CAP_FOOD)));

    // ------------------------------------------------------------------ materials

    /** Leather from the Reach's stags and the Drift's mantas (in {@code c:leathers}). */
    public static final DeferredItem<Item> STARHIDE = ITEMS.registerSimpleItem("starhide");
    /** String spun from Rimeglass (in {@code c:strings}). */
    public static final DeferredItem<Item> RIME_THREAD = ITEMS.registerSimpleItem("rime_thread");

    // ------------------------------------------------------------------ the pickaxes

    /** Iron's tier: mines Nebulite. */
    public static final Tier STARSTEEL_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 250, 6.0f, 2.0f, 14,
            () -> Ingredient.of(ModMaterials.STARSTEEL_INGOT.get()));
    /** Diamond's tier: mines Eclipsium. */
    public static final Tier NEBULITE_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1000, 7.5f, 2.5f, 12,
            () -> Ingredient.of(ModMaterials.NEBULITE_INGOT.get()));
    public static final DeferredItem<PickaxeItem> STARSTEEL_PICKAXE = ITEMS.registerItem("starsteel_pickaxe",
            p -> new PickaxeItem(STARSTEEL_TIER, p.attributes(PickaxeItem.createAttributes(STARSTEEL_TIER, 1.0f, -2.8f))));
    public static final DeferredItem<PickaxeItem> NEBULITE_PICKAXE = ITEMS.registerItem("nebulite_pickaxe",
            p -> new PickaxeItem(NEBULITE_TIER, p.attributes(PickaxeItem.createAttributes(NEBULITE_TIER, 1.0f, -2.8f))));

    // ------------------------------------------------------------------ worldgen

    public static final DeferredHolder<Feature<?>, FallenDriftwoodFeature> FALLEN_DRIFTWOOD = FEATURES.register("fallen_driftwood",
            FallenDriftwoodFeature::new);

    private ProvisionRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
    }

    private static DeferredItem<Item> food(String name, FoodProperties food) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().food(food));
    }

    /** The item tags this feature adds to (written by {@code ModItemTagsProvider} in the data run): items, and tags in tags. */
    public static void itemTags(BiConsumer<TagKey<Item>, Item[]> items, BiConsumer<TagKey<Item>, TagKey<Item>> tags) {
        items.accept(Tags.Items.LEATHERS, new Item[] {STARHIDE.get()});
        items.accept(Tags.Items.STRINGS, new Item[] {RIME_THREAD.get(), ModMaterials.UMBRAL_SILK.get()});
        items.accept(GILDING, new Item[] {ModMaterials.STARSTEEL_INGOT.get()});
        tags.accept(GILDING, Tags.Items.INGOTS_GOLD);
        items.accept(NEON_LICHENS, new Item[] {ModBlocks.MAGENTA_NEON_LICHEN.get().asItem(), ModBlocks.TEAL_NEON_LICHEN.get().asItem()});
        Item[] stone = {ModBlocks.STARFALL_STONE.get().asItem(), ModBlocks.DRIFTSTONE.get().asItem(), ModBlocks.UMBRAL_BASALT.get().asItem()};
        items.accept(ItemTags.STONE_TOOL_MATERIALS, stone);
        items.accept(ItemTags.STONE_CRAFTING_MATERIALS, stone);
        items.accept(Tags.Items.FOODS, new Item[] {HALO_BERRIES.get(), LUMEN_VENISON.get(), SEARED_LUMEN_VENISON.get(), MANTA_FILLET.get(),
                SEARED_MANTA_FILLET.get(), UMBRAL_CAP_ITEM.get()});
        items.accept(Tags.Items.FOODS_BERRY, new Item[] {HALO_BERRIES.get()});
        items.accept(Tags.Items.FOODS_RAW_MEAT, new Item[] {LUMEN_VENISON.get()});
        items.accept(Tags.Items.FOODS_COOKED_MEAT, new Item[] {SEARED_LUMEN_VENISON.get()});
        items.accept(Tags.Items.FOODS_RAW_FISH, new Item[] {MANTA_FILLET.get()});
        items.accept(Tags.Items.FOODS_COOKED_FISH, new Item[] {SEARED_MANTA_FILLET.get()});
        items.accept(Tags.Items.FOODS_VEGETABLE, new Item[] {UMBRAL_CAP_ITEM.get()});
        Item[] picks = {STARSTEEL_PICKAXE.get(), NEBULITE_PICKAXE.get()};
        items.accept(ItemTags.PICKAXES, picks);
        items.accept(ItemTags.CLUSTER_MAX_HARVESTABLES, picks);
        items.accept(Tags.Items.MINING_TOOL_TOOLS, picks);
    }

    /** The block tags this feature adds to (written by {@code ModBlockTagsProvider} in the data run). */
    public static void blockTags(BiConsumer<TagKey<Block>, Block[]> tag) {
        tag.accept(UmbralCapBlock.SOIL, new Block[] {ModBlocks.UMBRAL_BASALT.get(), ModBlocks.POLISHED_UMBRAL_BASALT.get(),
                ModBlocks.UMBRAL_BASALT_BRICKS.get()});
        tag.accept(BlockTags.SWORD_EFFICIENT, new Block[] {UMBRAL_CAP.get()});
    }
}
