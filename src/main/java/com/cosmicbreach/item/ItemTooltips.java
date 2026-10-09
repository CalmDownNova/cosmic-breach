package com.cosmicbreach.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The tooltip every item of this mod carries (1.2 design section 4): what it is, where it comes from and, for a material,
 * what it is used in. The words live in the lang files under {@code tooltip.cosmicbreach.<item>.what/from/used}; a unit test
 * fails if any registered item lacks its first two. Collapsed, the tooltip shows one dim line; Shift expands it.
 * Pure of the client so the key names and the lines are tested without a game.
 */
public final class ItemTooltips {
    public static final String SHIFT_KEY = "tooltip.cosmicbreach.shift";

    private ItemTooltips() {
    }

    public static String what(String path) {
        return "tooltip.cosmicbreach." + path + ".what";
    }

    public static String from(String path) {
        return "tooltip.cosmicbreach." + path + ".from";
    }

    public static String used(String path) {
        return "tooltip.cosmicbreach." + path + ".used";
    }

    /** The lines to add for an item (its path in this mod's namespace); {@code exists} says which lang keys have words. */
    public static List<Component> lines(String path, boolean expanded, Predicate<String> exists) {
        List<Component> out = new ArrayList<>();
        if (!exists.test(what(path))) {
            return out;
        }
        if (!expanded) {
            out.add(Component.translatable(SHIFT_KEY).withStyle(ChatFormatting.DARK_GRAY));
            return out;
        }
        out.add(Component.translatable(what(path)).withStyle(ChatFormatting.GRAY));
        if (exists.test(from(path))) {
            out.add(Component.translatable(from(path)).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (exists.test(used(path))) {
            out.add(Component.translatable(used(path)).withStyle(ChatFormatting.GOLD));
        }
        return out;
    }
}
