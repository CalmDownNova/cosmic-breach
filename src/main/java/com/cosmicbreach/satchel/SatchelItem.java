package com.cosmicbreach.satchel;

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

/** The Satchel: right-click (or B when worn) opens it. Its contents are the {@link SatchelContents} component. */
public class SatchelItem extends Item {
    public SatchelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            int index = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : SatchelLocator.OFFHAND;
            SatchelMenu.open(server, new SatchelLocator.Ref(false, index));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** No nesting: a Satchel does not go into a bundle or a shulker box. */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SatchelContents c = Satchels.contents(stack);
        long kinds = c.materials().values().stream().filter(n -> n > 0).count();
        long gear = c.gear().stream().filter(s -> !s.isEmpty()).count();
        if (kinds > 0 || gear > 0) {
            lines.add(Component.translatable("item.cosmicbreach.satchel.holds", kinds, gear).withStyle(ChatFormatting.GRAY));
        }
    }
}
