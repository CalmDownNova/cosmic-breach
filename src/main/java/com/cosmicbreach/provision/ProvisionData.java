package com.cosmicbreach.provision;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModMaterials;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The provisions' generated files (1.1): the Umbral Cap's state, model and drop, every item model, and the recipes (the two
 * pickaxes from Starsteel or Nebulite and sticks; glass bottles from Spire Quartz; shears from Starsteel; torches from Neon
 * Lichen; Rime Thread from Rimeglass; Driftwood planks from Halo Moss; venison and fillets seared in a furnace, a smoker or on a campfire). Tags go through
 * {@link ProvisionRegistry#itemTags} and {@link ProvisionRegistry#blockTags} into the shared tag providers.
 */
public final class ProvisionData {
    private ProvisionData() {
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        generator.addProvider(event.includeClient(), new Models(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), named("Provision loot tables", new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK)), lookup)));
        generator.addProvider(event.includeServer(), named("Provision recipes", new Recipes(output, lookup)));
    }

    /** Vanilla's recipe and loot providers have final names and the data run refuses two of one name: wrap them. */
    private static DataProvider named(String name, DataProvider inner) {
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput output) {
                return inner.run(output);
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    /** Block states and models. */
    public static final class Models extends BlockStateProvider {
        public Models(PackOutput output, ExistingFileHelper files) {
            super(output, CosmicBreach.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            simpleBlock(ProvisionRegistry.UMBRAL_CAP.get(), models().cross("umbral_cap", modLoc("block/umbral_cap")).renderType("cutout"));
            itemModels().withExistingParent("umbral_cap", mcLoc("item/generated")).texture("layer0", modLoc("block/umbral_cap"));
            for (Item item : new Item[] {ProvisionRegistry.HALO_BERRIES.get(), ProvisionRegistry.LUMEN_VENISON.get(),
                    ProvisionRegistry.SEARED_LUMEN_VENISON.get(), ProvisionRegistry.MANTA_FILLET.get(),
                    ProvisionRegistry.SEARED_MANTA_FILLET.get(), ProvisionRegistry.STARHIDE.get(), ProvisionRegistry.RIME_THREAD.get()}) {
                itemModels().basicItem(item);
            }
            itemModels().handheldItem(ProvisionRegistry.STARSTEEL_PICKAXE.get());
            itemModels().handheldItem(ProvisionRegistry.NEBULITE_PICKAXE.get());
        }

        @Override
        public String getName() {
            return "Block States: " + CosmicBreach.MOD_ID + " provisions";
        }
    }

    /** The Umbral Cap drops itself. */
    public static final class Loot extends BlockLootSubProvider {
        public Loot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(ProvisionRegistry.UMBRAL_CAP.get());
        }

        @Override
        protected void generate() {
            dropSelf(ProvisionRegistry.UMBRAL_CAP.get());
        }
    }

    public static final class Recipes extends RecipeProvider {
        public Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            pickaxe(out, ProvisionRegistry.STARSTEEL_PICKAXE.get(), ModMaterials.STARSTEEL_INGOT.get());
            pickaxe(out, ProvisionRegistry.NEBULITE_PICKAXE.get(), ModMaterials.NEBULITE_INGOT.get());
            ShapedRecipeBuilder.shaped(RecipeCategory.BREWING, Items.GLASS_BOTTLE, 3)
                    .define('#', ModBlocks.SPIRE_QUARTZ.get())
                    .pattern("# #")
                    .pattern(" # ")
                    .unlockedBy(getHasName(ModBlocks.SPIRE_QUARTZ.get()), has(ModBlocks.SPIRE_QUARTZ.get()))
                    .save(out, CosmicBreach.id("glass_bottle_from_spire_quartz"));
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Items.SHEARS)
                    .define('#', ModMaterials.STARSTEEL_INGOT.get())
                    .pattern(" #")
                    .pattern("# ")
                    .unlockedBy(getHasName(ModMaterials.STARSTEEL_INGOT.get()), has(ModMaterials.STARSTEEL_INGOT.get()))
                    .save(out, CosmicBreach.id("shears_from_starsteel"));
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Items.TORCH, 4)
                    .define('L', ProvisionRegistry.NEON_LICHENS)
                    .define('#', Tags.Items.RODS_WOODEN)
                    .pattern("L")
                    .pattern("#")
                    .unlockedBy("has_neon_lichen", has(ProvisionRegistry.NEON_LICHENS))
                    .save(out, CosmicBreach.id("torch_from_neon_lichen"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ProvisionRegistry.RIME_THREAD.get(), 4)
                    .requires(ModBlocks.RIMEGLASS.get())
                    .unlockedBy(getHasName(ModBlocks.RIMEGLASS.get()), has(ModBlocks.RIMEGLASS.get()))
                    .save(out);
            // an explored island has no fallen log (those lie in chunks generated since 1.1): its moss, pressed, is its first wood
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.DRIFTWOOD_PLANKS.get(), 2)
                    .requires(ModBlocks.HALO_MOSS.get())
                    .group("planks")
                    .unlockedBy(getHasName(ModBlocks.HALO_MOSS.get()), has(ModBlocks.HALO_MOSS.get()))
                    .save(out, CosmicBreach.id("driftwood_planks_from_halo_moss"));
            sear(out, ProvisionRegistry.LUMEN_VENISON.get(), ProvisionRegistry.SEARED_LUMEN_VENISON.get());
            sear(out, ProvisionRegistry.MANTA_FILLET.get(), ProvisionRegistry.SEARED_MANTA_FILLET.get());
        }

        private static void pickaxe(RecipeOutput out, ItemLike pick, ItemLike metal) {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, pick)
                    .define('X', metal)
                    .define('#', Tags.Items.RODS_WOODEN)
                    .pattern("XXX")
                    .pattern(" # ")
                    .pattern(" # ")
                    .unlockedBy(getHasName(metal), has(metal))
                    .save(out);
        }

        /** Like vanilla's meats: 200 ticks in a furnace, 100 in a smoker, 600 on a campfire. */
        private static void sear(RecipeOutput out, ItemLike raw, ItemLike cooked) {
            String name = BuiltInRegistries.ITEM.getKey(cooked.asItem()).getPath();
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(raw), RecipeCategory.FOOD, cooked, 0.35f, 200)
                    .unlockedBy(getHasName(raw), has(raw)).save(out, CosmicBreach.id(name));
            SimpleCookingRecipeBuilder.smoking(Ingredient.of(raw), RecipeCategory.FOOD, cooked, 0.35f, 100)
                    .unlockedBy(getHasName(raw), has(raw)).save(out, CosmicBreach.id(name + "_from_smoking"));
            SimpleCookingRecipeBuilder.campfireCooking(Ingredient.of(raw), RecipeCategory.FOOD, cooked, 0.35f, 600)
                    .unlockedBy(getHasName(raw), has(raw)).save(out, CosmicBreach.id(name + "_from_campfire_cooking"));
        }
    }
}
