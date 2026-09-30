package com.cosmicbreach.gear.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * Crafting from what a player carries: whether the carried stacks cover a list of counted ingredients, and
 * taking them. Each ingredient takes from what the ones before it left, so two ingredients that accept the
 * same item never count one stack twice. Only the main inventory and the off hand are used, never worn armor.
 */
public final class ForgeCrafting {
    private ForgeCrafting() {
    }

    /**
     * How many of each stack each need takes, as {@code takes[need][stack]}, or null if the stacks can't
     * cover every need. Pure: stacks are anything with a count.
     */
    public static <S> int @Nullable [][] plan(List<S> stacks, ToIntFunction<S> count, List<Predicate<S>> accepts, int[] amounts) {
        int[] left = new int[stacks.size()];
        for (int s = 0; s < stacks.size(); s++) {
            left[s] = Math.max(0, count.applyAsInt(stacks.get(s)));
        }
        int[][] takes = new int[accepts.size()][stacks.size()];
        for (int n = 0; n < accepts.size(); n++) {
            int wanted = amounts[n];
            for (int s = 0; s < stacks.size() && wanted > 0; s++) {
                if (left[s] > 0 && accepts.get(n).test(stacks.get(s))) {
                    int take = Math.min(wanted, left[s]);
                    takes[n][s] = take;
                    left[s] -= take;
                    wanted -= take;
                }
            }
            if (wanted > 0) {
                return null;
            }
        }
        return takes;
    }

    /** True if {@code stacks} hold every ingredient in its count. */
    public static boolean covers(List<SizedIngredient> needs, List<ItemStack> stacks) {
        return planFor(needs, stacks) != null;
    }

    /** What the player can craft from: the main inventory and the off hand. */
    public static List<ItemStack> carried(Inventory inventory) {
        List<ItemStack> stacks = new ArrayList<>(inventory.items);
        stacks.addAll(inventory.offhand);
        return stacks;
    }

    /** How many of {@code item} the player carries. */
    public static int count(Inventory inventory, Item item) {
        int n = 0;
        for (ItemStack stack : carried(inventory)) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** How many items the player carries that {@code need} accepts. */
    public static int count(Inventory inventory, SizedIngredient need) {
        int n = 0;
        for (ItemStack stack : carried(inventory)) {
            if (!stack.isEmpty() && need.ingredient().test(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Takes every need from the player's stacks. False (and nothing taken) if they don't cover them. */
    public static boolean take(Inventory inventory, List<SizedIngredient> needs) {
        List<ItemStack> stacks = carried(inventory);
        int[][] takes = planFor(needs, stacks);
        if (takes == null) {
            return false;
        }
        for (int[] need : takes) {
            for (int s = 0; s < need.length; s++) {
                if (need[s] > 0) {
                    stacks.get(s).shrink(need[s]);
                }
            }
        }
        inventory.setChanged();
        return true;
    }

    private static int @Nullable [][] planFor(List<SizedIngredient> needs, List<ItemStack> stacks) {
        List<Predicate<ItemStack>> accepts = new ArrayList<>();
        int[] amounts = new int[needs.size()];
        for (int n = 0; n < needs.size(); n++) {
            SizedIngredient need = needs.get(n);
            accepts.add(stack -> !stack.isEmpty() && need.ingredient().test(stack));
            amounts[n] = need.count();
        }
        return plan(stacks, ItemStack::getCount, accepts, amounts);
    }
}
