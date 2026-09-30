package com.cosmicbreach.accessory;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Whether a living thing wears an accessory, from its Curios slots. The slots are synced to the wearer and to everyone
 * who sees them, so this answers the same on both sides.
 */
public final class Worn {
    private Worn() {
    }

    public static boolean wears(@Nullable LivingEntity entity, Accessory accessory) {
        if (entity == null) {
            return false;
        }
        return CuriosApi.getCuriosInventory(entity).map(inventory -> inventory.isEquipped(accessory.item())).orElse(false);
    }
}
