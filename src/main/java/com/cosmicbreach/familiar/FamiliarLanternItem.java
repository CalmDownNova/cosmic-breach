package com.cosmicbreach.familiar;

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
import org.jetbrains.annotations.Nullable;

/**
 * The Familiar Lantern (GDD 8.2): the lantern is the familiar. It carries everything about it
 * ({@link FamiliarBond}); use it (or hold the familiar key, H) to summon or dismiss the familiar, tap the key to cycle
 * Attack, Guard and Passive. Lit with its familiar resting inside, empty while it is out, dark for 60 s after it died.
 */
public class FamiliarLanternItem extends Item {
    public FamiliarLanternItem(Properties properties) {
        super(properties);
    }

    /** The familiar bound to {@code stack}, or null if it isn't a bound lantern. */
    public static @Nullable FamiliarBond bond(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(FamiliarRegistry.BOND.get());
    }

    /** A lantern bound to {@code bond}. */
    public static ItemStack of(FamiliarBond bond) {
        ItemStack stack = new ItemStack(FamiliarRegistry.LANTERN.get());
        stack.set(FamiliarRegistry.BOND.get(), bond);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (bond(stack) == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer sp) {
            FamiliarSessions.toggle(sp, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        FamiliarBond b = bond(stack);
        return b == null ? super.getName(stack)
                : Component.translatable("item.cosmicbreach.familiar_lantern.bound", Component.translatable(b.kind().nameKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FamiliarBond b = bond(stack);
        if (b == null) {
            tooltip.add(Component.translatable("item.cosmicbreach.familiar_lantern.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.cosmicbreach.familiar_lantern.role." + b.kind().id()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cosmicbreach.familiar_lantern.mode", Component.translatable(b.mode().nameKey()))
                .withStyle(ChatFormatting.GOLD));
        Level level = context.level();
        if (level != null) {
            long now = level.getGameTime();
            if (b.state(false, now) == LanternState.DARK) {
                tooltip.add(Component.translatable("item.cosmicbreach.familiar_lantern.dark", LanternState.secondsLeft(b.darkUntil(), now))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                int pct = Math.round(b.healthAt(now) * 100f);
                if (pct < 100) {
                    tooltip.add(Component.translatable("item.cosmicbreach.familiar_lantern.resting", pct).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        Component key = Component.keybind("key.cosmicbreach.familiar");
        tooltip.add(Component.translatable("item.cosmicbreach.familiar_lantern.controls", key, key).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Changing the mode or the stored health must not bob the lantern in the hand. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }
}
