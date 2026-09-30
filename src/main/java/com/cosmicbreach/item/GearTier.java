package com.cosmicbreach.item;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.core.CombatMath;
import com.cosmicbreach.combat.data.WeaponDef;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A weapon's or armor piece's tier (GDD 3.4 and 3.5): the data component {@code cosmicbreach:gear_tier},
 * 1 to 4. A stack without it is at its unlock tier (a weapon's {@link WeaponDef#tier()}, a set piece's
 * {@link TieredGear#unlockTier()}); each reforge at the Astral Forge sets it one higher. Every tier above
 * the unlock tier multiplies a weapon's base damage, and an armor piece's armor and toughness, by
 * {@link CombatMath#TIER_STEP}, and gives the piece that tier's trim colour.
 */
public final class GearTier {
    public static final int MIN = 1;
    public static final int MAX = 4;

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CosmicBreach.MOD_ID);

    /** Saved with the stack and synced to clients (tooltips, trim colour). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIER =
            COMPONENTS.registerComponentType("gear_tier", builder -> builder
                    .persistent(Codec.intRange(MIN, MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Trim and glow colours by tier (0xRRGGBB): Starsteel gold, Nebulite cyan, Eclipsium violet, Solar white-gold. */
    private static final int[] COLORS = {0xFFD27A, 0x5FE3FF, 0xB98CFF, 0xFFF1C9};
    private static final String[] ROMAN = {"I", "II", "III", "IV"};

    private GearTier() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    /** The stack's tier: its component, else {@code unlockTier}. */
    public static int of(ItemStack stack, int unlockTier) {
        Integer tier = stack.isEmpty() ? null : stack.get(TIER.get());
        return clamp(tier == null ? unlockTier : Math.max(tier, unlockTier));
    }

    /** Reforges above the unlock tier: the exponent of the x1.15. */
    public static int stepsAbove(ItemStack stack, int unlockTier) {
        return steps(of(stack, unlockTier), unlockTier);
    }

    /** Tier {@code tier} of gear that unlocks at {@code unlockTier}: this many steps above it (never negative). */
    public static int steps(int tier, int unlockTier) {
        return Math.max(0, clamp(tier) - clamp(unlockTier));
    }

    /** x1.15 per step. */
    public static double multiplier(int steps) {
        return CombatMath.tierMultiplier(steps);
    }

    public static void set(ItemStack stack, int tier) {
        stack.set(TIER.get(), clamp(tier));
    }

    /**
     * The unlock tier of a reforgeable stack, or 0 if it can't be reforged: a combat weapon's from its data
     * (on the side asking), a set piece's from its set.
     */
    public static int unlockTier(ItemStack stack, boolean clientSide) {
        if (stack.getItem() instanceof TieredGear gear) {
            return clamp(gear.unlockTier());
        }
        WeaponDef weapon = CombatWeaponItem.weaponOf(stack, clientSide);
        return weapon == null ? 0 : clamp(weapon.tier());
    }

    /** 0xRRGGBB of a tier's trim and glow. */
    public static int color(int tier) {
        return COLORS[clamp(tier) - 1];
    }

    public static String roman(int tier) {
        return ROMAN[clamp(tier) - 1];
    }

    public static int clamp(int tier) {
        return Math.max(MIN, Math.min(MAX, tier));
    }
}
