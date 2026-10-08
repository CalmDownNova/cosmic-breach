package com.cosmicbreach.structure.sanctum;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Dying Star Heart (GDD 7.3): forged at Forge III from the Silent Sigil, the Solar Ember, the Hymn Crystal and four
 * Eclipsium Ingots, and set on the Regent's Throne ({@link SanctumThroneBlock}). Each summon takes a new one.
 */
public class DyingStarHeartItem extends Item {
    public DyingStarHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cosmicbreach.dying_star_heart.tip").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.cosmicbreach.dying_star_heart.what").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
