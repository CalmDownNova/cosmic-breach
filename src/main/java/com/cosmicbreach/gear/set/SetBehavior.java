package com.cosmicbreach.gear.set;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * An armor set's 2-piece and 4-piece bonuses as code: hooks {@link SetEvents} calls for every set a player
 * wears at least one piece of, with how many pieces are worn (so one class holds both thresholds, see
 * {@link ArmorSet#TWO_PIECES} and {@link ArmorSet#FULL_SET}). Server side except {@link #poiseBonus},
 * which the engine may ask on either side. Every hook defaults to doing nothing.
 */
public interface SetBehavior {
    SetBehavior NONE = new SetBehavior() {};

    /** Once a server tick while at least one piece is worn. */
    default void tick(ServerPlayer player, int pieces, long now) {
    }

    /**
     * The player landed after falling {@code fallDistance} blocks ({@code LivingFallEvent}). {@code cancelled}
     * is true when something else already took the fall damage away (a plunge landing), so all of it was avoided.
     */
    default void onLanding(ServerPlayer player, int pieces, float fallDistance, float damageMultiplier, boolean cancelled) {
    }

    /** Damage the player is about to take, before armor. Returns the new amount. */
    default float onHurt(ServerPlayer player, int pieces, DamageSource source, float amount) {
        return amount;
    }

    /** A hit the player is about to deal to {@code target}, before its armor. Returns the new amount. */
    default float onHitDealt(ServerPlayer player, int pieces, LivingEntity target, DamageSource source, float amount) {
        return amount;
    }

    /** Poise added to the player's own right now. */
    default double poiseBonus(Player player, int pieces) {
        return 0.0;
    }

    /** One of the player's combat events (a perfect dodge, an ability cast, a move starting). */
    default void onCombatEvent(ServerPlayer player, int pieces, PlayerCombat combat, CombatEvent event) {
    }
}
