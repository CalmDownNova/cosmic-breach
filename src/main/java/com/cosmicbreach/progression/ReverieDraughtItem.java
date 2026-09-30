package com.cosmicbreach.progression;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * The Reverie Draught (GDD section 3.3): drinking it refunds every spent stat point. A player with
 * nothing spent can't start drinking it, so one is never wasted. It leaves a glass bottle, like a
 * potion. The first one is free at Attunement 10 ({@link Attunements}).
 */
public class ReverieDraughtItem extends Item {
    public static final int DRINK_TICKS = 32;

    public ReverieDraughtItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (Attunements.of(player).spent().isZero()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.cosmicbreach.reverie.nothing"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        if (player != null && Attunements.of(player).spent().isZero()) {
            return stack; // nothing left to take back (spent elsewhere while drinking): keep the draught
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            Attunement before = Attunements.of(serverPlayer);
            Attunements.set(serverPlayer, before.respec());
            serverPlayer.displayClientMessage(Component.translatable("message.cosmicbreach.reverie.drunk",
                    before.spent().total()).withStyle(ChatFormatting.GOLD), true);
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (player != null && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cosmicbreach.reverie_draught.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
