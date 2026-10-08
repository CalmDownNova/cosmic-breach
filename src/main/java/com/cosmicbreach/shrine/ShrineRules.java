package com.cosmicbreach.shrine;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** The shrines' rules (1.1 design section 9), pure. */
public final class ShrineRules {
    private ShrineRules() {
    }

    /**
     * True while a save at {@code savedPos} in {@code savedDimension} is still where the player respawns (sleeping in a
     * bed, another shrine or a spawn point command moves the respawn and so ends it).
     */
    public static boolean saveActive(@Nullable BlockPos savedPos, String savedDimension, @Nullable BlockPos respawnPos, String respawnDimension) {
        return savedPos != null && savedPos.equals(respawnPos) && savedDimension.equals(respawnDimension);
    }

    /** True if a death keeps everything: in the mod's levels, saved to a shrine, not already kept by keepInventory, not a spectator. */
    public static boolean keeps(boolean inAetheria, boolean saveActive, boolean keepInventoryRule, boolean spectator) {
        return inAetheria && saveActive && !keepInventoryRule && !spectator;
    }
}
