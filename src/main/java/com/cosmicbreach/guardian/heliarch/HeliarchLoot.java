package com.cosmicbreach.guardian.heliarch;

import com.cosmicbreach.CosmicBreach;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.resources.ResourceLocation;

/**
 * What one participant's Reliquary holds (GDD 7.3's loot table), rolled when they open it. A first kill (the player
 * has no "Breach Sealed" yet) gives Last Light, the Heliarch's Crown, the Solar Heart, 8 to 12 Eclipsium Ingots,
 * 40,000 Attunement XP, the advancement and its two stat points. A repeat kill gives the Solar Heart, 4 to 8 ingots,
 * 8,000 XP, and by chance Umbra Cantor (15%), the Crown (10%) and a random charm (25%). Last Light never drops twice.
 * Pure: {@code random} gives numbers in [0, 1).
 */
public final class HeliarchLoot {
    public static final ResourceLocation LAST_LIGHT = CosmicBreach.id("last_light");
    public static final ResourceLocation UMBRA_CANTOR = CosmicBreach.id("umbra_cantor");
    public static final ResourceLocation CROWN = CosmicBreach.id("heliarchs_crown");
    public static final ResourceLocation SOLAR_HEART = CosmicBreach.id("solar_heart");
    public static final ResourceLocation ECLIPSIUM = CosmicBreach.id("eclipsium_ingot");
    /** The charms a repeat kill may give (GDD 5.2's charm slot), whichever of them exist in the game. */
    public static final List<ResourceLocation> CHARMS = List.of(CosmicBreach.id("halo_of_nine"), CosmicBreach.id("event_horizon_lens"),
            CosmicBreach.id("hourglass_of_vesper"), CosmicBreach.id("sunshard_compass"));

    public static final int FIRST_XP = 40_000;
    public static final int REPEAT_XP = 8_000;
    public static final int STAT_POINTS = 2;
    public static final double CANTOR_CHANCE = 0.15;
    public static final double CROWN_CHANCE = 0.10;
    public static final double CHARM_CHANCE = 0.25;

    /** An item and how many. */
    public record Drop(ResourceLocation item, int count) {
    }

    /** One participant's share. */
    public record Reward(List<Drop> drops, int xp, int statPoints, boolean firstKill) {
        public int count(ResourceLocation item) {
            int n = 0;
            for (Drop d : drops) {
                if (d.item().equals(item)) {
                    n += d.count();
                }
            }
            return n;
        }
    }

    private HeliarchLoot() {
    }

    /**
     * One player's Reliquary: {@code charms} are the charm ids that exist (a charm is skipped if none do),
     * {@code random} the numbers to roll with.
     */
    public static Reward roll(boolean firstKill, List<ResourceLocation> charms, DoubleSupplier random) {
        List<Drop> drops = new ArrayList<>();
        if (firstKill) {
            drops.add(new Drop(LAST_LIGHT, 1));
            drops.add(new Drop(CROWN, 1));
            drops.add(new Drop(SOLAR_HEART, 1));
            drops.add(new Drop(ECLIPSIUM, between(8, 12, random)));
            return new Reward(List.copyOf(drops), FIRST_XP, STAT_POINTS, true);
        }
        if (random.getAsDouble() < CANTOR_CHANCE) {
            drops.add(new Drop(UMBRA_CANTOR, 1));
        }
        if (random.getAsDouble() < CROWN_CHANCE) {
            drops.add(new Drop(CROWN, 1));
        }
        drops.add(new Drop(SOLAR_HEART, 1));
        drops.add(new Drop(ECLIPSIUM, between(4, 8, random)));
        if (random.getAsDouble() < CHARM_CHANCE && !charms.isEmpty()) {
            int i = Math.min(charms.size() - 1, (int) Math.floor(random.getAsDouble() * charms.size()));
            drops.add(new Drop(charms.get(i), 1));
        }
        return new Reward(List.copyOf(drops), REPEAT_XP, 0, false);
    }

    private static int between(int lo, int hi, DoubleSupplier random) {
        int n = lo + (int) Math.floor(Math.max(0.0, Math.min(0.999999, random.getAsDouble())) * (hi - lo + 1));
        return Math.min(hi, n);
    }
}
