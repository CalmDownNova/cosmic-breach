package com.cosmicbreach.accessory;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * How the traps' tells see a player (GDD 6.4's careful-movement rule, and the Sunshard Compass of 5.2): a Compass
 * wearer sees every tell within 8 blocks at any speed; anyone else only by moving carefully, within the trap's own
 * radius. The tells ask here on both sides.
 */
public final class TrapSight {
    private TrapSight() {
    }

    /** True if {@code player} sees tells whatever their speed (they wear the Sunshard Compass). */
    public static boolean anySpeed(@Nullable Player player) {
        return Worn.wears(player, Accessory.SUNSHARD_COMPASS);
    }

    /** How far a tell with its own {@code radius} shows to {@code player}. */
    public static double radius(@Nullable Player player, double radius) {
        return AccessoryRules.tellRadius(anySpeed(player), radius);
    }
}
