package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.core.CombatEvent;
import com.cosmicbreach.combat.core.CombatStateMachine;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * What gear adds to the engine's view of a player (GDD 3.4's "set bonuses" and "gear" terms), registered by the
 * gear code so the engine never depends on it: extra poise ({@link PoiseTracker}), a look at every combat event a
 * player's machine raises on the server (a perfect dodge, an ability cast) for set bonuses that answer them, and
 * the machine's gear numbers ({@link #applyTo}): extra dash charges, Haste, the Resonance gain and ability cost
 * multipliers, the out-of-combat drift's target and free air dashes; plus the dash's distance, which the client
 * moves. The machine's numbers are asked on both sides every tick, so a hook must answer from state both sides
 * have (worn armor, synced attachments, the level). Each part defaults to nothing.
 *
 * <p>For the accessories (GDD 5.2): max Resonance, parry window ticks and free dashes each time in the air for the
 * machine, level air dashes for the client's body, and every parry the server grants ({@link Hook#onParried}).
 */
public final class CombatHooks {
    /** One piece of gear logic's say. */
    public interface Hook {
        /** Poise added to the player's own (Player Poise = 10 + 0.5 x Resilience + set bonuses). */
        default double poiseBonus(Player player) {
            return 0.0;
        }

        /** A combat event the player's server machine raised this tick. */
        default void onCombatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
        }

        /** Dash charges added (the Driftweave's +1). Either side. */
        default int dashChargeBonus(Player player) {
            return 0;
        }

        /** Multiplies a dash's distance (the Driftweave's x1.2). Read by the client, which moves its player. */
        default double dashDistanceScale(Player player) {
            return 1.0;
        }

        /** Haste added (the Hymn of Alignment's +30). Either side. */
        default double hasteBonus(Player player) {
            return 0.0;
        }

        /** Multiplies the Resonance the player earns (the Hymn's x2). Either side. */
        default double resonanceGain(Player player) {
            return 1.0;
        }

        /** Multiplies what the player's abilities cost (the Choir Regalia's x0.9). Either side. */
        default double abilityCostScale(Player player) {
            return 1.0;
        }

        /** The share of max Resonance the out-of-combat drift settles at, or NaN for no say (the highest wins). */
        default double resonanceDriftTarget(Player player) {
            return Double.NaN;
        }

        /** True if the player's air dashes cost no charge right now (the Driftweave's Drift). Either side. */
        default boolean freeAirDashes(Player player) {
            return false;
        }

        /** Max Resonance added (the Choir Pendant's +20). Either side. */
        default int maxResonanceBonus(Player player) {
            return 0;
        }

        /** Ticks added to the parry window (the Event Horizon Lens's +2). Either side. */
        default int parryWindowBonus(Player player) {
            return 0;
        }

        /** Dashes each time in the air that cost no charge (the Twin Comet Band's +1). Either side. */
        default int airDashBonus(Player player) {
            return 0;
        }

        /** True if the player's air dashes fly level, so they chain without sinking (the Twin Comet Band). Read by the client. */
        default boolean airDashesHoldHeight(Player player) {
            return false;
        }

        /**
         * A parry the server just granted: it stopped {@code amount} damage from {@code attacker} (null when no living
         * thing threw it), {@code early} if it came in the parry's first two ticks (the perfect window, latency grace
         * included). Server, inside the damage event, after the engine's own parry reward.
         */
        default void onParried(ServerPlayer player, PlayerCombat combat, @Nullable LivingEntity attacker, float amount,
                               boolean early) {
        }
    }

    private static final List<Hook> HOOKS = new CopyOnWriteArrayList<>();

    private CombatHooks() {
    }

    public static void register(Hook hook) {
        HOOKS.add(hook);
    }

    /** Every hook's poise bonus for {@code player}, added up. */
    public static double poiseBonus(Player player) {
        double bonus = 0.0;
        for (Hook hook : HOOKS) {
            bonus += hook.poiseBonus(player);
        }
        return bonus;
    }

    /** Every hook's extra dash charges for {@code player}, added up. */
    public static int dashChargeBonus(Player player) {
        int bonus = 0;
        for (Hook hook : HOOKS) {
            bonus += hook.dashChargeBonus(player);
        }
        return bonus;
    }

    /** Every hook's dash distance multiplier for {@code player}, multiplied together. */
    public static double dashDistanceScale(Player player) {
        double scale = 1.0;
        for (Hook hook : HOOKS) {
            scale *= hook.dashDistanceScale(player);
        }
        return scale;
    }

    /** Every hook's Haste for {@code player}, added up. */
    public static double hasteBonus(Player player) {
        double bonus = 0.0;
        for (Hook hook : HOOKS) {
            bonus += hook.hasteBonus(player);
        }
        return bonus;
    }

    /** Every hook's Resonance gain multiplier for {@code player}, multiplied together. */
    public static double resonanceGain(Player player) {
        double gain = 1.0;
        for (Hook hook : HOOKS) {
            gain *= hook.resonanceGain(player);
        }
        return gain;
    }

    /** Every hook's ability cost multiplier for {@code player}, multiplied together. */
    public static double abilityCostScale(Player player) {
        double scale = 1.0;
        for (Hook hook : HOOKS) {
            scale *= hook.abilityCostScale(player);
        }
        return scale;
    }

    /** The highest drift target any hook gives {@code player}, or NaN if none has a say. */
    public static double resonanceDriftTarget(Player player) {
        double target = Double.NaN;
        for (Hook hook : HOOKS) {
            double t = hook.resonanceDriftTarget(player);
            if (!Double.isNaN(t) && (Double.isNaN(target) || t > target)) {
                target = t;
            }
        }
        return target;
    }

    /** True if any hook frees {@code player}'s air dashes. */
    public static boolean freeAirDashes(Player player) {
        for (Hook hook : HOOKS) {
            if (hook.freeAirDashes(player)) {
                return true;
            }
        }
        return false;
    }

    /** Every hook's max Resonance bonus for {@code player}, added up. */
    public static int maxResonanceBonus(Player player) {
        int bonus = 0;
        for (Hook hook : HOOKS) {
            bonus += hook.maxResonanceBonus(player);
        }
        return bonus;
    }

    /** Every hook's parry window ticks for {@code player}, added up. */
    public static int parryWindowBonus(Player player) {
        int bonus = 0;
        for (Hook hook : HOOKS) {
            bonus += hook.parryWindowBonus(player);
        }
        return bonus;
    }

    /** Every hook's free air dashes for {@code player}, added up. */
    public static int airDashBonus(Player player) {
        int bonus = 0;
        for (Hook hook : HOOKS) {
            bonus += hook.airDashBonus(player);
        }
        return bonus;
    }

    /** True if any hook makes {@code player}'s air dashes fly level. */
    public static boolean airDashesHoldHeight(Player player) {
        for (Hook hook : HOOKS) {
            if (hook.airDashesHoldHeight(player)) {
                return true;
            }
        }
        return false;
    }

    /** Tells every hook about a parry the server granted {@code player} (see {@link Hook#onParried}). */
    public static void parried(ServerPlayer player, PlayerCombat combat, @Nullable LivingEntity attacker, float amount,
                               boolean early) {
        for (Hook hook : HOOKS) {
            hook.onParried(player, combat, attacker, amount, early);
        }
    }

    /**
     * Sets the machine's gear numbers for {@code player} from every hook, with cosmic weather's multipliers
     * ({@code weatherGain}, {@code weatherCost}) folded in. Called by {@link PlayerCombat#tick} on both sides.
     */
    public static void applyTo(CombatStateMachine machine, Player player, double weatherGain, double weatherCost) {
        machine.setGearDashCharges(dashChargeBonus(player));
        machine.setGearHaste(hasteBonus(player));
        machine.setResonanceGain(weatherGain * resonanceGain(player));
        machine.setAbilityCostScale(weatherCost * abilityCostScale(player));
        machine.setDriftTarget(resonanceDriftTarget(player));
        machine.setFreeAirDashes(freeAirDashes(player));
        machine.setGearResonance(maxResonanceBonus(player));
        machine.setGearParryWindow(parryWindowBonus(player));
        machine.setGearAirDashes(airDashBonus(player));
    }

    /** Test only: every hook now (the mod's own are registered in the test run too), to put back afterwards. */
    static List<Hook> snapshotForTests() {
        return List.copyOf(HOOKS);
    }

    /** Test only: exactly these hooks from now on. */
    static void restoreForTests(List<Hook> hooks) {
        HOOKS.clear();
        HOOKS.addAll(hooks);
    }

    /** Tells every hook about one of {@code player}'s combat events. */
    public static void combatEvent(ServerPlayer player, PlayerCombat combat, CombatEvent event) {
        for (Hook hook : HOOKS) {
            hook.onCombatEvent(player, combat, event);
        }
    }
}
