package com.cosmicbreach.client.anim;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.item.CombatWeaponItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The animations the combat engine plays itself, for every weapon. A move's own animations come from
 * its data ({@code animation}, {@code charge.animation}, {@code land_animation}).
 */
public final class EngineAnimations {
    public static final ResourceLocation DASH_FORWARD = CosmicBreach.id("combat_dash_forward");
    public static final ResourceLocation DASH_BACK = CosmicBreach.id("combat_dash_back");
    /** A dash to the right; played mirrored for a dash to the left. */
    public static final ResourceLocation DASH_SIDE = CosmicBreach.id("combat_dash_side");
    public static final ResourceLocation PARRY = CosmicBreach.id("combat_parry");
    public static final ResourceLocation PARRY_SUCCESS = CosmicBreach.id("combat_parry_success");
    public static final ResourceLocation STAGGER = CosmicBreach.id("combat_stagger");

    private EngineAnimations() {
    }

    /** {@code animation} as the weapon in {@code player}'s main hand plays it (its own version, if it has one). */
    public static ResourceLocation forHeld(Player player, ResourceLocation animation) {
        WeaponDef weapon = CombatWeaponItem.weaponOf(player.getMainHandItem(), true);
        return weapon == null ? animation : weapon.animationFor(animation);
    }
}
