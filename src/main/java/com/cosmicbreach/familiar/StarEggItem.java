package com.cosmicbreach.familiar;

import com.cosmicbreach.world.weather.SolarFlare;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Star Egg (GDD 8.2), found in vaults. It hatches after 10 minutes of loaded time in a Brazier of Solenne, or at once
 * when it lies out under a Solar Flare (in a brazier or on the ground). What it holds is on the egg
 * ({@code cosmicbreach:star_egg}); an egg without it hatches any of the three.
 */
public class StarEggItem extends Item {
    public StarEggItem(Properties properties) {
        super(properties);
    }

    /** What {@code stack} holds, or null if it doesn't say. */
    public static @Nullable FamiliarKind kind(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(FamiliarRegistry.EGG.get());
    }

    /** An egg holding {@code kind}. */
    public static ItemStack of(FamiliarKind kind) {
        ItemStack stack = new ItemStack(FamiliarRegistry.STAR_EGG.get());
        stack.set(FamiliarRegistry.EGG.get(), kind);
        return stack;
    }

    /** The lantern {@code egg} hatches into: its kind (or any, by {@code random}), a new bond. */
    public static ItemStack hatch(ItemStack egg, RandomSource random) {
        FamiliarKind kind = kind(egg);
        if (kind == null) {
            kind = FamiliarKind.values()[random.nextInt(FamiliarKind.values().length)];
        }
        return FamiliarLanternItem.of(FamiliarBond.hatch(kind, new UUID(random.nextLong(), random.nextLong())));
    }

    /** Under a Solar Flare an egg lying out hatches at once. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel level && entity.tickCount % 10 == 0 && SolarFlare.exposed(entity)) {
            int count = stack.getCount();
            Vec3 at = entity.position();
            entity.setItem(hatch(stack, level.random));
            for (int i = 1; i < count; i++) {
                ItemEntity more = new ItemEntity(level, at.x, at.y, at.z, hatch(stack, level.random));
                more.setDefaultPickUpDelay();
                level.addFreshEntity(more);
            }
            level.playSound(null, at.x, at.y, at.z, FamiliarRegistry.HATCH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            FamiliarKind k = FamiliarLanternItem.bond(entity.getItem()).kind();
            FamiliarNet.fxAt(level, at.add(0, 0.3, 0), FamiliarFxPayload.HATCH, -1, k.ordinal());
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FamiliarKind k = kind(stack);
        tooltip.add(Component.translatable("item.cosmicbreach.star_egg.hint." + (k == null ? "unknown" : k.id())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cosmicbreach.star_egg.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
