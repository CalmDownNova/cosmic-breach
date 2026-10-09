package com.cosmicbreach.client.satchel;

import com.cosmicbreach.satchel.SatchelContents;
import com.cosmicbreach.satchel.SatchelMenu;
import com.cosmicbreach.satchel.SatchelNet;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Satchel's screen: two tabs. Materials: a grid of every type the satchel holds, with its count; left click takes a
 * stack out (shift: all that fits), right click switches pickup for that type, a click with an item on the cursor puts it
 * in. Gear: the 18 slots. Everything is a request to the server, which does the moving.
 */
public class SatchelScreen extends AbstractContainerScreen<SatchelMenu> {
    private static final int PANEL_TOP = 0xF2141822;
    private static final int PANEL_BOTTOM = 0xF40B0D12;
    private static final int GOLD = 0xFFFFC857;
    private static final int TEXT = 0xFFB4BCC8;
    private static final int DIM = 0xFF7C8492;
    private static final int CELL_BG = 0xFF1C2230;
    private static final int CELL_HOVER = 0xFF2C3548;
    private static final int SLOT_BG = 0xFF10141C;
    private static final int SLOT_EDGE = 0xFF384152;

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int GRID_X = SatchelMenu.GEAR_X;
    private static final int GRID_Y = SatchelMenu.GEAR_Y;
    private static final int TAB_Y = 16;
    private static final int TAB_W = 70;
    private static final int TAB_H = 16;

    private static boolean lastMaterials;
    private int scrollRow;

    public SatchelScreen(SatchelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 180;
        inventoryLabelY = SatchelMenu.INVENTORY_Y - 11;
        titleLabelY = 5;
        menu.materialsTab = lastMaterials;
    }

    /** The types the Materials tab lists: everything held, and every type with pickup switched off. */
    private List<ResourceLocation> types() {
        SatchelContents c = menu.contents();
        List<ResourceLocation> out = new ArrayList<>(c.materials().keySet());
        for (ResourceLocation off : c.pickupOff()) {
            if (!out.contains(off)) {
                out.add(off);
            }
        }
        out.removeIf(id -> c.count(id) == 0 && c.pickupOn(id));
        out.sort(ResourceLocation::compareTo);
        return out;
    }

    private int maxScroll(int types) {
        return Math.max(0, (types + COLS - 1) / COLS - ROWS);
    }

    private int cellAt(double mx, double my) {
        int x = (int) mx - leftPos - GRID_X;
        int y = (int) my - topPos - GRID_Y;
        if (x < 0 || y < 0 || x >= COLS * 18 || y >= ROWS * 18) {
            return -1;
        }
        return (scrollRow + y / 18) * COLS + x / 18;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, SLOT_EDGE);
        drawTab(g, 0, "gui.cosmicbreach.satchel.tab.materials", menu.materialsTab, mouseX, mouseY);
        drawTab(g, 1, "gui.cosmicbreach.satchel.tab.gear", !menu.materialsTab, mouseX, mouseY);
        if (!menu.materialsTab) {
            for (int i = 0; i < SatchelContents.GEAR_SLOTS; i++) {
                slotBox(g, leftPos + SatchelMenu.GEAR_X + (i % 9) * 18, topPos + SatchelMenu.GEAR_Y + (i / 9) * 18);
            }
        }
        for (int i = 0; i < 36; i++) {
            int col = i < 27 ? i % 9 : i - 27;
            int y = i < 27 ? SatchelMenu.INVENTORY_Y + (i / 9) * 18 : SatchelMenu.HOTBAR_Y;
            slotBox(g, leftPos + SatchelMenu.INVENTORY_X + col * 18, topPos + y);
        }
    }

    private static void slotBox(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    private void drawTab(GuiGraphics g, int index, String key, boolean active, int mx, int my) {
        int x = leftPos + 8 + index * (TAB_W + 4);
        int y = topPos + TAB_Y;
        boolean hover = mx >= x && mx < x + TAB_W && my >= y && my < y + TAB_H;
        g.fill(x, y, x + TAB_W, y + TAB_H, active ? CELL_HOVER : hover ? CELL_BG : SLOT_BG);
        g.renderOutline(x, y, TAB_W, TAB_H, active ? GOLD : SLOT_EDGE);
        g.drawCenteredString(font, Component.translatable(key), x + TAB_W / 2, y + 4, active ? GOLD : TEXT);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (menu.materialsTab) {
            renderMaterials(g, mouseX, mouseY);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderMaterials(GuiGraphics g, int mouseX, int mouseY) {
        SatchelContents c = menu.contents();
        List<ResourceLocation> types = types();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(types.size()));
        int hovered = cellAt(mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scrollRow + row) * COLS + col;
                int x = leftPos + GRID_X + col * 18;
                int y = topPos + GRID_Y + row * 18;
                boolean has = index < types.size();
                g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
                g.fill(x, y, x + 16, y + 16, has && index == hovered ? CELL_HOVER : CELL_BG);
                if (!has) {
                    continue;
                }
                ResourceLocation id = types.get(index);
                Item item = BuiltInRegistries.ITEM.get(id);
                ItemStack icon = new ItemStack(item);
                g.renderItem(icon, x, y);
                String count = Integer.toString(c.count(id));
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, count, x + 17 - font.width(count), y + 9, c.pickupOn(id) ? 0xFFFFFFFF : DIM, true);
                if (!c.pickupOn(id)) {
                    g.drawString(font, "x", x + 1, y, 0xFFFF7A6A, true);
                }
                g.pose().popPose();
            }
        }
        if (types.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.cosmicbreach.satchel.empty"), leftPos + imageWidth / 2, topPos + GRID_Y + 20, DIM);
        }
        if (hovered >= 0 && hovered < types.size()) {
            ResourceLocation id = types.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName());
            lines.add(Component.translatable("gui.cosmicbreach.satchel.holds", c.count(id)).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(c.pickupOn(id) ? "gui.cosmicbreach.satchel.pickup_on" : "gui.cosmicbreach.satchel.pickup_off")
                    .withStyle(c.pickupOn(id) ? ChatFormatting.GREEN : ChatFormatting.RED));
            lines.add(Component.translatable("gui.cosmicbreach.satchel.hint").withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int x, int y) {
        if (!menu.materialsTab) {
            super.renderTooltip(g, x, y);
        } else if (hoveredSlot != null && hoveredSlot.index >= SatchelContents.GEAR_SLOTS) {
            super.renderTooltip(g, x, y);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int tab = 0; tab < 2; tab++) {
            int x = leftPos + 8 + tab * (TAB_W + 4);
            int y = topPos + TAB_Y;
            if (mx >= x && mx < x + TAB_W && my >= y && my < y + TAB_H) {
                menu.materialsTab = tab == 0;
                lastMaterials = menu.materialsTab;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
        }
        if (menu.materialsTab) {
            int cell = cellAt(mx, my);
            if (cell >= 0) {
                List<ResourceLocation> types = types();
                if (!menu.getCarried().isEmpty()) {
                    send(SatchelMenu.DEPOSIT_CARRIED, ResourceLocation.withDefaultNamespace("air"), 0);
                } else if (cell < types.size()) {
                    ResourceLocation id = types.get(cell);
                    if (button == 1) {
                        send(SatchelMenu.TOGGLE, id, 0);
                    } else if (button == 0) {
                        Item item = BuiltInRegistries.ITEM.get(id);
                        send(SatchelMenu.WITHDRAW, id, hasShiftDown() ? SatchelContents.CAP : item.getDefaultMaxStackSize());
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (menu.materialsTab) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(dy), 0, maxScroll(types().size()));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void send(int action, ResourceLocation item, int amount) {
        PacketDistributor.sendToServer(new SatchelNet.SatchelActionPayload(menu.containerId, action, item, amount));
    }
}
