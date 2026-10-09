package com.cosmicbreach.satchel;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.item.CombatWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Which stacks a Satchel takes. The Materials tab: items in the data tags {@code satchel_materials} (every mod material)
 * and {@code satchel_basics} (the common basics), and only plain stacks: a stack with a custom name, enchantment or any
 * other component of its own is not a virtual slot's kind of item, so it is left alone and nothing is lost. The Gear tab:
 * armor, weapons and tools, mod or vanilla, and never a Satchel (no nesting).
 */
public final class SatchelFilter {
    public static final TagKey<Item> MATERIALS = TagKey.create(Registries.ITEM, CosmicBreach.id("satchel_materials"));
    public static final TagKey<Item> BASICS = TagKey.create(Registries.ITEM, CosmicBreach.id("satchel_basics"));

    private SatchelFilter() {
    }

    public static boolean isMaterial(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && !isSatchel(stack) && (stack.is(MATERIALS) || stack.is(BASICS));
    }

    public static boolean isGear(ItemStack stack) {
        if (stack.isEmpty() || isSatchel(stack)) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof ArmorItem || item instanceof SwordItem || item instanceof DiggerItem || item instanceof ProjectileWeaponItem
                || item instanceof TridentItem || item instanceof MaceItem || item instanceof CombatWeaponItem || stack.has(DataComponents.TOOL);
    }

    public static boolean isSatchel(ItemStack stack) {
        return stack.getItem() instanceof SatchelItem;
    }

    public static ResourceLocation id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
