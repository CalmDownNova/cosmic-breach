package com.cosmicbreach.gear.datagen;

import com.cosmicbreach.gear.forge.ForgeCategory;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.mount.MountGearItem;
import com.cosmicbreach.mount.ResonanceChimeItem;
import com.cosmicbreach.mount.StableCrystalItem;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;

/**
 * The Forge tab a generated recipe goes to by what it makes (1.1 design section 10), as a rule over the result's class so
 * it can be tested without a game: mount gear first (vanilla's barding is an {@link ArmorItem} too and must not land in
 * Armor), then armor, a combat weapon or a digging tool; anything else goes to Other. Hand-written recipes name their
 * tab themselves.
 */
final class ForgeTabRule {
    private ForgeTabRule() {
    }

    static ForgeCategory of(Class<?> result) {
        if (AnimalArmorItem.class.isAssignableFrom(result) || MountGearItem.class.isAssignableFrom(result)
                || ResonanceChimeItem.class.isAssignableFrom(result) || StableCrystalItem.class.isAssignableFrom(result)) {
            return ForgeCategory.MOUNT_GEAR;
        }
        if (ArmorItem.class.isAssignableFrom(result)) {
            return ForgeCategory.ARMOR;
        }
        if (CombatWeaponItem.class.isAssignableFrom(result)) {
            return ForgeCategory.WEAPONS;
        }
        if (DiggerItem.class.isAssignableFrom(result)) {
            return ForgeCategory.TOOLS;
        }
        return ForgeCategory.OTHER;
    }
}
