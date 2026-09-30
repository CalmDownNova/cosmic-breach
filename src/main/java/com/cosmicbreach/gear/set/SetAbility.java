package com.cosmicbreach.gear.set;

import net.minecraft.server.level.ServerPlayer;

/**
 * An armor set's active ability, cast with its own key (Set Ability, Z by default) while enough pieces are
 * worn. It costs no Resonance; its cooldown is {@link #baseCooldown()} shortened by Haste like any other
 * (GDD 3.4: base x 100 / (100 + Haste)), and is kept in the player's {@link SetState}.
 */
public interface SetAbility {
    /** Cooldown in ticks at zero Haste. */
    int baseCooldown();

    /** Pieces of the set that must be worn to cast it. */
    default int piecesRequired() {
        return ArmorSet.FULL_SET;
    }

    /**
     * Casts it at game time {@code now}. Returns false if it can't go off (nothing to aim at): then nothing
     * happened and the cooldown doesn't start.
     */
    boolean cast(ServerPlayer player, long now);
}
