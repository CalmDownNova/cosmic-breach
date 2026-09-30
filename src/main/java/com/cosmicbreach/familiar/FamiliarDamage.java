package com.cosmicbreach.familiar;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A familiar's hit ({@code cosmicbreach:familiar}): dealt by the familiar, caused by its owner, so the creature it hits
 * turns on the owner, and kill credit, Attunement XP and a boss's threat go to the owner.
 */
public final class FamiliarDamage {
    private FamiliarDamage() {
    }

    public static DamageSource of(Entity familiar, Player owner) {
        return new DamageSource(familiar.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(FamiliarRegistry.DAMAGE), familiar, owner);
    }
}
