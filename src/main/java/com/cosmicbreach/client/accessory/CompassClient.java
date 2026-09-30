package com.cosmicbreach.client.accessory;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.accessory.Accessory;
import com.cosmicbreach.accessory.AccessoryRegistry;
import com.cosmicbreach.accessory.AccessoryRules;
import com.cosmicbreach.accessory.CompassTarget;
import com.cosmicbreach.accessory.Worn;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Sunshard Compass on the client: its icon's needle (the item property {@code cosmicbreach:needle}, a share of a
 * turn clockwise from straight ahead, picking one of 16 frames) points at the vault the server found for the local
 * player ({@link CompassTarget}); with none it wanders. Worn in the charm slot it is out of sight, so while it is worn
 * the HUD shows it beside the hotbar, where the off hand's slot would be on the right. Client only.
 */
public final class CompassClient {
    public static final ResourceLocation NEEDLE = CosmicBreach.id("needle");
    public static final ResourceLocation LAYER = CosmicBreach.id("sunshard_compass");
    private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");

    private CompassClient() {
    }

    static void registerItemProperty() {
        ItemProperties.register(AccessoryRegistry.SUNSHARD_COMPASS.get(), NEEDLE,
                (ClampedItemPropertyFunction) (stack, level, entity, seed) -> (float) needle(entity));
    }

    /** The needle for the compass as {@code holder} carries it (the local player when there is no holder). */
    public static double needle(@Nullable Entity holder) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        ClientLevel level = mc.level;
        if (me == null || level == null) {
            return 0.0;
        }
        Entity at = holder != null ? holder : me;
        CompassTarget target = me.getData(AccessoryRegistry.COMPASS);
        if (!target.found()) {
            double t = level.getGameTime() / 60.0;
            return (t + 0.08 * Math.sin(level.getGameTime() * 0.21)) % 1.0; // no vault it knows: the needle wanders
        }
        return AccessoryRules.needle(at.getX(), at.getZ(), at.getYRot(), target.pos().getX() + 0.5, target.pos().getZ() + 0.5);
    }

    /** The target the local player's compass has now, or null. */
    public static @Nullable net.minecraft.core.BlockPos target() {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) {
            return null;
        }
        CompassTarget target = me.getData(AccessoryRegistry.COMPASS);
        return target.found() ? target.pos() : null;
    }

    /** True while the compass shows beside the hotbar (the scenario reads it). */
    public static boolean showing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.options.hideGui && !mc.player.isSpectator()
                && Worn.wears(mc.player, Accessory.SUNSHARD_COMPASS);
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!showing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int middle = graphics.guiWidth() / 2;
        int x = middle + 91;
        boolean offhandRight = mc.player.getMainArm() == HumanoidArm.LEFT && !mc.player.getOffhandItem().isEmpty();
        if (offhandRight) {
            x += 29;
        }
        int y = graphics.guiHeight() - 23;
        graphics.blitSprite(FRAME, x, y, 29, 24);
        graphics.renderItem(new ItemStack(AccessoryRegistry.SUNSHARD_COMPASS.get()), x + 10, y + 4);
    }
}
