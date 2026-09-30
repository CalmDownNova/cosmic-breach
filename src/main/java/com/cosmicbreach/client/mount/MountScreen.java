package com.cosmicbreach.client.mount;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.mount.MountMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.HorseInventoryMenu;

/** The vanilla horse screen for a celestial mount, with its tack slot drawn under the saddle and armor slots. */
public class MountScreen extends HorseInventoryScreen {
    static final ResourceLocation TACK_SLOT = CosmicBreach.id("container/mount/tack_slot");
    private final MountMenu mountMenu;

    /** Takes the horse menu type vanilla's screen is written for; ours is always a {@link MountMenu}. */
    public MountScreen(HorseInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, ((MountMenu) menu).mount(), 0);
        this.mountMenu = (MountMenu) menu;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (mountMenu.mount().isTamed()) {
            int i = (width - imageWidth) / 2;
            int j = (height - imageHeight) / 2;
            graphics.blitSprite(TACK_SLOT, i + MountMenu.TACK_X - 1, j + MountMenu.TACK_Y - 1, 18, 18);
        }
    }
}
