package com.cosmicbreach.client.gear;

import com.cosmicbreach.gear.forge.AstralForgeMenu;

/**
 * Where everything sits on the Astral Forge's screen (1.1 design section 10), in GUI pixels from the screen's top
 * left. A 24 pixel tab strip on the left, then the 256 pixel panel. Pure, so a test can check that nothing overlaps and
 * that the screen fits the smallest GUI Minecraft lays out: {@code Window.calculateScale} only picks a scale that
 * leaves at least 320 by 240, so a screen that fits that fits at every GUI scale and window size.
 */
public final class ForgeLayout {
    /** A box: its top left corner, width and height. */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public boolean overlaps(Rect o) {
            return x < o.right() && o.x() < right() && y < o.bottom() && o.y() < bottom();
        }

        /** This box moved by {@code dx, dy}, from panel into screen coordinates. */
        public Rect moved(int dx, int dy) {
            return new Rect(x + dx, y + dy, w, h);
        }

        public double centerX() {
            return x + w / 2.0;
        }

        public double centerY() {
            return y + h / 2.0;
        }
    }

    public static final int MIN_GUI_WIDTH = 320;
    public static final int MIN_GUI_HEIGHT = 240;

    public static final int TAB_STRIP = 24;
    public static final int BODY_X = TAB_STRIP;
    public static final int BODY_WIDTH = 256;
    public static final int WIDTH = BODY_X + BODY_WIDTH;
    public static final int HEIGHT = 216;

    public static final int TAB_TOP = 8;
    public static final int TAB_SIZE = 22;
    public static final int TAB_STEP = 24;
    public static final int MAX_TABS = (HEIGHT - TAB_TOP) / TAB_STEP;

    public static final Rect TITLE = new Rect(BODY_X + 8, 6, 180, 9);
    public static final Rect FORGE_TIER = new Rect(BODY_X + 192, 6, 56, 9);

    public static final int GRID_X = BODY_X + 8;
    public static final int GRID_Y = 18;
    public static final int COLS = 5;
    public static final int ROWS = 4;
    public static final int CELL = 18;
    public static final Rect GRID = new Rect(GRID_X, GRID_Y, COLS * CELL, ROWS * CELL);
    public static final Rect SCROLLBAR = new Rect(BODY_X + 100, GRID_Y, 10, ROWS * CELL - 1);

    public static final int DETAIL_X = BODY_X + 118;
    public static final int DETAIL_RIGHT = BODY_X + 248;
    /** The chosen recipe's result: an 18 pixel frame, the item one pixel in. */
    public static final Rect RESULT = new Rect(DETAIL_X, GRID_Y, 18, 18);
    /** Its name: two lines beside the result. */
    public static final Rect NAME = new Rect(DETAIL_X + 22, GRID_Y + 1, DETAIL_RIGHT - DETAIL_X - 22, 18);
    public static final int MAX_INGREDIENTS = 4;
    public static final int INGREDIENT_TOP = 39;
    public static final int INGREDIENT_COLUMN = 65;
    public static final Rect CRAFT = new Rect(DETAIL_RIGHT - 40, 77, 40, 14);
    public static final Rect RECIPE_TIER = new Rect(DETAIL_X, 80, CRAFT.x() - 4 - DETAIL_X, 9);

    public static final int SEPARATOR_TOP = 96;
    public static final int SEPARATOR_BOTTOM = AstralForgeMenu.INVENTORY_Y - 14;

    /** The reforge slot's frame (the slot itself comes from the menu). */
    public static final Rect PIECE = new Rect(AstralForgeMenu.REFORGE_X - 1, AstralForgeMenu.REFORGE_Y - 1, 18, 18);
    public static final Rect REFORGE_TITLE = new Rect(BODY_X + 32, 100, 80, 9);
    public static final Rect REFORGE_LINE = new Rect(BODY_X + 32, 110, 80, 9);
    /** The slotted piece one tier up. */
    public static final Rect PREVIEW = new Rect(BODY_X + 116, AstralForgeMenu.REFORGE_Y - 1, 18, 18);
    public static final Rect COST = new Rect(BODY_X + 140, AstralForgeMenu.REFORGE_Y - 1, 18, 18);
    public static final Rect COST_COUNT = new Rect(BODY_X + 160, AstralForgeMenu.REFORGE_Y + 4, 36, 9);
    /** With the slot empty, up to four reforgeable pieces from the Satchel's Gear tab, to load into it in one click. */
    public static final Rect SATCHEL_PICKS = new Rect(BODY_X + 116, AstralForgeMenu.REFORGE_Y - 1, 4 * 18, 18);
    public static final Rect REFORGE_BUTTON = new Rect(BODY_X + 200, AstralForgeMenu.REFORGE_Y - 1, 48, 14);
    /** With no piece in the slot there is no preview or cost, so the hint has the row up to the button. */
    public static final Rect REFORGE_HINT = new Rect(BODY_X + 32, 110, REFORGE_BUTTON.x() - 4 - (BODY_X + 32), 9);

    public static final Rect INVENTORY_LABEL = new Rect(AstralForgeMenu.INVENTORY_X, AstralForgeMenu.INVENTORY_Y - 11, 9 * 18, 9);

    private ForgeLayout() {
    }

    /**
     * True if a click at screen point ({@code x}, {@code y}) counts as outside the screen, for a screen whose top left is
     * ({@code left}, {@code top}) and that shows {@code shownTabs} tabs: beyond the image, or on the tab strip anywhere
     * but a shown tab. The strip belongs to the image so its tabs can be clicked, but only the tabs are drawn there, so a
     * click on the empty rest of it has to act like one beside the screen (an item on the cursor is thrown, as in any
     * other container screen) and not be silently swallowed.
     */
    public static boolean clickedOutside(double x, double y, int left, int top, int shownTabs) {
        double px = x - left;
        double py = y - top;
        if (px < 0 || py < 0 || px >= WIDTH || py >= HEIGHT) {
            return true;
        }
        if (px >= BODY_X) {
            return false;
        }
        for (int i = 0; i < shownTabs && i < MAX_TABS; i++) {
            if (tab(i).contains(px, py)) {
                return false;
            }
        }
        return true;
    }

    /** Tab {@code i} of the strip, top to bottom. */
    public static Rect tab(int i) {
        return new Rect(2, TAB_TOP + i * TAB_STEP, TAB_SIZE, TAB_SIZE);
    }

    /** Cell {@code i} of the visible grid page, row by row. */
    public static Rect cell(int i) {
        return new Rect(GRID_X + (i % COLS) * CELL, GRID_Y + (i / COLS) * CELL, CELL, CELL);
    }

    /** Ingredient {@code i}'s icon frame: two columns, two rows. */
    public static Rect ingredient(int i) {
        return new Rect(DETAIL_X + (i % 2) * INGREDIENT_COLUMN, INGREDIENT_TOP + (i / 2) * 18, 18, 18);
    }

    /** The "have/need" count beside ingredient {@code i}. */
    public static Rect ingredientCount(int i) {
        Rect icon = ingredient(i);
        return new Rect(icon.x() + 20, icon.y() + 5, INGREDIENT_COLUMN - 22, 9);
    }
}
