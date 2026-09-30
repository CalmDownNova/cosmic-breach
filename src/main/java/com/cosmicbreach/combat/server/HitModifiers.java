package com.cosmicbreach.combat.server;

import com.cosmicbreach.combat.core.MoveInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What else changes a hit a player deals, registered by the weapons and armor that need it (GDD 3.4's "Buffs"
 * term and crit gear): a bonus to the crit chance against a target (the Binary Edges' Mark) and a damage
 * multiplier for a move the attacker makes right now (the Edges' single blade while the other is thrown, the
 * Driftweave's aerial attacks during Drift), which may depend on the target (an Aligned enemy takes more from
 * abilities). {@link HitResolver} asks them for every hit. Server only.
 *
 * <p>For the accessories (GDD 5.2): a bonus to the crit multiplier (the Perihelion Loop's +25% after a dash, the
 * multiplier's "gear" term) and plunges that grow past their cap (the Gravity Loop).
 */
public final class HitModifiers {
    /** One weapon's say in a hit. Each part defaults to no change. */
    public interface Modifier {
        /** Added to the attacker's crit chance for a hit on {@code target} (the chance stays capped at 50%). */
        default double critChanceBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
            return 0.0;
        }

        /** Multiplies the damage of a hit of {@code move} by {@code attacker}. */
        default double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move) {
            return 1.0;
        }

        /**
         * Multiplies the damage of a hit of {@code move} by {@code attacker} on {@code target} (an Aligned enemy takes
         * more from abilities). Defaults to {@link #damageMultiplier(ServerPlayer, MoveInstance)}.
         */
        default double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move, @Nullable LivingEntity target) {
            return damageMultiplier(attacker, move);
        }

        /**
         * Added to the attacker's crit multiplier for a crit on {@code target} (GDD 3.4: 1.5 + 0.01 x Power + gear; the
         * multiplier stays capped at 2.5).
         */
        default double critMultiplierBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
            return 0.0;
        }

        /** True if {@code attacker}'s plunges keep growing with the height past their motion value cap (the Gravity Loop). */
        default boolean uncapsPlunge(@Nullable ServerPlayer attacker) {
            return false;
        }
    }

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();

    private HitModifiers() {
    }

    public static void register(Modifier modifier) {
        MODIFIERS.add(modifier);
    }

    /** Every modifier's crit chance bonus against {@code target}, added up. */
    public static double critChanceBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
        double bonus = 0.0;
        for (Modifier modifier : MODIFIERS) {
            bonus += modifier.critChanceBonus(attacker, target);
        }
        return bonus;
    }

    /** Every modifier's damage multiplier for {@code move}, multiplied together. */
    public static double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move) {
        double multiplier = 1.0;
        for (Modifier modifier : MODIFIERS) {
            multiplier *= modifier.damageMultiplier(attacker, move);
        }
        return multiplier;
    }

    /** Every modifier's damage multiplier for {@code move} on {@code target}, multiplied together. */
    public static double damageMultiplier(@Nullable ServerPlayer attacker, @Nullable MoveInstance move, @Nullable LivingEntity target) {
        double multiplier = 1.0;
        for (Modifier modifier : MODIFIERS) {
            multiplier *= modifier.damageMultiplier(attacker, move, target);
        }
        return multiplier;
    }

    /** Every modifier's crit multiplier bonus against {@code target}, added up. */
    public static double critMultiplierBonus(@Nullable ServerPlayer attacker, @Nullable LivingEntity target) {
        double bonus = 0.0;
        for (Modifier modifier : MODIFIERS) {
            bonus += modifier.critMultiplierBonus(attacker, target);
        }
        return bonus;
    }

    /** True if any modifier lifts {@code attacker}'s plunge cap. */
    public static boolean uncapsPlunge(@Nullable ServerPlayer attacker) {
        for (Modifier modifier : MODIFIERS) {
            if (modifier.uncapsPlunge(attacker)) {
                return true;
            }
        }
        return false;
    }

    /** Test only: every modifier now (the mod's own are registered in the test run too), to put back afterwards. */
    static List<Modifier> snapshotForTests() {
        return List.copyOf(MODIFIERS);
    }

    /** Test only: exactly these modifiers from now on. */
    static void restoreForTests(List<Modifier> modifiers) {
        MODIFIERS.clear();
        MODIFIERS.addAll(modifiers);
    }

    /** Test only: forget every modifier. */
    static void clearForTests() {
        MODIFIERS.clear();
    }
}
