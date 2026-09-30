package com.cosmicbreach.gear.forge;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.item.GearTier;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The Astral Forge's tier rules (GDD 3.5), pure: which guardian relic raises the Forge to each tier, what a
 * reforge to each tier costs, and whether a reforge may happen. Items are named by id so the rules test
 * without a game.
 */
public final class ForgeTiers {
    public static final ResourceLocation PRISM_HEART = CosmicBreach.id("prism_heart");
    public static final ResourceLocation LEVIATHAN_PEARL = CosmicBreach.id("leviathan_pearl");
    public static final ResourceLocation SOLAR_HEART = CosmicBreach.id("solar_heart");
    public static final ResourceLocation NEBULITE_INGOT = CosmicBreach.id("nebulite_ingot");
    public static final ResourceLocation ECLIPSIUM_INGOT = CosmicBreach.id("eclipsium_ingot");

    /** What a reforge takes besides the piece itself: {@code count} of {@code item}. */
    public record Cost(ResourceLocation item, int count) {}

    /** Why a reforge can or can't happen. */
    public enum Check {
        OK,
        /** Not a weapon or set piece. */
        NOT_REFORGEABLE,
        /** Already tier IV. */
        MAX_TIER,
        /** The Forge's own tier is below the tier the piece would reach. */
        FORGE_TOO_LOW,
        /** The player doesn't carry the next tier's metal. */
        MISSING_METAL
    }

    private ForgeTiers() {
    }

    /** The relic that raises a Forge to {@code toTier}: II Prism Heart, III Leviathan Pearl, IV Solar Heart. */
    public static Optional<ResourceLocation> upgradeItem(int toTier) {
        return switch (toTier) {
            case 2 -> Optional.of(PRISM_HEART);
            case 3 -> Optional.of(LEVIATHAN_PEARL);
            case 4 -> Optional.of(SOLAR_HEART);
            default -> Optional.empty();
        };
    }

    /** The tier a Forge at {@code forgeTier} reaches when {@code item} is used on it, if that item is its next relic. */
    public static Optional<Integer> upgradeWith(int forgeTier, ResourceLocation item) {
        int next = forgeTier + 1;
        return upgradeItem(next).filter(item::equals).map(id -> next);
    }

    /** The tier whose relic {@code item} is, or 0. */
    public static int relicTier(ResourceLocation item) {
        for (int tier = 2; tier <= GearTier.MAX; tier++) {
            if (upgradeItem(tier).filter(item::equals).isPresent()) {
                return tier;
            }
        }
        return 0;
    }

    /** The next tier's metal, for a reforge up to {@code toTier}: II 4 Nebulite Ingots, III 4 Eclipsium Ingots, IV 1 Solar Heart. */
    public static Optional<Cost> reforgeCost(int toTier) {
        return switch (toTier) {
            case 2 -> Optional.of(new Cost(NEBULITE_INGOT, 4));
            case 3 -> Optional.of(new Cost(ECLIPSIUM_INGOT, 4));
            case 4 -> Optional.of(new Cost(SOLAR_HEART, 1));
            default -> Optional.empty();
        };
    }

    /**
     * Whether a piece at {@code pieceTier} (0 if it isn't reforgeable) can be reforged one tier at a Forge of
     * {@code forgeTier} by a player carrying {@code metalCarried} of the next tier's metal.
     */
    public static Check check(int pieceTier, int forgeTier, int metalCarried) {
        if (pieceTier <= 0) {
            return Check.NOT_REFORGEABLE;
        }
        if (pieceTier >= GearTier.MAX) {
            return Check.MAX_TIER;
        }
        int target = pieceTier + 1;
        if (forgeTier < target) {
            return Check.FORGE_TOO_LOW;
        }
        int needed = reforgeCost(target).map(Cost::count).orElse(Integer.MAX_VALUE);
        return metalCarried >= needed ? Check.OK : Check.MISSING_METAL;
    }

    /** Light the Forge block gives off at a tier: brighter as it grows. */
    public static int light(int forgeTier) {
        return 4 + 2 * GearTier.clamp(forgeTier);
    }
}
