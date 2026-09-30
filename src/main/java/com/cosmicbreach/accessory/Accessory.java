package com.cosmicbreach.accessory;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The ten accessories (GDD 5.2) and the Curios slot each fits: two rings, one necklace, one charm (the charm holds a
 * relic). Six were registered as plain items by the features that drop them (the guardians, the Gyre Knight, the
 * Sanctum); they are {@link AccessoryItem}s now under the same ids, and the other four live in {@link AccessoryRegistry}.
 */
public enum Accessory {
    TWIN_COMET_BAND("twin_comet_band", Slot.RING),
    LEECHSTAR_SIGNET("leechstar_signet", Slot.RING),
    PERIHELION_LOOP("perihelion_loop", Slot.RING),
    GRAVITY_LOOP("gravity_loop", Slot.RING),
    HEART_OF_A_DYING_STAR("heart_of_a_dying_star", Slot.NECKLACE),
    CHOIR_PENDANT("choir_pendant", Slot.NECKLACE),
    HALO_OF_NINE("halo_of_nine", Slot.CHARM),
    EVENT_HORIZON_LENS("event_horizon_lens", Slot.CHARM),
    HOURGLASS_OF_VESPER("hourglass_of_vesper", Slot.CHARM),
    SUNSHARD_COMPASS("sunshard_compass", Slot.CHARM);

    /** A Curios slot type by its identifier ({@code data/curios/curios/slots/<id>.json}); the tags are {@code curios:<id>}. */
    public enum Slot {
        RING("ring", 2), NECKLACE("necklace", 1), CHARM("charm", 1);

        private final String id;
        private final int count;

        Slot(String id, int count) {
            this.id = id;
            this.count = count;
        }

        public String id() {
            return id;
        }

        /** How many a player has (GDD 5.2). */
        public int count() {
            return count;
        }
    }

    private final String path;
    private final Slot slot;
    private Item item;

    Accessory(String path, Slot slot) {
        this.path = path;
        this.slot = slot;
    }

    public String path() {
        return path;
    }

    public ResourceLocation id() {
        return CosmicBreach.id(path);
    }

    public Slot slot() {
        return slot;
    }

    /** The registered item (looked up once the registries are filled; air before that). */
    public Item item() {
        Item found = item;
        if (found == null || found == Items.AIR) {
            found = BuiltInRegistries.ITEM.get(id());
            item = found;
        }
        return found;
    }

    /** The accessory an item is, or null. */
    public static Accessory of(Item item) {
        return item instanceof AccessoryItem accessory ? accessory.kind() : null;
    }
}
