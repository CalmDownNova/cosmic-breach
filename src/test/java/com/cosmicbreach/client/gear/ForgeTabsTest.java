package com.cosmicbreach.client.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.gear.forge.ForgeCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Forge screen's tabs over the menu's recipe list (1.1 design section 10). */
class ForgeTabsTest {
    private static final ForgeCategory W = ForgeCategory.WEAPONS;
    private static final ForgeCategory A = ForgeCategory.ARMOR;
    private static final ForgeCategory T = ForgeCategory.TOOLS;
    private static final ForgeCategory M = ForgeCategory.MOUNT_GEAR;
    private static final ForgeCategory O = ForgeCategory.OTHER;

    @Test
    void onlyCategoriesWithRecipesGetATabInCategoryOrder() {
        ForgeTabs tabs = ForgeTabs.of(List.of(O, A, W, A, M, W));
        assertEquals(List.of(W, A, M, O), tabs.tabs(), "no tools recipe, so no tools tab; category order, not list order");
    }

    @Test
    void aTabHoldsItsRecipesInListOrder() {
        ForgeTabs tabs = ForgeTabs.of(List.of(O, A, W, A, M, W));
        assertEquals(List.of(2, 5), tabs.indicesIn(W));
        assertEquals(List.of(1, 3), tabs.indicesIn(A));
        assertEquals(List.of(), tabs.indicesIn(T));
    }

    @Test
    void theListsItHandsOutAreTheSameAndCannotBeChanged() {
        // the screen asks several times a frame: nothing is copied per call, so what it gets must be safe to share
        ForgeTabs tabs = ForgeTabs.of(List.of(O, A, W));
        assertSame(tabs.tabs(), tabs.tabs());
        assertSame(tabs.indicesIn(A), tabs.indicesIn(A));
        assertThrows(UnsupportedOperationException.class, () -> tabs.tabs().add(T));
        assertThrows(UnsupportedOperationException.class, () -> tabs.indicesIn(W).add(5));
        assertThrows(UnsupportedOperationException.class, () -> tabs.indicesIn(T).add(5));
    }

    @Test
    void theLastTabUsedComesBackWhileItHasRecipes() {
        ForgeTabs tabs = ForgeTabs.of(List.of(W, A, O));
        assertEquals(A, tabs.restore(A));
        assertEquals(W, tabs.restore(T), "a tab that is gone falls back to the first");
        assertEquals(W, tabs.restore(null));
    }

    @Test
    void noRecipesNoTabs() {
        ForgeTabs tabs = ForgeTabs.of(List.of());
        assertTrue(tabs.tabs().isEmpty());
        assertNull(tabs.restore(A));
    }
}
