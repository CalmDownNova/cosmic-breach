package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.data.MoveTraits;
import net.minecraft.resources.ResourceLocation;

/** Things the state machine reports from a tick. The server turns them into hits; the client into feel. */
public sealed interface CombatEvent {

    enum Action { ATTACK, ABILITY_COOLDOWN, ABILITY_RESONANCE, DASH, STAGGERED }

    record MoveStarted(MoveInstance move) implements CombatEvent {
    }

    /** Fired once per active tick; {@code activeTick} counts from 0. */
    record ActiveTick(MoveInstance move, int activeTick) implements CombatEvent {
    }

    record MoveEnded(MoveInstance move, boolean cancelled) implements CombatEvent {
    }

    /**
     * An input cut the move short ({@code by}, at tick {@code tick} of {@code stage}); its
     * {@link MoveEnded} (cancelled) follows. Effects hook this: the Gravity Well's Collapse fires early
     * when a dash frees the player.
     */
    record MoveCancelled(MoveInstance move, MoveTraits.CancelBy by, MoveTraits.Stage stage, int tick) implements CombatEvent {
    }

    /** Impact broke the player's poise: whatever it was doing stopped, and it can't act for {@code ticks}. */
    record Staggered(int ticks) implements CombatEvent {
    }

    record ChargeStarted(ResourceLocation chargedMove) implements CombatEvent {
    }

    /** The hold has reached the charged move's minimum: releasing now fires it. */
    record ChargeReady() implements CombatEvent {
    }

    record ChargeFull() implements CombatEvent {
    }

    record ChargeCancelled() implements CombatEvent {
    }

    record PlungeLanded(MoveInstance move, double fallBlocks) implements CombatEvent {
    }

    /**
     * The ability button was held at the first active tick: lift the user this many blocks, then hold them up
     * there for up to {@code hoverTicks} while the button stays held (they rise and hang with the launched foes).
     */
    record Rise(double height, int hoverTicks) implements CombatEvent {
    }

    record DashStarted() implements CombatEvent {
    }

    record DashEnded() implements CombatEvent {
    }

    record ParryStarted() implements CombatEvent {
    }

    record ParryWhiffed() implements CombatEvent {
    }

    record ParrySucceeded() implements CombatEvent {
    }

    record PerfectDodge() implements CombatEvent {
    }

    record ResonanceFull() implements CombatEvent {
    }

    record Denied(Action action) implements CombatEvent {
    }
}
