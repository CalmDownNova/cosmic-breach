package com.cosmicbreach.gear.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cosmicbreach.gear.forge.ForgeCategory;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.mount.MountGearItem;
import com.cosmicbreach.mount.ResonanceChimeItem;
import com.cosmicbreach.mount.StableCrystalItem;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

/**
 * Which Forge tab a generated recipe lands in by what it makes (1.1 design section 10). The data run files a recipe
 * whose result is a mount item under Mount Gear on its own, so content added later through the provider slots in
 * right with no signal if it didn't: vanilla barding is an armor item too, and a mount's saddle is neither armor nor
 * weapon.
 */
class ForgeTabRuleTest {
    @Test
    void mountGearGoesToMountGearEvenWhenItIsArmorToo() {
        assertEquals(ForgeCategory.MOUNT_GEAR, ForgeTabRule.of(MountGearItem.class));
        assertEquals(ForgeCategory.MOUNT_GEAR, ForgeTabRule.of(ResonanceChimeItem.class));
        assertEquals(ForgeCategory.MOUNT_GEAR, ForgeTabRule.of(StableCrystalItem.class));
        assertEquals(ForgeCategory.MOUNT_GEAR, ForgeTabRule.of(AnimalArmorItem.class), "barding extends ArmorItem: it must not land in Armor");
    }

    @Test
    void armorWeaponsAndToolsKeepTheirTabs() {
        assertEquals(ForgeCategory.ARMOR, ForgeTabRule.of(ArmorItem.class));
        assertEquals(ForgeCategory.WEAPONS, ForgeTabRule.of(CombatWeaponItem.class));
        assertEquals(ForgeCategory.TOOLS, ForgeTabRule.of(DiggerItem.class));
    }

    @Test
    void anythingElseIsOther() {
        assertEquals(ForgeCategory.OTHER, ForgeTabRule.of(Item.class));
    }
}
