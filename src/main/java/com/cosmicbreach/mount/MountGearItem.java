package com.cosmicbreach.mount;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A piece of mount gear ({@link MountGear}): put in its slot of the mount's inventory, or right-click a tamed mount
 * with it. The bardings carry their armor as a body-slot attribute modifier, so the mount's armor is vanilla's.
 */
public class MountGearItem extends Item {
    private final MountGear gear;

    public MountGearItem(Properties properties, MountGear gear) {
        super(properties);
        this.gear = gear;
    }

    public MountGear gear() {
        return gear;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cosmicbreach." + gear.id() + ".desc").withStyle(ChatFormatting.GRAY));
        String who = gear.fits(MountGear.Kind.STAG) && gear.fits(MountGear.Kind.MANTA) ? "both"
                : gear.fits(MountGear.Kind.STAG) ? "stag" : "manta";
        lines.add(Component.translatable("cosmicbreach.mount.gear_for." + who).withStyle(ChatFormatting.DARK_AQUA));
    }
}
