package com.cosmicbreach.onboarding;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Starfall Codex: using it opens the guide ({@link Codex#openFromItem}); used while sneaking in Aetheria it throws
 * a mote of light toward the nearest guardian's lair instead ({@link com.cosmicbreach.codex.LairMote}).
 */
public class CodexItem extends Item {
    public CodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() && com.cosmicbreach.world.AetheriaWorld.is(level)) {
            if (player instanceof ServerPlayer server) {
                com.cosmicbreach.codex.LairMote.throwFor(server);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        if (player instanceof ServerPlayer server) {
            Codex.openFromItem(server);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cosmicbreach.starfall_codex.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
