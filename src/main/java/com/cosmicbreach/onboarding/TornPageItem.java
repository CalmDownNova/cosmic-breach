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
 * A Torn Codex Page, found in a Fallen Rift's chest. Using it binds it into a Starfall Codex (given if the
 * player has none) and opens the Codex at "Build a ring". The page is used up.
 */
public class TornPageItem extends Item {
    public TornPageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack page = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            if (!Codex.carries(server)) {
                Codex.give(server);
            }
            server.setData(OnboardingRegistry.STATE, server.getData(OnboardingRegistry.STATE).withCodexOpened());
            Codex.open(server, Codex.BUILD_A_RING);
            page.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(page, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cosmicbreach.torn_codex_page.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
