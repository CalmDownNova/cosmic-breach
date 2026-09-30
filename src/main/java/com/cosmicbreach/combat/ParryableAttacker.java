package com.cosmicbreach.combat;

/**
 * An attacker that says which of its attacks can be parried (the gold telegraph, GDD section 4.1).
 * Without this interface only plain melee from a living, non-player mob is parryable.
 */
public interface ParryableAttacker {
    /** True while an attack that may be parried is in its active frames. */
    boolean isParryableAttackActive();

    /** The Impact of that attack. A parry deals twice this to the attacker's poise. */
    double attackImpact();

    /** {@code player} just parried the active attack (after its poise damage was dealt). Server side. */
    default void onParried(net.minecraft.world.entity.player.Player player) {
    }
}
