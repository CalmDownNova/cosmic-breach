package com.cosmicbreach.guardian;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * How a guardian chooses its next attack (Prism Colossus design v1, "Choosing attacks"; the Heliarch's rules in
 * GDD 7.3 are the same shape): weighted, each with a cooldown from its start, never the same attack twice in a row
 * unless it says it may repeat. An option marked as priority (a timed signature attack, or one answering a
 * condition, such as a player hugging it) goes first whenever it is ready. Pure.
 *
 * @param <A> the guardian's attack type
 */
public final class AttackPicker<A> {
    /**
     * One attack the guardian could make now (its condition already holds). {@code weight} is its share among
     * ordinary options; {@code cooldownTicks} run from when it starts.
     */
    public record Option<A>(A attack, double weight, int cooldownTicks, boolean repeatable, boolean priority) {
        public static <A> Option<A> weighted(A attack, double weight, int cooldownTicks) {
            return new Option<>(attack, weight, cooldownTicks, false, false);
        }

        public Option<A> asRepeatable() {
            return new Option<>(attack, weight, cooldownTicks, true, priority);
        }

        public Option<A> asPriority() {
            return new Option<>(attack, weight, cooldownTicks, repeatable, true);
        }
    }

    private final Map<A, Long> readyAt = new HashMap<>();
    private A last;

    /** True if {@code attack}'s cooldown is over at {@code now}. */
    public boolean ready(A attack, long now) {
        return now >= readyAt.getOrDefault(attack, Long.MIN_VALUE);
    }

    /** Ticks until {@code attack} is ready (0 if it is). */
    public long cooldownLeft(A attack, long now) {
        Long ready = readyAt.get(attack);
        return ready == null ? 0 : Math.max(0, ready - now);
    }

    /** The attack made last, or null. */
    public A last() {
        return last;
    }

    /**
     * Chooses among {@code options} at {@code now}, or null when none may go. {@code random} gives numbers in
     * [0, 1) for the weighted draw. Does not start the cooldown: call {@link #used} when the attack starts.
     */
    public A pick(List<Option<A>> options, long now, DoubleSupplier random) {
        List<Option<A>> allowed = new ArrayList<>();
        for (Option<A> option : options) {
            if (!ready(option.attack(), now) || (option.attack().equals(last) && !option.repeatable())) {
                continue;
            }
            if (option.priority()) {
                return option.attack();
            }
            if (option.weight() > 0) {
                allowed.add(option);
            }
        }
        if (allowed.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (Option<A> option : allowed) {
            total += option.weight();
        }
        double roll = Math.max(0.0, Math.min(0.999999, random.getAsDouble())) * total;
        for (Option<A> option : allowed) {
            roll -= option.weight();
            if (roll < 0) {
                return option.attack();
            }
        }
        return allowed.get(allowed.size() - 1).attack();
    }

    /** {@code attack} starts at {@code now}: its cooldown begins and it becomes the last attack. */
    public void used(A attack, int cooldownTicks, long now) {
        readyAt.put(attack, now + cooldownTicks);
        last = attack;
    }

    /** Sets an attack's cooldown to run until {@code tick} without making it the last attack (an opening delay). */
    public void holdUntil(A attack, long tick) {
        readyAt.put(attack, tick);
    }

    /** Forgets every cooldown and the last attack (a reset). */
    public void clear() {
        readyAt.clear();
        last = null;
    }
}
