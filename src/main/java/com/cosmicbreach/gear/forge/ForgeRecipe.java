package com.cosmicbreach.gear.forge;

import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.item.GearTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * An Astral Forge recipe ({@code "type": "cosmicbreach:astral_forge"}): the ingredients with their counts, the
 * result, and the Forge tier it needs. The Forge crafts from what the player carries, so there is no grid:
 *
 * <pre>
 * {
 *   "type": "cosmicbreach:astral_forge",
 *   "tier": 2,
 *   "order": 10,
 *   "ingredients": [
 *     { "item": "cosmicbreach:nebulite_ingot", "count": 4 },
 *     { "tag": "cosmicbreach:driftwood_logs", "count": 2 }
 *   ],
 *   "result": { "id": "cosmicbreach:comet_maul", "count": 1 }
 * }
 * </pre>
 *
 * {@code tier} defaults to 1 and {@code order} (its place among the recipes of its tier in the Forge's list)
 * to 100. Recipes of a tier above the Forge's show locked.
 */
public record ForgeRecipe(List<SizedIngredient> ingredients, ItemStack result, int tier, int order)
        implements Recipe<ForgeRecipe.Input> {
    public static final int DEFAULT_ORDER = 100;

    public static final MapCodec<ForgeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.nonEmptyList(SizedIngredient.FLAT_CODEC.listOf()).fieldOf("ingredients").forGetter(ForgeRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ForgeRecipe::result),
            Codec.intRange(GearTier.MIN, GearTier.MAX).optionalFieldOf("tier", 1).forGetter(ForgeRecipe::tier),
            Codec.INT.optionalFieldOf("order", DEFAULT_ORDER).forGetter(ForgeRecipe::order)
    ).apply(i, ForgeRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), ForgeRecipe::ingredients,
            ItemStack.STREAM_CODEC, ForgeRecipe::result,
            ByteBufCodecs.VAR_INT, ForgeRecipe::tier,
            ByteBufCodecs.VAR_INT, ForgeRecipe::order,
            ForgeRecipe::new);

    public ForgeRecipe {
        ingredients = List.copyOf(ingredients);
    }

    /** What the player carries, as the recipe sees it. */
    public record Input(List<ItemStack> stacks) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return stacks.get(index);
        }

        @Override
        public int size() {
            return stacks.size();
        }
    }

    /** True if the carried stacks hold every ingredient in its count. */
    @Override
    public boolean matches(Input input, Level level) {
        return ForgeCrafting.covers(ingredients, input.stacks());
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        ingredients.forEach(sized -> list.add(sized.ingredient()));
        return list;
    }

    /** Kept out of the vanilla recipe book: the Forge's own list shows it. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(GearRegistry.ASTRAL_FORGE.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GearRegistry.FORGE_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return GearRegistry.FORGE_RECIPE_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<ForgeRecipe> {
        @Override
        public MapCodec<ForgeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ForgeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
