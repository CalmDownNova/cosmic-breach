package com.cosmicbreach.onboarding;

import com.cosmicbreach.registry.ModBlocks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** A Starfall Shard: used on any frame of a Breach Ring, it opens the ring ({@link BreachRings#useShard}). */
public class StarfallShardItem extends Item {
    public StarfallShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos()).is(ModBlocks.BREACH_FRAME.get())) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player) {
            BreachRings.useShard(player, level, context.getClickedPos(), context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cosmicbreach.starfall_shard.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
