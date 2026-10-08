package com.cosmicbreach.client.gear;

import com.cosmicbreach.gear.forge.ForgeCategory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The Forge screen's tabs over its recipe list (1.1 design section 10), pure: given each recipe's category in list
 * order, the tabs to show (every category with a recipe, in {@link ForgeCategory} order, empty ones left out) and the
 * list indices each tab holds. The list stays the one the menu's button ids name recipes by. What it hands out is
 * built once and cannot be changed, so asking again copies nothing (the screen asks several times a frame).
 */
public final class ForgeTabs {
    private final Map<ForgeCategory, List<Integer>> byTab = new EnumMap<>(ForgeCategory.class);
    private final List<ForgeCategory> tabs;

    private ForgeTabs(List<ForgeCategory> categories) {
        Map<ForgeCategory, List<Integer>> building = new EnumMap<>(ForgeCategory.class);
        for (int i = 0; i < categories.size(); i++) {
            building.computeIfAbsent(categories.get(i), c -> new ArrayList<>()).add(i);
        }
        List<ForgeCategory> shown = new ArrayList<>();
        for (ForgeCategory category : ForgeCategory.values()) {
            List<Integer> indices = building.get(category);
            if (indices != null) {
                shown.add(category);
                byTab.put(category, List.copyOf(indices));
            }
        }
        tabs = List.copyOf(shown);
    }

    /** {@code categories.get(i)} is the category of recipe {@code i} of the Forge's list. */
    public static ForgeTabs of(List<ForgeCategory> categories) {
        return new ForgeTabs(categories);
    }

    /** The tabs to show, in order; empty when there are no recipes. */
    public List<ForgeCategory> tabs() {
        return tabs;
    }

    /** The list indices in {@code tab}, in list order (empty for a tab that isn't shown). */
    public List<Integer> indicesIn(ForgeCategory tab) {
        return byTab.getOrDefault(tab, List.of());
    }

    /** The tab to open: {@code remembered} while it is shown, else the first tab; null when there are no recipes. */
    public @Nullable ForgeCategory restore(@Nullable ForgeCategory remembered) {
        if (remembered != null && byTab.containsKey(remembered)) {
            return remembered;
        }
        return tabs.isEmpty() ? null : tabs.get(0);
    }
}
