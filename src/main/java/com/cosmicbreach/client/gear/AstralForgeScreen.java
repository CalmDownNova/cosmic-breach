package com.cosmicbreach.client.gear;

import com.cosmicbreach.client.gear.ForgeLayout.Rect;
import com.cosmicbreach.gear.forge.AstralForgeMenu;
import com.cosmicbreach.gear.forge.ForgeCategory;
import com.cosmicbreach.gear.forge.ForgeCrafting;
import com.cosmicbreach.gear.forge.ForgeRecipe;
import com.cosmicbreach.gear.forge.ForgeTiers;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.satchel.SatchelLocator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * The Astral Forge's screen (1.1 design section 10). Tabs down the left split the recipes by the category each
 * recipe names in its data (empty tabs are left out; the last tab used comes back); the grid shows the open tab's
 * recipes (higher tiers than this Forge locked, with the tier they need); the detail column shows the chosen recipe's
 * name fitted into its box, its ingredients with how many the player carries, its tier and the Forge button. Below,
 * the reforge slot with the next tier's preview, its cost and Reforge. Every item the screen paints shows its full
 * tooltip on hover, the same lines as in the inventory, and every text is fitted: wrapped, then smaller, and only as a
 * last resort cut short, with the whole text in a tooltip.
 *
 * <p>Recipes keep their index in {@link com.cosmicbreach.gear.forge.ForgeRecipes#sorted}, the list the menu's button
 * ids name, so tabs only filter what is shown. Everything a frame reads from the menu (that list, its tabs, what the
 * player carries, the reforge preview) is looked at once, at the start of the frame ({@link Frame}), and the paging
 * arithmetic is {@link ForgePaging}'s.
 */
public class AstralForgeScreen extends AbstractContainerScreen<AstralForgeMenu> {
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

    /** This game session's memory: the tab open when the screen last closed, and the recipe last chosen in each tab. */
    private static @Nullable ForgeCategory lastTab;
    private static final Map<ForgeCategory, ResourceLocation> LAST_RECIPE = new EnumMap<>(ForgeCategory.class);

    /** What the mouse is over, as the tooltip it gets: an item with its lines (its own, then any extra), or plain lines. */
    private record Hover(ItemStack stack, List<Component> lines) {}

    /** A text cut short in the last frame: its box in screen coordinates and the whole text. */
    private record Shortened(Rect box, String full) {}

    /**
     * What one frame works from, built once at its start: the menu's sorted list and the tabs over it, what the player
     * carries, and the slotted piece one tier up with the metal that costs (both empty when there is no next tier).
     * Input between two frames reads the last frame's, which is what the player saw when they clicked.
     */
    private record Frame(List<RecipeHolder<ForgeRecipe>> recipes, ForgeTabs tabs, List<ItemStack> carried, ItemStack preview, ItemStack metal) {}

    /** A text and the box it was fitted to, for remembering how it fitted. */
    private record FitKey(String text, int width, int height) {}

    /** More fits than this (the counts on the ingredients change as the player gathers) and the memory starts again. */
    private static final int MAX_FITS = 256;

    private enum Align { LEFT, CENTER, RIGHT }

    private @Nullable ForgeCategory tab;
    /** The chosen recipe, as its index in the menu's list (the index the Forge button sends), or -1. */
    private int selected = -1;
    private int scrollRow;
    private boolean draggingScroll;
    private final Inventory playerInventory;
    private Frame frame;
    private final List<Shortened> shortened = new ArrayList<>();
    private final Map<FitKey, TextFit.Fit> fits = new HashMap<>();
    private @Nullable List<Component> lastTooltip;
    /** How the chosen recipe's name was fitted in the last frame; null before the first frame. */
    private TextFit.Fit lastNameFit;

    public AstralForgeScreen(AstralForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        playerInventory = inventory;
        imageWidth = ForgeLayout.WIDTH;
        imageHeight = ForgeLayout.HEIGHT;
        frame = newFrame();
        openTab(frame.tabs().restore(lastTab), false);
    }

    @Override
    protected void init() {
        super.init();
        fits.clear(); // widths come from the font, which a resource reload can change under an open screen
    }

    // ------------------------------------------------------------------ state

    /** Looks at the menu once: its list, the tabs over it, what the player carries and the reforge preview. */
    private Frame newFrame() {
        List<RecipeHolder<ForgeRecipe>> recipes = menu.recipes();
        List<ForgeCategory> categories = new ArrayList<>(recipes.size());
        for (RecipeHolder<ForgeRecipe> holder : recipes) {
            categories.add(holder.value().category());
        }
        return new Frame(recipes, ForgeTabs.of(categories), ForgeCrafting.carried(playerInventory), upgradePreview(), reforgeMetal());
    }

    private List<RecipeHolder<ForgeRecipe>> recipes() {
        return frame.recipes();
    }

    private ForgeTabs tabs() {
        return frame.tabs();
    }

    /** The open tab's recipes, as indices into the menu's list. */
    private List<Integer> visible() {
        return tab == null ? List.of() : tabs().indicesIn(tab);
    }

    private @Nullable ForgeRecipe selectedRecipe() {
        List<RecipeHolder<ForgeRecipe>> list = recipes();
        return selected >= 0 && selected < list.size() ? list.get(selected).value() : null;
    }

    private boolean locked(ForgeRecipe recipe) {
        return recipe.tier() > menu.forgeTier();
    }

    /** True if what the player carries (the frame's look at it) covers {@code recipe}'s ingredients. */
    private boolean affordable(ForgeRecipe recipe) {
        return ForgeCrafting.covers(recipe.ingredients(), frame.carried());
    }

    /** Opens {@code category}'s tab (none if null) on the recipe last chosen there, else its first. */
    private void openTab(@Nullable ForgeCategory category, boolean clicked) {
        tab = category;
        scrollRow = 0;
        if (category == null) {
            selected = -1;
            return;
        }
        lastTab = category;
        List<Integer> indices = tabs().indicesIn(category);
        int pick = indices.isEmpty() ? -1 : indices.get(0);
        ResourceLocation remembered = LAST_RECIPE.get(category);
        if (remembered != null) {
            List<RecipeHolder<ForgeRecipe>> list = recipes();
            for (int index : indices) {
                if (list.get(index).id().equals(remembered)) {
                    pick = index;
                    break;
                }
            }
        }
        selected = pick;
        scrollToSelected();
        if (clicked) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    /** Chooses recipe {@code index} (of the menu's list) in the open tab and remembers it there. */
    private void choose(int index, boolean clicked) {
        selected = index;
        if (tab != null && index >= 0) {
            LAST_RECIPE.put(tab, recipes().get(index).id());
        }
        scrollToSelected();
        if (clicked) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0f));
        }
    }

    private void scrollToSelected() {
        int position = visible().indexOf(selected);
        if (position >= 0) {
            scrollRow = ForgePaging.scrollToShow(scrollRow, position, visible().size());
        }
    }

    /** {@code r} in screen coordinates. */
    private Rect at(Rect r) {
        return r.moved(leftPos, topPos);
    }

    /** The list index of the recipe under the mouse in the grid, or -1. */
    private int cellAt(double mouseX, double mouseY) {
        List<Integer> list = visible();
        for (int i = 0; i < ForgeLayout.ROWS * ForgeLayout.COLS; i++) {
            int position = ForgePaging.positionAt(i, scrollRow, list.size());
            if (position < 0) {
                break; // the cells fill in order: the first empty one ends the page
            }
            if (at(ForgeLayout.cell(i)).contains(mouseX, mouseY)) {
                return list.get(position);
            }
        }
        return -1;
    }

    /** The tab under the mouse, or null. */
    private @Nullable ForgeCategory tabAt(double mouseX, double mouseY) {
        List<ForgeCategory> list = tabs().tabs();
        for (int i = 0; i < list.size() && i < ForgeLayout.MAX_TABS; i++) {
            if (at(ForgeLayout.tab(i)).contains(mouseX, mouseY)) {
                return list.get(i);
            }
        }
        return null;
    }

    /** The slotted piece one tier up, for the preview; empty when there is no piece or no next tier. */
    private ItemStack upgradePreview() {
        ItemStack piece = menu.piece();
        int unlock = GearTier.unlockTier(piece, true);
        if (piece.isEmpty() || unlock == 0) {
            return ItemStack.EMPTY;
        }
        int tier = GearTier.of(piece, unlock);
        if (tier >= GearTier.MAX) {
            return ItemStack.EMPTY;
        }
        ItemStack next = piece.copy();
        GearTier.set(next, tier + 1);
        return next;
    }

    /** One of the next tier's metal (its count is drawn beside it), or empty. */
    private ItemStack reforgeMetal() {
        ItemStack piece = menu.piece();
        int unlock = GearTier.unlockTier(piece, true);
        if (piece.isEmpty() || unlock == 0) {
            return ItemStack.EMPTY;
        }
        int tier = GearTier.of(piece, unlock);
        return ForgeTiers.reforgeCost(tier + 1)
                .map(cost -> new ItemStack(BuiltInRegistries.ITEM.get(cost.item())))
                .orElse(ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ hover

    /** What the mouse is over and the tooltip it gets, or null. */
    private @Nullable Hover hoverAt(double mouseX, double mouseY) {
        ForgeCategory overTab = tabAt(mouseX, mouseY);
        if (overTab != null) {
            return new Hover(ItemStack.EMPTY, List.of(Component.translatable(overTab.translationKey())));
        }
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            ForgeRecipe recipe = recipes().get(cell).value();
            return item(recipe.result(), lockLine(recipe));
        }
        ForgeRecipe recipe = selectedRecipe();
        if (recipe != null) {
            if (at(ForgeLayout.RESULT).contains(mouseX, mouseY)) {
                return item(recipe.result(), lockLine(recipe));
            }
            for (int i = 0; i < recipe.ingredients().size() && i < ForgeLayout.MAX_INGREDIENTS; i++) {
                if (at(ForgeLayout.ingredient(i)).contains(mouseX, mouseY)) {
                    ItemStack shown = shown(recipe.ingredients().get(i));
                    return shown.isEmpty() ? null : item(shown, List.of());
                }
            }
        }
        ItemStack preview = frame.preview();
        if (!preview.isEmpty() && at(ForgeLayout.PREVIEW).contains(mouseX, mouseY)) {
            return item(preview, List.of(Component.translatable("gui.cosmicbreach.forge.preview").withStyle(ChatFormatting.GRAY)));
        }
        ItemStack metal = frame.metal();
        if (!metal.isEmpty() && at(ForgeLayout.COST).contains(mouseX, mouseY)) {
            return item(metal, List.of());
        }
        for (Shortened s : shortened) {
            if (s.box().contains(mouseX, mouseY)) {
                return new Hover(ItemStack.EMPTY, List.of(Component.literal(s.full())));
            }
        }
        return null;
    }

    /** The item's own inventory tooltip, then {@code extra}. */
    private Hover item(ItemStack stack, List<Component> extra) {
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
        lines.addAll(extra);
        return new Hover(stack, List.copyOf(lines));
    }

    private List<Component> lockLine(ForgeRecipe recipe) {
        return locked(recipe)
                ? List.of(Component.translatable("gui.cosmicbreach.forge.needs_tier", GearTier.roman(recipe.tier())).withColor(BAD))
                : List.of();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        frame = newFrame();
        super.render(graphics, mouseX, mouseY, partialTick);
        lastTooltip = null;
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            // the lines are built once and drawn the way vanilla's own slot tooltip draws them
            ItemStack stack = hoveredSlot.getItem();
            List<Component> lines = getTooltipFromContainerItem(stack);
            graphics.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
            lastTooltip = lines;
            return;
        }
        Hover hover = hoverAt(mouseX, mouseY);
        if (hover == null) {
            return;
        }
        if (hover.stack().isEmpty()) {
            graphics.renderComponentTooltip(font, hover.lines(), mouseX, mouseY);
        } else {
            graphics.renderTooltip(font, hover.lines(), hover.stack().getTooltipImage(), hover.stack(), mouseX, mouseY);
        }
        lastTooltip = hover.lines();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        shortened.clear();
        int left = leftPos + ForgeLayout.BODY_X;
        int top = topPos;
        int right = leftPos + ForgeLayout.WIDTH;
        int bottom = topPos + ForgeLayout.HEIGHT;
        graphics.fillGradient(left, top, right, bottom, PANEL_TOP, PANEL_BOTTOM);
        corners(graphics, left, top, right, bottom, 6);
        graphics.fill(left + 8, top + ForgeLayout.SEPARATOR_TOP, right - 8, top + ForgeLayout.SEPARATOR_TOP + 1, SEPARATOR);
        graphics.fill(left + 8, top + ForgeLayout.SEPARATOR_BOTTOM, right - 8, top + ForgeLayout.SEPARATOR_BOTTOM + 1, SEPARATOR);
        for (Slot slot : menu.slots) {
            slotFrame(graphics, leftPos + slot.x, topPos + slot.y);
        }
        drawTabs(graphics, mouseX, mouseY);
        drawLabels(graphics);
        drawGrid(graphics, mouseX, mouseY);
        drawScrollbar(graphics);
        drawDetail(graphics, mouseX, mouseY);
        drawReforge(graphics, mouseX, mouseY);
    }

    /** The labels are drawn with the panel, fitted like every other text, so the default label pass draws nothing. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawLabels(GuiGraphics graphics) {
        drawFitted(graphics, title, ForgeLayout.TITLE, WHITE, Align.LEFT);
        int tier = menu.forgeTier();
        drawFitted(graphics, Component.translatable("gui.cosmicbreach.forge.tier", GearTier.roman(tier)), ForgeLayout.FORGE_TIER,
                0xFF000000 | GearTier.color(tier), Align.RIGHT);
        drawFitted(graphics, playerInventoryTitle, ForgeLayout.INVENTORY_LABEL, DIM, Align.LEFT);
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        ForgeTabs model = tabs();
        List<ForgeCategory> list = model.tabs();
        List<RecipeHolder<ForgeRecipe>> all = recipes();
        for (int i = 0; i < list.size() && i < ForgeLayout.MAX_TABS; i++) {
            ForgeCategory category = list.get(i);
            Rect r = at(ForgeLayout.tab(i));
            boolean open = category == tab;
            int right = open ? r.right() + 1 : r.right();
            graphics.fill(r.x(), r.y(), right, r.bottom(), open ? PANEL_TOP : r.contains(mouseX, mouseY) ? CELL_HOVER : CELL_BG);
            outline(graphics, r.x(), r.y(), right, r.bottom(), open ? GOLD : SLOT_EDGE);
            if (open) {
                // no edge between the open tab and the panel, so the two read as one
                graphics.fill(r.right() - 1, r.y() + 1, right, r.bottom() - 1, PANEL_TOP);
            }
            ItemStack icon = all.get(model.indicesIn(category).get(0)).value().result();
            graphics.renderItem(icon, r.x() + (r.w() - 16) / 2, r.y() + (r.h() - 16) / 2);
        }
    }

    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RecipeHolder<ForgeRecipe>> list = recipes();
        List<Integer> indices = visible();
        for (int i = 0; i < ForgeLayout.ROWS * ForgeLayout.COLS; i++) {
            Rect r = at(ForgeLayout.cell(i));
            int position = ForgePaging.positionAt(i, scrollRow, indices.size());
            boolean filled = position >= 0;
            graphics.fill(r.x(), r.y(), r.right() - 1, r.bottom() - 1, filled && r.contains(mouseX, mouseY) ? CELL_HOVER : CELL_BG);
            if (!filled) {
                continue;
            }
            int index = indices.get(position);
            ForgeRecipe recipe = list.get(index).value();
            graphics.renderItem(recipe.result(), r.x() + 1, r.y() + 1);
            if (locked(recipe)) {
                graphics.fill(r.x(), r.y(), r.right() - 1, r.bottom() - 1, ABOVE_ITEMS, 0xB00A0C12);
                String roman = GearTier.roman(recipe.tier());
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 250);
                graphics.drawString(font, roman, r.x() + (ForgeLayout.CELL - 1 - font.width(roman)) / 2, r.y() + 5,
                        0xFF000000 | GearTier.color(recipe.tier()), true);
                graphics.pose().popPose();
            } else if (affordable(recipe)) {
                graphics.fill(r.right() - 4, r.y() + 1, r.right() - 2, r.y() + 3, ABOVE_ITEMS, GOOD);
            }
            if (index == selected) {
                outline(graphics, r.x() - 1, r.y() - 1, r.right(), r.bottom(), GOLD);
            }
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        Rect r = at(ForgeLayout.SCROLLBAR);
        graphics.fill(r.x(), r.y(), r.right(), r.bottom(), SLOT_BG);
        int count = visible().size();
        int thumb = 15;
        int ty = r.y() + ForgePaging.thumbOffset(scrollRow, count, r.h(), thumb);
        graphics.fill(r.x() + 1, ty + 1, r.right() - 1, ty + thumb - 1, ForgePaging.maxScroll(count) == 0 ? SLOT_EDGE : 0xFF8C7A52);
    }

    private void drawDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        ForgeRecipe recipe = selectedRecipe();
        if (recipe == null) {
            lastNameFit = null;
            drawFitted(graphics, Component.translatable("gui.cosmicbreach.forge.no_recipes"), ForgeLayout.NAME, DIM, Align.LEFT);
            return;
        }
        Rect result = at(ForgeLayout.RESULT);
        slotFrame(graphics, result.x() + 1, result.y() + 1);
        graphics.renderItem(recipe.result(), result.x() + 1, result.y() + 1);
        graphics.renderItemDecorations(font, recipe.result(), result.x() + 1, result.y() + 1);
        lastNameFit = drawFitted(graphics, recipe.result().getHoverName(), ForgeLayout.NAME, WHITE, Align.LEFT);

        List<SizedIngredient> needs = recipe.ingredients();
        for (int i = 0; i < needs.size() && i < ForgeLayout.MAX_INGREDIENTS; i++) {
            SizedIngredient need = needs.get(i);
            Rect box = at(ForgeLayout.ingredient(i));
            slotFrame(graphics, box.x() + 1, box.y() + 1);
            graphics.renderItem(shown(need), box.x() + 1, box.y() + 1);
            int have = ForgeCrafting.count(minecraft.player.getInventory(), need);
            drawFitted(graphics, Component.literal(Math.min(have, 999) + "/" + need.count()), ForgeLayout.ingredientCount(i),
                    have >= need.count() ? GOOD : BAD, Align.LEFT);
        }
        boolean locked = locked(recipe);
        Component tierLine = locked
                ? Component.translatable("gui.cosmicbreach.forge.needs_tier", GearTier.roman(recipe.tier()))
                : Component.translatable("gui.cosmicbreach.forge.recipe_tier", GearTier.roman(recipe.tier()));
        drawFitted(graphics, tierLine, ForgeLayout.RECIPE_TIER, locked ? BAD : 0xFF000000 | GearTier.color(recipe.tier()), Align.LEFT);
        drawButton(graphics, mouseX, mouseY, ForgeLayout.CRAFT, Component.translatable("gui.cosmicbreach.forge.craft"),
                craftable(recipe, frame.carried()));
    }

    /** Cell {@code i} of the Satchel pick strip, in screen coordinates. */
    private Rect pickCell(int i) {
        Rect strip = at(ForgeLayout.SATCHEL_PICKS);
        return new Rect(strip.x() + i * 18, strip.y(), 18, 18);
    }

    private void drawReforge(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFitted(graphics, Component.translatable("gui.cosmicbreach.forge.reforge_title"), ForgeLayout.REFORGE_TITLE, TEXT, Align.LEFT);
        ItemStack piece = menu.piece();
        int unlock = GearTier.unlockTier(piece, true);
        ForgeTiers.Check check = menu.reforgeCheck(minecraft.player);
        if (piece.isEmpty() || unlock == 0) {
            List<Integer> picks = piece.isEmpty() ? menu.satchelPicks(minecraft.player) : List.of();
            drawFitted(graphics, Component.translatable("gui.cosmicbreach.forge.reforge_hint"),
                    picks.isEmpty() ? ForgeLayout.REFORGE_HINT : ForgeLayout.REFORGE_LINE, DIM, Align.LEFT);
            List<ItemStack> gear = SatchelLocator.contentsOf(minecraft.player).map(c -> c.gear()).orElse(List.of());
            for (int i = 0; i < picks.size() && i < 4; i++) {
                Rect r = pickCell(i);
                slotFrame(graphics, r.x() + 1, r.y() + 1);
                graphics.renderItem(gear.get(picks.get(i)), r.x() + 1, r.y() + 1);
                if (r.contains(mouseX, mouseY)) {
                    graphics.renderTooltip(font, gear.get(picks.get(i)), mouseX, mouseY);
                }
            }
        } else {
            int tier = GearTier.of(piece, unlock);
            Component line;
            int color;
            if (tier >= GearTier.MAX) {
                line = Component.translatable("gui.cosmicbreach.forge.reforge_max");
                color = 0xFF000000 | GearTier.color(tier);
            } else if (check == ForgeTiers.Check.FORGE_TOO_LOW) {
                line = Component.translatable("gui.cosmicbreach.forge.needs_tier", GearTier.roman(tier + 1));
                color = BAD;
            } else {
                line = Component.translatable("gui.cosmicbreach.forge.reforge_step", GearTier.roman(tier), GearTier.roman(tier + 1));
                color = 0xFF000000 | GearTier.color(tier + 1);
            }
            drawFitted(graphics, line, ForgeLayout.REFORGE_LINE, color, Align.LEFT);
            ItemStack preview = frame.preview();
            if (!preview.isEmpty()) {
                Rect p = at(ForgeLayout.PREVIEW);
                slotFrame(graphics, p.x() + 1, p.y() + 1);
                graphics.renderItem(preview, p.x() + 1, p.y() + 1);
            }
            ItemStack metal = frame.metal();
            Optional<ForgeTiers.Cost> cost = ForgeTiers.reforgeCost(tier + 1);
            if (!metal.isEmpty() && cost.isPresent()) {
                Rect c = at(ForgeLayout.COST);
                slotFrame(graphics, c.x() + 1, c.y() + 1);
                graphics.renderItem(metal, c.x() + 1, c.y() + 1);
                int have = ForgeCrafting.count(minecraft.player.getInventory(), metal.getItem());
                drawFitted(graphics, Component.literal(Math.min(have, 999) + "/" + cost.get().count()), ForgeLayout.COST_COUNT,
                        have >= cost.get().count() ? GOOD : BAD, Align.LEFT);
            }
        }
        drawButton(graphics, mouseX, mouseY, ForgeLayout.REFORGE_BUTTON, Component.translatable("gui.cosmicbreach.forge.reforge"),
                check == ForgeTiers.Check.OK);
    }

    private void drawButton(GuiGraphics graphics, int mouseX, int mouseY, Rect box, Component label, boolean active) {
        Rect r = at(box);
        boolean hover = active && r.contains(mouseX, mouseY);
        int bg = !active ? 0xFF1A1E26 : hover ? 0xFF6A5528 : 0xFF4A3C1E;
        graphics.fill(r.x(), r.y(), r.right(), r.bottom(), bg);
        outline(graphics, r.x(), r.y(), r.right(), r.bottom(), active ? GOLD : SLOT_EDGE);
        drawFitted(graphics, label, new Rect(box.x() + 2, box.y() + 3, box.w() - 4, 9), active ? WHITE : DIM, Align.CENTER);
    }

    /**
     * Draws {@code text} inside {@code box} (panel coordinates): at full size if it fits on the box's lines, else
     * smaller, else cut short with "...", in which case hovering the box shows the whole text.
     */
    private TextFit.Fit drawFitted(GuiGraphics graphics, Component text, Rect box, int color, Align align) {
        String full = text.getString();
        if (fits.size() > MAX_FITS) {
            fits.clear();
        }
        TextFit.Fit fit = fits.computeIfAbsent(new FitKey(full, box.w(), box.h()),
                key -> TextFit.fit(key.text(), key.width(), key.height(), font.lineHeight, font::width));
        Rect r = at(box);
        float scale = fit.scale();
        float room = box.w() / scale;
        graphics.pose().pushPose();
        graphics.pose().translate(r.x(), r.y(), 0);
        graphics.pose().scale(scale, scale, 1.0f);
        for (int i = 0; i < fit.lines().size(); i++) {
            String line = fit.lines().get(i);
            int x = switch (align) {
                case LEFT -> 0;
                case CENTER -> (int) ((room - font.width(line)) / 2);
                case RIGHT -> (int) (room - font.width(line));
            };
            graphics.drawString(font, line, x, i * font.lineHeight, color, false);
        }
        graphics.pose().popPose();
        if (fit.shortened()) {
            shortened.add(new Shortened(r, full));
        }
        return fit;
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
            ForgeCategory overTab = tabAt(mouseX, mouseY);
            if (overTab != null) {
                if (overTab != tab) {
                    openTab(overTab, true);
                }
                return true;
            }
            int cell = cellAt(mouseX, mouseY);
            if (cell >= 0) {
                choose(cell, true);
                return true;
            }
            if (at(ForgeLayout.CRAFT).contains(mouseX, mouseY)) {
                return pressCraft() || true;
            }
            if (at(ForgeLayout.REFORGE_BUTTON).contains(mouseX, mouseY)) {
                return pressReforge() || true;
            }
            if (menu.piece().isEmpty()) {
                List<Integer> picks = menu.satchelPicks(minecraft.player);
                for (int i = 0; i < picks.size() && i < 4; i++) {
                    if (pickCell(i).contains(mouseX, mouseY)) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AstralForgeMenu.PICK_GEAR + picks.get(i));
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                        return true;
                    }
                }
            }
            if (at(ForgeLayout.SCROLLBAR).contains(mouseX, mouseY)) {
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
        Rect r = at(ForgeLayout.SCROLLBAR);
        float t = (float) ((mouseY - r.y() - 7) / (r.h() - 14));
        scrollRow = ForgePaging.dragTo(t, visible().size());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (at(ForgeLayout.GRID).contains(mouseX, mouseY) || at(ForgeLayout.SCROLLBAR).contains(mouseX, mouseY)) {
            scrollRow = ForgePaging.wheel(scrollRow, scrollY, visible().size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** The tab strip's empty parts count as outside the screen, so a click there with an item on the cursor throws it. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return ForgeLayout.clickedOutside(mouseX, mouseY, guiLeft, guiTop, tabs().tabs().size());
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

    // ------------------------------------------------------------------ test access (dev scenarios)

    public int recipeCount() {
        return recipes().size();
    }

    public int selectedIndex() {
        return selected;
    }

    /** Chooses recipe {@code index} of the menu's list, opening its tab and scrolling it into view. */
    public void select(int index) {
        List<RecipeHolder<ForgeRecipe>> list = recipes();
        if (list.isEmpty()) {
            return;
        }
        int i = Mth.clamp(index, 0, list.size() - 1);
        ForgeCategory category = list.get(i).value().category();
        if (category != tab) {
            tab = category;
            lastTab = category;
            scrollRow = 0;
        }
        choose(i, false);
    }

    /** True if the Forge button would craft the chosen recipe now (what the player carries is read live). */
    public boolean canCraftSelected() {
        return craftable(selectedRecipe(), ForgeCrafting.carried(playerInventory));
    }

    private boolean craftable(@Nullable ForgeRecipe recipe, List<ItemStack> carried) {
        return recipe != null && !locked(recipe) && ForgeCrafting.covers(recipe.ingredients(), carried);
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

    /** Screen coordinates of the middle of recipe {@code index}'s cell, opening its tab and scrolling it into view first. */
    public double[] cellCenter(int index) {
        select(index);
        int cell = ForgePaging.cellOf(visible().indexOf(index), scrollRow);
        if (cell < 0) {
            throw new IllegalStateException("recipe " + index + " is not on the page after scrolling to it");
        }
        return center(ForgeLayout.cell(cell));
    }

    /** Screen coordinates of the middle of recipe {@code index}'s cell if the open tab shows it right now, else null. */
    public double @Nullable [] visibleCellCenter(int index) {
        int position = visible().indexOf(index);
        int cell = position < 0 ? -1 : ForgePaging.cellOf(position, scrollRow);
        return cell < 0 ? null : center(ForgeLayout.cell(cell));
    }

    /** Screen coordinates of the middle of {@code category}'s tab. */
    public double[] tabCenter(ForgeCategory category) {
        int i = tabs().tabs().indexOf(category);
        if (i < 0 || i >= ForgeLayout.MAX_TABS) {
            throw new IllegalStateException("no tab for " + category.getSerializedName());
        }
        return center(ForgeLayout.tab(i));
    }

    public double[] craftButtonCenter() {
        return center(ForgeLayout.CRAFT);
    }

    public double[] reforgeButtonCenter() {
        return center(ForgeLayout.REFORGE_BUTTON);
    }

    public @Nullable ForgeCategory currentTab() {
        return tab;
    }

    public List<ForgeCategory> shownTabs() {
        return tabs().tabs();
    }

    /** The open tab's recipes, as indices of the menu's list. */
    public List<Integer> shownRecipes() {
        return visible();
    }

    public ForgeCategory categoryOf(int index) {
        return recipes().get(index).value().category();
    }

    /** The tooltip the last frame drew, or null. */
    public @Nullable List<Component> lastTooltip() {
        return lastTooltip;
    }

    /** The whole texts the last frame had to cut short; empty when everything fitted. */
    public List<String> shortenedTexts() {
        return shortened.stream().map(Shortened::full).toList();
    }

    /** How the chosen recipe's name was fitted in the last frame, or null before the first frame. */
    public TextFit.Fit lastNameFit() {
        return lastNameFit;
    }

    // ------------------------------------------------------------------ helpers

    private double[] center(Rect box) {
        Rect r = at(box);
        return new double[] {r.centerX(), r.centerY()};
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
