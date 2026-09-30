package com.cosmicbreach.item;

import com.cosmicbreach.combat.core.Grade;
import com.cosmicbreach.combat.core.Stat;
import com.cosmicbreach.combat.data.CombatData;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A weapon driven by the combat engine. Its moveset and numbers are the {@link WeaponDef} whose id
 * equals this item's registry id, looked up in the {@link CombatData} of the side asking.
 */
public class CombatWeaponItem extends Item {
    public CombatWeaponItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    public ResourceLocation weaponId() {
        return BuiltInRegistries.ITEM.getKey(this);
    }

    public @Nullable WeaponDef weapon(boolean clientSide) {
        return CombatData.forSide(clientSide).weapon(weaponId());
    }

    public static boolean isCombatWeapon(ItemStack stack) {
        return stack.getItem() instanceof CombatWeaponItem;
    }

    /** The weapon data of a stack, or null if it is not a combat weapon (or its data didn't load). */
    public static @Nullable WeaponDef weaponOf(ItemStack stack, boolean clientSide) {
        return stack.getItem() instanceof CombatWeaponItem item ? item.weapon(clientSide) : null;
    }

    /** No mining with a weapon. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Level level = context.level();
        WeaponDef def = weapon(level == null || level.isClientSide());
        if (def == null) {
            return;
        }
        int tier = GearTier.of(stack, def.tier());
        int steps = GearTier.steps(tier, def.tier());
        tooltip.add(Component.translatable("tooltip.cosmicbreach.gear_tier", GearTier.roman(tier))
                .withColor(steps > 0 ? GearTier.color(tier) : 0xAAAAAA));
        tooltip.add(Component.translatable("tooltip.cosmicbreach.base_damage",
                String.format(Locale.ROOT, "%.1f", def.baseDamage() * GearTier.multiplier(steps))).withStyle(ChatFormatting.GRAY));
        MutableComponent grades = Component.empty();
        boolean first = true;
        for (Stat stat : Stat.values()) {
            Grade grade = def.grades().get(stat);
            if (grade == null) {
                continue;
            }
            if (!first) {
                grades.append(", ");
            }
            grades.append(Component.translatable("cosmicbreach.stat." + stat.getSerializedName())).append(" " + grade.name());
            first = false;
        }
        if (!first) {
            tooltip.add(Component.translatable("tooltip.cosmicbreach.scaling", grades).withStyle(ChatFormatting.GRAY));
        }
    }
}
