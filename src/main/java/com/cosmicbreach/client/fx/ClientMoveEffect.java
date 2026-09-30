package com.cosmicbreach.client.fx;

import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.net.MoveEffectPayload;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * What a move effect ({@link MoveEffect}, by id in the move's JSON) looks like: the client half of the
 * server's {@code ServerMoveEffect} under the same id (an effect may be only visual, like a slam's cracks).
 * The move's moments come the way the other combat effects do: from the local player's own machine the
 * moment it predicts them, and from the server's payloads for everyone else ({@link CombatEffects}); the
 * moments only the server knows arrive for everyone as {@link #serverMoment}. Register with
 * {@link ClientMoveEffects#register}.
 */
public interface ClientMoveEffect {
    /** The move started on {@code player}. */
    default void started(Player player, MoveDef def, MoveEffect effect) {
    }

    /** The move's first active tick on {@code player}. */
    default void active(Player player, MoveDef def, MoveEffect effect) {
    }

    /**
     * A plunge carrying the effect landed after falling {@code fallBlocks}. Returns true if it drew the
     * landing itself, so the engine's own landing burst is left out.
     */
    default boolean landed(Player player, MoveDef def, MoveEffect effect, double fallBlocks) {
        return false;
    }

    /** A moment the server decided; {@code player} is null if its player isn't loaded here. */
    default void serverMoment(@Nullable Player player, MoveEffectPayload moment) {
    }
}
