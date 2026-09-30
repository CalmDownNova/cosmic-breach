package com.cosmicbreach.datagen;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.registry.ModBlocks.StoneSet;
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
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/**
 * Recipes, all under the {@code cosmicbreach} namespace (vanilla's helpers that take a name would put
 * them under {@code minecraft}, so this class names its own):
 *
 * <ul>
 *   <li>Building sets: slab, stairs and wall by crafting and by stonecutter; polished and bricks from 2x2
 *       (Starfall Stone to polished to bricks, Driftstone to bricks, Umbral Basalt to polished to bricks),
 *       and the stonecutter makes anything further down a chain from anything above it.</li>
 *   <li>Metals: raw metal and ore smelt (200 ticks) or blast (100) to ingots; nine ingots make a storage
 *       block and back; nine nuggets make an ingot and back.</li>
 *   <li>Driftwood: planks from any Driftwood log, wood from logs, and the usual wooden set.</li>
 *   <li>The Breach Frame (GDD 1.3): copper ingots in the corners, cobblestone-type stone in the other
 *       five slots, gives 6 (a 4 by 4 ring needs 12: two crafts).</li>
 *   <li>A Starbloom gives 2 Starbloom Seeds.</li>
 * </ul>
 */
public final class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        for (StoneSet set : ModBlocks.stoneSets()) {
            setRecipes(out, set);
        }
        // Starfall Stone > polished > bricks
        twoByTwo(out, ModBlocks.POLISHED_STARFALL_STONE.get(), ModBlocks.STARFALL_STONE.get());
        twoByTwo(out, ModBlocks.STARFALL_STONE_BRICKS.get(), ModBlocks.POLISHED_STARFALL_STONE.get());
        chain(out, ModBlocks.STARFALL_STONE_SET, ModBlocks.POLISHED_STARFALL_STONE_SET, ModBlocks.STARFALL_STONE_BRICK_SET);
        // Driftstone > bricks
        twoByTwo(out, ModBlocks.DRIFTSTONE_BRICKS.get(), ModBlocks.DRIFTSTONE.get());
        chain(out, ModBlocks.DRIFTSTONE_SET, ModBlocks.DRIFTSTONE_BRICK_SET);
        // Umbral Basalt > polished > bricks (the raw basalt is a pillar with no slabs of its own)
        twoByTwo(out, ModBlocks.POLISHED_UMBRAL_BASALT.get(), ModBlocks.UMBRAL_BASALT.get());
        twoByTwo(out, ModBlocks.UMBRAL_BASALT_BRICKS.get(), ModBlocks.POLISHED_UMBRAL_BASALT.get());
        for (ItemLike result : members(ModBlocks.POLISHED_UMBRAL_BASALT_SET, ModBlocks.UMBRAL_BASALT_BRICK_SET)) {
            stonecut(out, result, ModBlocks.UMBRAL_BASALT.get());
        }
        chain(out, ModBlocks.POLISHED_UMBRAL_BASALT_SET, ModBlocks.UMBRAL_BASALT_BRICK_SET);

        metal(out, ModMaterials.RAW_STARSTEEL.get(), ModBlocks.STARSTEEL_ORE.get(), ModMaterials.STARSTEEL_INGOT.get(),
                ModBlocks.STARSTEEL_BLOCK.get(), 0.7f);
        metal(out, ModMaterials.RAW_NEBULITE.get(), ModBlocks.NEBULITE_ORE.get(), ModMaterials.NEBULITE_INGOT.get(),
                ModBlocks.NEBULITE_BLOCK.get(), 0.9f);
        metal(out, ModMaterials.RAW_ECLIPSIUM.get(), ModBlocks.ECLIPSIUM_ORE.get(), ModMaterials.ECLIPSIUM_INGOT.get(),
                ModBlocks.ECLIPSIUM_BLOCK.get(), 1.0f);
        nuggets(out, ModMaterials.STARSTEEL_NUGGET.get(), ModMaterials.STARSTEEL_INGOT.get());
        nuggets(out, ModMaterials.ECLIPSIUM_NUGGET.get(), ModMaterials.ECLIPSIUM_INGOT.get());

        driftwood(out);

        // 6 a craft: a 4 by 4 ring's 12 frames from two crafts, still 8 copper and 10 stone (W4)
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.BREACH_FRAME.get(), 6)
                .define('C', Items.COPPER_INGOT)
                .define('S', ItemTags.STONE_CRAFTING_MATERIALS)
                .pattern("CSC")
                .pattern("SSS")
                .pattern("CSC")
                .unlockedBy(getHasName(Items.COPPER_INGOT), has(Items.COPPER_INGOT))
                .save(out);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.STARBLOOM_SEEDS.get(), 2)
                .requires(ModBlocks.STARBLOOM.get())
                .unlockedBy(getHasName(ModBlocks.STARBLOOM.get()), has(ModBlocks.STARBLOOM.get()))
                .save(out);
    }

    /** Slab, stairs and wall from the set's base, by crafting table and stonecutter. */
    private static void setRecipes(RecipeOutput out, StoneSet set) {
        ItemLike base = set.base().get();
        slabBuilder(RecipeCategory.BUILDING_BLOCKS, set.slab().get(), Ingredient.of(base))
                .unlockedBy(getHasName(base), has(base)).save(out);
        stairBuilder(set.stairs().get(), Ingredient.of(base)).unlockedBy(getHasName(base), has(base)).save(out);
        stonecut(out, set.slab().get(), base, 2);
        stonecut(out, set.stairs().get(), base, 1);
        if (set.wall() != null) {
            wallBuilder(RecipeCategory.DECORATIONS, set.wall().get(), Ingredient.of(base))
                    .unlockedBy(getHasName(base), has(base)).save(out);
            stonecut(out, set.wall().get(), base, 1);
        }
    }

    /** In a chain a > b > c, the stonecutter turns a into every member of b and c, and b into every member of c. */
    private static void chain(RecipeOutput out, StoneSet... sets) {
        for (int i = 0; i < sets.length; i++) {
            for (int j = i + 1; j < sets.length; j++) {
                for (ItemLike result : members(sets[j])) {
                    stonecut(out, result, sets[i].base().get());
                }
            }
        }
    }

    private static List<ItemLike> members(StoneSet... sets) {
        List<ItemLike> all = new ArrayList<>();
        for (StoneSet s : sets) {
            all.add(s.base().get());
            all.add(s.slab().get());
            all.add(s.stairs().get());
            if (s.wall() != null) {
                all.add(s.wall().get());
            }
        }
        return all;
    }

    private static void stonecut(RecipeOutput out, ItemLike result, ItemLike from) {
        boolean slab = BuiltInRegistries.ITEM.getKey(result.asItem()).getPath().endsWith("_slab");
        stonecut(out, result, from, slab ? 2 : 1);
    }

    private static void stonecut(RecipeOutput out, ItemLike result, ItemLike from, int count) {
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(from), RecipeCategory.BUILDING_BLOCKS, result, count)
                .unlockedBy(getHasName(from), has(from))
                .save(out, id(name(result) + "_from_" + name(from) + "_stonecutting"));
    }

    private static void twoByTwo(RecipeOutput out, ItemLike result, ItemLike from) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result, 4)
                .define('#', from)
                .pattern("##")
                .pattern("##")
                .unlockedBy(getHasName(from), has(from))
                .save(out);
    }

    private static void metal(RecipeOutput out, ItemLike raw, ItemLike ore, ItemLike ingot, ItemLike block, float xp) {
        for (ItemLike input : new ItemLike[] {raw, ore}) {
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(input), RecipeCategory.MISC, ingot, xp, 200)
                    .group(name(ingot)).unlockedBy(getHasName(input), has(input))
                    .save(out, id(name(ingot) + "_from_smelting_" + name(input)));
            SimpleCookingRecipeBuilder.blasting(Ingredient.of(input), RecipeCategory.MISC, ingot, xp, 100)
                    .group(name(ingot)).unlockedBy(getHasName(input), has(input))
                    .save(out, id(name(ingot) + "_from_blasting_" + name(input)));
        }
        nineBlock(out, block, ingot);
    }

    private static void nuggets(RecipeOutput out, ItemLike nugget, ItemLike ingot) {
        nineBlock(out, ingot, nugget);
    }

    /** Nine {@code small} make one {@code big}; one {@code big} gives nine {@code small}. */
    private static void nineBlock(RecipeOutput out, ItemLike big, ItemLike small) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, big)
                .define('#', small)
                .pattern("###")
                .pattern("###")
                .pattern("###")
                .unlockedBy(getHasName(small), has(small))
                .save(out, id(name(big) + "_from_" + name(small)));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, small, 9)
                .requires(big)
                .unlockedBy(getHasName(big), has(big))
                .save(out, id(name(small) + "_from_" + name(big)));
    }

    private static void driftwood(RecipeOutput out) {
        ItemLike planks = ModBlocks.DRIFTWOOD_PLANKS.get();
        planksFromLog(out, planks, ModItemTagsProvider.DRIFTWOOD_LOGS, 4);
        woodFromLogs(out, ModBlocks.DRIFTWOOD_WOOD.get(), ModBlocks.DRIFTWOOD_LOG.get());
        woodFromLogs(out, ModBlocks.STRIPPED_DRIFTWOOD_WOOD.get(), ModBlocks.STRIPPED_DRIFTWOOD_LOG.get());
        Ingredient p = Ingredient.of(planks);
        String has = getHasName(planks);
        slabBuilder(RecipeCategory.BUILDING_BLOCKS, ModBlocks.DRIFTWOOD_SLAB.get(), p).unlockedBy(has, has(planks)).save(out);
        stairBuilder(ModBlocks.DRIFTWOOD_STAIRS.get(), p).unlockedBy(has, has(planks)).save(out);
        fenceBuilder(ModBlocks.DRIFTWOOD_FENCE.get(), p).unlockedBy(has, has(planks)).save(out);
        fenceGateBuilder(ModBlocks.DRIFTWOOD_FENCE_GATE.get(), p).unlockedBy(has, has(planks)).save(out);
        doorBuilder(ModBlocks.DRIFTWOOD_DOOR_ITEM.get(), p).unlockedBy(has, has(planks)).save(out);
        trapdoorBuilder(ModBlocks.DRIFTWOOD_TRAPDOOR.get(), p).unlockedBy(has, has(planks)).save(out);
        buttonBuilder(ModBlocks.DRIFTWOOD_BUTTON.get(), p).unlockedBy(has, has(planks)).save(out);
        pressurePlateBuilder(RecipeCategory.REDSTONE, ModBlocks.DRIFTWOOD_PRESSURE_PLATE.get(), p)
                .unlockedBy(has, has(planks)).save(out);
    }

    private static String name(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
    }

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return CosmicBreach.id(path);
    }
}
