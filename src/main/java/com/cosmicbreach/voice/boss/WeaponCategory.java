package com.cosmicbreach.voice.boss;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.item.GearTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

/**
 * What hit a boss, for {@code weapon:<category>} lines (the voice script's list): one of this mod's combat weapons by its
 * gear tier (I to IV, reforged ones at their new tier); a vanilla sword, axe or mace; a vanilla bow's or crossbow's
 * arrow; a trident, thrown or held; a weapon from another mod; an empty hand. Anything else (a stick, a set ability, a
 * familiar) has no category and says nothing.
 */
public enum WeaponCategory {
    FORGE_TIER_1("forge_tier_1"),
    FORGE_TIER_2("forge_tier_2"),
    FORGE_TIER_3("forge_tier_3"),
    FORGE_TIER_4("forge_tier_4"),
    VANILLA_MELEE("vanilla_melee"),
    BOW("bow"),
    CROSSBOW("crossbow"),
    TRIDENT("trident"),
    MOD_WEAPON("mod_weapon"),
    BARE_HANDS("bare_hands");

    /** How the hit arrived: from the hand, an arrow, a thrown trident, or anything else. */
    public enum Delivery { MELEE, ARROW, TRIDENT, OTHER }

    /** What the weapon item is, for vanilla's: a sword, axe or mace; a trident; a bow; a crossbow; something else. */
    public enum Kind { BLADE, TRIDENT, BOW, CROSSBOW, OTHER }

    private final String id;

    WeaponCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static @Nullable WeaponCategory byId(String id) {
        for (WeaponCategory c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public static WeaponCategory forgeTier(int tier) {
        return switch (Math.max(1, Math.min(4, tier))) {
            case 1 -> FORGE_TIER_1;
            case 2 -> FORGE_TIER_2;
            case 3 -> FORGE_TIER_3;
            default -> FORGE_TIER_4;
        };
    }

    /**
     * The category of a hit, pure: {@code forgeTier} is the gear tier of this mod's combat weapon that dealt it (0 for
     * none); {@code namespace} is the weapon item's (empty for an empty hand); {@code kind} what that item is.
     */
    public static @Nullable WeaponCategory of(Delivery delivery, int forgeTier, String namespace, Kind kind) {
        if (forgeTier > 0) {
            return forgeTier(forgeTier);
        }
        if (delivery == Delivery.TRIDENT) {
            return TRIDENT;
        }
        if (namespace.isEmpty()) {
            return delivery == Delivery.MELEE ? BARE_HANDS : null;
        }
        if (!namespace.equals("minecraft")) {
            return namespace.equals(CosmicBreach.MOD_ID) ? null : MOD_WEAPON;
        }
        return switch (kind) {
            case BLADE -> delivery == Delivery.MELEE ? VANILLA_MELEE : null;
            case TRIDENT -> TRIDENT;
            case BOW -> delivery == Delivery.ARROW ? BOW : null;
            case CROSSBOW -> delivery == Delivery.ARROW ? CROSSBOW : null;
            case OTHER -> null;
        };
    }

    /** The category of a hit on a boss (server), or null. */
    public static @Nullable WeaponCategory classify(DamageSource source) {
        Entity direct = source.getDirectEntity();
        ItemStack weapon = source.getWeaponItem();
        ItemStack stack = weapon == null ? ItemStack.EMPTY : weapon;
        int tier = 0;
        if (stack.getItem() instanceof CombatWeaponItem item) {
            WeaponDef def = item.weapon(false);
            tier = GearTier.of(stack, def == null ? 1 : def.tier());
        }
        Delivery delivery = direct instanceof Player ? Delivery.MELEE : direct instanceof ThrownTrident ? Delivery.TRIDENT
                : direct instanceof AbstractArrow ? Delivery.ARROW : Delivery.OTHER;
        String namespace = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return of(delivery, tier, namespace, kind(stack.getItem()));
    }

    /** The item's id for {@code item:<id>} conditions, or empty. */
    public static String itemId(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        return weapon == null || weapon.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
    }

    private static Kind kind(Item item) {
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem) {
            return Kind.BLADE;
        }
        if (item instanceof TridentItem) {
            return Kind.TRIDENT;
        }
        if (item instanceof CrossbowItem) {
            return Kind.CROSSBOW;
        }
        return item instanceof BowItem ? Kind.BOW : Kind.OTHER;
    }
}
