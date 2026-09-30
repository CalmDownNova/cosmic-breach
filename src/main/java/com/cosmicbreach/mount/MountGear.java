package com.cosmicbreach.mount;

import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The celestial mounts' gear (GDD 8.1): which slot of the horse inventory it goes in, which mount wears it, and its
 * armor. What each piece does is read where it matters ({@link LumenStag}, {@link DriftManta}).
 */
public enum MountGear {
    /** The Stag's saddle (Forge I): rideable. */
    ASTRAL_SADDLE(Slot.SADDLE, 0, Kind.STAG),
    /** Armor 5 (Forge I). */
    STARSTEEL_BARDING(Slot.ARMOR, 5, Kind.STAG),
    /** Armor 8 (Forge II), for either mount. */
    NEBULITE_BARDING(Slot.ARMOR, 8, Kind.STAG, Kind.MANTA),
    /** A fourth jump (Forge II). */
    COMET_BRIDLE(Slot.TACK, 0, Kind.STAG),
    /** Glide sink -30% (Forge II). */
    HALO_REINS(Slot.TACK, 0, Kind.STAG),
    /** The Manta's saddle: rideable. */
    DRIFT_HARNESS(Slot.SADDLE, 0, Kind.MANTA),
    /** Phase Blink cooldown -30%. */
    NEBULA_REINS(Slot.TACK, 0, Kind.MANTA),
    /** Flight speed +15%. */
    GALE_FINS(Slot.TACK, 0, Kind.MANTA);

    public enum Slot { SADDLE, ARMOR, TACK }

    public enum Kind {
        STAG, MANTA;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Slot slot;
    private final int armor;
    private final Kind[] fits;

    MountGear(Slot slot, int armor, Kind... fits) {
        this.slot = slot;
        this.armor = armor;
        this.fits = fits;
    }

    public Slot slot() {
        return slot;
    }

    public int armor() {
        return armor;
    }

    public boolean fits(Kind kind) {
        for (Kind k : fits) {
            if (k == kind) {
                return true;
            }
        }
        return false;
    }

    /** The item id, {@code cosmicbreach:<id>}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The gear a stack is, or null. */
    public static @Nullable MountGear of(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof MountGearItem item ? item.gear() : null;
    }
}
