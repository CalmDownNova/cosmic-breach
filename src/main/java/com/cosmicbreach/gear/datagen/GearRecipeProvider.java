package com.cosmicbreach.gear.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.datagen.ModItemTagsProvider;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.GearSets;
import com.cosmicbreach.gear.forge.ForgeRecipe;
import com.cosmicbreach.progression.ProgressionRegistry;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModItems;
import com.cosmicbreach.registry.ModMaterials;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * The Astral Forge's own recipe (a crafting table: 4 Starsteel Ingots in the corners, 4 Starfall Stone on the
 * sides, a Spire Quartz in the middle) and every Forge recipe so far (GDD 3.5 and 4.2), written as
 * {@code data/cosmicbreach/recipe/forge/<result>.json}. Later tasks add Forge recipes the same way, or as
 * plain JSON.
 */
public final class GearRecipeProvider extends RecipeProvider {
    public GearRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, GearRegistry.ASTRAL_FORGE.get())
                .define('I', ModMaterials.STARSTEEL_INGOT.get())
                .define('S', ModBlocks.STARFALL_STONE.get())
                .define('Q', ModBlocks.SPIRE_QUARTZ.get())
                .pattern("ISI")
                .pattern("SQS")
                .pattern("ISI")
                .unlockedBy(getHasName(ModMaterials.STARSTEEL_INGOT.get()), has(ModMaterials.STARSTEEL_INGOT.get()))
                .save(out);

        // Forge I
        forge(out, ModItems.MERIDIAN.get(), 1, 10, need(ModMaterials.STARSTEEL_INGOT.get(), 3), need(ModBlocks.SPIRE_QUARTZ.get(), 1),
                need(Tags.Items.RODS_WOODEN, 1));
        forge(out, GearRegistry.VANGUARD.helmet().get(), 1, 20, need(ModMaterials.STARSTEEL_INGOT.get(), 5), need(ModItems.STARSHARD.get(), 1));
        forge(out, GearRegistry.VANGUARD.chestplate().get(), 1, 21, need(ModMaterials.STARSTEEL_INGOT.get(), 8),
                need(ModItems.STARSHARD.get(), 2));
        forge(out, GearRegistry.VANGUARD.leggings().get(), 1, 22, need(ModMaterials.STARSTEEL_INGOT.get(), 7),
                need(ModItems.STARSHARD.get(), 2));
        forge(out, GearRegistry.VANGUARD.boots().get(), 1, 23, need(ModMaterials.STARSTEEL_INGOT.get(), 4), need(ModItems.STARSHARD.get(), 1));
        forge(out, ProgressionRegistry.REVERIE_DRAUGHT.get(), 1, 50, need(ModBlocks.STARBLOOM.get(), 1),
                need(ModMaterials.STARSTEEL_INGOT.get(), 2), need(Items.GLASS_BOTTLE, 1));
        // Forge II. The Comet Maul takes a Heartstone where the GDD says a Meteorite block: a Meteorite drops
        // itself only with Silk Touch, and its Heartstone is the meteor's heart anyway.
        forge(out, ModItems.COMET_MAUL.get(), 2, 10, need(ModMaterials.NEBULITE_INGOT.get(), 4), need(ModMaterials.HEARTSTONE.get(), 1),
                need(ModItems.STARSHARD.get(), 2), need(ModItemTagsProvider.DRIFTWOOD_LOGS, 2));
        forge(out, ModItems.BINARY_EDGES.get(), 2, 20, need(ModMaterials.GYRE_BLADE.get(), 2), need(ModMaterials.NEBULITE_INGOT.get(), 3),
                need(ModBlocks.RIMEGLASS.get(), 1));
        // Forge II: the Driftweave (GDD 5.1)
        forge(out, GearSets.DRIFTWEAVE.helmet().get(), 2, 30, need(ModMaterials.NEBULITE_INGOT.get(), 3), need(ModBlocks.RIMEGLASS.get(), 2),
                need(Tags.Items.STRINGS, 4));
        forge(out, GearSets.DRIFTWEAVE.chestplate().get(), 2, 31, need(ModMaterials.NEBULITE_INGOT.get(), 5),
                need(ModMaterials.GYRE_CORE.get(), 1), need(Tags.Items.STRINGS, 8));
        forge(out, GearSets.DRIFTWEAVE.leggings().get(), 2, 32, need(ModMaterials.NEBULITE_INGOT.get(), 4),
                need(ModBlocks.RIMEGLASS.get(), 2), need(Tags.Items.STRINGS, 6));
        forge(out, GearSets.DRIFTWEAVE.boots().get(), 2, 33, need(ModMaterials.NEBULITE_INGOT.get(), 2), need(ModBlocks.RIMEGLASS.get(), 1),
                need(Tags.Items.STRINGS, 4));
        // Forge III: the Choir Astrolabe (GDD 4.2)
        forge(out, ModItems.CHOIR_ASTROLABE.get(), 3, 10, need(ModMaterials.LEVIATHAN_SCALE.get(), 1), need(ModMaterials.ECLIPSIUM_INGOT.get(), 3), need(ModBlocks.SPIRE_QUARTZ.get(), 2), need(ModMaterials.UMBRAL_SILK.get(), 1));
        // Forge III: the Choir Regalia (GDD 5.1)
        forge(out, GearSets.REGALIA.helmet().get(), 3, 30, need(ModMaterials.ECLIPSIUM_INGOT.get(), 3), need(com.cosmicbreach.provision.ProvisionRegistry.GILDING, 2),
                need(ModMaterials.UMBRAL_SILK.get(), 2));
        forge(out, GearSets.REGALIA.chestplate().get(), 3, 31, need(ModMaterials.ECLIPSIUM_INGOT.get(), 5), need(com.cosmicbreach.provision.ProvisionRegistry.GILDING, 3),
                need(ModMaterials.UMBRAL_SILK.get(), 4));
        forge(out, GearSets.REGALIA.leggings().get(), 3, 32, need(ModMaterials.ECLIPSIUM_INGOT.get(), 4), need(com.cosmicbreach.provision.ProvisionRegistry.GILDING, 2),
                need(ModMaterials.UMBRAL_SILK.get(), 3));
        forge(out, GearSets.REGALIA.boots().get(), 3, 33, need(ModMaterials.ECLIPSIUM_INGOT.get(), 2), need(com.cosmicbreach.provision.ProvisionRegistry.GILDING, 1),
                need(ModMaterials.UMBRAL_SILK.get(), 2));
    }

    private static SizedIngredient need(ItemLike item, int count) {
        return SizedIngredient.of(item, count);
    }

    private static SizedIngredient need(TagKey<Item> tag, int count) {
        return SizedIngredient.of(tag, count);
    }

    private static void forge(RecipeOutput out, ItemLike result, int tier, int order, SizedIngredient... ingredients) {
        String name = BuiltInRegistries.ITEM.getKey(result.asItem()).getPath();
        out.accept(CosmicBreach.id("forge/" + name), new ForgeRecipe(new ArrayList<>(List.of(ingredients)), new ItemStack(result),
                tier, order, tab(result.asItem())), null);
    }

    /**
     * A generated recipe's Forge tab (1.1 design section 10) follows its result (see {@link ForgeTabRule}: mount gear,
     * armor, a combat weapon or a digging tool; anything else goes to Other). It is written out as "category", so every
     * recipe file names its tab. Fully qualified names keep this change out of the import block, which other lanes edit.
     */
    private static com.cosmicbreach.gear.forge.ForgeCategory tab(Item item) {
        return ForgeTabRule.of(item.getClass());
    }
}
