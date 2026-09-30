package com.cosmicbreach.gear.forge;

import com.cosmicbreach.gear.GearRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Every Astral Forge recipe in the order the Forge lists them: by tier, then {@code order}, then id. Both
 * sides build the same list from the same recipes (the server sends them all to each client), so the menu
 * names a recipe by its index in it.
 */
public final class ForgeRecipes {
    public static final Comparator<RecipeHolder<ForgeRecipe>> ORDER = Comparator
            .<RecipeHolder<ForgeRecipe>>comparingInt(h -> h.value().tier())
            .thenComparingInt(h -> h.value().order())
            .thenComparing(h -> h.id().toString());

    private ForgeRecipes() {
    }

    public static List<RecipeHolder<ForgeRecipe>> sorted(RecipeManager recipes) {
        List<RecipeHolder<ForgeRecipe>> list = new ArrayList<>(recipes.getAllRecipesFor(GearRegistry.FORGE_RECIPE_TYPE.get()));
        list.sort(ORDER);
        return list;
    }
}
