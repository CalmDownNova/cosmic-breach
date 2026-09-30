package com.cosmicbreach.guardian;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A guardian's targeting (Prism Colossus design v1): the player who dealt it the most damage in the last
 * {@value #MEMORY_TICKS} ticks, else the nearest; re-checked every {@value #CHECK_TICKS} ticks. Pure: the guardian
 * records damage and asks with the players it may target.
 */
public final class BossTargeting {
    /** Damage older than this no longer counts toward the target (10 s). */
    public static final int MEMORY_TICKS = 200;
    /** How often the guardian re-picks its target (2 s). */
    public static final int CHECK_TICKS = 40;

    /** A player the guardian may target: who, and its squared distance. */
    public record Candidate(UUID id, double distanceSqr) {
    }

    private record Hit(long tick, double amount) {
    }

    private final Map<UUID, ArrayDeque<Hit>> hits = new HashMap<>();

    /** {@code player} dealt {@code amount} at {@code now}. */
    public void recordDamage(UUID player, double amount, long now) {
        if (amount > 0) {
            hits.computeIfAbsent(player, id -> new ArrayDeque<>()).addLast(new Hit(now, amount));
        }
    }

    /** Damage {@code player} dealt within the memory at {@code now}. */
    public double recentDamage(UUID player, long now) {
        ArrayDeque<Hit> list = hits.get(player);
        if (list == null) {
            return 0.0;
        }
        expire(list, now);
        double sum = 0.0;
        for (Hit hit : list) {
            sum += hit.amount();
        }
        return sum;
    }

    /** The target among {@code candidates}: most recent damage, else the nearest; empty if there are none. */
    public Optional<UUID> choose(Collection<Candidate> candidates, long now) {
        Candidate best = null;
        double bestDamage = 0.0;
        for (Candidate c : candidates) {
            double d = recentDamage(c.id(), now);
            if (d > bestDamage + 1e-9 || (d > 0 && Math.abs(d - bestDamage) <= 1e-9 && best != null && c.distanceSqr() < best.distanceSqr())) {
                best = c;
                bestDamage = d;
            }
        }
        if (best != null) {
            return Optional.of(best.id());
        }
        Candidate nearest = null;
        for (Candidate c : candidates) {
            if (nearest == null || c.distanceSqr() < nearest.distanceSqr()) {
                nearest = c;
            }
        }
        return nearest == null ? Optional.empty() : Optional.of(nearest.id());
    }

    /** True on the ticks the target is re-checked, counted from {@code since}. */
    public static boolean checkDue(long now, long since) {
        return Math.floorMod(now - since, CHECK_TICKS) == 0;
    }

    /** Forgets everything (a reset). */
    public void clear() {
        hits.clear();
    }

    private static void expire(ArrayDeque<Hit> list, long now) {
        Iterator<Hit> it = list.iterator();
        while (it.hasNext()) {
            if (now - it.next().tick() >= MEMORY_TICKS) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
