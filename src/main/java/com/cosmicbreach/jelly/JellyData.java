package com.cosmicbreach.jelly;

import com.cosmicbreach.CosmicBreach;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The drift jelly's generated files: the two gels' item models and the cooking recipes (drift gel to candied gel: 200 ticks in
 * a furnace, 100 in a smoker, 600 on a campfire, like the levels' other food, {@code ProvisionData}).
 */
public final class JellyData {
    private JellyData() {
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        generator.addProvider(event.includeClient(), new Models(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), named("Drift jelly recipes", new Recipes(output, lookup)));
    }

    /** Vanilla's recipe provider has a final name and the data run refuses two of one name: wrap it. */
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

    public static final class Models extends ItemModelProvider {
        public Models(PackOutput output, ExistingFileHelper files) {
            super(output, CosmicBreach.MOD_ID, files);
        }

        @Override
        protected void registerModels() {
            basicItem(Jellies.DRIFT_GEL.get());
            basicItem(Jellies.CANDIED_GEL.get());
        }

        @Override
        public String getName() {
            return "Item Models: " + CosmicBreach.MOD_ID + " drift jelly";
        }
    }

    public static final class Recipes extends RecipeProvider {
        public Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            candy(out, Jellies.DRIFT_GEL.get(), Jellies.CANDIED_GEL.get());
        }

        private static void candy(RecipeOutput out, ItemLike raw, ItemLike cooked) {
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
