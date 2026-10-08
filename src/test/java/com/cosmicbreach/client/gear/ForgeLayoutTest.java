package com.cosmicbreach.client.gear;

import static com.cosmicbreach.client.gear.ForgeLayout.BODY_WIDTH;
import static com.cosmicbreach.client.gear.ForgeLayout.BODY_X;
import static com.cosmicbreach.client.gear.ForgeLayout.COST;
import static com.cosmicbreach.client.gear.ForgeLayout.COST_COUNT;
import static com.cosmicbreach.client.gear.ForgeLayout.CRAFT;
import static com.cosmicbreach.client.gear.ForgeLayout.DETAIL_RIGHT;
import static com.cosmicbreach.client.gear.ForgeLayout.DETAIL_X;
import static com.cosmicbreach.client.gear.ForgeLayout.FORGE_TIER;
import static com.cosmicbreach.client.gear.ForgeLayout.GRID;
import static com.cosmicbreach.client.gear.ForgeLayout.GRID_Y;
import static com.cosmicbreach.client.gear.ForgeLayout.HEIGHT;
import static com.cosmicbreach.client.gear.ForgeLayout.MAX_INGREDIENTS;
import static com.cosmicbreach.client.gear.ForgeLayout.MAX_TABS;
import static com.cosmicbreach.client.gear.ForgeLayout.MIN_GUI_HEIGHT;
import static com.cosmicbreach.client.gear.ForgeLayout.MIN_GUI_WIDTH;
import static com.cosmicbreach.client.gear.ForgeLayout.NAME;
import static com.cosmicbreach.client.gear.ForgeLayout.PIECE;
import static com.cosmicbreach.client.gear.ForgeLayout.PREVIEW;
import static com.cosmicbreach.client.gear.ForgeLayout.RECIPE_TIER;
import static com.cosmicbreach.client.gear.ForgeLayout.REFORGE_BUTTON;
import static com.cosmicbreach.client.gear.ForgeLayout.REFORGE_HINT;
import static com.cosmicbreach.client.gear.ForgeLayout.REFORGE_LINE;
import static com.cosmicbreach.client.gear.ForgeLayout.REFORGE_TITLE;
import static com.cosmicbreach.client.gear.ForgeLayout.RESULT;
import static com.cosmicbreach.client.gear.ForgeLayout.SCROLLBAR;
import static com.cosmicbreach.client.gear.ForgeLayout.SEPARATOR_BOTTOM;
import static com.cosmicbreach.client.gear.ForgeLayout.SEPARATOR_TOP;
import static com.cosmicbreach.client.gear.ForgeLayout.TITLE;
import static com.cosmicbreach.client.gear.ForgeLayout.WIDTH;
import static com.cosmicbreach.client.gear.ForgeLayout.ingredient;
import static com.cosmicbreach.client.gear.ForgeLayout.ingredientCount;
import static com.cosmicbreach.client.gear.ForgeLayout.tab;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.client.gear.ForgeLayout.Rect;
import com.cosmicbreach.gear.forge.AstralForgeMenu;
import com.cosmicbreach.gear.forge.ForgeCategory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Forge screen's layout (1.1 design section 10): it fits the smallest GUI Minecraft lays out, so it fits at every
 * GUI scale and window size, and no two boxes overlap, so no text or icon draws over another.
 */
class ForgeLayoutTest {
    private static void assertDisjoint(List<Rect> boxes) {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                assertFalse(boxes.get(i).overlaps(boxes.get(j)), boxes.get(i) + " overlaps " + boxes.get(j));
            }
        }
    }

    private static void assertInside(Rect box, Rect area) {
        assertTrue(box.x() >= area.x() && box.y() >= area.y() && box.right() <= area.right() && box.bottom() <= area.bottom(),
                box + " is not inside " + area);
    }

    @Test
    void theWholeScreenFitsTheSmallestGuiMinecraftLaysOut() {
        assertTrue(WIDTH <= MIN_GUI_WIDTH, "width " + WIDTH);
        assertTrue(HEIGHT <= MIN_GUI_HEIGHT, "height " + HEIGHT);
    }

    @Test
    void theDetailColumnsBoxesNeverOverlap() {
        List<Rect> boxes = new ArrayList<>(List.of(RESULT, NAME, CRAFT, RECIPE_TIER));
        for (int i = 0; i < MAX_INGREDIENTS; i++) {
            boxes.add(ingredient(i));
            boxes.add(ingredientCount(i));
        }
        assertDisjoint(boxes);
        Rect column = new Rect(DETAIL_X, GRID_Y, DETAIL_RIGHT - DETAIL_X, SEPARATOR_TOP - GRID_Y);
        for (Rect box : boxes) {
            assertInside(box, column);
        }
        assertEquals(108, NAME.w(), "room for every current name on two lines at full size");
        assertEquals(18, NAME.h(), "two lines");
    }

    @Test
    void theReforgeRowsBoxesNeverOverlap() {
        assertDisjoint(List.of(PIECE, REFORGE_LINE, PREVIEW, COST, COST_COUNT, REFORGE_BUTTON));
        assertDisjoint(List.of(PIECE, REFORGE_TITLE, PREVIEW, COST, REFORGE_BUTTON));
        assertDisjoint(List.of(PIECE, REFORGE_HINT, REFORGE_BUTTON));
        Rect row = new Rect(BODY_X, SEPARATOR_TOP + 1, BODY_WIDTH, SEPARATOR_BOTTOM - SEPARATOR_TOP);
        for (Rect box : List.of(PIECE, REFORGE_TITLE, REFORGE_LINE, REFORGE_HINT, PREVIEW, COST, COST_COUNT, REFORGE_BUTTON)) {
            assertInside(box, row);
        }
    }

    @Test
    void theGridScrollbarAndDetailSitSideBySideUnderTheTitle() {
        assertTrue(GRID.right() <= SCROLLBAR.x());
        assertTrue(SCROLLBAR.right() <= DETAIL_X);
        assertTrue(GRID.bottom() <= SEPARATOR_TOP);
        assertDisjoint(List.of(TITLE, FORGE_TIER, GRID, SCROLLBAR, RESULT, NAME));
    }

    @Test
    void everyCategoryCanHaveATabInTheStrip() {
        assertTrue(MAX_TABS >= ForgeCategory.values().length);
        Rect strip = new Rect(0, 0, BODY_X, HEIGHT);
        List<Rect> tabs = new ArrayList<>();
        for (int i = 0; i < MAX_TABS; i++) {
            assertInside(tab(i), strip);
            tabs.add(tab(i));
        }
        assertDisjoint(tabs);
    }

    /** A click on panel point (px, py) of a screen whose top left is (100, 40), as a click in screen coordinates. */
    private static boolean outside(double px, double py, int shownTabs) {
        return ForgeLayout.clickedOutside(100 + px, 40 + py, 100, 40, shownTabs);
    }

    @Test
    void aClickBeyondTheImageIsOutsideAndOneOnThePanelIsNot() {
        assertTrue(outside(-1, 100, 4), "left of the image");
        assertTrue(outside(150, -1, 4), "above it");
        assertTrue(outside(WIDTH, 100, 4), "right of it");
        assertTrue(outside(150, HEIGHT, 4), "below it");
        assertFalse(outside(BODY_X, 0, 4), "the panel's top left corner");
        assertFalse(outside(WIDTH - 1, HEIGHT - 1, 4), "the panel's bottom right corner");
        assertFalse(outside(BODY_X + 100, 100, 4), "the middle of the panel");
    }

    @Test
    void theTabStripIsOutsideEverywhereThatIsNotATab() {
        // the strip is part of the screen's image (so the tabs can be clicked) but only the tabs are drawn there: a click
        // on the empty rest of it with an item on the cursor has to throw the item, as a click beside the screen does
        for (int i = 0; i < 4; i++) {
            Rect tab = tab(i);
            assertFalse(outside(tab.x(), tab.y(), 4), "tab " + i + " top left");
            assertFalse(outside(tab.centerX(), tab.centerY(), 4), "tab " + i + " middle");
            assertFalse(outside(tab.right() - 1, tab.bottom() - 1, 4), "tab " + i + " bottom right");
        }
        Rect first = tab(0);
        assertTrue(outside(0, first.centerY(), 4), "the pixels left of the tabs");
        assertTrue(outside(first.x() - 1, first.centerY(), 4));
        assertTrue(outside(first.centerX(), first.y() - 1, 4), "above the first tab");
        assertTrue(outside(first.centerX(), first.bottom(), 4), "the gap between two tabs");
        assertTrue(outside(first.centerX(), tab(3).bottom(), 4), "just below the last tab");
        assertTrue(outside(first.centerX(), HEIGHT - 1, 4), "the strip's empty bottom");
        assertFalse(outside(BODY_X - 1, tab(1).centerY(), 4), "a tab reaches the panel's edge");
    }

    @Test
    void aTabThatIsNotShownIsNotATab() {
        assertFalse(outside(tab(2).centerX(), tab(2).centerY(), 3), "the third of three");
        assertTrue(outside(tab(3).centerX(), tab(3).centerY(), 3), "a fourth that is left out");
        assertTrue(outside(tab(0).centerX(), tab(0).centerY(), 0), "no recipes, no tabs: the whole strip is outside");
        assertTrue(outside(tab(MAX_TABS).centerX(), tab(MAX_TABS).centerY(), 100), "more tabs than the strip has room for");
    }

    @Test
    void theInventorySitsInTheMiddleOfThePanelBelowTheReforgeRow() {
        int left = AstralForgeMenu.INVENTORY_X - BODY_X;
        int right = WIDTH - (AstralForgeMenu.INVENTORY_X + 9 * 18);
        assertEquals(left, right, "centred in the panel");
        assertTrue(AstralForgeMenu.INVENTORY_Y > SEPARATOR_BOTTOM);
        assertTrue(AstralForgeMenu.HOTBAR_Y + 16 <= HEIGHT);
        assertInside(PIECE, new Rect(BODY_X, 0, BODY_WIDTH, HEIGHT));
    }
}
