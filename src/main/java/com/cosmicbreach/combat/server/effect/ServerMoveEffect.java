package com.cosmicbreach.combat.server.effect;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.MoveInstance;
import com.cosmicbreach.combat.data.MoveEffect;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.net.MoveEffectPayload;
import com.cosmicbreach.net.ModNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What a move effect ({@link MoveEffect}, by id in the move's JSON) does on the server, hooked to the
 * move's life as the player's state machine reports it. Register one with {@link ServerMoveEffects#register};
 * every hook has a do-nothing default, so a handler overrides only the moments it needs. Its visuals are a
 * separate client handler under the same id; the server tells the clients about the moments only it
 * knows with {@link Use#send}.
 */
public interface ServerMoveEffect {
    /** The move started (its startup tick 0). */
    default void started(Use use) {
    }

    /** One of the move's active ticks, counted from 0. */
    default void activeTick(Use use, int activeTick) {
    }

    /** A plunge carrying this effect landed after falling {@code fallBlocks}. */
    default void landed(Use use, double fallBlocks) {
    }

    /** An input cut the move short, at tick {@code tick} of {@code stage}; {@link #ended} follows. */
    default void cancelled(Use use, MoveTraits.CancelBy by, MoveTraits.Stage stage, int tick) {
    }

    /** The move ended, run out or cut short (an input, a stagger, a weapon swap, death). */
    default void ended(Use use, boolean cancelled) {
    }

    /**
     * True if this effect deals the move's damage itself (the Gravity Well's Collapse): the engine then
     * skips the move's hitbox on its active ticks.
     */
    default boolean dealsItsOwnHits() {
        return false;
    }

    /** One use of a move with the effect: who, the move, the effect's parameters, the server's game time. */
    record Use(ServerPlayer player, PlayerCombat combat, MoveInstance move, MoveEffect effect, long now) {
        public ServerLevel level() {
            return player.serverLevel();
        }

        public double param(String name, double fallback) {
            return effect.param(name, fallback);
        }

        public int intParam(String name, int fallback) {
            return effect.intParam(name, fallback);
        }

        /** Tells the player and everyone tracking it about one of this effect's moments. */
        public void send(int stage, Vec3 at, float value, int ticks) {
            ModNetworking.sendToTrackersAndSelf(player, new MoveEffectPayload(player.getId(), effect.id(), stage, at, value, ticks));
        }
    }
}
