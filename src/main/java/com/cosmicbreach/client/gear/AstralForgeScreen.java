package com.cosmicbreach.client.gear;

import com.cosmicbreach.gear.forge.AstralForgeMenu;
import com.cosmicbreach.gear.forge.ForgeCrafting;
import com.cosmicbreach.gear.forge.ForgeRecipe;
import com.cosmicbreach.gear.forge.ForgeTiers;
import com.cosmicbreach.item.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * The Astral Forge's screen, in the manner of a stonecutter: every Forge recipe in a grid (recipes of a higher
 * tier than this Forge show locked, with the tier they need), the chosen recipe's ingredients with how many
 * the player carries, and Forge to craft it from the inventory. Below, the reforge slot: a weapon or armor
 * piece, what the next tier costs, and Reforge.
 */
public class AstralForgeScreen extends AbstractContainerScreen<AstralForgeMenu> {
    public static final int WIDTH = 230;
    public static final int HEIGHT = 216;

    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final int CELL = 18;
    private static final int SCROLL_X = 100;
    private static final int SCROLL_W = 10;
    private static final int DETAIL_X = 118;
    private static final int DETAIL_R = 222;
    private static final int INGREDIENTS_Y = 42;
    private static final int CRAFT_Y = 78;
    private static final int BUTTON_H = 14;
    private static final int REFORGE_BUTTON_X = 172;
    private static final int SEPARATOR_Y = 96;

    private static final int PANEL_TOP = 0xF2141822;
    private static final int PANEL_BOTTOM = 0xF40B0D12;
    private static final int GOLD = 0xFFFFC857;
    private static final int WHITE = 0xFFF4F6FA;
    private static final int TEXT = 0xFFB4BCC8;
    private static final int DIM = 0xFF7C8492;
    private static final int GOOD = 0xFF7EE08A;
    private static final int BAD = 0xFFFF7A6A;
    private static final int CELL_BG = 0xFF1C2230;
    private static final int CELL_HOVER = 0xFF2C3548;
    private static final int SLOT_BG = 0xFF10141C;
    private static final int SLOT_EDGE = 0xFF384152;
    private static final int SEPARATOR = 0x22FFFFFF;
    /** A z above rendered items (they draw up to about 232), for the lock shade and the ready dot. */
    private static final int ABOVE_ITEMS = 240;

    private int selected = 0;
    private int scrollRow = 0;
    private boolean draggingScroll;

    public AstralForgeScreen(AstralForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelX = AstralForgeMenu.INVENTORY_X;
        inventoryLabelY = AstralForgeMenu.INVENTORY_Y - 11;
    }

    // ------------------------------------------------------------------ state

    private List<RecipeHolder<ForgeRecipe>> recipes() {
        return menu.recipes();
    }

    private @Nullable ForgeRecipe selectedRecipe() {
        List<RecipeHolder<ForgeRecipe>> recipes = recipes();
        return selected >= 0 && selected < recipes.size() ? recipes.get(selected).value() : null;
    }

    private int maxScroll() {
        int rows = (recipes().size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    private boolean locked(ForgeRecipe recipe) {
        return recipe.tier() > menu.forgeTier();
    }

    private boolean affordable(ForgeRecipe recipe) {
        return ForgeCrafting.covers(recipe.ingredients(), ForgeCrafting.carried(minecraft.player.getInventory()));
    }

    /** Test access: the recipe list as the screen shows it, and a click on one of its cells. */
    public int recipeCount() {
        return recipes().size();
    }

    public int selectedIndex() {
        return selected;
    }

    public void select(int index) {
        selected = Mth.clamp(index, 0, Math.max(0, recipes().size() - 1));
        int row = selected / COLS;
        if (row < scrollRow) {
            scrollRow = row;
        } else if (row >= scrollRow + ROWS) {
            scrollRow = row - ROWS + 1;
        }
    }

    /** True if the Forge button would craft the selected recipe now. */
    public boolean canCraftSelected() {
        ForgeRecipe recipe = selectedRecipe();
        return recipe != null && !locked(recipe) && affordable(recipe);
    }

    public boolean isLocked(int index) {
        List<RecipeHolder<ForgeRecipe>> list = recipes();
        return index >= 0 && index < list.size() && locked(list.get(index).value());
    }

    /** The index of the recipe making {@code item}, or -1. */
    public int indexOf(Item item) {
        List<RecipeHolder<ForgeRecipe>> list = recipes();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).value().result().is(item)) {
                return i;
            }
        }
        return -1;
    }

    /** Screen coordinates of the middle of recipe {@code index}'s cell, scrolling it into view first (dev tests). */
    public double[] cellCenter(int index) {
        select(index);
        int i = index - scrollRow * COLS;
        return new double[] {leftPos + GRID_X + (i % COLS) * CELL + CELL / 2.0, topPos + GRID_Y + (i / COLS) * CELL + CELL / 2.0};
    }

    /** Screen coordinates of the middle of the Forge button (dev tests). */
    public double[] craftButtonCenter() {
        return new double[] {leftPos + (DETAIL_X + DETAIL_R) / 2.0, topPos + CRAFT_Y + BUTTON_H / 2.0};
    }

    /** Screen coordinates of the middle of the Reforge button (dev tests). */
    public double[] reforgeButtonCenter() {
        return new double[] {leftPos + (REFORGE_BUTTON_X + DETAIL_R) / 2.0, topPos + AstralForgeMenu.REFORGE_Y - 1 + BUTTON_H / 2.0};
    }

    /** Presses Forge (the same as clicking it). */
    public boolean pressCraft() {
        if (!canCraftSelected()) {
            return false;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }

    /** Presses Reforge. */
    public boolean pressReforge() {
        if (menu.reforgeCheck(minecraft.player) != ForgeTiers.Check.OK) {
            return false;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AstralForgeMenu.REFORGE_BUTTON);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderForgeTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fillGradient(left, top, left + imageWidth, top + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        corners(graphics, left, top, left + imageWidth, top + imageHeight, 6);
        graphics.fill(left + 8, top + SEPARATOR_Y, left + imageWidth - 8, top + SEPARATOR_Y + 1, SEPARATOR);
        graphics.fill(left + 8, top + AstralForgeMenu.INVENTORY_Y - 14, left + imageWidth - 8, top + AstralForgeMenu.INVENTORY_Y - 13,
                SEPARATOR);
        for (Slot slot : menu.slots) {
            slotFrame(graphics, left + slot.x, top + slot.y);
        }
        drawGrid(graphics, mouseX, mouseY);
        drawScrollbar(graphics);
        drawDetail(graphics, mouseX, mouseY);
        drawReforge(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, WHITE, false);
        int tier = menu.forgeTier();
        Component tierText = Component.translatable("gui.cosmicbreach.forge.tier", GearTier.roman(tier));
        graphics.drawString(font, tierText, imageWidth - 8 - font.width(tierText), 6, 0xFF000000 | GearTier.color(tier), false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
    }

    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RecipeHolder<ForgeRecipe>> list = recipes();
        int first = scrollRow * COLS;
        for (int i = 0; i < ROWS * COLS; i++) {
            int index = first + i;
            int x = leftPos + GRID_X + (i % COLS) * CELL;
            int y = topPos + GRID_Y + (i / COLS) * CELL;
            boolean hover = inside(mouseX, mouseY, x, y, CELL, CELL);
            graphics.fill(x, y, x + CELL - 1, y + CELL - 1, hover && index < list.size() ? CELL_HOVER : CELL_BG);
            if (index >= list.size()) {
                continue;
            }
            ForgeRecipe recipe = list.get(index).value();
            graphics.renderItem(recipe.result(), x + 1, y + 1);
            if (locked(recipe)) {
                graphics.fill(x, y, x + CELL - 1, y + CELL - 1, ABOVE_ITEMS, 0xB00A0C12);
                String roman = GearTier.roman(recipe.tier());
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 250);
                graphics.drawString(font, roman, x + (CELL - 1 - font.width(roman)) / 2, y + 5, 0xFF000000 | GearTier.color(recipe.tier()), true);
                graphics.pose().popPose();
            } else if (affordable(recipe)) {
                graphics.fill(x + CELL - 4, y + 1, x + CELL - 2, y + 3, ABOVE_ITEMS, GOOD);
            }
            if (index == selected) {
                outline(graphics, x - 1, y - 1, x + CELL, y + CELL, GOLD);
            }
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int x = leftPos + SCROLL_X;
        int y = topPos + GRID_Y;
        int h = ROWS * CELL - 1;
        graphics.fill(x, y, x + SCROLL_W, y + h, SLOT_BG);
        int max = maxScroll();
        int thumb = 15;
        int ty = max == 0 ? y : y + (int) ((h - thumb) * (scrollRow / (float) max));
        graphics.fill(x + 1, ty + 1, x + SCROLL_W - 1, ty + thumb - 1, max == 0 ? SLOT_EDGE : 0xFF8C7A52);
    }

    private void drawDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        ForgeRecipe recipe = selectedRecipe();
        int x = leftPos + DETAIL_X;
        int y = topPos + GRID_Y;
        if (recipe == null) {
            graphics.drawString(font, Component.translatable("gui.cosmicbreach.forge.no_recipes"), x, y + 4, DIM, false);
            return;
        }
        slotFrame(graphics, x + 1, y + 1);
        graphics.renderItem(recipe.result(), x + 1, y + 1);
        graphics.renderItemDecorations(font, recipe.result(), x + 1, y + 1);
        List<FormattedCharSequence> name = font.split(recipe.result().getHoverName(), DETAIL_R - DETAIL_X - 22);
        graphics.drawString(font, name.get(0), x + 22, y + 1, WHITE, false);
        boolean locked = locked(recipe);
        Component tierLine = Component.translatable("gui.cosmicbreach.forge.recipe_tier", GearTier.roman(recipe.tier()));
        graphics.drawString(font, tierLine, x + 22, y + 11, locked ? BAD : 0xFF000000 | GearTier.color(recipe.tier()), false);

        List<SizedIngredient> needs = recipe.ingredients();
        for (int i = 0; i < needs.size() && i < 4; i++) {
            SizedIngredient need = needs.get(i);
            int cx = x + (i % 2) * 52;
            int cy = topPos + INGREDIENTS_Y + (i / 2) * 18;
            slotFrame(graphics, cx + 1, cy + 1);
            ItemStack shown = shown(need);
            graphics.renderItem(shown, cx + 1, cy + 1);
            int have = ForgeCrafting.count(minecraft.player.getInventory(), need);
            String count = Math.min(have, 999) + "/" + need.count();
            graphics.drawString(font, count, cx + 20, cy + 5, have >= need.count() ? GOOD : BAD, false);
        }
        drawButton(graphics, mouseX, mouseY, x, topPos + CRAFT_Y, DETAIL_R - DETAIL_X, craftLabel(recipe), canCraftSelected());
    }

    private Component craftLabel(ForgeRecipe recipe) {
        if (locked(recipe)) {
            return Component.translatable("gui.cosmicbreach.forge.needs_tier", GearTier.roman(recipe.tier()));
        }
        return Component.translatable("gui.cosmicbreach.forge.craft");
    }

    private void drawReforge(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + 32;
        int y = topPos + AstralForgeMenu.REFORGE_Y - 3;
        graphics.drawString(font, Component.translatable("gui.cosmicbreach.forge.reforge_title"), x, y, TEXT, false);
        ItemStack piece = menu.piece();
        int unlock = GearTier.unlockTier(piece, true);
        ForgeTiers.Check check = menu.reforgeCheck(minecraft.player);
        Component line;
        int lineColor = DIM;
        if (piece.isEmpty() || unlock == 0) {
            line = Component.translatable("gui.cosmicbreach.forge.reforge_hint");
        } else {
            int tier = GearTier.of(piece, unlock);
            if (tier >= GearTier.MAX) {
                line = Component.translatable("gui.cosmicbreach.forge.reforge_max");
                lineColor = 0xFF000000 | GearTier.color(tier);
            } else {
                line = Component.translatable("gui.cosmicbreach.forge.reforge_step", GearTier.roman(tier), GearTier.roman(tier + 1));
                lineColor = check == ForgeTiers.Check.FORGE_TOO_LOW ? BAD : 0xFF000000 | GearTier.color(tier + 1);
                if (check == ForgeTiers.Check.FORGE_TOO_LOW) {
                    line = Component.translatable("gui.cosmicbreach.forge.needs_tier", GearTier.roman(tier + 1));
                }
                Optional<ForgeTiers.Cost> cost = ForgeTiers.reforgeCost(tier + 1);
                if (cost.isPresent()) {
                    Item metal = BuiltInRegistries.ITEM.get(cost.get().item());
                    int cx = leftPos + 128;
                    int cy = topPos + AstralForgeMenu.REFORGE_Y - 1;
                    slotFrame(graphics, cx + 1, cy + 1);
                    graphics.renderItem(new ItemStack(metal), cx + 1, cy + 1);
                    int have = ForgeCrafting.count(minecraft.player.getInventory(), metal);
                    graphics.drawString(font, Math.min(have, 999) + "/" + cost.get().count(), cx + 20, cy + 5,
                            have >= cost.get().count() ? GOOD : BAD, false);
                }
            }
        }
        graphics.drawString(font, line, x, y + 10, lineColor, false);
        drawButton(graphics, mouseX, mouseY, leftPos + REFORGE_BUTTON_X, topPos + AstralForgeMenu.REFORGE_Y - 1,
                DETAIL_R - REFORGE_BUTTON_X, Component.translatable("gui.cosmicbreach.forge.reforge"), check == ForgeTiers.Check.OK);
    }

    private void drawButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, Component label, boolean active) {
        boolean hover = active && inside(mouseX, mouseY, x, y, w, BUTTON_H);
        int bg = !active ? 0xFF1A1E26 : hover ? 0xFF6A5528 : 0xFF4A3C1E;
        graphics.fill(x, y, x + w, y + BUTTON_H, bg);
        outline(graphics, x, y, x + w, y + BUTTON_H, active ? GOLD : SLOT_EDGE);
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, active ? WHITE : DIM, false);
    }

    private void renderForgeTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RecipeHolder<ForgeRecipe>> list = recipes();
        int first = scrollRow * COLS;
        for (int i = 0; i < ROWS * COLS && first + i < list.size(); i++) {
            int x = leftPos + GRID_X + (i % COLS) * CELL;
            int y = topPos + GRID_Y + (i / COLS) * CELL;
            if (inside(mouseX, mouseY, x, y, CELL, CELL)) {
                ForgeRecipe recipe = list.get(first + i).value();
                List<Component> lines = new ArrayList<>();
                lines.add(recipe.result().getHoverName());
                if (locked(recipe)) {
                    lines.add(Component.translatable("gui.cosmicbreach.forge.needs_tier", GearTier.roman(recipe.tier()))
                            .withColor(BAD));
                }
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        ForgeRecipe recipe = selectedRecipe();
        if (recipe != null) {
            for (int i = 0; i < recipe.ingredients().size() && i < 4; i++) {
                int cx = leftPos + DETAIL_X + (i % 2) * 52;
                int cy = topPos + INGREDIENTS_Y + (i / 2) * 18;
                if (inside(mouseX, mouseY, cx, cy, 18, 18)) {
                    graphics.renderTooltip(font, shown(recipe.ingredients().get(i)), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    /** The ingredient's item to show: tags cycle through their items once a second. */
    private ItemStack shown(SizedIngredient need) {
        ItemStack[] items = need.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        long second = minecraft.level == null ? 0 : minecraft.level.getGameTime() / 20;
        return items[(int) (second % items.length)];
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<RecipeHolder<ForgeRecipe>> list = recipes();
            int first = scrollRow * COLS;
            for (int i = 0; i < ROWS * COLS && first + i < list.size(); i++) {
                int x = leftPos + GRID_X + (i % COLS) * CELL;
                int y = topPos + GRID_Y + (i / COLS) * CELL;
                if (inside(mouseX, mouseY, x, y, CELL, CELL)) {
                    selected = first + i;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0f));
                    return true;
                }
            }
            if (inside(mouseX, mouseY, leftPos + DETAIL_X, topPos + CRAFT_Y, DETAIL_R - DETAIL_X, BUTTON_H)) {
                return pressCraft() || true;
            }
            if (inside(mouseX, mouseY, leftPos + REFORGE_BUTTON_X, topPos + AstralForgeMenu.REFORGE_Y - 1,
                    DETAIL_R - REFORGE_BUTTON_X, BUTTON_H)) {
                return pressReforge() || true;
            }
            if (inside(mouseX, mouseY, leftPos + SCROLL_X, topPos + GRID_Y, SCROLL_W, ROWS * CELL)) {
                draggingScroll = true;
                dragScroll(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll) {
            dragScroll(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragScroll(double mouseY) {
        float t = (float) ((mouseY - topPos - GRID_Y - 7) / (ROWS * CELL - 15));
        scrollRow = Mth.clamp(Math.round(t * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y, SCROLL_X + SCROLL_W - GRID_X, ROWS * CELL)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        graphics.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    private static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void corners(GuiGraphics graphics, int left, int top, int right, int bottom, int corner) {
        graphics.fill(left, top, right, top + 1, 0x33FFC857);
        graphics.fill(left, bottom - 1, right, bottom, 0x33FFC857);
        graphics.fill(left, top, left + 1, bottom, 0x33FFC857);
        graphics.fill(right - 1, top, right, bottom, 0x33FFC857);
        graphics.fill(left, top, left + corner, top + 1, GOLD);
        graphics.fill(left, top, left + 1, top + corner, GOLD);
        graphics.fill(right - corner, top, right, top + 1, GOLD);
        graphics.fill(right - 1, top, right, top + corner, GOLD);
        graphics.fill(left, bottom - 1, left + corner, bottom, GOLD);
        graphics.fill(left, bottom - corner, left + 1, bottom, GOLD);
        graphics.fill(right - corner, bottom - 1, right, bottom, GOLD);
        graphics.fill(right - 1, bottom - corner, right, bottom, GOLD);
    }
}
